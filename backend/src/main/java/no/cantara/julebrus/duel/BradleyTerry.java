package no.cantara.julebrus.duel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Bradley–Terry strengths from pairwise outcomes, fitted with a few rounds of
 * the standard MM iteration. Strengths are returned normalised to a geometric
 * mean of 1, so 2.0 means "twice as likely to win against an average beer".
 *
 * A small pseudo-count makes the fit stable when a beer has only won or only lost.
 */
public final class BradleyTerry {

    public record Outcome(UUID winner, UUID loser) {
    }

    private static final double PSEUDO = 0.5;
    private static final int ITERATIONS = 100;

    public Map<UUID, Double> fit(List<Outcome> outcomes) {
        Set<UUID> items = new TreeSet<>();
        Map<UUID, Double> wins = new HashMap<>();
        Map<UUID, Map<UUID, Double>> games = new HashMap<>();
        for (Outcome o : outcomes) {
            items.add(o.winner());
            items.add(o.loser());
            wins.merge(o.winner(), 1.0, Double::sum);
            games.computeIfAbsent(o.winner(), k -> new HashMap<>()).merge(o.loser(), 1.0, Double::sum);
            games.computeIfAbsent(o.loser(), k -> new HashMap<>()).merge(o.winner(), 1.0, Double::sum);
        }
        if (items.isEmpty()) return Map.of();

        Map<UUID, Double> p = new HashMap<>();
        for (UUID i : items) p.put(i, 1.0);

        for (int it = 0; it < ITERATIONS; it++) {
            Map<UUID, Double> next = new HashMap<>();
            for (UUID i : items) {
                double w = wins.getOrDefault(i, 0.0) + PSEUDO;
                double denom = 0.0;
                for (Map.Entry<UUID, Double> e : games.getOrDefault(i, Map.of()).entrySet()) {
                    denom += e.getValue() / (p.get(i) + p.get(e.getKey()));
                }
                denom += PSEUDO / (p.get(i) + 1.0);
                next.put(i, w / denom);
            }
            double logSum = next.values().stream().mapToDouble(Math::log).sum();
            double scale = Math.exp(logSum / items.size());
            next.replaceAll((k, v) -> v / scale);
            p = next;
        }

        Map<UUID, Double> sorted = new LinkedHashMap<>();
        p.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }
}
