package no.cantara.julebrus.beer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The beer page: season numbers and the room's words, with two guards. Nothing derived from
 * other people's rolls before three of them, and the notes only once the reader has rolled
 * the beer. Notes come without names, a note from an unrevealed table stays at the table,
 * and earlier years are summed up per season.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:beerpage;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BeerPageTest {

    static final String ADMIN = "dev@cantara.no";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void tasteFirstReadAfterwards() throws Exception {
        String anna = "page-anna@cantara.no", bo = "page-bo@cantara.no", cato = "page-cato@cantara.no", dina = "page-dina@cantara.no";
        for (String who : List.of(ADMIN, anna, bo, cato, dina)) ConsentSupport.accept(mvc, who);
        // A name earlier years know, so the history block has something to say.
        String beer = call(ADMIN, "/api/beers", Map.of("name", "7 Fjell 7 Sorter Christmas Ale", "brewery", "7 Fjell", "abv", 6.5)).get("id").asText();

        // Nobody has rolled: facts and history only, everything else closed.
        mvc.perform(get("/api/beers/" + beer).header("X-Dev-User", anna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beer.name").value("7 Fjell 7 Sorter Christmas Ale"))
                .andExpect(jsonPath("$.season.tastings").value(0))
                .andExpect(jsonPath("$.season.rank").doesNotExist())
                .andExpect(jsonPath("$.season.histogram").doesNotExist())
                .andExpect(jsonPath("$.tags").doesNotExist())
                .andExpect(jsonPath("$.notesOpen").value(false))
                .andExpect(jsonPath("$.notesLocked").value("NEED_ROLL"))
                .andExpect(jsonPath("$.minShared").value(3))
                .andExpect(jsonPath("$.history[0].year").value(2019))
                .andExpect(jsonPath("$.history[0].ratings").value(3))
                .andExpect(jsonPath("$.history[0].mean").value(3.6));
        mvc.perform(get("/api/beers/00000000-0000-4000-8000-00000000dead").header("X-Dev-User", anna)).andExpect(status().isNotFound());

        // Bo rolls with notes; Anna rolls too. Two rolls: Anna has rolled but it is still too few to share.
        call(bo, "/api/tastings", Map.of("beerId", beer, "score", 5, "notes", "privat kommentar",
                "aroma", "appelsinskall og nellik", "taste", "karamell", "tags", List.of("krydret", "søt")));
        call(anna, "/api/tastings", Map.of("beerId", beer, "score", 3, "appearance", "mørk", "tags", List.of("krydret")));
        mvc.perform(get("/api/beers/" + beer).header("X-Dev-User", anna))
                .andExpect(jsonPath("$.season.tastings").value(2))
                .andExpect(jsonPath("$.mine.score").value(3))
                .andExpect(jsonPath("$.notesOpen").value(false))
                .andExpect(jsonPath("$.notesLocked").value("NEED_MORE"))
                .andExpect(jsonPath("$.tags").doesNotExist())
                .andExpect(jsonPath("$.season.histogram").doesNotExist());

        // Cato rolls at an open table with Dina, who has not rolled yet: Cato's note is the table's until the reveal.
        JsonNode table = call(cato, "/api/sessions", null);
        String tid = table.get("id").asText();
        call(dina, "/api/sessions/join", Map.of("code", table.get("joinCode").asText()));
        call(cato, "/api/sessions/" + tid + "/beers", Map.of("beerId", beer));
        call(cato, "/api/tastings", Map.of("beerId", beer, "sessionId", tid, "score", 4, "finish", "lang og tørr", "tags", List.of("tørr")));

        MvcResult res = mvc.perform(get("/api/beers/" + beer).header("X-Dev-User", anna))
                .andExpect(jsonPath("$.season.tastings").value(3))
                .andExpect(jsonPath("$.season.people").value(3))
                .andExpect(jsonPath("$.season.rank").value(1))
                .andExpect(jsonPath("$.season.histogram[2]").value(1))
                .andExpect(jsonPath("$.season.histogram[3]").value(1))
                .andExpect(jsonPath("$.season.histogram[4]").value(1))
                .andExpect(jsonPath("$.tags[0].tag").value("krydret"))
                .andExpect(jsonPath("$.tags[0].count").value(2))
                .andExpect(jsonPath("$.tags", hasSize(3)))
                .andExpect(jsonPath("$.notesOpen").value(true))
                .andExpect(jsonPath("$.notes", hasSize(1)))
                .andExpect(jsonPath("$.notes[0].aroma").value("appelsinskall og nellik"))
                .andReturn();
        String body = res.getResponse().getContentAsString();
        // No name, no e-mail, no time, no free comment on the shared notes.
        assertThat(body).doesNotContain("page-bo").doesNotContain("@cantara.no").doesNotContain("privat kommentar")
                .doesNotContain("\"userId\"").doesNotContain("lang og tørr");
        // Dina has not rolled: she gets the tags and the histogram, not the notes.
        mvc.perform(get("/api/beers/" + beer).header("X-Dev-User", dina))
                .andExpect(jsonPath("$.notesOpen").value(false))
                .andExpect(jsonPath("$.notesLocked").value("NEED_ROLL"))
                .andExpect(jsonPath("$.notes", hasSize(0)))
                .andExpect(jsonPath("$.tags", hasSize(3)));
        // Dina rolls: the table reveals, and Cato's note joins the shared ones for everyone who rolled.
        call(dina, "/api/tastings", Map.of("beerId", beer, "sessionId", tid, "score", 2));
        mvc.perform(get("/api/beers/" + beer).header("X-Dev-User", anna))
                .andExpect(jsonPath("$.notes", hasSize(2)));
        mvc.perform(get("/api/beers/" + beer).header("X-Dev-User", dina))
                .andExpect(jsonPath("$.notesOpen").value(true))
                .andExpect(jsonPath("$.notes", hasSize(3)))
                .andExpect(jsonPath("$.mine.score").value(2));

        // A duel shows up in the record.
        String other = call(ADMIN, "/api/beers", Map.of("name", "Sideøl", "abv", 6.5)).get("id").asText();
        call(cato, "/api/sessions/" + tid + "/beers", Map.of("beerId", other));
        call(cato, "/api/tastings", Map.of("beerId", other, "sessionId", tid, "score", 2));
        call(cato, "/api/duels", Map.of("sessionId", tid, "winnerBeerId", beer, "loserBeerId", other));
        mvc.perform(get("/api/beers/" + beer).header("X-Dev-User", cato))
                .andExpect(jsonPath("$.duels.wins").value(1))
                .andExpect(jsonPath("$.duels.losses").value(0))
                .andExpect(jsonPath("$.duels.rank").value(1));
    }

    private JsonNode call(String who, String path, Object body) throws Exception {
        var req = post(path).header("X-Dev-User", who).contentType(APPLICATION_JSON);
        if (body != null) req = req.content(json.writeValueAsString(body));
        MvcResult res = mvc.perform(req).andReturn();
        assertThat(res.getResponse().getStatus()).as(path + " -> " + res.getResponse().getContentAsString()).isLessThan(300);
        String out = res.getResponse().getContentAsString();
        return out.isEmpty() ? null : json.readTree(out);
    }
}
