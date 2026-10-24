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
                .andExpect(jsonPath("$.texts.en").value(containsString("get in touch with the organisers")));

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

    @Autowired
    jakarta.persistence.EntityManager em;
    @Autowired
    no.cantara.julebrus.consent.ConsentEventRepository events;

    @Test
    void privacyTextsArePublicWithoutLoginOrConsent() throws Exception {
        // A person who has never accepted anything: 200 with both texts and the version.
        mvc.perform(get("/api/privacy").header("X-Dev-User", "privacy-visitor@cantara.no")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(ConsentService.CONSENT_VERSION))
                .andExpect(jsonPath("$.texts.nb").value(containsString("Personvern")))
                .andExpect(jsonPath("$.texts.en").value(containsString("Privacy")))
                .andExpect(content().string(not(containsString("privacy-visitor"))));
        // No identity at all (no X-Dev-User; the dev chain still supplies the default dev user, which has not consented either in this test).
        mvc.perform(get("/api/privacy")).andExpect(status().isOk());
        mvc.perform(get("/api/config")).andExpect(jsonPath("$.contactEmail").doesNotExist());
    }

    @Test
    void acceptWithdrawAcceptLeavesOneRowAndThreeEvents() throws Exception {
        String who = "consent-audit@cantara.no";
        ConsentSupport.accept(mvc, who);
        mvc.perform(post("/api/consent/withdraw").header("X-Dev-User", who)).andExpect(status().isOk());
        ConsentSupport.accept(mvc, who);

        var id = users.upsert(who, "x").getId();
        org.assertj.core.api.Assertions.assertThat(consents.findAll().stream().filter(c -> c.getUserId().equals(id))).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(consents.findAll().stream().filter(c -> c.getUserId().equals(id)).findFirst().get().active()).isTrue();
        org.assertj.core.api.Assertions.assertThat(events.findByUserIdOrderByAtAsc(id))
                .extracting(e -> e.getKind().name()).containsExactly("ACCEPT", "WITHDRAW", "ACCEPT");
    }

    @Test
    void interceptorAppliesToPathVariantsAndUnknownApiPaths() throws Exception {
        String who = "consent-variants@cantara.no";
        for (String path : new String[]{"/api/beers/", "/api/beers.json", "/api/nope", "/api/history/me/", "/api/admin/history/claims/"}) {
            var r = mvc.perform(get(path).header("X-Dev-User", who)).andReturn().getResponse();
            org.assertj.core.api.Assertions.assertThat(r.getStatus()).as(path).isNotEqualTo(200);
            org.assertj.core.api.Assertions.assertThat(r.getContentAsString()).as(path).doesNotContain("\"name\"");
        }
        mvc.perform(get("/api/beers").header("X-Dev-User", who)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/api/beers").header("X-Dev-User", who))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/beers").header("X-Dev-User", who))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).isEmpty());
        // A path that is not an exact exempt one is not exempt: a sibling of /api/consent.
        mvc.perform(get("/api/consentx").header("X-Dev-User", who)).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/me/x").header("X-Dev-User", who)).andExpect(status().is4xxClientError());
    }

    @Test
    void devUserHeaderWithoutAtSignIsRefusedNotACrash() throws Exception {
        mvc.perform(get("/api/me").header("X-Dev-User", "nobody")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        mvc.perform(get("/api/me").header("X-Dev-User", "@x.no")).andExpect(status().isBadRequest());
    }

    @Test
    void parallelFirstRequestsFromOneNewPersonAllSucceed() throws Exception {
        String who = "parallel-" + System.nanoTime() + "@cantara.no";
        int n = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(get("/api/me").header("X-Dev-User", who)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (var f : futures) org.assertj.core.api.Assertions.assertThat(f.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }
    }
}
