package no.cantara.julebrus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Who pays for the hosting. A blank name hides the sponsor line everywhere. */
@ConfigurationProperties(prefix = "julebrus.sponsor")
public record SponsorProperties(@DefaultValue("eXOReaction") String name,
                                @DefaultValue("https://exoreaction.com") String url) {

    public boolean visible() {
        return name != null && !name.isBlank();
    }

    /** Only http(s) links are exposed, so a misconfigured value cannot become a javascript: link. */
    public String safeUrl() {
        if (url == null) return null;
        String u = url.trim();
        return u.startsWith("https://") || u.startsWith("http://") ? u : null;
    }
}
