package no.cantara.julebrus.push;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Subscriptions with keys configured: config, subscribe, re-subscribe, unsubscribe, keygen for admins. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:push;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PushTest {

    static Vapid.Keys keys;

    @DynamicPropertySource
    static void vapid(DynamicPropertyRegistry registry) throws Exception {
        keys = Vapid.generate();
        registry.add("julebrus.push.vapid-public-key", () -> keys.publicKey());
        registry.add("julebrus.push.vapid-private-key", () -> keys.privateKey());
        registry.add("julebrus.push.nudge-time", () -> "17:00");
    }

    @Autowired
    MockMvc mvc;

    @Test
    void subscribeAndForget() throws Exception {
        String who = "push@cantara.no", admin = "dev@cantara.no";
        ConsentSupport.accept(mvc, who);
        ConsentSupport.accept(mvc, admin);

        mvc.perform(get("/api/push/config").header("X-Dev-User", who))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicKey").value(keys.publicKey()))
                .andExpect(jsonPath("$.nudgeTime").value("17:00:00"));
        mvc.perform(get("/api/push/me").header("X-Dev-User", who)).andExpect(jsonPath("$.endpoints", hasSize(0)));

        // What a browser hands over: its endpoint, its P-256 public key and its auth secret.
        KeyPair browser = WebPushCrypto.generateKeyPair();
        String p256dh = WebPushCrypto.b64(WebPushCrypto.uncompressed((ECPublicKey) browser.getPublic()));
        String endpoint = "https://push.example.org/send/abc";
        String body = "{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"BTBZMqHH6r4Tts7J_aSIgg\"}}";

        mvc.perform(post("/api/push/subscriptions").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                        .content(body.formatted("http://insecure.example.org/x", p256dh)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PUSH_SUBSCRIPTION"));
        mvc.perform(post("/api/push/subscriptions").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                        .content(body.formatted(endpoint, "notakey")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PUSH_SUBSCRIPTION"));
        mvc.perform(post("/api/push/subscriptions").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                        .content(body.formatted(endpoint, p256dh)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.endpoints[0]").value(endpoint));
        // The same browser again (new keys after a service-worker update) is still one row.
        mvc.perform(post("/api/push/subscriptions").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                        .content(body.formatted(endpoint, p256dh)))
                .andExpect(jsonPath("$.endpoints", hasSize(1)));

        // Somebody else cannot remove it; the owner can.
        mvc.perform(delete("/api/push/subscriptions").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                .content("{\"endpoint\":\"%s\"}".formatted(endpoint))).andExpect(status().isOk());
        mvc.perform(get("/api/push/me").header("X-Dev-User", who)).andExpect(jsonPath("$.endpoints", hasSize(1)));
        mvc.perform(delete("/api/push/subscriptions").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                .content("{\"endpoint\":\"%s\"}".formatted(endpoint))).andExpect(jsonPath("$.endpoints", hasSize(0)));

        // Keygen is for admins.
        mvc.perform(get("/api/push/keygen").header("X-Dev-User", who)).andExpect(status().isForbidden());
        mvc.perform(get("/api/push/keygen").header("X-Dev-User", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").isString()).andExpect(jsonPath("$.privateKey").isString());
    }
}
