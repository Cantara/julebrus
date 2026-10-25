package no.cantara.julebrus.history;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What Vinmonopolet's lists say about a beer from the archive, found through the vote matches. Facts are about the PRODUCT as
 * Vinmonopolet lists it, not about the beer in the glass of that year: recipes change. How sure the match is travels with every fact.
 *
 * <p>Tiers, strongest first: CONFIRMED (an administrator decided), MATCHED (the exact name is on a list), PROBABLE (a close name,
 * or my reviewed reading with high confidence), GUESS (my reviewed reading with medium confidence). Nothing is shown for
 * review/none/not-a-product, and an administrator's decision always replaces the machine's.</p>
 */
@Component
public class HistoricBeerFacts {

    public enum Tier { CONFIRMED, MATCHED, PROBABLE, GUESS }

    /** One product the name points to, in the years it applies to. {@code poletUrl} only when there is exactly one article number. */
    public record Fact(List<Integer> years, Tier tier, String productName, List<String> articleNos, String brewery, String style,
                       List<BigDecimal> abvPercent, List<Integer> listedYears, BigDecimal latestPriceNok, Integer latestPriceYear,
                       String poletUrl) {
    }

    private final JdbcTemplate jdbc;

    public HistoricBeerFacts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Pick(int year, Tier tier, String productName, List<String> articleNos) {
    }

    public List<Fact> forName(String name) {
        List<Pick> picks = new ArrayList<>();
        jdbc.query("select season_year, machine_status, machine_method, machine_article_no, machine_article_options, "
                + "machine_product_name, decision, decision_article_no, decision_product_name "
                + "from vote_matches where lower(vote_beer) = lower(?) order by season_year", rs -> {
            int year = rs.getInt(1);
            String status = rs.getString(2), method = rs.getString(3), art = rs.getString(4), options = rs.getString(5);
            String machineName = rs.getString(6), decision = rs.getString(7), decisionArt = rs.getString(8), decisionName = rs.getString(9);
            if (decision != null) {
                switch (decision) {
                    case "CHOSEN" -> picks.add(pick(year, Tier.CONFIRMED, decisionName, decisionArt, null));
                    case "CONFIRMED" -> picks.add(pick(year, Tier.CONFIRMED, machineName != null ? machineName : decisionName,
                            decisionArt != null ? decisionArt : art, options));
                    default -> { /* NONE or NOT_A_PRODUCT: a person says there is nothing to show */ }
                }
                return;
            }
            Tier tier = tierOf(status, method);
            if (tier != null && machineName != null) picks.add(pick(year, tier, machineName, art, options));
        }, name);

        Map<String, List<Pick>> grouped = new LinkedHashMap<>();
        for (Pick p : picks) grouped.computeIfAbsent(p.tier() + "\u0000" + p.productName(), k -> new ArrayList<>()).add(p);
        List<Fact> out = new ArrayList<>();
        for (List<Pick> g : grouped.values()) out.add(fact(g));
        out.sort((a, b) -> a.tier() != b.tier() ? a.tier().compareTo(b.tier()) : Integer.compare(a.years().get(0), b.years().get(0)));
        return out;
    }

    static Tier tierOf(String status, String method) {
        if ("auto".equals(status)) return Tier.MATCHED;
        if (!"likely".equals(status)) return null;
        return "analysis-medium".equals(method) ? Tier.GUESS : Tier.PROBABLE;
    }

    private Pick pick(int year, Tier tier, String productName, String articleNo, String options) {
        Set<String> arts = new LinkedHashSet<>();
        if (articleNo != null && !articleNo.isBlank()) arts.add(articleNo.trim());
        if (arts.isEmpty() && options != null && !options.isBlank()) {
            for (String o : options.split(";")) if (!o.isBlank()) arts.add(o.trim());
        }
        if (arts.isEmpty() && productName != null) {
            arts.addAll(jdbc.queryForList("select article_no from vmp_products where lower(name) = lower(?) order by article_no",
                    String.class, productName));
        }
        return new Pick(year, tier, productName, List.copyOf(arts));
    }

    private Fact fact(List<Pick> g) {
        Pick first = g.get(0);
        Set<String> arts = new LinkedHashSet<>();
        for (Pick p : g) arts.addAll(p.articleNos());
        List<String> articleNos = List.copyOf(arts);
        String brewery = null, style = null;
        Set<BigDecimal> abv = new LinkedHashSet<>();
        if (!articleNos.isEmpty()) {
            String in = String.join(",", Arrays.stream(new String[articleNos.size()]).map(x -> "?").toList());
            for (var row : jdbc.queryForList("select brewery, style, abv_percent from vmp_products where article_no in (" + in + ")",
                    articleNos.toArray())) {
                if (brewery == null && row.get("brewery") != null) brewery = (String) row.get("brewery");
                if (style == null && row.get("style") != null) style = (String) row.get("style");
                if (row.get("abv_percent") != null) abv.add(((BigDecimal) row.get("abv_percent")).stripTrailingZeros());
            }
        }
        // Listings: by article number where we have one, otherwise by the product's name.
        TreeSet<Integer> listed = new TreeSet<>();
        BigDecimal price = null;
        Integer priceYear = null;
        List<Map<String, Object>> listings = articleNos.isEmpty()
                ? jdbc.queryForList("select list_year, price_nok, abv_percent from vmp_listings where lower(name) = lower(?)", first.productName())
                : jdbc.queryForList("select list_year, price_nok, abv_percent from vmp_listings where lower(name) = lower(?) "
                + "or product_article_no in (" + String.join(",", Arrays.stream(new String[articleNos.size()]).map(x -> "?").toList()) + ")",
                concat(first.productName(), articleNos));
        for (var l : listings) {
            int y = ((Number) l.get("list_year")).intValue();
            listed.add(y);
            if (l.get("price_nok") != null && (priceYear == null || y > priceYear)) {
                price = (BigDecimal) l.get("price_nok");
                priceYear = y;
            }
            if (l.get("abv_percent") != null) abv.add(((BigDecimal) l.get("abv_percent")).stripTrailingZeros());
        }
        List<Integer> years = g.stream().map(Pick::year).distinct().sorted().toList();
        String url = articleNos.size() == 1 ? "https://www.vinmonopolet.no/p/" + articleNos.get(0) : null;
        return new Fact(years, first.tier(), first.productName(), articleNos, brewery, style, List.copyOf(abv), List.copyOf(listed),
                price, priceYear, url);
    }

    private static Object[] concat(String first, List<String> rest) {
        Object[] a = new Object[rest.size() + 1];
        a[0] = first;
        for (int i = 0; i < rest.size(); i++) a[i + 1] = rest.get(i);
        return a;
    }
}
