package no.cantara.julebrus.tasting;

import no.cantara.julebrus.config.SeasonProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TastingRulesTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private final SeasonProperties season = new SeasonProperties(
            2026, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 23), OSLO, 5, 5, null);
    private final TastingRules rules = new TastingRules(season);

    private static Instant at(String osloLocal) {
        return java.time.LocalDateTime.parse(osloLocal).atZone(OSLO).toInstant();
    }

    private static TastingRules.Ledger ledger(int today, Set<LocalDate> soloDays) {
        return new TastingRules.Ledger(today, soloDays, false);
    }

    @Test
    void acceptsAnOrdinaryGroupTasting() {
        assertThatCode(() -> rules.check(at("2026-12-05T19:00"), false, ledger(2, Set.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesBeforeAndAfterTheSeason() {
        assertThatThrownBy(() -> rules.check(at("2026-10-31T23:59"), false, ledger(0, Set.of())))
                .isInstanceOfSatisfying(TastingRules.RuleViolation.class,
                        v -> org.assertj.core.api.Assertions.assertThat(v.violation()).isEqualTo(TastingRules.Violation.OUTSIDE_SEASON));
        assertThatThrownBy(() -> rules.check(at("2026-12-24T00:01"), false, ledger(0, Set.of())))
                .isInstanceOf(TastingRules.RuleViolation.class);
        assertThatCode(() -> rules.check(at("2026-12-23T23:59"), false, ledger(0, Set.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void dayBoundaryIsOsloNotUtc() {
        // 2026-10-31T23:30 UTC is already 1 Nov 00:30 in Oslo.
        Instant lateUtc = Instant.parse("2026-10-31T23:30:00Z");
        assertThatCode(() -> rules.check(lateUtc, false, ledger(0, Set.of()))).doesNotThrowAnyException();
    }

    @Test
    void fifthTastingIsFineSixthIsRefused() {
        assertThatCode(() -> rules.check(at("2026-11-10T20:00"), false, ledger(4, Set.of())))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> rules.check(at("2026-11-10T20:00"), false, ledger(5, Set.of())))
                .isInstanceOfSatisfying(TastingRules.RuleViolation.class,
                        v -> org.assertj.core.api.Assertions.assertThat(v.violation()).isEqualTo(TastingRules.Violation.DAILY_LIMIT));
    }

    @Test
    void soloDaysCapDistinctDaysNotTastings() {
        Set<LocalDate> fiveDays = Set.of(
                LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6));

        // Another solo tasting on a day that is already a solo day is fine.
        assertThatCode(() -> rules.check(at("2026-11-06T21:00"), true, ledger(1, fiveDays)))
                .doesNotThrowAnyException();
        // A solo tasting on a sixth day is not.
        assertThatThrownBy(() -> rules.check(at("2026-11-07T21:00"), true, ledger(0, fiveDays)))
                .isInstanceOfSatisfying(TastingRules.RuleViolation.class,
                        v -> org.assertj.core.api.Assertions.assertThat(v.violation()).isEqualTo(TastingRules.Violation.SOLO_DAYS_EXHAUSTED));
        // A group tasting on that sixth day is always fine.
        assertThatCode(() -> rules.check(at("2026-11-07T21:00"), false, ledger(0, fiveDays)))
                .doesNotThrowAnyException();
    }

    @Test
    void eachBeerIsRatedOnce() {
        assertThatThrownBy(() -> rules.check(at("2026-11-10T20:00"), false,
                new TastingRules.Ledger(0, Set.of(), true)))
                .isInstanceOfSatisfying(TastingRules.RuleViolation.class,
                        v -> org.assertj.core.api.Assertions.assertThat(v.violation()).isEqualTo(TastingRules.Violation.ALREADY_RATED));
    }
}
