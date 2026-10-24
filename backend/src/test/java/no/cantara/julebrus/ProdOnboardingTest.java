package no.cantara.julebrus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A brand-new person through the production chain, call by call as the app makes them:
 * /api/me before consent, accept, /api/me again, the team page's reads and its save, home.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prodonboarding;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.security.oauth2.client.registration.google.client-id=fake",
        "spring.security.oauth2.client.registration.google.client-secret=fake"})
@AutoConfigureMockMvc
class ProdOnboardingTest {

    @Autowired
    MockMvc mvc;

    static RequestPostProcessor login(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email).claim("name", "Ny Person").subject(email));
    }

    @Test
    void aNewPersonGetsFromGoogleToHome() throws Exception {
        String who = "ny-" + System.nanoTime() + "@cantara.no";

        // 1. First load: /api/me says consent is required; nothing else is read.
        mvc.perform(get("/api/me").with(login(who))).andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(true))
                .andExpect(jsonPath("$.teamsChosen").value(false));
        String version = mvc.perform(get("/api/consent").with(login(who))).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"version\":\"([^\"]+)\".*", "$1");

        // 2. Accept, then the refresh the consent page does before it leaves.
        mvc.perform(post("/api/consent/accept").with(login(who)).with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"version\":\"%s\"}".formatted(version))).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(login(who))).andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(false))
                .andExpect(jsonPath("$.teamsChosen").value(false))
                .andExpect(jsonPath("$.weight.weight").value(0.5));

        // 3. The team page: config, teams, save (a PUT through CSRF), and the refresh after.
        mvc.perform(get("/api/config").with(login(who))).andExpect(status().isOk());
        mvc.perform(get("/api/teams").with(login(who))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(put("/api/me/teams").with(login(who)).with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"teamIds\":[]}")).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(login(who))).andExpect(jsonPath("$.teamsChosen").value(true));

        // 4. Home's calls.
        for (String path : new String[]{"/api/beers/least-tasted?limit=5", "/api/tastings/mine", "/api/beers", "/api/tips/me", "/api/sessions/mine"}) {
            mvc.perform(get(path).with(login(who))).andExpect(status().isOk());
        }
        // A PUT without the CSRF token is refused, as the browser would be: the client retries with the cookie.
        mvc.perform(put("/api/me/teams").with(login(who)).contentType(APPLICATION_JSON).content("{\"teamIds\":[]}"))
                .andExpect(status().isForbidden());
    }
}
