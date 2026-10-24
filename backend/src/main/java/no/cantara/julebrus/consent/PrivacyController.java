package no.cantara.julebrus.consent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public privacy page (Google's OAuth consent screen needs a privacy policy URL, which
 * is /privacy in the SPA). Same texts as the consent notice, no user data, no login:
 * permitted in {@code SecurityConfig} and excluded from {@link ConsentInterceptor}.
 */
@RestController
public class PrivacyController {

    public record PrivacyView(String version, ConsentService.Texts texts) {
    }

    private final ConsentService consents;

    public PrivacyController(ConsentService consents) {
        this.consents = consents;
    }

    @GetMapping("/api/privacy")
    public PrivacyView privacy() {
        return new PrivacyView(ConsentService.CONSENT_VERSION, consents.texts());
    }
}
