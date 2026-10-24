package no.cantara.julebrus;

import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.consent.UserConsent;
import no.cantara.julebrus.consent.UserConsentRepository;
import no.cantara.julebrus.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consent is enforced on the server for every API call except the ones needed to give it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ConsentTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserService users;
    @Autowired
    UserConsentRepository consents;

    @Test
    void blockedBeforeAcceptAllowedAfter() throws Exception {
        String who = "consent-flow@cantara.no";

        mvc.perform(get("/api/beers").header("X-Dev-User", who)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(post("/api/sessions").header("X-Dev-User", who)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(get("/api/history/me").header("X-Dev-User", who)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/raters").header("X-Dev-User", who)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        // The exceptions: who am I, the consent itself, the public config.
        mvc.perform(get("/api/me").header("X-Dev-User", who)).andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(true))
                .andExpect(jsonPath("$.consentVersion").value(ConsentService.CONSENT_VERSION));
        mvc.perform(get("/api/config").header("X-Dev-User", who)).andExpect(status().isOk());
        mvc.perform(get("/api/consent").header("X-Dev-User", who)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.version").value(ConsentService.CONSENT_VERSION))
                .andExpect(jsonPath("$.texts.nb").value(containsString("eXOReaction")))
                .andExpect(jsonPath("$.texts.en").value(containsString("eXOReaction")))
                .andExpect(jsonPath("$.texts.nb").value(not(containsString("{contact}"))))
                .andExpect(jsonPath("$.texts.en").value(containsString("contact the organisers")));

        ConsentSupport.accept(mvc, who);

        mvc.perform(get("/api/beers").header("X-Dev-User", who)).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("X-Dev-User", who)).andExpect(jsonPath("$.consentRequired").value(false));
        mvc.perform(get("/api/consent").header("X-Dev-User", who)).andExpect(jsonPath("$.accepted").value(true));

        // Consent is per person: someone else is still blocked.
        mvc.perform(get("/api/beers").header("X-Dev-User", "consent-other@cantara.no")).andExpect(status().isForbidden());
    }

    @Test
    void acceptingAnOutdatedVersionIsRefused() throws Exception {
        mvc.perform(post("/api/consent/accept").header("X-Dev-User", "consent-old@cantara.no")
                        .contentType(APPLICATION_JSON).content("{\"version\":\"2020-0\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_MISMATCH"));
        mvc.perform(post("/api/consent/accept").header("X-Dev-User", "consent-old@cantara.no")
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/beers").header("X-Dev-User", "consent-old@cantara.no")).andExpect(status().isForbidden());
    }

    @Test
    void askedAgainWhenTheVersionChanges() throws Exception {
        String who = "consent-newversion@cantara.no";
        // Someone who accepted an earlier version is not covered by the current one.
        consents.save(new UserConsent(users.upsert(who, "x").getId(), "2025-0"));

        mvc.perform(get("/api/beers").header("X-Dev-User", who)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(get("/api/me").header("X-Dev-User", who)).andExpect(jsonPath("$.consentRequired").value(true));

        ConsentSupport.accept(mvc, who);
        mvc.perform(get("/api/beers").header("X-Dev-User", who)).andExpect(status().isOk());
    }

    @Test
    void withdrawingBlocksAgainAndAcceptingRestores() throws Exception {
        String who = "consent-withdraw@cantara.no";
        ConsentSupport.accept(mvc, who);
        mvc.perform(get("/api/beers").header("X-Dev-User", who)).andExpect(status().isOk());

        mvc.perform(post("/api/consent/withdraw").header("X-Dev-User", who)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false));
        mvc.perform(get("/api/beers").header("X-Dev-User", who)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(get("/api/me").header("X-Dev-User", who)).andExpect(jsonPath("$.consentRequired").value(true));

        ConsentSupport.accept(mvc, who);
        mvc.perform(get("/api/beers").header("X-Dev-User", who)).andExpect(status().isOk());
    }

    @Test
    void publicConfigShowsSponsorAndNothingElse() throws Exception {
        mvc.perform(get("/api/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsor.name").value("eXOReaction"))
                .andExpect(jsonPath("$.sponsor.url").value("https://exoreaction.com"))
                .andExpect(content().string(not(containsString("secret"))));
    }
}
