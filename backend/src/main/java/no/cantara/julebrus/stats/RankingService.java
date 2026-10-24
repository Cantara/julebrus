package no.cantara.julebrus.stats;

import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.duel.BradleyTerry;
import no.cantara.julebrus.duel.DuelRepository;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.weight.RaterWeightService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The two rankings, shared by the stats page and the finale. */
@Service
public class RankingService {

    /** How many ratings a beer needs before its own mean outweighs the prior. */
    static final double PRIOR_WEIGHT = 5.0;
    static final double SCALE_MIDPOINT = 3.5;

    /**
     * {@code ratings} counts people; {@code effectiveVotes} is the sum of their weights.
     * {@code mean} and {@code bayesian} are weighted, so a rater with weight 0 is
     * listed among the ratings but moves nothing.
     */
    public record BeerRank(int rank, UUID beerId, String name, String brewery, long ratings, double effectiveVotes,
                           double mean, double bayesian, double controversy) {
    }

    public record DuelRank(int rank, UUID beerId, String name, String brewery, double strength) {
    }

    private final BeerRepository beers;
    private final TastingRepository tastings;
    private final DuelRepository duels;
    private final RaterWeightService weights;

    public RankingService(BeerRepository beers, TastingRepository tastings, DuelRepository duels, RaterWeightService weights) {
        this.beers = beers;
        this.tastings = tastings;
        this.duels = duels;
        this.weights = weights;
    }

    /** The official ranking: weighted 1 to 6 scores, shrunk towards the season mean. */
    public List<BeerRank> beerRanking() {
        Map<UUID, Double> weightOf = weights.weightsByUserId();
        Map<UUID, Beer> beerById = beerIndex();
        // Only approved beers count; a pending or rejected one is left out until an admin says yes.
        List<Tasting> all = tastings.findAll().stream()
                .filter(t -> beerById.get(t.getBeerId()) != null && beerById.get(t.getBeerId()).isApproved()).toList();

        double totalW = all.stream().mapToDouble(t -> weightOf.getOrDefault(t.getUserId(), 0.0)).sum();
        double globalMean = totalW == 0 ? SCALE_MIDPOINT
                : all.stream().mapToDouble(t -> t.getScore() * weightOf.getOrDefault(t.getUserId(), 0.0)).sum() / totalW;
        BayesianAverage bayes = new BayesianAverage(globalMean, PRIOR_WEIGHT);

        List<BeerRank> ranked = all.stream().collect(Collectors.groupingBy(Tasting::getBeerId)).entrySet().stream()
                .map(e -> {
                    List<Tasting> ts = e.getValue();
                    double w = ts.stream().mapToDouble(t -> weightOf.getOrDefault(t.getUserId(), 0.0)).sum();
                    double sum = ts.stream().mapToDouble(t -> t.getScore() * weightOf.getOrDefault(t.getUserId(), 0.0)).sum();
                    double mean = w == 0 ? Double.NaN : sum / w;
                    double variance = w == 0 ? 0 : Math.max(0, ts.stream()
                            .mapToDouble(t -> weightOf.getOrDefault(t.getUserId(), 0.0) * Math.pow(t.getScore() - mean, 2)).sum() / w);
                    Beer b = beerById.get(e.getKey());
                    return new BeerRank(0, e.getKey(), name(b), brewery(b), ts.size(), w,
                            w == 0 ? 0 : mean, bayes.of(sum, w), Math.sqrt(variance));
                })
                .sorted(Comparator.comparingDouble(BeerRank::bayesian).reversed()
                        .thenComparing(Comparator.comparingDouble(BeerRank::effectiveVotes).reversed()))
                .toList();
        return withRank(ranked, (r, i) -> new BeerRank(i, r.beerId(), r.name(), r.brewery(), r.ratings(),
                r.effectiveVotes(), r.mean(), r.bayesian(), r.controversy()));
    }

    /** The sidebar: pairwise duels, independent of how generous each rater is. */
    public List<DuelRank> duelRanking() {
        Map<UUID, Beer> beerById = beerIndex();
        List<BradleyTerry.Outcome> outcomes = duels.findAll().stream()
                .filter(d -> beerById.getOrDefault(d.getWinnerBeerId(), null) != null && beerById.get(d.getWinnerBeerId()).isApproved()
                        && beerById.getOrDefault(d.getLoserBeerId(), null) != null && beerById.get(d.getLoserBeerId()).isApproved())
                .map(d -> new BradleyTerry.Outcome(d.getWinnerBeerId(), d.getLoserBeerId()))
                .toList();
        List<DuelRank> ranked = new BradleyTerry().fit(outcomes).entrySet().stream()
                .map(e -> new DuelRank(0, e.getKey(), name(beerById.get(e.getKey())),
                        brewery(beerById.get(e.getKey())), e.getValue()))
                .toList();
        return withRank(ranked, (r, i) -> new DuelRank(i, r.beerId(), r.name(), r.brewery(), r.strength()));
    }

    private Map<UUID, Beer> beerIndex() {
        return beers.findAll().stream().collect(Collectors.toMap(Beer::getId, Function.identity()));
    }

    private static String name(Beer b) {
        return b == null ? "?" : b.getName();
    }

    private static String brewery(Beer b) {
        return b == null ? null : b.getBrewery();
    }

    private interface Ranker<T> {
        T apply(T item, int rank);
    }

    private static <T> List<T> withRank(List<T> items, Ranker<T> ranker) {
        return java.util.stream.IntStream.range(0, items.size())
                .mapToObj(i -> ranker.apply(items.get(i), i + 1))
                .toList();
    }
}
