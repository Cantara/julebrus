package no.cantara.julebrus.admin;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The organiser can see who still owes a consent or a team answer, and the chores count them. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:adminnag;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminNagTest {

    static final String ADMIN = "dev@cantara.no";
    static final String CANTARA = "00000000-0000-4000-8000-000000000001";

    @Autowired
    MockMvc mvc;

    @Test
    void whoOwesAConsentAndWhoOwesATeam() throws Exception {
        String silent = "nag-silent@cantara.no", undecided = "nag-undecided@cantara.no", later = "nag-later@cantara.no", fan = "nag-fan@cantara.no";
        ConsentSupport.accept(mvc, ADMIN);
        mvc.perform(put("/api/me/teams").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON).content("{\"teamIds\":[]}")).andExpect(status().isOk());
        // Signed in, never consented.
        mvc.perform(get("/api/me").header("X-Dev-User", silent)).andExpect(status().isOk());
        // Consented, team question open.
        ConsentSupport.accept(mvc, undecided);
        // Consented, answered "later" and answered Cantara.
        ConsentSupport.accept(mvc, later);
        mvc.perform(put("/api/me/teams").header("X-Dev-User", later).contentType(APPLICATION_JSON).content("{\"teamIds\":[]}")).andExpect(status().isOk());
        ConsentSupport.accept(mvc, fan);
        mvc.perform(put("/api/me/teams").header("X-Dev-User", fan).contentType(APPLICATION_JSON)
                .content("{\"teamIds\":[\"%s\"]}".formatted(CANTARA))).andExpect(status().isOk());

        mvc.perform(get("/api/admin/people").header("X-Dev-User", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='%s')].consented".formatted(silent)).value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.email=='%s')].teamsChosen".formatted(silent)).value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.email=='%s')].teamsChosen".formatted(undecided)).value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.email=='%s')].teamsChosen".formatted(later)).value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.email=='%s')].teams.length()".formatted(later)).value(hasItem(0)))
                .andExpect(jsonPath("$[?(@.email=='%s')].teams[0]".formatted(fan)).value(hasItem("Cantara")));
        mvc.perform(get("/api/me").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$.chores.noConsent").value(1))
                .andExpect(jsonPath("$.chores.noTeam").value(1));
    }
}
