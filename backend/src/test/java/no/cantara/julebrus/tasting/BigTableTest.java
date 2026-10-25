package no.cantara.julebrus.tasting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A party of eight is a different evening: the daily limit at such a table is twelve, elsewhere it stays five. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:bigtable;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BigTableTest {

    static final String ADMIN = "dev@cantara.no";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void eightAtTheTableLiftsTheLimitToTwelveForThatTableOnly() throws Exception {
        String host = "big-host@cantara.no";
        List<String> guests = new ArrayList<>();
        for (int i = 1; i <= 7; i++) guests.add("big-guest" + i + "@cantara.no");
        ConsentSupport.accept(mvc, ADMIN);
        ConsentSupport.accept(mvc, host);
        for (String g : guests) ConsentSupport.accept(mvc, g);

        JsonNode session = json.readTree(mvc.perform(post("/api/sessions").header("X-Dev-User", host))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String sid = session.get("id").asText(), code = session.get("joinCode").asText();
        // Seven guests join: the table is eight, so the ordinary five no longer applies there.
        for (String g : guests.subList(0, 6)) join(g, code);
        mvc.perform(get("/api/me").header("X-Dev-User", host)).andExpect(jsonPath("$.status.maxPerDay").value(5))
                .andExpect(jsonPath("$.status.bigTable").value(false));
        join(guests.get(6), code);
        mvc.perform(get("/api/me").header("X-Dev-User", host)).andExpect(jsonPath("$.status.maxPerDay").value(12))
                .andExpect(jsonPath("$.status.bigTable").value(true))
                .andExpect(jsonPath("$.season.bigTableSize").value(8))
                .andExpect(jsonPath("$.season.maxTastingsPerDayBigTable").value(12));

        List<String> beers = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            String id = json.readTree(mvc.perform(post("/api/beers").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                            .content("{\"name\":\"Selskapsøl %d\",\"abv\":6.5}".formatted(i))).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString()).get("id").asText();
            beers.add(id);
            if (i <= 6) {
                mvc.perform(post("/api/sessions/" + sid + "/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\"}".formatted(id))).andExpect(status().isOk());
            }
        }
        // Six rolls at the big table: the sixth, over the ordinary limit, is fine here.
        for (String beer : beers.subList(0, 6)) {
            mvc.perform(post("/api/tastings").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                    .content("{\"beerId\":\"%s\",\"sessionId\":\"%s\",\"score\":4}".formatted(beer, sid))).andExpect(status().isCreated());
        }
        // A seventh beer alone the same evening is over the five that still hold away from the big table.
        mvc.perform(post("/api/tastings").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\",\"score\":4}".formatted(beers.get(6))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DAILY_LIMIT"));
        mvc.perform(get("/api/me").header("X-Dev-User", host)).andExpect(jsonPath("$.status.tastingsToday").value(6));
    }

    private void join(String who, String code) throws Exception {
        mvc.perform(post("/api/sessions/join").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))).andExpect(status().isOk());
    }
}
