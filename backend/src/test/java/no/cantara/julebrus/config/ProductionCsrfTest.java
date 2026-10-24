package no.cantara.julebrus.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The production chain (Google login, CSRF on), as the SPA sees it. The frontend reads
 * the XSRF-TOKEN cookie and echoes it in X-XSRF-TOKEN on every POST, so the cookie has
 * to exist before the first POST: a GET must issue it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:csrf;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
})
class ProductionCsrfTest {

    @Autowired
    MockMvc mvc;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor kari() {
        return oidcLogin().idToken(t -> t.claim("email", "kari@cantara.no").claim("name", "Kari"));
    }

    @Test
    void aPlainGetIssuesTheXsrfCookieSoTheFirstPostCanCarryIt() throws Exception {
        MvcResult first = mvc.perform(get("/api/me").with(kari()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie xsrf = first.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).as("XSRF-TOKEN cookie on the first GET").isNotNull();
        assertThat(xsrf.getValue()).isNotBlank();

        // What the SPA does next: POST with the cookie and the matching header.
        String version = first.getResponse().getContentAsString().replaceAll(".*\"consentVersion\":\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/consent/accept").with(kari())
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType("application/json").content("{\"version\":\"" + version + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aPostWithoutTheTokenIsStillRefused() throws Exception {
        mvc.perform(post("/api/sessions").with(kari()))
                .andExpect(status().isForbidden());
    }
}
