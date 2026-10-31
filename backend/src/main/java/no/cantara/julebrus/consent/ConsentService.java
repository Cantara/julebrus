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
    public static final String CONSENT_VERSION = "2026-8";

    public record Texts(String nb, String en) {
    }

    private final UserConsentRepository consents;
    private final ConsentEventRepository events;
    private final Texts texts;
    private final Texts summary;

    public ConsentService(UserConsentRepository consents, ConsentEventRepository events, SponsorProperties sponsor,
                          ContactProperties contact) {
        this.consents = consents;
        this.events = events;
        this.texts = new Texts(
                render("consent/consent-nb.md", sponsor, contact, "arrangørene"),
                render("consent/consent-en.md", sponsor, contact, "the organisers"));
        this.summary = new Texts(
                render("consent/consent-summary-nb.md", sponsor, contact, "arrangørene"),
                render("consent/consent-summary-en.md", sponsor, contact, "the organisers"));
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

    /** The short plain-language version shown first; the full texts above remain the binding ones. */
    public Texts summary() {
        return summary;
    }

    /** Everyone whose consent to the current version is active. Admin overviews and weights cover only these. */
    @Transactional(readOnly = true)
    public java.util.Set<UUID> activeUserIds() {
        return new java.util.HashSet<>(consents.findActiveUserIds(CONSENT_VERSION));
    }

    /**
     * The newest version each person still has an active consent to, whatever the version. Someone on an
     * older version is not "consented" anywhere data is shown to others, but they did answer once and are
     * asked again on their next visit: the admin list tells the two apart.
     */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, String> newestActiveVersions() {
        java.util.Map<UUID, String> newest = new java.util.HashMap<>();
        for (UserConsent c : consents.findByWithdrawnAtIsNull()) {
            newest.merge(c.getUserId(), c.getConsentVersion(), (a, b) -> compareVersions(a, b) >= 0 ? a : b);
        }
        return newest;
    }

    /** Versions are "year-n": by year, then by n as a number, so 2026-10 is newer than 2026-7. */
    static int compareVersions(String a, String b) {
        String[] x = a.split("-", 2), y = b.split("-", 2);
        int byYear = x[0].compareTo(y[0]);
        if (byYear != 0) return byYear;
        try {
            return Integer.compare(Integer.parseInt(x.length > 1 ? x[1] : "0"), Integer.parseInt(y.length > 1 ? y[1] : "0"));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
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
