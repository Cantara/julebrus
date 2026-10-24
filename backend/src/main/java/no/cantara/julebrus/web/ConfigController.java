package no.cantara.julebrus.web;

import no.cantara.julebrus.config.ContactProperties;
import no.cantara.julebrus.config.SponsorProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, no login: what the footer and the privacy page need to show. Never put secrets here (the contact address is meant to be public). */
@RestController
public class ConfigController {

    public record Sponsor(String name, String url) {
    }

    /** {@code sponsor} is null when no sponsor line should be shown. */
    /** {@code contactEmail} is where to ask for deletion; null when not configured. */
    public record PublicConfig(Sponsor sponsor, String contactEmail) {
    }

    private final SponsorProperties sponsor;

    private final ContactProperties contact;

    public ConfigController(SponsorProperties sponsor, ContactProperties contact) {
        this.sponsor = sponsor;
        this.contact = contact;
    }

    @GetMapping("/api/config")
    public PublicConfig config() {
        return new PublicConfig(sponsor.visible() ? new Sponsor(sponsor.name().trim(), sponsor.safeUrl()) : null,
                contact.present() ? contact.email().trim() : null);
    }
}
