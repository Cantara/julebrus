package no.cantara.julebrus.photo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static no.cantara.julebrus.photo.PhotoFixtures.jpeg;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The production security chain around the photo endpoints: login, CSRF, consent, and who is an admin when nothing is configured. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:photosec;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.security.oauth2.client.registration.google.client-id=fake",
        "spring.security.oauth2.client.registration.google.client-secret=fake"})
@AutoConfigureMockMvc
class PhotoSecurityTest {

    @Autowired
    MockMvc mvc;

    static RequestPostProcessor login(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email).claim("name", "Test " + email).subject(email));
    }

    private void accept(String who) throws Exception {
        mvc.perform(post("/api/consent/accept").with(login(who)).with(csrf()).contentType("application/json")
                .content("{\"version\":\"" + no.cantara.julebrus.consent.ConsentService.CONSENT_VERSION + "\"}")).andExpect(status().isOk());
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg", jpeg(100, 100));
    }

    @Test
    void anonymousCallersGetNothing() throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        mvc.perform(multipart("/api/beers/" + id + "/photos").file(file()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/beers/" + id + "/photo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/beers/photos/" + id + "/image")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/photos")).andExpect(status().isUnauthorized());
    }

    @Test
    void uploadNeedsCsrfAndConsent() throws Exception {
        String who = "photo-sec-" + System.nanoTime() + "@x.no";
        String beer = java.util.UUID.randomUUID().toString();
        // no CSRF token: refused before anything else
        mvc.perform(multipart("/api/beers/" + beer + "/photos").file(file()).with(login(who))).andExpect(status().isForbidden());
        // CSRF but no consent yet
        mvc.perform(multipart("/api/beers/" + beer + "/photos").file(file()).with(login(who)).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        mvc.perform(get("/api/beers/" + beer + "/photo").with(login(who))).andExpect(status().isForbidden());
        accept(who);
        // consented: now the request reaches the controller (unknown beer is a 404, not a gate)
        mvc.perform(multipart("/api/beers/" + beer + "/photos").file(file()).with(login(who)).with(csrf())).andExpect(status().isNotFound());
        // delete and decision need CSRF too
        mvc.perform(delete("/api/beers/photos/" + beer).with(login(who))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/photos/" + beer + "/decision").with(login(who)).contentType("application/json")
                .content("{\"decision\":\"APPROVE\"}")).andExpect(status().isForbidden());
    }

    @Test
    void withoutJulebrusAdminsNobodyIsAnAdmin() throws Exception {
        String who = "totto@exoreaction.com";
        accept(who);
        mvc.perform(get("/api/admin/photos").with(login(who))).andExpect(status().isForbidden());
        mvc.perform(get("/api/me").with(login(who))).andExpect(jsonPath("$.admin").value(false));
    }
}
