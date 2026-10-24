package no.cantara.julebrus.tip;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The tipping game on its own H2 database: the rules on placing a tip, changing and
 * withdrawing it, and the league and award in the finale preview.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:tips;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TipTest {

    @Autowired
    MockMvc mvc;

    static String idOf(String json) {
        return json.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void tipsFollowTheRulesAndScoreAgainstThePodium() throws Exception {
        String admin = "dev@cantara.no", ida = "ida@cantara.no", jon = "jon@cantara.no";
        for (String who : new String[]{admin, ida, jon}) ConsentSupport.accept(mvc, who);

        String[] beer = new String[4];
        for (int i = 0; i < 3; i++) {
            beer[i] = idOf(mvc.perform(post("/api/beers").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                    .content("{\"name\":\"Tippeøl %d\",\"abv\":6.5}".formatted(i + 1))).andReturn().getResponse().getContentAsString());
        }
        // Added by a non-admin: pending until approved, so not in the ranking and not tippable.
        beer[3] = idOf(mvc.perform(post("/api/beers").header("X-Dev-User", ida).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Ventende\",\"abv\":6.5}")).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/beers").header("X-Dev-User", ida))
                .andExpect(jsonPath("$[?(@.name=='Tippeøl 1')].counts").value(true))
                .andExpect(jsonPath("$[?(@.name=='Ventende')].counts").value(false));

        mvc.perform(get("/api/tips/me").header("X-Dev-User", ida))
                .andExpect(jsonPath("$.tip").doesNotExist()).andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.tipped").value(0));

        String tip = "{\"firstBeerId\":\"%s\",\"secondBeerId\":\"%s\",\"thirdBeerId\":\"%s\"}";
        mvc.perform(put("/api/tips").header("X-Dev-User", ida).contentType(APPLICATION_JSON)
                        .content(tip.formatted(beer[0], beer[0], beer[1])))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TIP_DUPLICATE"));
        mvc.perform(put("/api/tips").header("X-Dev-User", ida).contentType(APPLICATION_JSON)
                        .content(tip.formatted(beer[0], beer[1], beer[3])))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TIP_BEER"));

        // Ida tips 1-2-3 and changes her mind to 2-1-3; Jon tips 3-2-1.
        mvc.perform(put("/api/tips").header("X-Dev-User", ida).contentType(APPLICATION_JSON)
                        .content(tip.formatted(beer[0], beer[1], beer[2])))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tip.podium[0].name").value("Tippeøl 1")).andExpect(jsonPath("$.tipped").value(1));
        mvc.perform(put("/api/tips").header("X-Dev-User", ida).contentType(APPLICATION_JSON)
                        .content(tip.formatted(beer[1], beer[0], beer[2])))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tip.podium[0].name").value("Tippeøl 2")).andExpect(jsonPath("$.tipped").value(1));
        mvc.perform(put("/api/tips").header("X-Dev-User", jon).contentType(APPLICATION_JSON)
                        .content(tip.formatted(beer[2], beer[1], beer[0])))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipped").value(2));

        // Withdrawing and tipping again keeps one tip per person.
        mvc.perform(delete("/api/tips").header("X-Dev-User", jon)).andExpect(jsonPath("$.tipped").value(1));
        mvc.perform(put("/api/tips").header("X-Dev-User", jon).contentType(APPLICATION_JSON)
                        .content(tip.formatted(beer[2], beer[1], beer[0])))
                .andExpect(jsonPath("$.tipped").value(2));

        // Before the reveal the finale only says how many tipped.
        mvc.perform(get("/api/finale").header("X-Dev-User", ida))
                .andExpect(jsonPath("$.tips").doesNotExist()).andExpect(jsonPath("$.tipped").value(2));

        // The crowd makes beer 1 win, 2 second, 3 third: Ida 1+1+3 = 5, Jon 1+3+1 = 5, Ida tipped first.
        for (String who : new String[]{admin, ida, jon}) {
            int[] score = {6, 4, 2};
            for (int i = 0; i < 3; i++) {
                mvc.perform(post("/api/tastings").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\",\"score\":%d}".formatted(beer[i], score[i]))).andExpect(status().isCreated());
            }
        }
        mvc.perform(get("/api/finale").param("preview", "true").header("X-Dev-User", admin))
                .andExpect(jsonPath("$.podium[0].name").value("Tippeøl 1"))
                .andExpect(jsonPath("$.tips", hasSize(2)))
                .andExpect(jsonPath("$.tips[0].name").value("ida"))
                .andExpect(jsonPath("$.tips[0].points").value(5))
                .andExpect(jsonPath("$.tips[0].tip", contains("Tippeøl 2", "Tippeøl 1", "Tippeøl 3")))
                .andExpect(jsonPath("$.tips[1].name").value("jon"))
                .andExpect(jsonPath("$.tips[1].points").value(5))
                .andExpect(jsonPath("$.awards[?(@.id=='TIPSTER')].winnerName").value("ida"))
                .andExpect(jsonPath("$.awards[?(@.id=='TIPSTER')].detail").value("5 av 9 poeng"));
    }
}
