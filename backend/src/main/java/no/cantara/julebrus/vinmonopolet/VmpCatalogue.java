package no.cantara.julebrus.vinmonopolet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read access to the imported product and listing names: existence checks for a decision and the type-ahead search. */
@Component
public class VmpCatalogue {

    public record Hit(String articleNo, String name, String source) {
    }

    private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}+");
    private static final java.util.regex.Pattern NON_ALNUM = java.util.regex.Pattern.compile("[^a-z0-9]+");

    private final JdbcTemplate jdbc;

    public VmpCatalogue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean articleExists(String articleNo) {
        return count("select count(*) from vmp_products where article_no = ?", articleNo) > 0;
    }

    public boolean nameExists(String name) {
        return count("select count(*) from vmp_products where name = ?", name) > 0
                || count("select count(*) from vmp_listings where name = ?", name) > 0;
    }

    /** True if the name is the product's own name or the name of a listing under that article number. */
    public boolean nameBelongsToArticle(String name, String articleNo) {
        return count("select count(*) from vmp_products where article_no = ? and name = ?", articleNo, name) > 0
                || count("select count(*) from vmp_listings where name = ? and (article_no = ? or product_article_no = ?)",
                name, articleNo, articleNo) > 0;
    }

    public String productName(String articleNo) {
        List<String> n = jdbc.queryForList("select name from vmp_products where article_no = ?", String.class, articleNo);
        return n.isEmpty() ? null : n.get(0);
    }

    /**
     * Products first, then listing names that no product carries; case-, accent- and Nordic-letter-insensitive
     * ("juleol" finds "Juleøl"); names that start with the query before names that merely contain it.
     */
    public List<Hit> search(String q, int limit) {
        String needle = fold(q);
        if (needle.isEmpty()) return List.of();
        List<Hit> hits = new ArrayList<>();
        Set<String> productNames = new HashSet<>();
        jdbc.query("select article_no, name from vmp_products", rs -> {
            String name = rs.getString(2);
            productNames.add(name);
            if (fold(name).contains(needle)) hits.add(new Hit(rs.getString(1), name, "PRODUCT"));
        });
        jdbc.query("select distinct name from vmp_listings", rs -> {
            String name = rs.getString(1);
            if (!productNames.contains(name) && fold(name).contains(needle)) hits.add(new Hit(null, name, "LISTING"));
        });
        hits.sort(Comparator.<Hit>comparingInt(h -> fold(h.name()).startsWith(needle) ? 0 : 1)
                .thenComparingInt(h -> h.articleNo() != null ? 0 : 1)
                .thenComparing(h -> fold(h.name())));
        return hits.size() > limit ? hits.subList(0, limit) : hits;
    }

    static String fold(String s) {
        if (s == null) return "";
        String n = s.toLowerCase(Locale.ROOT).replace("ø", "o").replace("æ", "ae").replace("å", "a").replace("’", "'");
        n = MARKS.matcher(Normalizer.normalize(n, Normalizer.Form.NFD)).replaceAll("");
        return NON_ALNUM.matcher(n).replaceAll(" ").trim();
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
