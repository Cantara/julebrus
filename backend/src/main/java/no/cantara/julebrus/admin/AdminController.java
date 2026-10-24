package no.cantara.julebrus.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.weight.RaterWeight;
import no.cantara.julebrus.weight.RaterWeightService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The jury room. Lists every rater this season with the signals that smell like
 * rigging, and lets an admin flag a season or override a weight. Flagging is a
 * human decision; the detector only points.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record RaterOverview(String email, String displayName, int scores, double weight, String reason,
                                boolean flagged, String flagNote,
                                /** share of scores that are 1 or 6 */ double extremeShare,
                                /** largest |brewery mean - own mean| over breweries with 3+ scores */ double breweryBias,
                                String mostFavouredBrewery) {
    }

    public record FlagRequest(@NotBlank String email, Integer seasonYear, boolean flagged, String note) {
    }

    public record OverrideRequest(@NotBlank String email, Integer seasonYear,
                                  @DecimalMin("0.0") @DecimalMax("1.0") double weight, @NotBlank String reason) {
    }

    private final CurrentUser currentUser;
    private final AuthProperties auth;
    private final SeasonProperties season;
    private final UserRepository users;
    private final TastingRepository tastings;
    private final BeerRepository beers;
    private final RaterWeightService weights;

    public AdminController(CurrentUser currentUser, AuthProperties auth, SeasonProperties season, UserRepository users,
                           TastingRepository tastings, BeerRepository beers, RaterWeightService weights) {
        this.currentUser = currentUser;
        this.auth = auth;
        this.season = season;
        this.users = users;
        this.tastings = tastings;
        this.beers = beers;
        this.weights = weights;
    }

    private void requireAdmin() {
        if (!auth.isAdmin(currentUser.get().getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only");
        }
    }

    @GetMapping("/raters")
    public List<RaterOverview> raters() {
        requireAdmin();
        Map<UUID, Beer> beerById = beers.findAll().stream().collect(Collectors.toMap(Beer::getId, Function.identity()));
        Map<UUID, List<Tasting>> byUser = tastings.findAll().stream().collect(Collectors.groupingBy(Tasting::getUserId));

        return users.findAll().stream().map(u -> {
            List<Tasting> mine = byUser.getOrDefault(u.getId(), List.of());
            RaterWeight w = weights.current(u);
            double extreme = mine.isEmpty() ? 0 : mine.stream().filter(t -> t.getScore() == 1 || t.getScore() == 6).count() / (double) mine.size();
            double own = mine.stream().mapToInt(Tasting::getScore).average().orElse(0);
            Map<String, Double> byBrewery = mine.stream()
                    .filter(t -> beerById.get(t.getBeerId()) != null && beerById.get(t.getBeerId()).getBrewery() != null)
                    .collect(Collectors.groupingBy(t -> beerById.get(t.getBeerId()).getBrewery(),
                            Collectors.collectingAndThen(Collectors.toList(),
                                    l -> l.size() >= 3 ? l.stream().mapToInt(Tasting::getScore).average().orElse(own) - own : 0.0)));
            var top = byBrewery.entrySet().stream().max(Comparator.comparingDouble(e -> Math.abs(e.getValue())));
            return new RaterOverview(u.getEmail(), u.getDisplayName(), mine.size(), w.weight(), w.getReason(),
                    w.isFlagged(), w.getFlagNote(), extreme,
                    top.map(e -> Math.abs(e.getValue())).orElse(0.0), top.map(Map.Entry::getKey).orElse(null));
        }).sorted(Comparator.comparingDouble(RaterOverview::breweryBias).reversed()).toList();
    }

    @PostMapping("/raters/flag")
    public RaterWeight flag(@Valid @RequestBody FlagRequest in) {
        requireAdmin();
        return weights.flag(in.email(), in.seasonYear() == null ? season.year() : in.seasonYear(), in.flagged(), in.note());
    }

    @PostMapping("/raters/override")
    public RaterWeight override(@Valid @RequestBody OverrideRequest in) {
        requireAdmin();
        return weights.override(in.email(), in.seasonYear() == null ? season.year() : in.seasonYear(), in.weight(), in.reason());
    }
}
