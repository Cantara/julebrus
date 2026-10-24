package no.cantara.julebrus.weight;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A person whose earlier seasons come in through an admin-linked historic name is a
 * veteran, not a rookie, and the weight already stored for this season follows.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:veteran;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class VeteranWeightTest {

    @Autowired
    MockMvc mvc;

    @Test
    void linkingAHistoricNameTurnsARookieIntoAVeteran() throws Exception {
        String who = "veteran@cantara.no", admin = "dev@cantara.no";
        ConsentSupport.accept(mvc, who);
        ConsentSupport.accept(mvc, admin);

        // First look: nothing links this login to the past, so 0.5, and that row is stored.
        String me = mvc.perform(get("/api/me").header("X-Dev-User", who))
                .andExpect(jsonPath("$.weight.weight").value(0.5))
                .andExpect(jsonPath("$.weight.reason").value("Første sesong. Full vekt neste år etter en vanlig sesong."))
                .andReturn().getResponse().getContentAsString();
        String userId = me.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");

        // An admin links "Totto" (11 votes in 2019 in the bundled history) to this login.
        mvc.perform(post("/api/admin/history/raters/link").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Totto\",\"userId\":\"%s\"}".formatted(userId)))
                .andExpect(status().isOk());

        // The stored weight is re-decided on the next read: a normal 2019 gives full weight.
        mvc.perform(get("/api/me").header("X-Dev-User", who))
                .andExpect(jsonPath("$.weight.weight").value(1.0))
                .andExpect(jsonPath("$.weight.reason").value("Vanlig sesong i 2019."));

        // An admin override is final: the policy leaves it alone afterwards.
        mvc.perform(post("/api/admin/raters/override").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"weight\":0.5,\"reason\":\"Prøver seg\"}".formatted(who)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me").header("X-Dev-User", who))
                .andExpect(jsonPath("$.weight.weight").value(0.5))
                .andExpect(jsonPath("$.weight.reason").value("Prøver seg"));
    }
}
