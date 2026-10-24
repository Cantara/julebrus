package no.cantara.julebrus.landing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Under the dev profile everyone is the dev user, so "/" is the app; /velkommen, /welcome and /landing show the landing page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LandingDevProfileTest {

    @Autowired
    MockMvc mvc;

    @Test
    void rootIsTheAppAndWelcomeIsTheLandingPage() throws Exception {
        assertThat(mvc.perform(get("/")).andReturn().getResponse().getForwardedUrl()).isEqualTo("/index.html");
        var res = mvc.perform(get("/velkommen")).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader("Content-Security-Policy")).isEqualTo(LandingController.CSP);
        for (String path : new String[]{"/welcome", "/landing"}) {
            var other = mvc.perform(get(path)).andReturn().getResponse();
            assertThat(other.getStatus()).as(path).isEqualTo(200);
            assertThat(other.getForwardedUrl()).as(path).isNull();
            assertThat(other.getHeader("Content-Security-Policy")).as(path).isEqualTo(LandingController.CSP);
        }
    }
}
