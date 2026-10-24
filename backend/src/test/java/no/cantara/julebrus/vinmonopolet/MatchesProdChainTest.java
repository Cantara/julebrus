package no.cantara.julebrus.vinmonopolet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The match-review endpoints on the production chain: login, the consent gate, CSRF on every write, and the admin check. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:matchesprod;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.security.oauth2.client.registration.google.client-id=fake",
        "spring.security.oauth2.client.registration.google.client-secret=fake"})
@AutoConfigureMockMvc
class MatchesProdChainTest {

    static final String ADMIN = "totto@exoreaction.com"; // the default in application.yml
    static final String BULK = "{\"action\":\"CONFIRM\",\"year\":1998,\"status\":\"likely\"}";

    @Autowired
    MockMvc mvc;

    static RequestPostProcessor login(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email).claim("name", "Test " + email).subject(email));
    }

    void consent(String email) throws Exception {
        mvc.perform(post("/api/consent/accept").with(login(email)).with(csrf()).contentType("application/json")
                .content("{\"version\":\"" + no.cantara.julebrus.consent.ConsentService.CONSENT_VERSION + "\"}")).andExpect(status().isOk());
    }

    @Test
    void anonymousCallersAreSentToLogin() throws Exception {
        for (String p : new String[]{"/api/admin/matches", "/api/admin/matches/overrides.csv", "/api/admin/products/search?q=a"}) {
            mvc.perform(get(p)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theConsentGateComesBeforeEverythingEvenForAnAdmin() throws Exception {
        String fresh = "matches-noconsent-" + System.nanoTime() + "@x.no";
        for (String p : new String[]{"/api/admin/matches", "/api/admin/matches/overrides.csv", "/api/admin/products/search?q=a"}) {
            mvc.perform(get(p).with(login(fresh))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        }
        mvc.perform(post("/api/admin/matches/bulk").with(login(fresh)).with(csrf()).contentType("application/json").content(BULK))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    void writesNeedTheCsrfTokenEvenForAnAdmin() throws Exception {
        consent(ADMIN);
        String id = java.util.UUID.randomUUID().toString();
        mvc.perform(post("/api/admin/matches/bulk").with(login(ADMIN)).contentType("application/json").content(BULK))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/matches/" + id + "/decision").with(login(ADMIN)).contentType("application/json")
                .content("{\"action\":\"NONE\",\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/vinmonopolet/import").with(login(ADMIN))).andExpect(status().isForbidden());
        // with the token the same calls get through to the real handlers
        mvc.perform(post("/api/admin/matches/bulk").with(login(ADMIN)).with(csrf()).contentType("application/json").content(BULK))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(true));
        mvc.perform(post("/api/admin/matches/" + id + "/decision").with(login(ADMIN)).with(csrf()).contentType("application/json")
                .content("{\"action\":\"NONE\",\"version\":0}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/vinmonopolet/import").with(login(ADMIN)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/matches").with(login(ADMIN))).andExpect(status().isOk());
    }

    @Test
    void aConsentedNonAdminIsForbiddenOnEveryEndpointEvenWithCsrf() throws Exception {
        String plain = "matches-plain-" + System.nanoTime() + "@x.no";
        consent(plain);
        String id = java.util.UUID.randomUUID().toString();
        mvc.perform(get("/api/admin/matches").with(login(plain))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/admin/matches/overrides.csv").with(login(plain))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/products/search?q=jule").with(login(plain))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/matches/bulk").with(login(plain)).with(csrf()).contentType("application/json").content(BULK))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/matches/" + id + "/decision").with(login(plain)).with(csrf()).contentType("application/json")
                .content("{\"action\":\"NONE\",\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/vinmonopolet/import").with(login(plain)).with(csrf())).andExpect(status().isForbidden());
    }
}
