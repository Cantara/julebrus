package no.cantara.julebrus.finale;

import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.session.SessionRepository;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.stats.RankingService;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * The 23 December result: podiums and season awards. Hidden until the reveal
 * time, except for an admin preview.
 */
@Service
public class FinaleService {

    public record Totals(long tastings, long beers, long people, long sessions) {
    }

    public record Award(String id, String title, String description, String winnerName, String detail) {
    }

    public record FinaleView(int year, Instant revealAt, boolean revealed, boolean preview, Totals totals,
                             List<RankingService.BeerRank> podium, List<RankingService.DuelRank> duelPodium,
                             List<Award> awards) {
    }

    static final int MIN_RATED_FOR_OFFSET_AWARDS = 10;
    static final int MIN_PREDICTIONS = 5;

    private final SeasonProperties season;
    private final RankingService ranking;
    private final TastingRepository tastings;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final Clock clock;

    public FinaleService(SeasonProperties season, RankingService ranking, TastingRepository tastings,
                         SessionRepository sessions, UserRepository users, Clock clock) {
        this.season = season;
        this.ranking = ranking;
        this.tastings = tastings;
        this.sessions = sessions;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinaleView view(boolean preview) {
        Instant revealAt = season.revealAt();
        boolean revealed = !clock.instant().isBefore(revealAt);
        List<Tasting> all = tastings.findAll();
        Totals totals = new Totals(all.size(),
                all.stream().map(Tasting::getBeerId).distinct().count(),
                all.stream().map(Tasting::getUserId).distinct().count(),
                all.stream().map(Tasting::getSessionId).filter(java.util.Objects::nonNull).distinct().count());
        if (!revealed && !preview) {
            return new FinaleView(season.year(), revealAt, false, false, totals, null, null, null);
        }
        return new FinaleView(season.year(), revealAt, revealed, preview && !revealed, totals,
                ranking.beerRanking().stream().limit(3).toList(),
                ranking.duelRanking().stream().limit(3).toList(),
                awards(all));
    }

    List<Award> awards(List<Tasting> all) {
        Map<UUID, String> nameOf = users.findAll().stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
        Map<UUID, List<Tasting>> byUser = all.stream().collect(Collectors.groupingBy(Tasting::getUserId));
        Map<UUID, List<Tasting>> byBeer = all.stream().collect(Collectors.groupingBy(Tasting::getBeerId));
        Map<UUID, TastingSession> sessionById = sessions.findAll().stream()
                .collect(Collectors.toMap(TastingSession::getId, Function.identity()));

        List<Award> out = new ArrayList<>();

        out.add(pick("MOST_TASTINGS", "Årets smaker", "Flest terningkast i sesongen", byUser, nameOf,
                ts -> (double) ts.size(), true, ts -> ts.size() + " øl", ts -> true));

        // Signed offset from the crowd (others' mean on the same beer), per person.
        Function<List<Tasting>, List<Double>> offsets = ts -> ts.stream().map(t -> {
            List<Tasting> others = byBeer.getOrDefault(t.getBeerId(), List.of()).stream()
                    .filter(o -> !o.getUserId().equals(t.getUserId())).toList();
            if (others.isEmpty()) return null;
            return t.getScore() - others.stream().mapToInt(Tasting::getScore).average().orElse(0);
        }).filter(java.util.Objects::nonNull).toList();

        out.add(pick("CONTRARIAN", "Årets kontrær", "Lengst fra flokken, minst %d øl".formatted(MIN_RATED_FOR_OFFSET_AWARDS), byUser, nameOf,
                ts -> offsets.apply(ts).stream().mapToDouble(Math::abs).average().orElse(0), true,
                ts -> "%.2f fra snittet".formatted(offsets.apply(ts).stream().mapToDouble(Math::abs).average().orElse(0)),
                ts -> offsets.apply(ts).size() >= MIN_RATED_FOR_OFFSET_AWARDS));

        out.add(pick("GENEROUS", "Årets julenisse", "Snillest med terningen, minst %d øl".formatted(MIN_RATED_FOR_OFFSET_AWARDS), byUser, nameOf,
                ts -> offsets.apply(ts).stream().mapToDouble(d -> d).average().orElse(0), true,
                ts -> "+%.2f over snittet".formatted(offsets.apply(ts).stream().mapToDouble(d -> d).average().orElse(0)),
                ts -> offsets.apply(ts).size() >= MIN_RATED_FOR_OFFSET_AWARDS));

        out.add(pick("STRICT", "Årets strengeste", "Hardest med terningen, minst %d øl".formatted(MIN_RATED_FOR_OFFSET_AWARDS), byUser, nameOf,
                ts -> offsets.apply(ts).stream().mapToDouble(d -> d).average().orElse(0), false,
                ts -> "%.2f under snittet".formatted(offsets.apply(ts).stream().mapToDouble(d -> d).average().orElse(0)),
                ts -> offsets.apply(ts).size() >= MIN_RATED_FOR_OFFSET_AWARDS));

        // Prediction accuracy against the group average in the session where it was made.
        Function<List<Tasting>, List<Double>> predictionErrors = ts -> ts.stream()
                .filter(t -> t.getPrediction() != null && t.getSessionId() != null)
                .map(t -> {
                    double avg = byBeer.getOrDefault(t.getBeerId(), List.of()).stream()
                            .filter(o -> t.getSessionId().equals(o.getSessionId()))
                            .mapToInt(Tasting::getScore).average().orElse(Double.NaN);
                    return Double.isNaN(avg) ? null : Math.abs(t.getPrediction().doubleValue() - avg);
                }).filter(java.util.Objects::nonNull).toList();
        out.add(pick("SOMMELIER", "Årets sommelier", "Gjettet gruppens snitt best, minst %d gjetninger".formatted(MIN_PREDICTIONS), byUser, nameOf,
                ts -> predictionErrors.apply(ts).stream().mapToDouble(d -> d).average().orElse(9), false,
                ts -> "%.2f bom i snitt".formatted(predictionErrors.apply(ts).stream().mapToDouble(d -> d).average().orElse(0)),
                ts -> predictionErrors.apply(ts).size() >= MIN_PREDICTIONS));

        // Explorer: ratings given when fewer than three others had tasted the beer.
        ToDoubleFunction<List<Tasting>> explorer = ts -> ts.stream().filter(t ->
                byBeer.getOrDefault(t.getBeerId(), List.of()).stream()
                        .filter(o -> !o.getUserId().equals(t.getUserId()) && o.getTastedAt().isBefore(t.getTastedAt()))
                        .count() < 3).count();
        out.add(pick("EXPLORER", "Årets oppdager", "Flest øl smakt før flokken kom", byUser, nameOf,
                explorer::applyAsDouble, true, ts -> (int) explorer.applyAsDouble(ts) + " øl først ute", ts -> true));

        // Social: distinct people tasted with.
        Function<List<Tasting>, Set<UUID>> coTasters = ts -> {
            Set<UUID> s = new HashSet<>();
            UUID me = ts.isEmpty() ? null : ts.get(0).getUserId();
            ts.stream().map(Tasting::getSessionId).filter(java.util.Objects::nonNull).distinct()
                    .map(sessionById::get).filter(java.util.Objects::nonNull)
                    .flatMap(x -> x.getMembers().stream()).map(TastingSession.Member::userId)
                    .filter(id -> !id.equals(me)).forEach(s::add);
            return s;
        };
        out.add(pick("SOCIAL", "Årets midtpunkt", "Smakt sammen med flest forskjellige folk", byUser, nameOf,
                ts -> (double) coTasters.apply(ts).size(), true, ts -> coTasters.apply(ts).size() + " bordvenner", ts -> true));

        return out;
    }

    /** Picks the best qualifier; a tie lists everyone on it, in display-name order. */
    private static Award pick(String id, String title, String description, Map<UUID, List<Tasting>> byUser,
                              Map<UUID, String> nameOf, ToDoubleFunction<List<Tasting>> score, boolean highest,
                              Function<List<Tasting>, String> detail, java.util.function.Predicate<List<Tasting>> qualifies) {
        List<Map.Entry<UUID, List<Tasting>>> candidates = byUser.entrySet().stream()
                .filter(e -> qualifies.test(e.getValue()))
                .filter(e -> !highest || score.applyAsDouble(e.getValue()) > 0)
                .toList();
        if (candidates.isEmpty()) return new Award(id, title, description, null, "Ingen kvalifiserte i år");
        double best = candidates.stream().mapToDouble(e -> score.applyAsDouble(e.getValue()))
                .reduce(highest ? Double::max : Double::min).orElse(0);
        List<Map.Entry<UUID, List<Tasting>>> winners = candidates.stream()
                .filter(e -> Math.abs(score.applyAsDouble(e.getValue()) - best) < 1e-9)
                .sorted(Comparator.comparing(e -> nameOf.getOrDefault(e.getKey(), "?")))
                .toList();
        String names = winners.size() == 1 ? nameOf.getOrDefault(winners.get(0).getKey(), "?")
                : winners.stream().map(e -> nameOf.getOrDefault(e.getKey(), "?")).collect(Collectors.joining(", "))
                .replaceFirst(", ([^,]+)$", " og $1");
        return new Award(id, title, description, names, detail.apply(winners.get(0).getValue()));
    }
}
