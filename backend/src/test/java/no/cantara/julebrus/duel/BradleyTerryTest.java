package no.cantara.julebrus.duel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BradleyTerryTest {

    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();

    @Test
    void emptyInputGivesEmptyRanking() {
        assertThat(new BradleyTerry().fit(List.of())).isEmpty();
    }

    @Test
    void transitiveWinsProduceTheObviousOrder() {
        List<BradleyTerry.Outcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            outcomes.add(new BradleyTerry.Outcome(a, b));
            outcomes.add(new BradleyTerry.Outcome(b, c));
            outcomes.add(new BradleyTerry.Outcome(a, c));
        }
        Map<UUID, Double> p = new BradleyTerry().fit(outcomes);
        assertThat(p.keySet()).containsExactly(a, b, c);
        assertThat(p.get(a)).isGreaterThan(p.get(b));
        assertThat(p.get(b)).isGreaterThan(p.get(c));
    }

    @Test
    void anUndefeatedBeerStaysFinite() {
        Map<UUID, Double> p = new BradleyTerry().fit(List.of(
                new BradleyTerry.Outcome(a, b), new BradleyTerry.Outcome(a, b), new BradleyTerry.Outcome(a, c)));
        p.values().forEach(v -> assertThat(v).isFinite().isPositive());
        assertThat(p.keySet().iterator().next()).isEqualTo(a);
    }

    @Test
    void splitDecisionsAreRoughlyEqual() {
        Map<UUID, Double> p = new BradleyTerry().fit(List.of(
                new BradleyTerry.Outcome(a, b), new BradleyTerry.Outcome(b, a),
                new BradleyTerry.Outcome(a, b), new BradleyTerry.Outcome(b, a)));
        assertThat(p.get(a)).isCloseTo(p.get(b), org.assertj.core.data.Offset.offset(1e-6));
    }
}
