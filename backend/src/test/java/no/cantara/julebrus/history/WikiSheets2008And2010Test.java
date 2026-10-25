package no.cantara.julebrus.history;

import no.cantara.julebrus.landing.RepoData;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * 2008 and 2010 are parsed from Google-spreadsheet HTML on the wiki (tools/extract_wiki_tables.py, sheet_2008 and
 * sheet_2010). The expected numbers here are the ones the sheets print themselves; the test recomputes them from the
 * committed data/votes.csv, so a parse that puts a score in the wrong column fails. See data/SOURCES.md.
 */
class WikiSheets2008And2010Test {

    static List<Map<String, String>> votes(int year) {
        return RepoData.csv(RepoData.DATA.resolve("votes.csv")).stream().filter(r -> r.get("year").equals(String.valueOf(year))).toList();
    }

    static List<Map<String, String>> of(List<Map<String, String>> rows, String key, String value) {
        return rows.stream().filter(r -> r.get(key).equals(value)).toList();
    }

    static double mean(List<Map<String, String>> rows) {
        return rows.stream().mapToDouble(r -> Double.parseDouble(r.get("score"))).average().orElseThrow();
    }

    static Map<String, String> weights(List<Map<String, String>> rows) {
        return rows.stream().collect(Collectors.toMap(r -> r.get("voter"), r -> r.get("weight"), (a, b) -> {
            assertThat(a).as("one weight per voter").isEqualTo(b);
            return a;
        }));
    }

    @Test
    void the2008SheetsOwnCountsAndMeans() {
        List<Map<String, String>> votes = votes(2008);
        assertThat(votes).hasSize(144);
        // "Sum Ølstemmer" and "Snitt avgitt stemme" cover the beers, not the three Fenalår columns.
        Predicate<Map<String, String>> beer = r -> !r.get("beer").startsWith("Fenalår");
        Map<String, double[]> stated = Map.of("Totto", new double[]{9, 3.33}, "Petter", new double[]{12, 4.08},
                "Alvin", new double[]{11, 3.27}, "Elin", new double[]{4, 4.75}, "Erik D", new double[]{5, 4});
        stated.forEach((voter, s) -> {
            List<Map<String, String>> mine = of(votes, "voter", voter).stream().filter(beer).toList();
            assertThat(mine).as(voter).hasSize((int) s[0]);
            assertThat(mean(mine)).as(voter).isCloseTo(s[1], offset(0.006));
        });
        // "Total number of votes" and "Number of votes 0.5 weight" per beer.
        Map<String, int[]> perBeer = Map.of("Aass", new int[]{10, 2}, "Aass Premium", new int[]{12, 2},
                "Ringnes Julebokk", new int[]{11, 2}, "Nøgne Ø - Underlig jul", new int[]{6, 3}, "Fenalår Grillstad", new int[]{9, 3});
        perBeer.forEach((name, n) -> {
            List<Map<String, String>> rows = of(votes, "beer", name);
            assertThat(rows).as(name).hasSize(n[0]);
            assertThat(of(rows, "weight", "0.5")).as(name + " at 0.5").hasSize(n[1]);
        });
        // The weights follow from those counts: four voters at 0.5, the rest at 1. Janken scored nothing.
        Map<String, String> w = weights(votes);
        assertThat(w).hasSize(17).doesNotContainKey("Janken");
        assertThat(w.entrySet().stream().filter(e -> e.getValue().equals("0.5")).map(Map.Entry::getKey))
                .containsExactlyInAnyOrder("Erik D", "Stig L", "Mads", "Agnes");
        assertThat(w.values()).allMatch(v -> v.equals("1") || v.equals("0.5"));
    }

