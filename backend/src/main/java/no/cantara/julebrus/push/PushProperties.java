package no.cantara.julebrus.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

/**
 * VAPID keys (base64url, raw: 65-byte uncompressed public point, 32-byte private scalar)
 * and when the daily nudge goes out. Blank keys switch the whole feature off.
 */
@ConfigurationProperties(prefix = "julebrus.push")
public record PushProperties(String vapidPublicKey, String vapidPrivateKey, String subject, LocalTime nudgeTime) {

    public PushProperties {
        if (nudgeTime == null) nudgeTime = LocalTime.of(17, 0);
    }

    public boolean enabled() {
        return vapidPublicKey != null && !vapidPublicKey.isBlank() && vapidPrivateKey != null && !vapidPrivateKey.isBlank();
    }
}
