package no.cantara.julebrus.beer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the beer CSV we use for the seed and for admin imports:
 * {@code name,brewery,style,abv,vinmonopolet_id}. Comma or semicolon separated,
 * quotes honoured, decimal comma allowed in abv, header and {@code #} lines skipped.
 * Vinmonopolet's own exports are semicolon separated with a decimal comma, so a
 * trimmed-down export pastes straight in.
 */
public final class BeerCsv {

    public record Row(int line, String name, String brewery, String style, BigDecimal abv, String vinmonopoletId,
                      String error) {
        public boolean valid() {
            return error == null;
        }
    }

    private BeerCsv() {
    }

    public static List<Row> parse(String text) {
        List<Row> out = new ArrayList<>();
        if (text == null) return out;
        String[] lines = text.split("\\r?\\n");
        boolean header = true;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            if (header) {
                header = false;
                if (line.toLowerCase(Locale.ROOT).contains("name")) continue; // header row
            }
            char sep = line.chars().filter(c -> c == ';').count() > line.chars().filter(c -> c == ',').count() ? ';' : ',';
            List<String> cells = split(line, sep);
            while (cells.size() < 5) cells.add("");
            String name = cells.get(0).trim();
            if (name.isEmpty()) {
                out.add(new Row(i + 1, name, null, null, null, null, "Mangler navn"));
                continue;
            }
            BigDecimal abv = null;
            String abvText = cells.get(3).trim().replace("%", "").replace(',', '.');
            if (!abvText.isEmpty()) {
                try {
                    abv = new BigDecimal(abvText);
                } catch (NumberFormatException e) {
                    out.add(new Row(i + 1, name, blankToNull(cells.get(1)), blankToNull(cells.get(2)), null,
                            blankToNull(cells.get(4)), "Ugyldig alkoholprosent: " + cells.get(3).trim()));
                    continue;
                }
            }
            out.add(new Row(i + 1, name, blankToNull(cells.get(1)), blankToNull(cells.get(2)), abv, blankToNull(cells.get(4)), null));
        }
        return out;
    }

    /** Lower-cased, whitespace-collapsed name for duplicate detection. */
    public static String key(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static List<String> split(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cur.append(c);
            } else if (c == '"') quoted = true;
            else if (c == sep) { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }
}
