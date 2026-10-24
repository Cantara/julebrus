package no.cantara.julebrus.stats;

import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The palate fingerprint (which tags a person scores above their own mean) and the
 * taste twins (whose scores move with yours, and whose move against).
 */
@RestController
@RequestMapping("/api/stats/palate")
public class PalateController {

    static final int MIN_COMMON = 3;
    static final int MIN_TAG_COUNT = 2;
    static final int MIN_COMMON_FOR_PAIRS = 4;

    public record Match(UUID userId, String displayName, double similarity, int common,
                        /** the beer you agreed on most, or disagreed on most */ String beerName, int yourScore, int theirScore) {
    }

    public record Palate(long tastings, double mean, List<PalateMath.TagLift> likes, List<PalateMath.TagLift> dislikes,
                         Match twin, Match opposite, int minCommon) {
    }

    public record Pair(String a, String b, double similarity, int common) {
    }

    public record Pairs(List<Pair> twins, List<Pair> opposites, int minCommon) {
    }

    private final TastingRepository tastings;
    private final UserRepository users;
    private final BeerRepository beers;
    private final CurrentUser currentUser;

    public PalateController(TastingRepository tastings, UserRepository users, BeerRepository beers, CurrentUser currentUser) {
        this.tastings = tastings;
        this.users = users;
        this.beers = beers;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    public Palate me() {
        UUID me = currentUser.get().getId();
        Map<UUID, Map<UUID, Integer>> scores = scoresByUser();
        Map<UUID, Integer> mine = scores.getOrDefault(me, Map.of());
        List<Tasting> myTastings = tastings.findByUserIdOrderByTastedAtDesc(me);

        List<PalateMath.TagLift> lifts = PalateMath.tagLifts(
                myTastings.stream().map(t -> Map.entry(t.getTags(), t.getScore())).toList(), MIN_TAG_COUNT);
        List<PalateMath.TagLift> likes = lifts.stream().filter(l -> l.lift() > 0).limit(5).toList();
        List<PalateMath.TagLift> dislikes = lifts.stream().filter(l -> l.lift() < 0)
                .sorted(Comparator.comparingDouble(PalateMath.TagLift::lift)).limit(5).toList();

        Map<UUID, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Beer> beerById = beers.findAll().stream().collect(Collectors.toMap(Beer::getId, Function.identity()));
        Match twin = null, opposite = null;
        for (Map.Entry<UUID, Map<UUID, Integer>> e : scores.entrySet()) {
            if (e.getKey().equals(me)) continue;
            double sim = PalateMath.similarity(mine, e.getValue(), MIN_COMMON);
            if (Double.isNaN(sim)) continue;
            Match m = match(e.getKey(), userById, sim, mine, e.getValue(), beerById, sim >= 0);
            if (twin == null || sim > twin.similarity()) twin = m;
            if (opposite == null || sim < opposite.similarity()) opposite = m;
        }
        if (twin != null && opposite != null && twin.userId().equals(opposite.userId())) opposite = null;

        return new Palate(myTastings.size(), myTastings.stream().mapToInt(Tasting::getScore).average().orElse(0),
                likes, dislikes, twin, opposite, MIN_COMMON);
    }

    /** Everyone's closest and most opposite pairs, for the finale. */
    @GetMapping("/pairs")
    public Pairs pairs() {
        Map<UUID, Map<UUID, Integer>> scores = scoresByUser();
        Map<UUID, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<UUID> ids = new ArrayList<>(scores.keySet());
        List<Pair> all = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                Map<UUID, Integer> a = scores.get(ids.get(i)), b = scores.get(ids.get(j));
                double sim = PalateMath.similarity(a, b, MIN_COMMON_FOR_PAIRS);
                if (Double.isNaN(sim)) continue;
                all.add(new Pair(name(userById, ids.get(i)), name(userById, ids.get(j)), sim, PalateMath.common(a, b)));
            }
        }
        List<Pair> twins = all.stream().sorted(Comparator.comparingDouble(Pair::similarity).reversed()).limit(3).toList();
        List<Pair> opposites = all.stream().sorted(Comparator.comparingDouble(Pair::similarity)).limit(3).toList();
        return new Pairs(twins, opposites, MIN_COMMON_FOR_PAIRS);
    }

    private Map<UUID, Map<UUID, Integer>> scoresByUser() {
        Map<UUID, Map<UUID, Integer>> out = new HashMap<>();
        for (Tasting t : tastings.findAll()) {
            out.computeIfAbsent(t.getUserId(), k -> new HashMap<>()).put(t.getBeerId(), t.getScore());
        }
        return out;
    }

    private static String name(Map<UUID, User> userById, UUID id) {
        User u = userById.get(id);
        return u == null ? "?" : u.getDisplayName();
    }

    private static Match match(UUID other, Map<UUID, User> userById, double sim, Map<UUID, Integer> mine,
                               Map<UUID, Integer> theirs, Map<UUID, Beer> beerById, boolean closest) {
        UUID best = null;
        int bestDiff = closest ? Integer.MAX_VALUE : -1;
        for (Map.Entry<UUID, Integer> e : mine.entrySet()) {
            Integer t = theirs.get(e.getKey());
            if (t == null) continue;
            int diff = Math.abs(e.getValue() - t);
            if (closest ? diff < bestDiff : diff > bestDiff) {
                bestDiff = diff;
                best = e.getKey();
            }
        }
        Beer b = best == null ? null : beerById.get(best);
        return new Match(other, name(userById, other), sim, PalateMath.common(mine, theirs),
                b == null ? null : b.getName(), best == null ? 0 : mine.get(best), best == null ? 0 : theirs.get(best));
    }
}
