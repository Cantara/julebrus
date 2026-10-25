package no.cantara.julebrus.history;

import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    public record SeasonView(int year, String title, BigDecimal scaleMin, BigDecimal scaleMax,
                             String weightingDescription, String sourceUrl) {
        static SeasonView of(HistoricSeason s) {
            return new SeasonView(s.getYear(), s.getTitle(), s.getScaleMin(), s.getScaleMax(),
                    s.getWeightingDescription(), s.getSourceUrl());
        }
    }

    /** Original score and weight, plus the score mapped onto 1–6 for comparison. */
    public record RatingView(int year, String beerName, String brewery, String raterName, String raterKey,
                             BigDecimal score, BigDecimal weight, double normalised, String notes) {
    }

    /** One historic person after name joining, with whether a login is attached. */
    public record RaterView(String name, boolean linked, long ratings, int firstYear, int lastYear) {
    }

    /** One beer in one season: weighted mean on the original scale and on 1–6. */
    public record BeerSeasonView(int year, String beerName, String brewery, long ratings, double weightedMean,
                                 double normalisedMean) {
    }

    private final HistoricSeasonRepository seasons;
    private final HistoricRatingRepository ratings;
    private final CurrentUser currentUser;
    private final ClaimService claims;

    public HistoryController(HistoricSeasonRepository seasons, HistoricRatingRepository ratings, CurrentUser currentUser,
                             ClaimService claims) {
        this.seasons = seasons;
        this.ratings = ratings;
        this.currentUser = currentUser;
        this.claims = claims;
    }

    @GetMapping("/seasons")
    public List<SeasonView> seasons() {
        return seasons.findAll().stream().map(SeasonView::of)
                .sorted((a, b) -> Integer.compare(b.year(), a.year())).toList();
    }

    /**
     * The logged-in person's own ratings from earlier years: matched on email, plus the
     * every rating of any person (rater_key) an admin has approved for them. One query, so each rating appears once.
     */
    @GetMapping("/me")
    public List<RatingView> mine() {
        User me = currentUser.get();
        Map<Integer, HistoricSeason> byYear = seasonIndex();
        Set<String> keys = claims.approvedKeysOf(me.getId());
        List<HistoricRating> found = keys.isEmpty()
                ? ratings.findByRaterEmailIgnoreCaseOrderBySeasonYearDescBeerNameAsc(me.getEmail())
                : ratings.findMineByEmailOrApprovedKeys(me.getEmail(), keys);
        return found.stream().map(r -> view(r, byYear)).toList();
    }

    /** Everything said about one beer in earlier years, matched on name. */
    @GetMapping("/beers")
    public List<RatingView> beer(@RequestParam String name) {
        Map<Integer, HistoricSeason> byYear = seasonIndex();
        return ratings.findByBeerNameIgnoreCaseOrderBySeasonYearDesc(name).stream().map(r -> view(r, byYear)).toList();
    }

    /** A season's result list, weighted the way it was weighted at the time. */
    @GetMapping("/seasons/{year}")
    public List<BeerSeasonView> season(@PathVariable int year) {
        HistoricSeason season = seasons.findById(year)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
        return seasonResults(season, ratings.findBySeasonYearOrderByBeerNameAsc(year));
    }

    /** Who has tasted over the years, joined by raters.csv. Emails are not exposed. */
    @GetMapping("/raters")
    public List<RaterView> raters() {
        return ratings.summariseRaters().stream()
                .map(r -> new RaterView(r.raterKey(), r.raterEmail() != null, r.ratings(), r.firstYear(), r.lastYear()))
                .toList();
    }

    /**
     * One beer across every recorded season: all its votes on 1–6, weighted as each year weighted
     * them, shrunk towards the all-time mean the same way the season ranking is (prior weight 5
     * votes) so a beer with a handful of votes does not top the list on luck.
     */
    public record AllTimeRow(int rank, String beerName, String brewery, List<Integer> years, long ratings,
                             double weightSum, double mean, double bayesian, int bestYear, double bestMean) {
    }

    /** Beers with at least {@code min} votes over the years, best first. */
    @GetMapping("/alltime")
    public List<AllTimeRow> allTime(@RequestParam(defaultValue = "5") int min) {
        return allTime(ratings.findAll(), seasonIndex(), Math.max(1, min));
    }

    static List<AllTimeRow> allTime(List<HistoricRating> all, Map<Integer, HistoricSeason> byYear, int min) {
        record Vote(String name, String brewery, int year, double score, double weight) {
        }
        List<Vote> votes = all.stream().map(r -> {
            HistoricSeason s = byYear.get(r.getSeasonYear());
            double score = s == null ? r.getScore().doubleValue() : s.normalise(r.getScore());
            return new Vote(r.getBeerName(), r.getBrewery(), r.getSeasonYear(), score, r.getWeight().doubleValue());
        }).toList();
        double totalW = votes.stream().mapToDouble(Vote::weight).sum();
        double allTimeMean = totalW == 0 ? 3.5 : votes.stream().mapToDouble(v -> v.score() * v.weight()).sum() / totalW;
        var bayes = new no.cantara.julebrus.stats.BayesianAverage(allTimeMean, 5.0);
        Map<String, List<Vote>> perBeer = votes.stream().collect(Collectors.groupingBy(v -> v.name().toLowerCase(java.util.Locale.ROOT),
                java.util.LinkedHashMap::new, Collectors.toList()));
        List<AllTimeRow> rows = new ArrayList<>();
        for (List<Vote> vs : perBeer.values()) {
            if (vs.size() < min) continue;
            double w = vs.stream().mapToDouble(Vote::weight).sum();
            if (w == 0) continue;
            double sum = vs.stream().mapToDouble(v -> v.score() * v.weight()).sum();
            Map<Integer, List<Vote>> perYear = vs.stream().collect(Collectors.groupingBy(Vote::year, java.util.TreeMap::new, Collectors.toList()));
            int bestYear = 0;
            double bestMean = -1;
            for (var e : perYear.entrySet()) {
                double yw = e.getValue().stream().mapToDouble(Vote::weight).sum();
                if (yw == 0) continue;
                double ym = e.getValue().stream().mapToDouble(v -> v.score() * v.weight()).sum() / yw;
                if (ym > bestMean) {
                    bestMean = ym;
                    bestYear = e.getKey();
                }
            }
            Vote first = vs.get(0);
            String brewery = vs.stream().map(Vote::brewery).filter(b -> b != null && !b.isBlank()).findFirst().orElse(null);
            rows.add(new AllTimeRow(0, first.name(), brewery, List.copyOf(perYear.keySet()), vs.size(),
                    Math.round(w * 100) / 100.0, Math.round(sum / w * 100) / 100.0, Math.round(bayes.of(sum, w) * 1000) / 1000.0,
                    bestYear, Math.round(bestMean * 100) / 100.0));
        }
        rows.sort(java.util.Comparator.comparingDouble(AllTimeRow::bayesian).reversed()
                .thenComparing(java.util.Comparator.comparingDouble(AllTimeRow::weightSum).reversed()).thenComparing(AllTimeRow::beerName));
        List<AllTimeRow> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            AllTimeRow r = rows.get(i);
            ranked.add(new AllTimeRow(i + 1, r.beerName(), r.brewery(), r.years(), r.ratings(), r.weightSum(), r.mean(), r.bayesian(),
                    r.bestYear(), r.bestMean()));
        }
        return ranked;
    }

    /** One row per season with its size and winner, for the history page and its charts. */
    public record SeasonOverview(int year, String title, long raters, long beers, long votes,
                                 String winnerName, Double winnerWeightedMean, Double winnerNormalisedMean,
                                 String weightingDescription, String sourceUrl) {
    }

    @GetMapping("/overview")
    public List<SeasonOverview> overview() {
        Map<Integer, List<HistoricRating>> byYear = ratings.findAll().stream()
                .collect(Collectors.groupingBy(HistoricRating::getSeasonYear));
        return seasons.findAll().stream()
                .sorted((a, b) -> Integer.compare(b.getYear(), a.getYear()))
                .map(season -> {
                    List<HistoricRating> rs = byYear.getOrDefault(season.getYear(), List.of());
                    BeerSeasonView top = seasonResults(season, rs).stream().findFirst().orElse(null);
                    return new SeasonOverview(season.getYear(), season.getTitle(),
                            rs.stream().map(r -> r.getRaterKey() == null ? r.getRaterName() : r.getRaterKey()).distinct().count(),
                            rs.stream().map(r -> r.getBeerName().toLowerCase()).distinct().count(),
                            rs.size(),
                            top == null ? null : top.beerName(),
                            top == null ? null : top.weightedMean(),
                            top == null ? null : top.normalisedMean(),
                            season.getWeightingDescription(), season.getSourceUrl());
                })
                .toList();
    }

    /** Per season: how the logged-in person scored against everyone else, on 1–6. */
    public record MySeason(int year, long myRatings, double myMean, double crowdMean) {
    }

    @GetMapping("/me/summary")
    public List<MySeason> mySummary() {
        Map<Integer, HistoricSeason> byYear = seasonIndex();
        List<RatingView> mine = mine();
        if (mine.isEmpty()) return List.of();
        Map<Integer, List<HistoricRating>> all = ratings.findAll().stream()
                .collect(Collectors.groupingBy(HistoricRating::getSeasonYear));
        return mine.stream().collect(Collectors.groupingBy(RatingView::year)).entrySet().stream()
                .map(e -> {
                    HistoricSeason season = byYear.get(e.getKey());
                    double myMean = e.getValue().stream().mapToDouble(RatingView::normalised).average().orElse(0);
                    List<HistoricRating> rs = all.getOrDefault(e.getKey(), List.of());
                    double w = rs.stream().mapToDouble(r -> r.getWeight().doubleValue()).sum();
                    double crowd = w == 0 ? 0 : rs.stream()
                            .mapToDouble(r -> (season == null ? r.getScore().doubleValue() : season.normalise(r.getScore())) * r.getWeight().doubleValue())
                            .sum() / w;
                    return new MySeason(e.getKey(), e.getValue().size(), myMean, crowd);
                })
                .sorted(java.util.Comparator.comparingInt(MySeason::year))
                .toList();
    }

    private List<BeerSeasonView> seasonResults(HistoricSeason season, List<HistoricRating> rows) {
        Map<String, List<HistoricRating>> byBeer = rows.stream()
                .collect(Collectors.groupingBy(r -> r.getBeerName().toLowerCase(), java.util.LinkedHashMap::new, Collectors.toList()));
        return byBeer.values().stream().map(rs -> {
            double w = rs.stream().mapToDouble(r -> r.getWeight().doubleValue()).sum();
            if (w == 0) return null;
            double weighted = rs.stream().mapToDouble(r -> r.getScore().doubleValue() * r.getWeight().doubleValue()).sum() / w;
            double normalised = rs.stream().mapToDouble(r -> season.normalise(r.getScore()) * r.getWeight().doubleValue()).sum() / w;
            HistoricRating first = rs.get(0);
            return new BeerSeasonView(season.getYear(), first.getBeerName(), first.getBrewery(), rs.size(), weighted, normalised);
        }).filter(java.util.Objects::nonNull)
          .sorted((a, b) -> Double.compare(b.weightedMean(), a.weightedMean())).toList();
    }

    private Map<Integer, HistoricSeason> seasonIndex() {
        return seasons.findAll().stream().collect(Collectors.toMap(HistoricSeason::getYear, Function.identity()));
    }

    private static RatingView view(HistoricRating r, Map<Integer, HistoricSeason> byYear) {
        HistoricSeason s = byYear.get(r.getSeasonYear());
        double normalised = s == null ? r.getScore().doubleValue() : s.normalise(r.getScore());
        return new RatingView(r.getSeasonYear(), r.getBeerName(), r.getBrewery(), r.getRaterName(),
                r.getRaterKey() == null ? r.getRaterName() : r.getRaterKey(), r.getScore(),
                r.getWeight(), normalised, r.getNotes());
    }
}
