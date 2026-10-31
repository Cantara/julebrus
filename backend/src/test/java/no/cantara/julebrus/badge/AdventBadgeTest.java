package no.cantara.julebrus.badge;

import no.cantara.julebrus.config.SeasonProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The Advent badge counts doors, not beers: every door from 1 to 23 December, without a single roll. */
class AdventBadgeTest {

    final BadgeEvaluator evaluator = new BadgeEvaluator(new SeasonProperties(2026, LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 12, 23), ZoneId.of("Europe/Oslo"), 5, 5, LocalTime.of(18, 0)));

    static BadgeEvaluator.Context withDoors(Set<LocalDate> doors) {
        return new BadgeEvaluator.Context(List.of(), 0, Set.of(), Map.of(), Map.of(), Map.of(), 0, doors);
    }

    @Test
    void everyDoorInDecemberEarnsItWithoutABeer() {
        Set<LocalDate> doors = new HashSet<>();
        for (LocalDate d = LocalDate.of(2026, 12, 1); !d.isAfter(LocalDate.of(2026, 12, 23)); d = d.plusDays(1)) doors.add(d);
        assertThat(evaluator.evaluate(withDoors(doors))).containsExactly(Badge.ADVENT);
        doors.remove(LocalDate.of(2026, 12, 12));
        assertThat(evaluator.evaluate(withDoors(doors))).doesNotContain(Badge.ADVENT);
        assertThat(evaluator.evaluate(new BadgeEvaluator.Context(List.of(), 0, Set.of(), Map.of(), Map.of(), Map.of(), 0)))
                .isEmpty();
    }
}
