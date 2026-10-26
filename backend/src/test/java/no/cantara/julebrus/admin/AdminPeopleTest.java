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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The organiser's list of everyone signed in, and the weight override that fixes a missing-data mistake
 * (a veteran at 0.5). Consent rules hold: no consent, no weight, no scores, no earlier seasons, no override.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:adminpeople;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminPeopleTest {

    private static final String ADMIN = "dev@cantara.no";

    @Autowired
    MockMvc mvc;

    @Test
    void onlyAdminsSeeThePeopleList() throws Exception {
        ConsentSupport.accept(mvc, "plain@cantara.no");
        mvc.perform(get("/api/admin/people").header("X-Dev-User", "plain@cantara.no")).andExpect(status().isForbidden());
    }

    @Test
    void everyoneIsListedButOnlyConsentedPeopleHaveAWeight() throws Exception {
        ConsentSupport.accept(mvc, ADMIN);
        ConsentSupport.accept(mvc, "leid@cantara.no");
        // Signs in (a users row exists) but never consents.
        mvc.perform(get("/api/me").header("X-Dev-User", "silent@cantara.no")).andExpect(status().isOk());

        mvc.perform(get("/api/admin/people").header("X-Dev-User", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='leid@cantara.no')].consented").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.email=='leid@cantara.no')].weight").value(hasItem(0.5)))
                .andExpect(jsonPath("$[?(@.email=='silent@cantara.no')].consented").value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.email=='silent@cantara.no')].weight").value(hasItem((Object) null)))
                .andExpect(jsonPath("$[?(@.email=='silent@cantara.no')].historyYears.length()").value(hasItem(0)));
    }

    @Test
    void anOverrideGivesAVeteranFullWeightAndSticksEvenWhenTheNameIsLinkedLater() throws Exception {
        ConsentSupport.accept(mvc, ADMIN);
        ConsentSupport.accept(mvc, "leid2@cantara.no");
        mvc.perform(get("/api/me").header("X-Dev-User", "leid2@cantara.no")).andExpect(status().isOk());

        mvc.perform(post("/api/admin/raters/override").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"leid2@cantara.no\",\"weight\":1.0,\"reason\":\"Mangler data de siste årene\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/people").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$[?(@.email=='leid2@cantara.no')].weight").value(hasItem(1.0)))
                .andExpect(jsonPath("$[?(@.email=='leid2@cantara.no')].overridden").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.email=='leid2@cantara.no')].reason").value(hasItem("Mangler data de siste årene")));
    }

    @Test
    void noWeightIsKeptForSomeoneWithoutConsent() throws Exception {
        ConsentSupport.accept(mvc, ADMIN);
        mvc.perform(get("/api/me").header("X-Dev-User", "nope@cantara.no")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/raters/override").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"nope@cantara.no\",\"weight\":1.0,\"reason\":\"x\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/raters/override").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"never-signed-in@cantara.no\",\"weight\":1.0,\"reason\":\"x\"}"))
                .andExpect(status().isNotFound());
    }
}
