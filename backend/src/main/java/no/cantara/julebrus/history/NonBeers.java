package no.cantara.julebrus.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What was scored at the old tastings but is not a beer: cured meat (fenalår and its cousins), the hosts, a few junk rows.
 * The history views leave these out unless the reader asks for them.
 *
 * <p>An item is not a beer if its name matches one of the patterns the landing page already uses
 * ({@code nonBeerPatterns} in landing/history-stats.json, written by tools/to_landing_stats.py), or if the match review says
 * NOT_A_PRODUCT (an administrator's decision, or my reviewed {@code analysis} pass). An administrator who decides that an
 * item IS a beer (confirmed, chosen or "no Vinmonopolet product") always wins over the patterns.</p>
 */
@Component
public class NonBeers {

    private final JdbcTemplate jdbc;
    private final List<Pattern> patterns;

    public NonBeers(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.patterns = loadPatterns(json);
    }

    /** One request's view of the question; reads the match table once. */
    public static final class Judge {
        private final List<Pattern> patterns;
        private final Set<String> beerByDecision;
        private final Set<String> notABeer;

        Judge(List<Pattern> patterns, Set<String> beerByDecision, Set<String> notABeer) {
            this.patterns = patterns;
            this.beerByDecision = beerByDecision;
            this.notABeer = notABeer;
        }

        public boolean isNonBeer(int year, String name) {
            if (name == null) return false;
            String key = key(year, name);
            if (beerByDecision.contains(key)) return false;
            if (notABeer.contains(key)) return true;
            String n = name.toLowerCase(Locale.ROOT);
            for (Pattern p : patterns) {
                if (p.matcher(n).find()) return true;
            }
            return false;
        }

        /** True if the name is not a beer in any of the given years. */
        public boolean isNonBeerInAny(Iterable<Integer> years, String name) {
            for (int y : years) {
                if (isNonBeer(y, name)) return true;
            }
            return false;
        }
    }

    public Judge judge() {
        Set<String> beer = new HashSet<>();
        Set<String> nonBeer = new HashSet<>();
        jdbc.query("select season_year, vote_beer, machine_status, machine_method, decision from vote_matches", rs -> {
            String key = key(rs.getInt(1), rs.getString(2));
            String decision = rs.getString(5);
            if (decision != null) {
                if ("NOT_A_PRODUCT".equals(decision)) nonBeer.add(key);
                else beer.add(key);
            } else if ("not-a-product".equals(rs.getString(3)) && "analysis".equals(rs.getString(4))) {
                // Only my reviewed pass counts here: the machine's own "non-beer words" also caught a homebrew, which is a beer.
                nonBeer.add(key);
            }
        });
        return new Judge(patterns, beer, nonBeer);
    }

    static String key(int year, String name) {
        return year + "\u0000" + name.toLowerCase(Locale.ROOT).trim();
    }

    private static List<Pattern> loadPatterns(ObjectMapper json) {
        try (InputStream in = new ClassPathResource("landing/history-stats.json").getInputStream()) {
            JsonNode node = json.readTree(in).path("nonBeerPatterns");
            List<Pattern> out = new ArrayList<>();
            for (JsonNode p : node) out.add(Pattern.compile(p.asText().toLowerCase(Locale.ROOT)));
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
