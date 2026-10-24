package no.cantara.julebrus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real production security chain (no dev profile, a fake Google registration, H2 for the
 * database): who gets through without logging in, CSRF on logout, and that consent still blocks
 * a logged-in person who has not accepted. oidcLogin() stands in for Google.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prodchain;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.security.oauth2.client.registration.google.client-id=fake",
        "spring.security.oauth2.client.registration.google.client-secret=fake"})
@AutoConfigureMockMvc
class ProdChainTest {

    @Autowired
    MockMvc mvc;

    static RequestPostProcessor login(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email).claim("name", "Test " + email).subject(email));
    }

    @Test
    void publicPagesAnswerAnonymouslyAndTheRestAsksForLogin() throws Exception {
        for (String path : new String[]{"/api/privacy", "/api/config", "/privacy", "/privacy/", "/"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        for (String path : new String[]{"/api/me", "/api/beers", "/api/consent", "/api/history/claim-candidates",
                "/api/admin/history/claims"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        String html = mvc.perform(get("/privacy")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Personvern og samtykke").contains("Privacy and consent");
    }

    @Test
    void loggedInWithoutConsentIsBlockedExceptOnTheExemptPaths() throws Exception {
        String who = "prod-nc-" + System.nanoTime() + "@x.no";
        mvc.perform(get("/api/beers").with(login(who))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/history/claims").with(login(who))).andExpect(status().isForbidden());
        mvc.perform(get("/api/me").with(login(who))).andExpect(status().isOk());
        mvc.perform(get("/api/consent").with(login(who))).andExpect(status().isOk());
        mvc.perform(get("/api/privacy").with(login(who))).andExpect(status().isOk());
        mvc.perform(post("/api/consent/accept").with(login(who)).contentType("application/json")
                .content("{\"version\":\"" + no.cantara.julebrus.consent.ConsentService.CONSENT_VERSION + "\"}"))
                .andExpect(status().isForbidden()); // CSRF is on in production
        mvc.perform(post("/api/consent/accept").with(login(who)).with(csrf()).contentType("application/json")
                .content("{\"version\":\"" + no.cantara.julebrus.consent.ConsentService.CONSENT_VERSION + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/beers").with(login(who))).andExpect(status().isOk());
    }

    @Test
    void logoutNeedsCsrfAndWorksWhileConsentIsMissing() throws Exception {
        String who = "prod-logout-" + System.nanoTime() + "@x.no";
        mvc.perform(post("/api/logout").with(login(who))).andExpect(status().isForbidden());
        var res = mvc.perform(post("/api/logout").with(login(who)).with(csrf())).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(302);
        assertThat(res.getHeader("Location")).endsWith("/");
    }

    /** Path tricks must neither bypass consent on a protected path nor reach data through an exempt-looking one. */
    @Test
    void pathVariantsDoNotBypassConsentOrLeak() throws Exception {
        String who = "prod-variants-" + System.nanoTime() + "@x.no";
        for (String path : new String[]{"/api/beers/", "/api/beers;x=1", "/api/beers//", "/api/beers.json",
                "/api/me/../beers", "/api/consent/../beers", "/api/config/../beers", "/api/privacy/../beers",
                "/api/me/", "/api/consent/;a=b", "/api/me;jsessionid=1/../beers"}) {
            assertBlocked(path, get(path).with(login(who)));
        }
        // A literal double slash in the request URI (MockMvc would read "//api/..." as a host).
        for (String uri : new String[]{"//api/beers", "/api//beers"}) {
            assertBlocked(uri, get("/x").with(login(who)).with(r -> { r.setRequestURI(uri); r.setServletPath(""); r.setPathInfo(uri); return r; }));
        }
        assertBlocked("//api/me anonymous", get("/x").with(r -> { r.setRequestURI("//api/me"); r.setServletPath(""); r.setPathInfo("//api/me"); return r; }));
        // Anonymous callers get nothing from the exempt-looking variants either.
        for (String path : new String[]{"/api/me/", "/api/me;x=1", "/api/consent/", "/api/consent/x"}) {
            assertBlocked(path, get(path));
        }
    }

    private void assertBlocked(String path, MockHttpServletRequestBuilder req) {
        try {
            var res = mvc.perform(req).andReturn().getResponse();
            assertThat(res.getStatus()).as(path).isNotEqualTo(200);
            assertThat(res.getContentAsString()).as(path).doesNotContain("\"email\"").doesNotContain("\"name\":\"");
        } catch (Exception rejectedByTheFirewall) {
            // Spring Security's firewall refusing the request outright is also a block.
            assertThat(rejectedByTheFirewall.toString()).as(path).containsAnyOf("Rejected", "RequestRejected", "not allowed");
        }
    }
}
