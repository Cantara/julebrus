package no.cantara.julebrus.weight;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RaterWeightPolicyTest {

    @Test
    void firstTimersStartAtHalf() {
        assertThat(RaterWeightPolicy.decide(Optional.empty()).weight()).isEqualTo(0.5);
    }

    @Test
    void aNormalSeasonEarnsFullWeight() {
        var d = RaterWeightPolicy.decide(Optional.of(new RaterWeightPolicy.PreviousSeason(2025, 7, false)));
        assertThat(d.weight()).isEqualTo(1.0);
    }

    @Test
    void fewerThanSevenScoresStaysAtHalf() {
        var d = RaterWeightPolicy.decide(Optional.of(new RaterWeightPolicy.PreviousSeason(2025, 6, false)));
        assertThat(d.weight()).isEqualTo(0.5);
        assertThat(d.reason()).contains("6 terningkast");
    }

    @Test
    void riggingMutesTheNextSeasonEvenWithManyScores() {
        var d = RaterWeightPolicy.decide(Optional.of(new RaterWeightPolicy.PreviousSeason(2025, 40, true)));
        assertThat(d.weight()).isEqualTo(0.0);
    }
}
