package no.cantara.julebrus.weight;

import java.util.Optional;

/**
 * How much a person's scores count this season, decided by how their most recent
 * season went. Pure, so the rule is easy to read and easy to test.
 *
 * <ul>
 *   <li>Never tasted before: 0.5. Welcome, prove yourself.</li>
 *   <li>Fewer than {@value #MIN_SCORES_FOR_FULL_WEIGHT} scores last time: stays at 0.5.</li>
 *   <li>Flagged for rigging last time (6 to everything from Bergen, 1 to the rest): 0.
 *       They can still join and vote, it just doesn't move the averages.</li>
 *   <li>Otherwise: 1.0.</li>
 * </ul>
 */
public final class RaterWeightPolicy {

    public static final int MIN_SCORES_FOR_FULL_WEIGHT = 7;
    public static final double ROOKIE = 0.5;
    public static final double FULL = 1.0;
    public static final double MUTED = 0.0;

    public record PreviousSeason(int year, int scoresLogged, boolean flagged) {
    }

    public record Decision(double weight, String reason) {
    }

    private RaterWeightPolicy() {
    }

    public static Decision decide(Optional<PreviousSeason> previous) {
        if (previous.isEmpty()) {
            return new Decision(ROOKIE, "Første sesong. Full vekt neste år etter en vanlig sesong.");
        }
        PreviousSeason p = previous.get();
        if (p.flagged()) {
            return new Decision(MUTED, "Flagget for juks i %d. Terningkastene dine lagres, men teller ikke i år.".formatted(p.year()));
        }
        if (p.scoresLogged() < MIN_SCORES_FOR_FULL_WEIGHT) {
            return new Decision(ROOKIE, "Bare %d terningkast i %d. Minst %d gir full vekt neste år."
                    .formatted(p.scoresLogged(), p.year(), MIN_SCORES_FOR_FULL_WEIGHT));
        }
        return new Decision(FULL, "Vanlig sesong i %d.".formatted(p.year()));
    }
}
