package no.cantara.julebrus.history;

import no.cantara.julebrus.user.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
    public record RatingView(int year, String beerName, String brewery, String raterName, BigDecimal score,
                             BigDecimal weight, double normalised, String notes) {
    }

    /** One beer in one season: weighted mean on the original scale and on 1–6. */
    public record BeerSeasonView(int year, String beerName, String brewery, long ratings, double weightedMean,
                                 double normalisedMean) {
    }

    private final HistoricSeasonRepository seasons;
    private final HistoricRatingRepository ratings;
    private final CurrentUser currentUser;

    public HistoryController(HistoricSeasonRepository seasons, HistoricRatingRepository ratings, CurrentUser currentUser) {
        this.seasons = seasons;
        this.ratings = ratings;
        this.currentUser = currentUser;
    }

    @GetMapping("/seasons")
    public List<SeasonView> seasons() {
        return seasons.findAll().stream().map(SeasonView::of)
                .sorted((a, b) -> Integer.compare(b.year(), a.year())).toList();
    }

    /** The logged-in person's own ratings from earlier years, matched on email. */
    @GetMapping("/me")
    public List<RatingView> mine() {
        Map<Integer, HistoricSeason> byYear = seasonIndex();
        return ratings.findByRaterEmailIgnoreCaseOrderBySeasonYearDescBeerNameAsc(currentUser.get().getEmail()).stream()
                .map(r -> view(r, byYear)).toList();
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
        Map<String, List<HistoricRating>> byBeer = ratings.findBySeasonYearOrderByBeerNameAsc(year).stream()
                .collect(Collectors.groupingBy(r -> r.getBeerName().toLowerCase(), java.util.LinkedHashMap::new, Collectors.toList()));
        return byBeer.values().stream().map(rs -> {
            double w = rs.stream().mapToDouble(r -> r.getWeight().doubleValue()).sum();
            double weighted = rs.stream().mapToDouble(r -> r.getScore().doubleValue() * r.getWeight().doubleValue()).sum() / w;
            double normalised = rs.stream().mapToDouble(r -> season.normalise(r.getScore()) * r.getWeight().doubleValue()).sum() / w;
            HistoricRating first = rs.get(0);
            return new BeerSeasonView(year, first.getBeerName(), first.getBrewery(), rs.size(), weighted, normalised);
        }).sorted((a, b) -> Double.compare(b.weightedMean(), a.weightedMean())).toList();
    }

    private Map<Integer, HistoricSeason> seasonIndex() {
        return seasons.findAll().stream().collect(Collectors.toMap(HistoricSeason::getYear, Function.identity()));
    }

    private static RatingView view(HistoricRating r, Map<Integer, HistoricSeason> byYear) {
        HistoricSeason s = byYear.get(r.getSeasonYear());
        double normalised = s == null ? r.getScore().doubleValue() : s.normalise(r.getScore());
        return new RatingView(r.getSeasonYear(), r.getBeerName(), r.getBrewery(), r.getRaterName(), r.getScore(),
                r.getWeight(), normalised, r.getNotes());
    }
}
