package no.cantara.julebrus.vinmonopolet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads Vinmonopolet's juleøl lists and the machine vote matches (data/vinmonopolet/, copied into the jar under
 * {@code vinmonopolet/} by the Maven resource mapping) into {@code vmp_products}, {@code vmp_listings} and
 * {@code vote_matches}.
 *
 * <p>Idempotent and additive: a product or listing that is already there is left alone, a vote match is inserted when
 * its (year, beer) is new, and when a newer vote_matches.csv differs only the machine_* columns and candidates are
 * updated. A human decision is never written, cleared or deleted here, and rows missing from the files are not deleted.
 * Listings have no natural key (the lists repeat a name within a year), so they are matched as a multiset on
 * (year, article number, name): a key that occurs n times in the file and m &lt; n times in the table gets n - m inserts.</p>
 */
@Service
public class VmpImportService {

    private static final Logger log = LoggerFactory.getLogger(VmpImportService.class);

    public record Sources(Resource products, Resource listings, Resource votes) {
        public static Sources classpath() {
            return new Sources(new ClassPathResource("vinmonopolet/products.csv"),
                    new ClassPathResource("vinmonopolet/listings.csv"),
                    new ClassPathResource("vinmonopolet/vote_matches.csv"));
        }
    }

    /** What one run did. {@code duplicates} counts repeated (year, beer) rows within vote_matches.csv; the first one wins. */
    public record Counts(int read, int inserted, int updated, int unchanged, int skipped, int duplicates) {
    }

    public record Result(Counts products, Counts listings, Counts voteMatches) {
    }

    private final JdbcTemplate jdbc;
    private final VoteMatchRepository matches;

    public VmpImportService(JdbcTemplate jdbc, VoteMatchRepository matches) {
        this.jdbc = jdbc;
        this.matches = matches;
    }

    @Transactional
    public Result importClasspath() throws IOException {
        return importFrom(Sources.classpath());
    }

    @Transactional
    public Result importFrom(Sources src) throws IOException {
        Counts products = importProducts(src.products());
        Counts listings = importListings(src.listings());
        Counts votes = importVoteMatches(src.votes());
        log.info("Vinmonopolet import: products {}, listings {}, vote matches {}", products, listings, votes);
        return new Result(products, listings, votes);
    }