    @Test
    void the2010SheetsOwnCountsAndMeans() {
        List<Map<String, String>> votes = votes(2010);
        assertThat(votes).hasSize(172); // 171 from the wiki page plus Eirik's vote it lacks (ADDED_VOTES)
        // "Antall" per voter, over every column (the Fenalår included); the last three are in the sheet's second table.
        // Eirik: the wiki page says 9, the final workbook 10, because it has his vote on Aass Juleøl premium.
        Map.of("Totto", 24, "Petter", 15, "Dyveke", 7, "Kjetil J. D.", 8, "Stig Lau", 10, "Eirik", 10)
                .forEach((voter, n) -> assertThat(of(votes, "voter", voter)).as(voter).hasSize(n));
        // "Gjennomsnitt" (unweighted) per beer matches for every beer; a few of them here. Aass Juleøl premium: the wiki page
        // states 4.43 (without Eirik's vote); the final workbook states 4.5 and reproduces it with his 5.
        Map.of("Dahls", 2.92, "Aass Juleøl premium", 4.5, "Berentsen Julefnugg", 4.00, "Nøgne Ø Special Holiday Ale", 5.00, "fenalår Smedstuen", 4.56)
                .forEach((name, m) -> assertThat(mean(of(votes, "beer", name))).as(name).isCloseTo(m, offset(0.006)));
        // "Vektet snitt" and "Stemmevolum" match for Dahls; for most other beers the sheet's weighted numbers do not
        // follow from its own vote rows (Aas Juleøl: stated volume 8, listed weight 7). Both are kept as they are.
        List<Map<String, String>> dahls = of(votes, "beer", "Dahls");
        double w = dahls.stream().mapToDouble(r -> Double.parseDouble(r.get("weight"))).sum();
        assertThat(w).isEqualTo(10.5);
        assertThat(dahls.stream().mapToDouble(r -> Double.parseDouble(r.get("score")) * Double.parseDouble(r.get("weight"))).sum() / w)
                .isCloseTo(2.81, offset(0.006));
        Map<String, String> aas = RepoData.csv(RepoData.DATA.resolve("results.csv")).stream()
                .filter(r -> r.get("year").equals("2010") && r.get("beer").equals("Aas Juleøl")).findFirst().orElseThrow();
        assertThat(aas.get("weight_sum")).isEqualTo("8");
        assertThat(of(votes, "beer", "Aas Juleøl").stream().mapToDouble(r -> Double.parseDouble(r.get("weight"))).sum()).isEqualTo(7.0);
        // Weights from the "Vekting" column; Cynthia scored nothing; notes ("snasent!", "b") are not votes.
        Map<String, String> wt = weights(votes);
        assertThat(wt).hasSize(16).doesNotContainKey("Cynthia").containsEntry("Ingvild", "0.5").containsEntry("Kjetil J. D.", "0");
        assertThat(of(votes, "beer", "Vertskapet")).isEmpty();
        assertThat(of(of(votes, "voter", "Eirik"), "beer", "Ægir julebrygg")).isEmpty();
        // Eirik's vote the wiki's HTML snapshot lacks, from the final workbook (data/SOURCES.md).
        List<Map<String, String>> eirikPremium = of(of(votes, "voter", "Eirik"), "beer", "Aass Juleøl premium");
        assertThat(eirikPremium).hasSize(1);
        assertThat(eirikPremium.get(0)).containsEntry("score", "5").containsEntry("weight", "1");
        // with it the workbook's own stated weighted mean (4.5) and volume (8) follow from the votes
        List<Map<String, String>> premium = of(votes, "beer", "Aass Juleøl premium");
        assertThat(premium.stream().mapToDouble(r -> Double.parseDouble(r.get("weight"))).sum()).isEqualTo(8.0);
        assertThat(premium.stream().mapToDouble(r -> Double.parseDouble(r.get("score")) * Double.parseDouble(r.get("weight"))).sum() / 8.0)
                .isCloseTo(4.5, offset(0.006));
    }

    @Test
    void theAppFilesHoldTheSameVotes() {
        for (int year : new int[]{2008, 2010}) {
            List<Map<String, String>> app = RepoData.csv(Path.of("src", "main", "resources", "history", year + ".csv"));
            assertThat(app).as(year + ".csv").hasSize(votes(year).size());
        }
    }
}
