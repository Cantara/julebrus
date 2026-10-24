package no.cantara.julebrus.stats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BayesianAverageTest {

    private final BayesianAverage bayes = new BayesianAverage(3.5, 5);

    @Test
    void oneSixDoesNotBeatManyFives() {
        double lonelySix = bayes.of(6, 1);
        double solidFives = bayes.of(5 * 12, 12);
        assertThat(solidFives).isGreaterThan(lonelySix);
    }

    @Test
    void convergesToThePlainMeanWithManyRatings() {
        assertThat(bayes.of(5.0 * 1000, 1000)).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void noRatingsGivesThePrior() {
        assertThat(bayes.of(0, 0)).isEqualTo(3.5);
    }
}
