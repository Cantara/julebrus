package no.cantara.julebrus.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MCP endpoint over a real port (the servlet is mounted beside the DispatcherServlet, so
 * MockMvc never sees it): token gate, the JSON-RPC handshake, the tool list and a tool call
 * against the imported history. Speaks Streamable HTTP by hand, as any client would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "julebrus.mcp.token=test-token")
@ActiveProfiles("dev")
class McpServerTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void refusesWithoutTheToken() throws Exception {
        HttpResponse<String> none = post(initialize(), null, null);
        assertThat(none.statusCode()).isEqualTo(401);
        assertThat(none.headers().firstValue("WWW-Authenticate")).contains("Bearer");

        HttpResponse<String> wrong = post(initialize(), "wrong", null);
        assertThat(wrong.statusCode()).isEqualTo(401);
    }

    @Test
    void handshakeListsTheToolsAndAnswersACall() throws Exception {
        HttpResponse<String> init = post(initialize(), "test-token", null);
        assertThat(init.statusCode()).isEqualTo(200);
        JsonNode initResult = firstResult(init);
        assertThat(initResult.path("serverInfo").path("name").asText()).isEqualTo("julebrus");
        assertThat(initResult.path("capabilities").has("tools")).isTrue();
        String session = init.headers().firstValue("Mcp-Session-Id").orElseThrow();

        HttpResponse<String> initialized = post(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"), "test-token", session);
        assertThat(initialized.statusCode()).isIn(200, 202);

        HttpResponse<String> list = post(Map.of("jsonrpc", "2.0", "id", 2, "method", "tools/list"), "test-token", session);
        assertThat(list.statusCode()).isEqualTo(200);
        List<String> names = new ArrayList<>();
        firstResult(list).path("tools").forEach(t -> names.add(t.path("name").asText()));
        assertThat(names).containsExactlyInAnyOrder("list_seasons", "season_results", "beer_history", "list_raters",
                "rater_history", "current_ranking", "team_ranking", "duel_ranking", "season_status", "finale");

        HttpResponse<String> call = post(Map.of("jsonrpc", "2.0", "id", 3, "method", "tools/call",
                "params", Map.of("name", "list_seasons", "arguments", Map.of())), "test-token", session);
        assertThat(call.statusCode()).isEqualTo(200);
        JsonNode result = firstResult(call);
        assertThat(result.path("isError").asBoolean(false)).isFalse();
        JsonNode seasons = json.readTree(result.path("content").get(0).path("text").asText());
        List<Integer> years = new ArrayList<>();
        seasons.forEach(s -> years.add(s.path("year").asInt()));
        assertThat(years).contains(2019, 1998);

        HttpResponse<String> one = post(Map.of("jsonrpc", "2.0", "id", 4, "method", "tools/call",
                "params", Map.of("name", "season_results", "arguments", Map.of("year", 2019))), "test-token", session);
        JsonNode rows = json.readTree(firstResult(one).path("content").get(0).path("text").asText());
        assertThat(rows.size()).isEqualTo(26);
        assertThat(rows.get(0).path("weightedMean").asDouble()).isGreaterThanOrEqualTo(rows.get(1).path("weightedMean").asDouble());

        HttpResponse<String> missing = post(Map.of("jsonrpc", "2.0", "id", 5, "method", "tools/call",
                "params", Map.of("name", "season_results", "arguments", Map.of("year", 1066))), "test-token", session);
        assertThat(firstResult(missing).path("isError").asBoolean()).isTrue();
    }

    @Test
    void endpointStaysOutsideTheWebAppsOwnGates() throws Exception {
        // No consent, no login, no CSRF token: the bearer token is the whole access control.
        HttpResponse<String> init = post(initialize(), "test-token", null);
        assertThat(init.statusCode()).isEqualTo(200);
    }

    private static Map<String, Object> initialize() {
        return Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of(
                "protocolVersion", "2025-06-18",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "test", "version", "0")));
    }

    private HttpResponse<String> post(Map<String, Object> body, String token, String session) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (session != null) b.header("Mcp-Session-Id", session).header("MCP-Protocol-Version", "2025-06-18");
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The result of the first JSON-RPC response in the body, whether it came as plain JSON or as SSE. */
    private JsonNode firstResult(HttpResponse<String> response) throws IOException {
        String body = response.body();
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (contentType.startsWith("text/event-stream")) {
            for (String line : body.split("\n")) {
                if (line.startsWith("data:")) {
                    JsonNode msg = json.readTree(line.substring(5).trim());
                    if (msg.has("result")) return msg.get("result");
                    if (msg.has("error")) throw new AssertionError("JSON-RPC error: " + msg.get("error"));
                }
            }
            throw new AssertionError("No JSON-RPC response in stream: " + body);
        }
        JsonNode msg = json.readTree(body);
        if (msg.has("error")) throw new AssertionError("JSON-RPC error: " + msg.get("error"));
        return msg.get("result");
    }
}
