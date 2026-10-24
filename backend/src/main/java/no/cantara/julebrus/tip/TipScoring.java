package no.cantara.julebrus.tip;

import java.util.List;
import java.util.UUID;

/**
 * Pure: points for a tip against the official podium. Three for a beer in its right
 * spot, one for a beer on the podium in the wrong spot. Nine is a perfect tip.
 */
public final class TipScoring {

    public static final int RIGHT_SPOT = 3;
    public static final int ON_PODIUM = 1;
    public static final int MAX = 3 * RIGHT_SPOT;

    private TipScoring() {
    }

    /** {@code podium} is the official top three, gold first; shorter when the season had fewer beers. */
    public static int points(List<UUID> tip, List<UUID> podium) {
        int points = 0;
        for (int i = 0; i < tip.size(); i++) {
            UUID beer = tip.get(i);
            if (i < podium.size() && beer.equals(podium.get(i))) points += RIGHT_SPOT;
            else if (podium.contains(beer)) points += ON_PODIUM;
        }
        return points;
    }
}
