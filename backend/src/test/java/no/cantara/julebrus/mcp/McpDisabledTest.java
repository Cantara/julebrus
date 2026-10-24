package no.cantara.julebrus.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** With no token configured, /mcp does not exist as far as the outside can tell. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class McpDisabledTest {

    @LocalServerPort
    int port;

    @Test
    void answers404WhateverIsPresented() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        for (String auth : new String[]{null, "Bearer anything"}) {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"));
            if (auth != null) b.header("Authorization", auth);
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(res.statusCode()).isEqualTo(404);
        }
    }
}
