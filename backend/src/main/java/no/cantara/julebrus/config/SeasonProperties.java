package no.cantara.julebrus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
        LocalTime finaleTime,
        /** A table with this many members or more is a big table, with its own daily limit. */
        int bigTableSize,
        /** The daily limit for a tasting at a big table. */
        int maxTastingsPerDayBigTable
) {
    @ConstructorBinding
    public SeasonProperties {
        if (zone == null) zone = ZoneId.of("Europe/Oslo");
        if (maxTastingsPerDay <= 0) maxTastingsPerDay = 5;
        if (maxSoloDays < 0) maxSoloDays = 5;
        if (finaleTime == null) finaleTime = LocalTime.of(18, 0);
        if (bigTableSize <= 0) bigTableSize = 8;
        if (maxTastingsPerDayBigTable <= 0) maxTastingsPerDayBigTable = 12;
    }

    /** The rules without the big-table pair, which then take their defaults (8 and 12). */
    public SeasonProperties(int year, LocalDate start, LocalDate end, ZoneId zone, int maxTastingsPerDay, int maxSoloDays,
                            LocalTime finaleTime) {
        this(year, start, end, zone, maxTastingsPerDay, maxSoloDays, finaleTime, 0, 0);
    }

    /** True when a table of this many people is a big table. */
    public boolean isBigTable(int tableSize) {
        return tableSize >= bigTableSize;
    }

    /** The daily limit for a tasting at a table of this many people (1 means alone). */
    public int maxTastingsPerDay(int tableSize) {
        return isBigTable(tableSize) ? maxTastingsPerDayBigTable : maxTastingsPerDay;
    }

    public Instant revealAt() {
        return end.atTime(finaleTime).atZone(zone).toInstant();
    }

    /** Podium tips close when the last day starts: the night before the reveal, Oslo time. */
    public Instant tipDeadline() {
        return end.atStartOfDay(zone).toInstant();
    }
}
