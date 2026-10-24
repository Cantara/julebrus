package no.cantara.julebrus.badge;

import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.tasting.Tasting;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Pure badge rules over a person's tasting log. Anything that needs more context
 * than the log (crowd means, co-tasters) is passed in, so this stays unit testable
 * and badges can be re-tuned mid-season without a migration.
 */
public final class BadgeEvaluator {

    public record Context(
            List<Tasting> mine,
            int soloDaysUsed,
            Set<UUID> coTasters,
            /** beerId -> number of ratings by others at the moment this person rated it */
            Map<UUID, Long> othersBefore,
            /** beerId -> crowd mean excluding this person */
            Map<UUID, Double> crowdMean,
            /** beerId -> group average revealed in the session the person predicted in */
            Map<UUID, Double> groupAverage,
            /** correct guesses in revealed blind pours */
            int blindCorrect
    ) {
    }

    public static final int BLIND_TASTER_HITS = 3;

    private final SeasonProperties season;

    public BadgeEvaluator(SeasonProperties season) {
        this.season = season;
    }

    public Set<Badge> evaluate(Context ctx) {
        Set<Badge> out = EnumSet.noneOf(Badge.class);
        List<Tasting> mine = ctx.mine();
        if (ctx.blindCorrect() >= BLIND_TASTER_HITS) out.add(Badge.BLIND_TASTER);
        if (mine.isEmpty()) return out;

        out.add(Badge.FIRST_BLOOD);

        TreeSet<LocalDate> days = new TreeSet<>();
        mine.forEach(t -> days.add(t.getTastedDay()));
        if (longestStreak(days) >= 7) out.add(Badge.FULL_WEEK);

        if (mine.stream().anyMatch(t -> ctx.othersBefore().getOrDefault(t.getBeerId(), 0L) < 3)) {
            out.add(Badge.EXPLORER);
        }
        if (ctx.soloDaysUsed() >= season.maxSoloDays()) out.add(Badge.LONE_WOLF);
        if (ctx.coTasters().size() >= 10) out.add(Badge.SOCIAL_BUTTERFLY);

        List<Double> deviations = mine.stream()
                .filter(t -> ctx.crowdMean().containsKey(t.getBeerId()))
                .map(t -> Math.abs(t.getScore() - ctx.crowdMean().get(t.getBeerId())))
                .toList();
        if (deviations.size() >= 10 && deviations.stream().mapToDouble(d -> d).average().orElse(0) >= 1.5) {
            out.add(Badge.CONTRARIAN);
        }

        LocalDate dec1 = LocalDate.of(season.year(), 12, 1);
        LocalDate dec23 = LocalDate.of(season.year(), 12, 23);
        boolean advent = true;
        for (LocalDate d = dec1; !d.isAfter(dec23); d = d.plusDays(1)) {
            if (!days.contains(d)) { advent = false; break; }
        }
        if (advent) out.add(Badge.ADVENT);

        long goodPredictions = mine.stream()
                .filter(t -> t.getPrediction() != null && ctx.groupAverage().containsKey(t.getBeerId()))
                .filter(t -> Math.abs(t.getPrediction().doubleValue() - ctx.groupAverage().get(t.getBeerId())) <= 0.5)
                .count();
        if (goodPredictions >= 10) out.add(Badge.SOMMELIER);

        if (days.contains(dec23)) out.add(Badge.CLOSING_TIME);
        return out;
    }

    static int longestStreak(TreeSet<LocalDate> days) {
        int best = 0, run = 0;
        LocalDate prev = null;
        for (LocalDate d : days) {
            run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        return best;
    }
}
