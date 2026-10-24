package no.cantara.julebrus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "julebrus.auth")
public record AuthProperties(List<String> allowedDomains, List<String> admins) {

    public AuthProperties {
        allowedDomains = allowedDomains == null ? List.of()
                : allowedDomains.stream().map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).toList();
        admins = admins == null ? List.of()
                : admins.stream().map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).toList();
    }

    public boolean isAdmin(String email) {
        return email != null && admins.contains(email.toLowerCase(Locale.ROOT));
    }

    public boolean isAllowed(String email) {
        if (allowedDomains.isEmpty()) return true;
        if (email == null || !email.contains("@")) return false;
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        return allowedDomains.contains(domain);
    }
}
