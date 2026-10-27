package no.cantara.julebrus.push;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An hour before a planned tasting, every attendee's browsers get one reminder; nobody else, and never twice. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pushreminder;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PushReminderTest {

    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-12-05T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @DynamicPropertySource
    static void vapid(DynamicPropertyRegistry registry) throws Exception {
        Vapid.Keys keys = Vapid.generate();
        registry.add("julebrus.push.vapid-public-key", keys::publicKey);
        registry.add("julebrus.push.vapid-private-key", keys::privateKey);
        registry.add("julebrus.push.nudge-time", () -> "23:59");
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    MutableClock clock;
    @Autowired
    PushService push;
    @MockitoBean
    WebPushClient client;

    @Test
    void attendeesAreRemindedOnceAnHourBefore() throws Exception {
        when(client.send(any(), anyString(), anyString())).thenReturn(201);
        String kari = "rem-kari@cantara.no", ola = "rem-ola@cantara.no", nosy = "rem-nosy@cantara.no";
        for (String who : new String[]{kari, ola, nosy}) {
            ConsentSupport.accept(mvc, who);
            subscribe(who, "https://push.example.org/" + who);
        }
        Instant starts = clock.now.plus(Duration.ofHours(6));
        String event = mvc.perform(post("/api/events").header("X-Dev-User", kari).contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Juleøl hos Kari\",\"startsAt\":\"" + starts + "\",\"online\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = event.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/events/" + id + "/join").header("X-Dev-User", ola)).andExpect(status().isOk());

        // Too early: nothing. (The nudge time is set late so it cannot interfere.)
        clock.now = starts.minus(Duration.ofMinutes(61));
        push.tick();
        verify(client, never()).send(any(), anyString(), anyString());

        // An hour before: one message to each attendee's browser, none to the bystander.
        clock.now = starts.minus(Duration.ofMinutes(60));
        push.tick();
        ArgumentCaptor<PushSubscription> to = ArgumentCaptor.forClass(PushSubscription.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(client, times(2)).send(to.capture(), payload.capture(), anyString());
        assertThat(to.getAllValues()).extracting(PushSubscription::getEndpoint)
                .containsExactlyInAnyOrder("https://push.example.org/" + kari, "https://push.example.org/" + ola);
        assertThat(payload.getAllValues().get(0)).contains("Smaking om en time").contains("Juleøl hos Kari, digitalt. 2 påmeldte.")
                .contains("\"url\":\"/smakinger\"").contains("\"tag\":\"event-" + id + "\"");

        // The next minute, and the minute after: nothing more.
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        push.tick();
        clock.now = starts.minus(Duration.ofMinutes(5));
        push.tick();
        verify(client, times(2)).send(any(), anyString(), eq(push.subject()));
    }

    private void subscribe(String who, String endpoint) throws Exception {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        String p256dh = WebPushCrypto.b64(WebPushCrypto.uncompressed((ECPublicKey) browser.getPublic()));
        mvc.perform(post("/api/push/subscriptions").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                        .content("{\"endpoint\":\"%s\",\"keys\":{\"p256dh\":\"%s\",\"auth\":\"BTBZMqHH6r4Tts7J_aSIgg\"}}".formatted(endpoint, p256dh)))
                .andExpect(status().isOk());
    }
}
