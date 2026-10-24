package no.cantara.julebrus.consent;

import no.cantara.julebrus.config.ContactProperties;
import no.cantara.julebrus.config.SponsorProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Versioned consent. Bump {@link #CONSENT_VERSION} whenever the texts change in a way
 * people should re-read: everyone is asked again on their next visit.
 */
@Service
public class ConsentService {

    /**
     * The version of consent-nb.md / consent-en.md people must have accepted.
     *
     * <p>DRAFT: the consent texts are a draft for the organisers to review and approve
     * before the season opens. Nobody has given them legal sign-off.</p>
     */
    public static final String CONSENT_VERSION = "2026-4";

    public record Texts(String nb, String en) {
    }

    private final UserConsentRepository consents;
    private final ConsentEventRepository events;
    private final Texts texts;

    public ConsentService(UserConsentRepository consents, ConsentEventRepository events, SponsorProperties sponsor,
                          ContactProperties contact) {
        this.consents = consents;
        this.events = events;
        this.texts = new Texts(
                render("consent/consent-nb.md", sponsor, contact, "arrangørene"),
                render("consent/consent-en.md", sponsor, contact, "the organisers"));
    }

    /** Fills {sponsor} and {contact} in a text ("the organisers" stands in for a blank contact). Without a sponsor only the line that carries {sponsor} is dropped. */
    private static String render(String path, SponsorProperties sponsor, ContactProperties contact, String noContact) {
        String raw;
        try {
            raw = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String sponsorName = sponsor.visible() ? sponsor.name().trim() : null;
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            if (line.contains("{sponsor}")) {
                if (sponsorName == null) continue;
                line = line.replace("{sponsor}", sponsorName);
            }
            out.append(line).append('\n');
        }
        return out.toString().replace("{contact}", contact.present() ? contact.email().trim() : noContact).strip();
    }

    public Texts texts() {
        return texts;
    }

    /** Everyone whose consent to the current version is active. Admin overviews and weights cover only these. */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeUserIds() {
        return new java.util.HashSet<>(consents.findActiveUserIds(CONSENT_VERSION));
    }

    @Transactional(readOnly = true)
    public boolean hasAccepted(UUID userId) {
        return consents.findByUserIdAndConsentVersion(userId, CONSENT_VERSION).map(UserConsent::active).orElse(false);
    }

    @Transactional
    public void accept(UUID userId) {
        consents.findByUserIdAndConsentVersion(userId, CONSENT_VERSION)
                .ifPresentOrElse(UserConsent::accept,
                        () -> consents.save(new UserConsent(userId, CONSENT_VERSION)));
        events.save(new ConsentEvent(userId, CONSENT_VERSION, ConsentEvent.Kind.ACCEPT));
    }

    /** Withdraws every active consent the user has given, whatever its version. */
    @Transactional
    public void withdraw(UUID userId) {
        consents.findByUserIdAndWithdrawnAtIsNull(userId).forEach(c -> {
            c.withdraw();
            events.save(new ConsentEvent(userId, c.getConsentVersion(), ConsentEvent.Kind.WITHDRAW));
        });
    }
}
