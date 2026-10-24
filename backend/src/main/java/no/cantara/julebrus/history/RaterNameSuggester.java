package no.cantara.julebrus.history;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Decides which historic rater names look like the logged-in person. Old spreadsheets
 * used first names ("Eirik", "Eirik L"), so a name is suggested when it equals or
 * starts with the first word of the Google display name, ignoring case and accents.
 * Pure, no Spring. A suggestion only orders the list; it never links anything.
 */
public final class RaterNameSuggester {

    private RaterNameSuggester() {
    }

    /** Lower case, combining accents stripped ("José" and "Jose" match), whitespace collapsed. */
    static String normalise(String s) {
        if (s == null) return "";
        String decomposed = Normalizer.normalize(s.trim(), Normalizer.Form.NFD);
        // å decomposes to a + ring and so folds to a; ø and æ have no decomposition and stay as they are.
        String folded = decomposed.replaceAll("\\p{M}+", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static String firstName(String displayName) {
        String n = normalise(displayName);
        int space = n.indexOf(' ');
        return space < 0 ? n : n.substring(0, space);
    }

    /** 0 = the name is exactly the first name, 1 = it starts with it, 2 = no match. */
    public static int rank(String displayName, String raterName) {
        String first = firstName(displayName);
        if (first.isEmpty()) return 2;
        String rater = normalise(raterName);
        if (rater.equals(first)) return 0;
        return rater.startsWith(first) ? 1 : 2;
    }

    public static boolean suggested(String displayName, String raterName) {
        return rank(displayName, raterName) < 2;
    }
}
