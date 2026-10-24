package no.cantara.julebrus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Only bound under the dev profile; see application-dev.yml. */
@ConfigurationProperties(prefix = "julebrus.dev-user")
public record DevUserProperties(String email, String name) {
}
