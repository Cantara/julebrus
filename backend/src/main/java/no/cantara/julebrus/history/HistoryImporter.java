package no.cantara.julebrus.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads earlier seasons from CSV files on the classpath at startup, once per season.
 *
 * <p>Layout: {@code history/seasons.csv} describes each season, and
 * {@code history/<year>.csv} holds that season's ratings. See
 * {@code src/main/resources/history/README.md} for the columns.</p>
 */
@Component
public class HistoryImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoryImporter.class);

    private final HistoricSeasonRepository seasons;
    private final HistoricRatingRepository ratings;

    public HistoryImporter(HistoricSeasonRepository seasons, HistoricRatingRepository ratings) {
        this.seasons = seasons;
        this.ratings = ratings;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource seasonsCsv = resolver.getResource("classpath:history/seasons.csv");
        if (!seasonsCsv.exists()) return;

        for (List<String> row : Csv.read(seasonsCsv)) {
            int year = Integer.parseInt(row.get(0));
            if (seasons.existsById(year)) continue;
            seasons.save(new HistoricSeason(year, row.get(1), new BigDecimal(row.get(2)), new BigDecimal(row.get(3)),
                    nullIfBlank(row.get(4)), nullIfBlank(row.get(5))));
            log.info("Imported historic season {}", year);
        }

        for (Resource csv : resolver.getResources("classpath:history/[0-9]*.csv")) {
            String name = csv.getFilename();
            if (name == null) continue;
            int year = Integer.parseInt(name.substring(0, name.indexOf('.')));
            if (ratings.existsBySeasonYear(year)) continue;
            if (!seasons.existsById(year)) {
                log.warn("history/{} has no row in seasons.csv, skipping", name);
                continue;
            }
            List<HistoricRating> batch = new ArrayList<>();
            for (List<String> row : Csv.read(csv)) {
                batch.add(new HistoricRating(year, row.get(0), nullIfBlank(row.get(1)), row.get(2), nullIfBlank(row.get(3)),
                        new BigDecimal(row.get(4)), row.get(5).isBlank() ? BigDecimal.ONE : new BigDecimal(row.get(5)),
                        nullIfBlank(row.get(6))));
            }
            ratings.saveAll(batch);
            log.info("Imported {} historic ratings for {}", batch.size(), year);
        }
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Minimal RFC 4180 reader: header row skipped, quotes and embedded commas handled. */
    static final class Csv {
        static List<List<String>> read(Resource resource) throws IOException {
            List<List<String>> rows = new ArrayList<>();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean header = true;
                while ((line = in.readLine()) != null) {
                    if (header) { header = false; continue; }
                    if (line.isBlank() || line.startsWith("#")) continue;
                    rows.add(split(line));
                }
            }
            return rows;
        }

        static List<String> split(String line) {
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
                else if (c == ',') { out.add(cur.toString().trim()); cur.setLength(0); }
                else cur.append(c);
            }
            out.add(cur.toString().trim());
            while (out.size() < 7) out.add("");
            return out;
        }
    }
}
