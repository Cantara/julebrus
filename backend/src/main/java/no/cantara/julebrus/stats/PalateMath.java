package no.cantara.julebrus.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure maths behind the palate fingerprint and the taste twins. */
public final class PalateMath {

    private PalateMath() {
    }

    /**
     * How alike two people's palates are, over the beers both have rated: Pearson
     * correlation when both vary, else closeness of their scores. Returns NaN when
     * fewer than {@code minCommon} beers are shared.
     */
    public static double similarity(Map<UUID, Integer> a, Map<UUID, Integer> b, int minCommon) {
        List<double[]> pairs = new ArrayList<>();
        for (Map.Entry<UUID, Integer> e : a.entrySet()) {
            Integer other = b.get(e.getKey());
            if (other != null) pairs.add(new double[]{e.getValue(), other});
        }
        if (pairs.size() < minCommon) return Double.NaN;
        double ma = pairs.stream().mapToDouble(p -> p[0]).average().orElse(0);
        double mb = pairs.stream().mapToDouble(p -> p[1]).average().orElse(0);
        double cov = 0, va = 0, vb = 0;
        for (double[] p : pairs) {
            cov += (p[0] - ma) * (p[1] - mb);
            va += (p[0] - ma) * (p[0] - ma);
            vb += (p[1] - mb) * (p[1] - mb);
        }
        if (va > 0 && vb > 0) return cov / Math.sqrt(va * vb);
        // One of them gave the same score to everything: fall back to how close the scores are, on -1..1.
        double meanAbs = pairs.stream().mapToDouble(p -> Math.abs(p[0] - p[1])).average().orElse(0);
        return 1 - 2 * meanAbs / 5;
    }

    /** Number of beers both have rated. */
    public static int common(Map<UUID, Integer> a, Map<UUID, Integer> b) {
        int n = 0;
        for (UUID k : a.keySet()) if (b.containsKey(k)) n++;
        return n;
    }

    public record TagLift(String tag, double lift, int count) {
    }

    /**
     * For each tag, how much higher (or lower) the person scores beers they gave that
     * tag, against their own mean. Only tags used at least {@code minCount} times.
     */
    public static List<TagLift> tagLifts(List<Map.Entry<Set<String>, Integer>> tastings, int minCount) {
        double mean = tastings.stream().mapToInt(Map.Entry::getValue).average().orElse(0);
        Map<String, double[]> acc = new java.util.TreeMap<>();
        for (Map.Entry<Set<String>, Integer> t : tastings) {
            for (String tag : t.getKey()) {
                double[] a = acc.computeIfAbsent(tag, k -> new double[2]);
                a[0] += t.getValue();
                a[1] += 1;
            }
        }
        List<TagLift> out = new ArrayList<>();
        for (Map.Entry<String, double[]> e : acc.entrySet()) {
            if (e.getValue()[1] >= minCount) {
                out.add(new TagLift(e.getKey(), e.getValue()[0] / e.getValue()[1] - mean, (int) e.getValue()[1]));
            }
        }
        out.sort((x, y) -> Double.compare(y.lift(), x.lift()));
        return out;
    }
}
