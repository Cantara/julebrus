package no.cantara.julebrus.history;

import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
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
