package no.cantara.julebrus.weight;

import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.history.HistoricRatingRepository;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Looks up, or decides and stores, a person's weight for the current season. Once
 * stored it stays put for the season, so a weight never changes under someone's
 * feet mid-December. Admins can override or flag through {@code AdminController}.
 */
@Service
public class RaterWeightService {

    private final RaterWeightRepository weights;
    private final HistoricRatingRepository history;
    private final TastingRepository tastings;
    private final UserRepository users;
    private final SeasonProperties season;

    public RaterWeightService(RaterWeightRepository weights, HistoricRatingRepository history,
                              TastingRepository tastings, UserRepository users, SeasonProperties season) {
        this.weights = weights;
        this.history = history;
        this.tastings = tastings;
        this.users = users;
        this.season = season;
    }

    /**
     * Deliberately not transactional: a person's first requests can arrive together, the
     * losing insert hits the unique (season, email) and must not poison a surrounding
     * transaction. The loser re-reads the winner's row.
     *
     * <p>WARNING: do not call this from a {@code @Transactional} method: the failed insert would
     * mark that transaction rollback-only.</p>
     */
    public RaterWeight current(User user) {
        return weights.findBySeasonYearAndEmailIgnoreCase(season.year(), user.getEmail())
                .orElseGet(() -> {
                    RaterWeightPolicy.Decision d = RaterWeightPolicy.decide(mostRecentPreviousSeason(user));
                    try {
                        return weights.saveAndFlush(new RaterWeight(season.year(), user.getEmail(), d.weight(), d.reason()));
                    } catch (org.springframework.dao.DataIntegrityViolationException raced) {
                        return weights.findBySeasonYearAndEmailIgnoreCase(season.year(), user.getEmail()).orElseThrow(() -> raced);
                    }
                });
    }

    /** Weights by user id for this season's averages. Users without a stored row get one. */
    public Map<UUID, Double> weightsByUserId() {
        Map<UUID, Double> out = new HashMap<>();
        for (User u : users.findAll()) out.put(u.getId(), current(u).weight());
        return out;
    }

    @Transactional
    public RaterWeight flag(String email, int seasonYear, boolean flagged, String note) {
        RaterWeight w = weights.findBySeasonYearAndEmailIgnoreCase(seasonYear, email)
                .orElseGet(() -> weights.save(new RaterWeight(seasonYear, email, RaterWeightPolicy.FULL, "Created by admin")));
        w.flag(flagged, note);
        return w;
    }

    @Transactional
    public RaterWeight override(String email, int seasonYear, double weight, String reason) {
        RaterWeight w = weights.findBySeasonYearAndEmailIgnoreCase(seasonYear, email)
                .orElseGet(() -> weights.save(new RaterWeight(seasonYear, email, weight, reason)));
        w.override(weight, reason);
        return w;
    }

    private Optional<RaterWeightPolicy.PreviousSeason> mostRecentPreviousSeason(User user) {
        TreeSet<Integer> years = new TreeSet<>(history.findSeasonYearsByEmail(user.getEmail()));
        years.addAll(tastings.findSeasonYears(user.getId()));
        Integer previous = years.lower(season.year());
        if (previous == null) return Optional.empty();

        int scores = history.countBySeasonYearAndRaterEmailIgnoreCase(previous, user.getEmail())
                + tastings.countByUserIdAndTastedDayBetween(user.getId(),
                LocalDate.of(previous, 1, 1), LocalDate.of(previous, 12, 31));
        boolean flagged = weights.findBySeasonYearAndEmailIgnoreCase(previous, user.getEmail())
                .map(RaterWeight::isFlagged).orElse(false);
        return Optional.of(new RaterWeightPolicy.PreviousSeason(previous, scores, flagged));
    }
}
