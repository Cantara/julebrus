package no.cantara.julebrus.push;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without VAPID keys the feature is off: the config says so and subscribing is refused. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PushOffTest {

    @Autowired
    MockMvc mvc;

    @Test
    void offWithoutKeys() throws Exception {
        String who = "push-off@cantara.no";
        ConsentSupport.accept(mvc, who);
        mvc.perform(get("/api/push/config").header("X-Dev-User", who))
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.publicKey").doesNotExist());
        mvc.perform(post("/api/push/subscriptions").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://x.example/y\",\"keys\":{\"p256dh\":\"a\",\"auth\":\"b\"}}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PUSH_OFF"));
    }
}
