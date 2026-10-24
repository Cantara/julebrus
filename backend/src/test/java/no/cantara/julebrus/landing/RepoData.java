package no.cantara.julebrus.landing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Reads the repository's data/ files directly (tests run in backend/), independently of the generator. */
public final class RepoData {

    public static final Path DATA = Path.of("..", "data");
    static final Path RATERS = Path.of("src", "main", "resources", "history", "raters.csv");

    private RepoData() {
    }

    /** Rows as header-keyed maps. A small RFC 4180 reader: quotes, doubled quotes, commas and newlines in quotes. */
    public static List<Map<String, String>> csv(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cell.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { row.add(cell.toString()); cell.setLength(0); }
            else if (c == '\n') { row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>(); }
            else if (c != '\r') cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        List<String> head = rows.get(0);
        List<Map<String, String>> out = new ArrayList<>();
        for (List<String> r : rows.subList(1, rows.size())) {
            if (r.size() == 1 && r.get(0).isBlank()) continue;
            if (!r.isEmpty() && r.get(0).startsWith("#")) continue;
            Map<String, String> m = new HashMap<>();
            for (int i = 0; i < head.size(); i++) m.put(head.get(i), i < r.size() ? r.get(i) : "");
            out.add(m);
        }
        return out;
    }

    public static final Path BEER_NAMES = Path.of("src", "main", "resources", "history", "beer_names.csv");

    /** The rows with beer names corrected by the app's beer_names.csv, as tools/to_landing_stats.py does. */
    static List<Map<String, String>> corrected(List<Map<String, String>> rows) {
        Map<String, String> names = new HashMap<>();
        csv(BEER_NAMES).forEach(r -> names.put(r.get("year").trim() + "|" + r.get("as_written").trim(), r.get("canonical").trim()));
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> r : rows) {
            Map<String, String> c = new HashMap<>(r);
            c.computeIfPresent("beer", (k, v) -> names.getOrDefault(r.get("year") + "|" + v.trim(), v));
            out.add(c);
        }
        return out;
    }

    public static List<Map<String, String>> votes() {
        return corrected(csv(DATA.resolve("votes.csv")));
    }

    /** Every name of a person the repository holds: voters, 2016 participants, and both columns of raters.csv. */
    static Set<String> personNames() {
        Set<String> names = new TreeSet<>();
        votes().forEach(r -> names.add(r.get("voter").trim()));
        csv(DATA.resolve("participation.csv")).forEach(r -> names.add(r.get("voter").trim()));
        for (Map<String, String> r : csv(RATERS)) {
            names.add(r.getOrDefault("name", "").trim());
            names.add(r.getOrDefault("canonical_name", "").trim());
        }
        names.remove("");
        return names;
    }

    /** The names that occur in {@code text} as a whole word (case-sensitive; letters or digits on either side do not count). */
    static List<String> namesIn(String text, Set<String> names) {
        List<String> hits = new ArrayList<>();
        for (String name : names) {
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])");
            if (p.matcher(text).find()) hits.add(name);
        }
        return hits;
    }
}
