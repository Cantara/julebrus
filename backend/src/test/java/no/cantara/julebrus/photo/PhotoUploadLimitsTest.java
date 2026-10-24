package no.cantara.julebrus.photo;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The multipart size limit is enforced by the servlet container, so it needs a real HTTP request (MockMvc skips it).
 * An oversized upload must come back as a clean 413 with a stable code, never a 500 or a dropped connection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PhotoUploadLimitsTest {

    @LocalServerPort
    int port;
    @Autowired
    MockMvc mvc;

    private HttpResponse<String> upload(String beer, byte[] file) throws Exception {
        String boundary = "----t" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.writeBytes(file);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/beers/" + beer + "/photos"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Dev-User", "dev@cantara.no")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void oversizeUploadsAreAClean413() throws Exception {
        ConsentSupport.accept(mvc, null);
        String beer = mvc.perform(post("/api/beers").contentType(APPLICATION_JSON).content("{\"name\":\"Limit beer " + System.nanoTime() + "\",\"abv\":6}"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        byte[] seven = new byte[7 * 1024 * 1024];
        new Random(1).nextBytes(seven);
        HttpResponse<String> r = upload(beer, seven);
        assertThat(r.statusCode()).isEqualTo(413);
        assertThat(r.body()).contains("PHOTO_TOO_LARGE");

        byte[] twelve = new byte[12 * 1024 * 1024];
        HttpResponse<String> big = upload(beer, twelve);
        assertThat(big.statusCode()).isEqualTo(413);
        assertThat(big.body()).contains("PHOTO_TOO_LARGE");

        // just under the limit passes the size gate and then fails on content, not on size
        byte[] under = new byte[5 * 1024 * 1024];
        new Random(2).nextBytes(under);
        HttpResponse<String> notAPicture = upload(beer, under);
        assertThat(notAPicture.statusCode()).isEqualTo(415);
        assertThat(notAPicture.body()).contains("PHOTO_FORMAT");
    }
}
