package no.cantara.julebrus.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.cantara.julebrus.consent.ConsentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The live stream over a real port (SseEmitter needs the async servlet machinery that
 * MockMvc does not run): members get an event for every write at their table, outsiders
 * get nothing, and the dev impersonation works through the query parameter that
 * EventSource has to use.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:live;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@ActiveProfiles("dev")
class LiveRevealTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    @Autowired
    SessionEvents events;

    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void everyWriteAtTheTableReachesTheOtherPhones() throws Exception {
        String host = "live-host@cantara.no", guest = "live-guest@cantara.no", nosy = "live-nosy@cantara.no";
        for (String who : List.of(host, guest, nosy)) {
            post(who, "/api/consent/accept", Map.of("version", ConsentService.CONSENT_VERSION));
        }
        JsonNode session = post(host, "/api/sessions", null);
        String sid = session.get("id").asText();
        String code = session.get("joinCode").asText();
        JsonNode beer = post(host, "/api/beers", Map.of("name", "Direkteøl", "abv", 6.5));
        String beerId = beer.get("id").asText();
        post(guest, "/api/sessions/join", Map.of("code", code));

        // An outsider is refused before any stream opens.
        HttpResponse<String> refused = http.send(HttpRequest.newBuilder(uri("/api/sessions/" + sid + "/events"))
                .header("X-Dev-User", nosy).header("Accept", "text/event-stream").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(refused.statusCode()).isEqualTo(403);

        // The guest listens through ?devUser=, as the browser's EventSource would in dev.
        List<String> seen = new CopyOnWriteArrayList<>();
        CompletableFuture<HttpResponse<Stream<String>>> stream = http.sendAsync(
                HttpRequest.newBuilder(uri("/api/sessions/" + sid + "/events?devUser=" + guest))
                        .header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofLines());
        HttpResponse<Stream<String>> response = stream.get(10, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        Thread reader = new Thread(() -> response.body().forEach(line -> {
            if (line.startsWith("event:")) seen.add(line.substring(6).trim());
        }));
        reader.setDaemon(true);
        reader.start();
        waitFor(() -> seen.contains("hello"));
        assertThat(events.listeners(java.util.UUID.fromString(sid))).isEqualTo(1);

        // The host adds a beer, rates it, and starts a blind round: three events, in order, after commit.
        post(host, "/api/sessions/" + sid + "/beers", Map.of("beerId", beerId));
        waitFor(() -> seen.contains("session"));
        post(host, "/api/tastings", Map.of("beerId", beerId, "sessionId", sid, "score", 5));
        waitFor(() -> seen.contains("tasting"));
        JsonNode beer2 = post(host, "/api/beers", Map.of("name", "Direkteøl 2", "abv", 6.5));
        post(host, "/api/sessions/" + sid + "/beers", Map.of("beerId", beer2.get("id").asText()));
        post(host, "/api/sessions/" + sid + "/blind", Map.of("answerBeerId", beerId,
                "optionBeerIds", List.of(beerId, beer2.get("id").asText())));
        waitFor(() -> seen.contains("blind"));
        assertThat(seen).containsSubsequence("hello", "session", "tasting", "blind");
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("Timed out waiting for an event");
            Thread.sleep(25);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private JsonNode post(String who, String path, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).header("X-Dev-User", who).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : json.writeValueAsString(body)));
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).as(path + " -> " + res.body()).isLessThan(300);
        return res.body().isEmpty() ? null : json.readTree(res.body());
    }
}
