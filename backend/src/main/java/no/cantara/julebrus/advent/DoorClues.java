package no.cantara.julebrus.advent;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Pure: one clue a day about the current leader while the ranking is hidden, vaguest first. Each clue
 * falls back to a plainer one when the beer lacks the fact it needs, so a door never comes up empty.
 */
public final class DoorClues {

    /** The leader as the clues see it. */
    public record Leader(String name, String brewery, String style, BigDecimal abv, long ratings) {
    }

    /** How many different clues there are; door n of the blackout gets clue n, the last one repeating. */
    public static final int COUNT = 6;
    private static final BigDecimal STRONG = new BigDecimal("7.0");

    private DoorClues() {
    }

    /** The clue for the n-th day of the blackout (0 is the first), or null when there is no leader yet. */
    public static String clue(int n, Leader leader) {
        if (leader == null) return null;
        return switch (Math.min(Math.max(n, 0), COUNT - 1)) {
            case 0 -> abvClue(leader);
            case 1 -> leader.ratings() >= 10 ? "Lederen har fått %d terningkast eller flere.".formatted(leader.ratings() / 5 * 5)
                    : "Lederen har fått under ti terningkast. Få, men gode.";
            case 2 -> blank(leader.style()) ? abvClue(leader) : "Lederen er en %s.".formatted(leader.style().toLowerCase(Locale.ROOT));
            case 3 -> "Navnet har %d ord.".formatted(words(leader.name()));
            case 4 -> blank(leader.brewery()) ? "Navnet begynner på %s.".formatted(first(leader.name()))
                    : "Bryggeriet begynner på %s.".formatted(first(leader.brewery()));
            default -> "Navnet begynner på %s og slutter på %s.".formatted(first(leader.name()), last(leader.name()));
        };
    }

    private static String abvClue(Leader l) {
        if (l.abv() == null) return "Lederen er fra Vinmonopolet, altså over 4,7 %.";
        return l.abv().compareTo(STRONG) >= 0 ? "Lederen er på 7 % eller mer." : "Lederen er under 7 %.";
    }

    private static int words(String s) {
        return s == null || s.isBlank() ? 0 : s.trim().split("\\s+").length;
    }

    private static String first(String s) {
        return s == null || s.isBlank() ? "?" : s.trim().substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static String last(String s) {
        if (s == null || s.isBlank()) return "?";
        String t = s.trim();
        return t.substring(t.length() - 1).toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
