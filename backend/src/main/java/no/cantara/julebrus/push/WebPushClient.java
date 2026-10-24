package no.cantara.julebrus.push;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;

/** Delivers one encrypted message to a push service. Returns the HTTP status; 404 and 410 mean the browser is gone. */
@Component
public class WebPushClient {

    private final PushProperties props;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WebPushClient(PushProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    public int send(PushSubscription sub, String payloadJson, String subject) throws Exception {
        byte[] body = WebPushCrypto.encrypt(payloadJson.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.b64(sub.getP256dh()), WebPushCrypto.b64(sub.getAuth()));
        String auth = Vapid.authorization(sub.getEndpoint(), subject,
                new Vapid.Keys(props.vapidPublicKey(), props.vapidPrivateKey()), clock.instant());
        HttpRequest req = HttpRequest.newBuilder(URI.create(sub.getEndpoint()))
                .timeout(Duration.ofSeconds(20))
                .header("TTL", "86400")
                .header("Urgency", "normal")
                .header("Content-Type", "application/octet-stream")
                .header("Content-Encoding", "aes128gcm")
                .header("Authorization", auth)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
        return res.statusCode();
    }

    /** Is the configured key pair usable at all? Checked at startup so a typo fails loudly. */
    public void verifyKeys() throws GeneralSecurityException {
        WebPushCrypto.keyPair(WebPushCrypto.b64(props.vapidPublicKey()), WebPushCrypto.b64(props.vapidPrivateKey()));
    }
}
