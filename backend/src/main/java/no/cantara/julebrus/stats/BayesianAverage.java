package no.cantara.julebrus.stats;

/**
 * Shrinks a beer's mean towards the global mean when it has few ratings, so a
 * single 6 from one enthusiast does not top the list.
 */
public final class BayesianAverage {

    private final double priorMean;
    private final double priorWeight;

    public BayesianAverage(double priorMean, double priorWeight) {
        this.priorMean = priorMean;
        this.priorWeight = priorWeight;
    }

    public double of(double sum, double count) {
        return (priorWeight * priorMean + sum) / (priorWeight + count);
    }
}
