package no.cantara.julebrus.tasting;

import no.cantara.julebrus.config.SeasonProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * The season rules as a pure function. No Spring, no JPA, fully unit testable.
 *
 * <ul>
 *   <li>A tasting must fall inside the season window.</li>
 *   <li>A person may rate at most {@code maxTastingsPerDay} beers per Oslo day.</li>
 *   <li>A person may have at most {@code maxSoloDays} distinct days with a solo tasting.</li>
 *   <li>A beer is rated once per person.</li>
 * </ul>
 */
public final class TastingRules {

    public enum Violation { OUTSIDE_SEASON, DAILY_LIMIT, SOLO_DAYS_EXHAUSTED, ALREADY_RATED }

    public static final class RuleViolation extends RuntimeException {
        private final Violation violation;

        public RuleViolation(Violation violation, String message) {
            super(message);
            this.violation = violation;
        }

        public Violation violation() {
            return violation;
        }
    }

    /** What the rules need to know about the person's history. */
    public record Ledger(int tastingsToday, Set<LocalDate> soloDays, boolean alreadyRatedBeer) {
    }

    private final SeasonProperties season;

    public TastingRules(SeasonProperties season) {
        this.season = season;
    }

    public LocalDate dayOf(Instant at) {
        return at.atZone(season.zone()).toLocalDate();
    }

    public boolean inSeason(LocalDate day) {
        return !day.isBefore(season.start()) && !day.isAfter(season.end());
    }

    /**
     * @param at     when the tasting happens
     * @param solo   true when the tasting is not part of a session with other members
     * @param ledger the person's history as of now
     * @throws RuleViolation when the tasting must be refused
     */
    public void check(Instant at, boolean solo, Ledger ledger) {
        LocalDate day = dayOf(at);
        if (!inSeason(day)) {
            throw new RuleViolation(Violation.OUTSIDE_SEASON,
                    "Season runs %s to %s".formatted(season.start(), season.end()));
        }
        if (ledger.alreadyRatedBeer()) {
            throw new RuleViolation(Violation.ALREADY_RATED, "You have already rated this beer");
        }
        if (ledger.tastingsToday() >= season.maxTastingsPerDay()) {
            throw new RuleViolation(Violation.DAILY_LIMIT,
                    "Max %d tastings per day".formatted(season.maxTastingsPerDay()));
        }
        if (solo && !ledger.soloDays().contains(day) && ledger.soloDays().size() >= season.maxSoloDays()) {
            throw new RuleViolation(Violation.SOLO_DAYS_EXHAUSTED,
                    "All %d solo days are used. Find someone to taste with.".formatted(season.maxSoloDays()));
        }
    }
}
