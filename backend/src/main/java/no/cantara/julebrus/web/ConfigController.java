package no.cantara.julebrus.web;

import no.cantara.julebrus.config.SponsorProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, no login: what the sign-in and footer need to show. Never put secrets here. */
@RestController
public class ConfigController {

    public record Sponsor(String name, String url) {
    }

    /** {@code sponsor} is null when no sponsor line should be shown. */
    public record PublicConfig(Sponsor sponsor) {
    }

    private final SponsorProperties sponsor;

    public ConfigController(SponsorProperties sponsor) {
        this.sponsor = sponsor;
    }

    @GetMapping("/api/config")
    public PublicConfig config() {
        return new PublicConfig(sponsor.visible() ? new Sponsor(sponsor.name().trim(), sponsor.safeUrl()) : null);
    }
}
