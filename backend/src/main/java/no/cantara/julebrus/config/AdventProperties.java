package no.cantara.julebrus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The advent calendar game: daily doors with a question, late doors, the release-day countdown,
 * the shared star and the final-week blackout of the ranking.
 */
@ConfigurationProperties(prefix = "julebrus.advent")
public record AdventProperties(
        /** Doors up to and including this day can still be opened late without a key (the first days, and the days before launch). */
        LocalDate freeRevivalUntil,
        /** Vinmonopolet's Christmas beer release ("slippdagen"); Home counts down to it. */
        LocalDate releaseDay,
        /** Local time the shops open on release day. */
        LocalTime releaseTime,
        /** From the start of this day until the reveal the ranking is hidden ("mørkelagt"); null switches it off. */
        LocalDate blackoutFrom,
        /** Doors the whole house must open together to light the star and show the top ten without order. */
        int houseGoal
) {
    public AdventProperties {
        if (releaseTime == null) releaseTime = LocalTime.of(10, 0);
        if (houseGoal <= 0) houseGoal = 500;
    }
}
