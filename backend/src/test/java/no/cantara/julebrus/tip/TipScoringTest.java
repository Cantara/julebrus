package no.cantara.julebrus.tip;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TipScoringTest {

    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();

    @Test
    void threeForTheRightSpotOneForThePodiumNothingForTheRest() {
        List<UUID> podium = List.of(a, b, c);
        assertThat(TipScoring.points(List.of(a, b, c), podium)).isEqualTo(9);
        assertThat(TipScoring.points(List.of(c, b, a), podium)).isEqualTo(3 + 1 + 1);
        assertThat(TipScoring.points(List.of(a, d, b), podium)).isEqualTo(3 + 0 + 1);
        assertThat(TipScoring.points(List.of(d, d, d), podium)).isZero();
    }

    @Test
    void aShortPodiumStillScoresWhatItHas() {
        assertThat(TipScoring.points(List.of(a, b, c), List.of(a))).isEqualTo(3);
        assertThat(TipScoring.points(List.of(b, a, c), List.of(a))).isEqualTo(1);
        assertThat(TipScoring.points(List.of(a, b, c), List.of())).isZero();
    }
}
