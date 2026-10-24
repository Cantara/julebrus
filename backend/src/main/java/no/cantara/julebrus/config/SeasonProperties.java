package no.cantara.julebrus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
        int maxSoloDays,
        /** Local time on the last day when the final result is revealed. */
        LocalTime finaleTime
) {
    public SeasonProperties {
        if (zone == null) zone = ZoneId.of("Europe/Oslo");
        if (maxTastingsPerDay <= 0) maxTastingsPerDay = 5;
        if (maxSoloDays < 0) maxSoloDays = 5;
        if (finaleTime == null) finaleTime = LocalTime.of(18, 0);
    }

    public Instant revealAt() {
        return end.atTime(finaleTime).atZone(zone).toInstant();
    }
}
