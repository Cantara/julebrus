package no.cantara.julebrus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The three rules of the season, plus where "a day" starts and ends.
 */
@ConfigurationProperties(prefix = "julebrus.season")
public record SeasonProperties(
        int year,
        LocalDate start,
        LocalDate end,
        ZoneId zone,
        int maxTastingsPerDay,
        int maxSoloDays
) {
    public SeasonProperties {
        if (zone == null) zone = ZoneId.of("Europe/Oslo");
        if (maxTastingsPerDay <= 0) maxTastingsPerDay = 5;
        if (maxSoloDays < 0) maxSoloDays = 5;
    }
}