    private Counts importProducts(Resource res) throws IOException {
        List<Map<String, String>> rows = read(res);
        if (rows == null) return new Counts(0, 0, 0, 0, 0, 0);
        Set<String> have = new HashSet<>(jdbc.queryForList("select article_no from vmp_products", String.class));
        List<Object[]> batch = new ArrayList<>();
        int skipped = 0, unchanged = 0;
        for (Map<String, String> r : rows) {
            String art = blank(r.get("article_no"));
            String name = blank(r.get("name"));
            if (art == null || name == null) { skipped++; continue; }
            if (!have.add(art)) { unchanged++; continue; }
            try {
                batch.add(new Object[]{art, name, blank(r.get("brewery")), blank(r.get("style")), blank(r.get("country")),
                        blank(r.get("region")), dec(r.get("abv_percent")), dec(r.get("volume_l")),
                        blank(r.get("organic")) != null, integer(r.get("first_year")), integer(r.get("last_year"))});
            } catch (NumberFormatException e) {
                have.remove(art);
                skipped++;
                log.warn("vmp products.csv: skipped article {} ({})", art, e.getMessage());
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate("insert into vmp_products (article_no, name, brewery, style, country, region, abv_percent, volume_l, "
                    + "organic, first_year, last_year) values (?,?,?,?,?,?,?,?,?,?,?)", batch);
        }
        return new Counts(rows.size(), batch.size(), 0, unchanged, skipped, 0);
    }

    private Counts importListings(Resource res) throws IOException {
        List<Map<String, String>> rows = read(res);
        if (rows == null) return new Counts(0, 0, 0, 0, 0, 0);
        Map<String, Integer> have = new HashMap<>();
        jdbc.query("select list_year, article_no, name from vmp_listings", rs -> {
            have.merge(listingKey(rs.getInt(1), rs.getString(2), rs.getString(3)), 1, Integer::sum);
        });
        List<Object[]> batch = new ArrayList<>();
        int skipped = 0, unchanged = 0;
        for (Map<String, String> r : rows) {
            String name = blank(r.get("name"));
            Integer year;
            try {
                year = integer(r.get("year"));
                if (name == null || year == null) { skipped++; continue; }
                String art = blank(r.get("article_no"));
                String key = listingKey(year, art, name);
                Integer left = have.get(key);
                if (left != null && left > 0) {
                    have.put(key, left - 1);
                    unchanged++;
                    continue;
                }
                batch.add(new Object[]{year, art, name, blank(r.get("selection")), dec(r.get("price_nok")), dec(r.get("abv_percent")),
                        blank(r.get("product_article_no")), blank(r.get("match"))});
            } catch (NumberFormatException e) {
                skipped++;
                log.warn("vmp listings.csv: skipped a row of {} ({})", name, e.getMessage());
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate("insert into vmp_listings (list_year, article_no, name, selection, price_nok, abv_percent, "
                    + "product_article_no, link_basis) values (?,?,?,?,?,?,?,?)", batch);
        }
        return new Counts(rows.size(), batch.size(), 0, unchanged, skipped, 0);
    }

    private Counts importVoteMatches(Resource res) throws IOException {
        List<Map<String, String>> rows = read(res);
        if (rows == null) return new Counts(0, 0, 0, 0, 0, 0);
        Map<String, VoteMatch> have = new HashMap<>();
        for (VoteMatch m : matches.findAll()) have.put(matchKey(m.getYear(), m.getVoteBeer()), m);
        Set<String> seen = new HashSet<>();
        List<VoteMatch> fresh = new ArrayList<>();
        List<VoteMatch> changed = new ArrayList<>();
        int skipped = 0, duplicates = 0, unchanged = 0;
        for (Map<String, String> r : rows) {
            String beer = blank(r.get("vote_beer"));
            String status = blank(r.get("status"));
            int year;
            BigDecimal score;
            try {
                Integer y = integer(r.get("year"));
                if (beer == null || status == null || y == null) { skipped++; continue; }
                year = y;
                score = dec(r.get("score"));
            } catch (NumberFormatException e) {
                skipped++;
                log.warn("vote_matches.csv: skipped a row ({})", e.getMessage());
                continue;
            }
            String key = matchKey(year, beer);
            if (!seen.add(key)) {
                duplicates++;
                log.warn("vote_matches.csv repeats ({}, {}); the first row is used", year, beer);
                continue;
            }
            VoteMatch m = have.get(key);
            boolean isNew = m == null;
            if (isNew) m = new VoteMatch(year, beer);
            boolean differs = m.applyMachine(status, blank(r.get("method")), score, blank(r.get("product_article_no")),
                    blank(r.get("article_options")), blank(r.get("product_name")), blank(r.get("list_year")),
                    blank(r.get("candidates")));
            if (isNew) fresh.add(m);
            else if (differs) changed.add(m);
            else unchanged++;
        }
        matches.saveAll(fresh);
        matches.saveAll(changed);
        return new Counts(rows.size(), fresh.size(), changed.size(), unchanged, skipped, duplicates);
    }

    private static List<Map<String, String>> read(Resource res) throws IOException {
        if (res == null || !res.exists()) {
            log.warn("Vinmonopolet import: {} not found, skipped", res == null ? "(none)" : res.getDescription());
            return null;
        }
        try (InputStream in = res.getInputStream()) {
            return CsvTable.read(in);
        }
    }

    static String matchKey(int year, String beer) {
        return year + "\u0000" + beer;
    }

    private static String listingKey(int year, String article, String name) {
        return year + "\u0000" + (article == null ? "" : article) + "\u0000" + name;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BigDecimal dec(String s) {
        String v = blank(s);
        return v == null ? null : new BigDecimal(v);
    }

    private static Integer integer(String s) {
        String v = blank(s);
        return v == null ? null : Integer.valueOf(v);
    }
}
