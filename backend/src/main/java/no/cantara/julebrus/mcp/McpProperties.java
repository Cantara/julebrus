package no.cantara.julebrus.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The bearer token agents present on /mcp. Blank means the endpoint is off. */
@ConfigurationProperties(prefix = "julebrus.mcp")
public record McpProperties(String token) {

    public boolean enabled() {
        return token != null && !token.isBlank();
    }
}
