package no.cantara.julebrus.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.cantara.julebrus.ConsentSupport;
import no.cantara.julebrus.photo.PhotoFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "I kveld": the day's wall shows who tasted what, the tables, approved labels and the
 * blind rounds, with cheers on each line, and never a person's dice. A day average per beer
 * appears only from three rolls, and someone who withdrew consent is gone from it.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:feed;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FeedTest {

    static final String ADMIN = "dev@cantara.no";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void theWallShowsTheDayWithoutAnyonesDice() throws Exception {
        String anna = "feed-anna@cantara.no", bo = "feed-bo@cantara.no", cato = "feed-cato@cantara.no", gone = "feed-gone@cantara.no";
        for (String who : List.of(ADMIN, anna, bo, cato, gone)) ConsentSupport.accept(mvc, who);
        String x = beer("Kveldsøl X"), y = beer("Kveldsøl Y");

        // Anna rolls X alone; Bo hosts Cato and both roll X at the table; the fourth person rolls Y and withdraws.
        rate(anna, x, null, 6);
        JsonNode table = call(bo, "/api/sessions", null);
        String tid = table.get("id").asText();
        call(cato, "/api/sessions/join", Map.of("code", table.get("joinCode").asText()));
        call(bo, "/api/sessions/" + tid + "/beers", Map.of("beerId", x));
        call(bo, "/api/sessions/" + tid + "/beers", Map.of("beerId", y));
        rate(bo, x, tid, 2);
        rate(cato, x, tid, 4);
        rate(gone, y, null, 1);
        mvc.perform(post("/api/consent/withdraw").header("X-Dev-User", gone)).andExpect(status().isOk());

        // A blind round at the table, revealed by Cato's (wrong) guess.
        JsonNode round = call(bo, "/api/sessions/" + tid + "/blind", Map.of("answerBeerId", x, "optionBeerIds", List.of(x, y)));
        call(cato, "/api/sessions/" + tid + "/blind/" + round.get("id").asText() + "/guess", Map.of("beerId", y));

        // An approved label.
        String photo = json.readTree(mvc.perform(multipart("/api/beers/" + y + "/photos")
                        .file(new MockMultipartFile("file", "label.jpg", "image/jpeg", PhotoFixtures.jpeg(300, 200))).header("X-Dev-User", anna))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/admin/photos/" + photo + "/decision").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}")).andExpect(status().isOk());

        MvcResult res = mvc.perform(get("/api/feed").header("X-Dev-User", anna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tastings").value(3))
                .andExpect(jsonPath("$.people").value(3))
                .andExpect(jsonPath("$.openTables", hasSize(1)))
                .andExpect(jsonPath("$.openTables[0].hostName").value("feed-bo"))
                .andExpect(jsonPath("$.openTables[0].members").value(2))
                .andExpect(jsonPath("$.openTables[0].mine").value(false))
                .andExpect(jsonPath("$.emojis[0]").value("🍻"))
                .andReturn();
        String body = res.getResponse().getContentAsString();
        JsonNode feed = json.readTree(body);
        // Never a score, never an e-mail, and the withdrawn person is not there.
        assertThat(body).doesNotContain("\"score\"").doesNotContain("@cantara.no").doesNotContain("feed-gone");
        List<String> kinds = feed.get("entries").findValues("kind").stream().map(JsonNode::asText).toList();
        assertThat(kinds).containsExactlyInAnyOrder("TASTING", "TASTING", "TASTING", "TABLE", "BLIND", "PHOTO");
        JsonNode annas = entry(feed, "TASTING", "feed-anna");
        assertThat(annas.get("me").asBoolean()).isTrue();
        assertThat(annas.get("beerName").asText()).isEqualTo("Kveldsøl X");
        assertThat(annas.get("tableId").isNull()).isTrue();
        JsonNode catos = entry(feed, "TASTING", "feed-cato");
        assertThat(catos.get("tableHost").asText()).isEqualTo("feed-bo");
        assertThat(catos.get("tableSize").asInt()).isEqualTo(2);
        JsonNode blind = entry(feed, "BLIND", "feed-bo");
        assertThat(blind.get("correct").asInt()).isZero();
        assertThat(blind.get("guesses").asInt()).isEqualTo(1);
        assertThat(blind.get("beerName").asText()).isEqualTo("Kveldsøl X");
        // X was rolled three times today, so its day average shows; Y only by the withdrawn person, so it is gone too.
        assertThat(feed.get("beers")).hasSize(1);
        assertThat(feed.get("beers").get(0).get("name").asText()).isEqualTo("Kveldsøl X");
        assertThat(feed.get("beers").get(0).get("count").asInt()).isEqualTo(3);
        assertThat(feed.get("beers").get(0).get("average").asDouble()).isEqualTo(4.0);
        // Another day is empty.
        mvc.perform(get("/api/feed?day=2020-01-01").header("X-Dev-User", anna))
                .andExpect(jsonPath("$.entries", hasSize(0))).andExpect(jsonPath("$.tastings").value(0));

        // Cheers: Bo cheers Anna's roll, Cato too, and Anna sees the tally without having cheered herself.
        String target = annas.get("id").asText();
        mvc.perform(put("/api/feed/reactions").header("X-Dev-User", bo).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("kind", "TASTING", "targetId", target, "emoji", "🍻"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].emoji").value("🍻"))
                .andExpect(jsonPath("$[0].count").value(1)).andExpect(jsonPath("$[0].mine").value(true));
        mvc.perform(put("/api/feed/reactions").header("X-Dev-User", cato).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("kind", "TASTING", "targetId", target, "emoji", "🍻"))))
                .andExpect(jsonPath("$[0].count").value(2));
        mvc.perform(put("/api/feed/reactions").header("X-Dev-User", cato).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("kind", "TASTING", "targetId", target, "emoji", "🔥"))))
                .andExpect(jsonPath("$", hasSize(2)));
        JsonNode again = json.readTree(mvc.perform(get("/api/feed").header("X-Dev-User", anna)).andReturn().getResponse().getContentAsString());
        JsonNode cheered = entry(again, "TASTING", "feed-anna").get("reactions");
        assertThat(cheered.get(0).get("count").asInt()).isEqualTo(2);
        assertThat(cheered.get(0).get("mine").asBoolean()).isFalse();
        // A second tap takes the cheer back.
        mvc.perform(put("/api/feed/reactions").header("X-Dev-User", bo).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("kind", "TASTING", "targetId", target, "emoji", "🍻"))))
                .andExpect(jsonPath("$[0].count").value(1)).andExpect(jsonPath("$[0].mine").value(false));
        // Only the fixed set, only things that are on the wall.
        mvc.perform(put("/api/feed/reactions").header("X-Dev-User", bo).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("kind", "TASTING", "targetId", target, "emoji", "💩"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("FEED_EMOJI"));
        mvc.perform(put("/api/feed/reactions").header("X-Dev-User", bo).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("kind", "PHOTO", "targetId", target, "emoji", "🍻"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FEED_TARGET"));
    }

    private static JsonNode entry(JsonNode feed, String kind, String name) {
        for (JsonNode e : feed.get("entries")) {
            if (e.get("kind").asText().equals(kind) && name.equals(e.path("displayName").asText(null))) return e;
        }
        throw new AssertionError("No " + kind + " entry by " + name + " in " + feed);
    }

    private String beer(String name) throws Exception {
        return call(ADMIN, "/api/beers", Map.of("name", name, "abv", 6.5)).get("id").asText();
    }

    private void rate(String who, String beer, String session, int score) throws Exception {
        Map<String, Object> body = session == null ? Map.of("beerId", beer, "score", score)
                : Map.of("beerId", beer, "sessionId", session, "score", score);
        call(who, "/api/tastings", body);
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
