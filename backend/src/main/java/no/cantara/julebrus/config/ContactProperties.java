package no.cantara.julebrus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where people write to ask for deletion. Blank means "contact the organisers" in the consent text. */
@ConfigurationProperties(prefix = "julebrus.contact")
public record ContactProperties(String email) {

    public boolean present() {
        return email != null && !email.isBlank();
    }
}
