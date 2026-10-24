package no.cantara.julebrus.finale;

import no.cantara.julebrus.blind.BlindService;
import no.cantara.julebrus.blind.BlindTally;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.session.SessionRepository;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.stats.RankingService;
import no.cantara.julebrus.team.Team;
import no.cantara.julebrus.team.TeamService;
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

    /** One team's own number one, by the same Bayesian ranking over that team's raters only. */
    public record TeamWinner(String slug, String team, UUID beerId, String beerName, String brewery, double bayesian,
                             long ratings) {
    }

    public record FinaleView(int year, Instant revealAt, boolean revealed, boolean preview, Totals totals,
                             List<RankingService.BeerRank> podium, List<RankingService.DuelRank> duelPodium,
                             List<Award> awards, List<TeamWinner> teamWinners) {
    }

    static final int MIN_RATED_FOR_OFFSET_AWARDS = 10;
    static final int MIN_PREDICTIONS = 5;
    static final int MIN_BLIND_GUESSES = 3;

    private final SeasonProperties season;
    private final RankingService ranking;
    private final TastingRepository tastings;
    private final SessionRepository sessions;
    private final UserRepository users;
    private final BlindService blind;
    private final TeamService teams;
    private final Clock clock;

    public FinaleService(SeasonProperties season, RankingService ranking, TastingRepository tastings,
                         SessionRepository sessions, UserRepository users, BlindService blind, TeamService teams,
                         Clock clock) {
        this.season = season;
        this.ranking = ranking;
        this.tastings = tastings;
        this.sessions = sessions;
        this.users = users;
        this.blind = blind;
        this.teams = teams;
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
            return new FinaleView(season.year(), revealAt, false, false, totals, null, null, null, null);
        }
        return new FinaleView(season.year(), revealAt, revealed, preview && !revealed, totals,
                ranking.beerRanking().stream().limit(3).toList(),
                ranking.duelRanking().stream().limit(3).toList(),
                awards(all), teamWinners());
    }

    /** Teams with at least one ranked beer, in name order. */
    List<TeamWinner> teamWinners() {
        List<TeamWinner> out = new ArrayList<>();
        for (Team t : teams.enabled()) {
            Set<UUID> raters = t.getMembers().stream().map(Team.Member::userId).collect(Collectors.toSet());
            if (raters.isEmpty()) continue;
            ranking.beerRanking(raters).stream().findFirst().ifPresent(top -> out.add(new TeamWinner(
                    t.getSlug(), t.getName(), top.beerId(), top.name(), top.brewery(), top.bayesian(), top.ratings())));
        }
        return out;
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

        out.add(blindTaster(nameOf));
        out.add(teamOfTheYear(all));

        return out;
    }

    /** Tastings per member, so a small team can beat a big one. Members who never tasted still count. */
    Award teamOfTheYear(List<Tasting> all) {
        String id = "TEAM", title = "Årets lag", description = "Flest terningkast per hode";
        Map<UUID, Long> perUser = all.stream().collect(Collectors.groupingBy(Tasting::getUserId, Collectors.counting()));
        record Score(Team team, double perHead, long tastings) {
        }
        List<Score> scores = teams.enabled().stream()
                .filter(t -> !t.getMembers().isEmpty())
                .map(t -> {
                    long n = t.getMembers().stream().mapToLong(m -> perUser.getOrDefault(m.userId(), 0L)).sum();
                    return new Score(t, (double) n / t.getMembers().size(), n);
                })
                .filter(s -> s.tastings() > 0)
                .toList();
        if (scores.isEmpty()) return new Award(id, title, description, null, "Ingen lag har smakt i år");
        double best = scores.stream().mapToDouble(Score::perHead).max().orElse(0);
        List<Score> winners = scores.stream().filter(s -> Math.abs(s.perHead() - best) < 1e-9)
                .sorted(Comparator.comparing(s -> s.team().getName())).toList();
        String names = winners.stream().map(s -> s.team().getName()).collect(Collectors.joining(", "))
                .replaceFirst(", ([^,]+)$", " og $1");
        Score top = winners.get(0);
        return new Award(id, title, description, names,
                "%.1f øl per hode, %d i alt".formatted(top.perHead(), top.tastings()).replace('.', ','));
    }

    /**
     * Blind pours are scored from the rounds, not the tasting log: most correct guesses,
     * ties broken by hit rate, at least {@value MIN_BLIND_GUESSES} guesses.
     */
    private Award blindTaster(Map<UUID, String> nameOf) {
        String id = "BLIND_TASTER", title = "Årets blindsmaker";
        String description = "Flest riktige i blindsmaking, minst %d gjetninger".formatted(MIN_BLIND_GUESSES);
        Map<UUID, BlindTally> tally = blind.tally();
        List<Map.Entry<UUID, BlindTally>> candidates = tally.entrySet().stream()
                .filter(e -> e.getValue().guesses() >= MIN_BLIND_GUESSES && e.getValue().correct() > 0)
                .toList();
        if (candidates.isEmpty()) return new Award(id, title, description, null, "Ingen kvalifiserte i år");
        Comparator<Map.Entry<UUID, BlindTally>> best = Comparator
                .<Map.Entry<UUID, BlindTally>>comparingInt(e -> e.getValue().correct())
                .thenComparingDouble(e -> e.getValue().accuracy());
        Map.Entry<UUID, BlindTally> top = candidates.stream().max(best).orElseThrow();
        List<Map.Entry<UUID, BlindTally>> winners = candidates.stream()
                .filter(e -> best.compare(e, top) == 0)
                .sorted(Comparator.comparing(e -> nameOf.getOrDefault(e.getKey(), "?")))
                .toList();
        String names = winners.stream().map(e -> nameOf.getOrDefault(e.getKey(), "?"))
                .collect(Collectors.joining(", ")).replaceFirst(", ([^,]+)$", " og $1");
        BlindTally t = top.getValue();
        return new Award(id, title, description, names, "%d av %d riktig".formatted(t.correct(), t.guesses()));
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
