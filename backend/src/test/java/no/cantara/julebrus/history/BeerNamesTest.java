package no.cantara.julebrus.history;

import no.cantara.julebrus.landing.RepoData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** beer_names.csv: applied to ratings already in the database, idempotent, reversible, and well formed. */
@SpringBootTest
@ActiveProfiles("dev")
class BeerNamesTest {

    static final int YEAR = 1999; // no real season; the other tests do not use it

    @Autowired
    HistoryImporter importer;
    @Autowired
    HistoricRatingRepository ratings;
    @Autowired
    HistoricSeasonRepository seasons;

    Map<String, String> real() throws Exception {
        return new HashMap<>(HistoryImporter.readBeerNames(new ClassPathResource("history/beer_names.csv")));
    }

    List<HistoricRating> season() {
        return ratings.findBySeasonYearOrderByBeerNameAsc(YEAR);
    }

    @Test
    void correctsExistingRatingsWithoutLosingThem() throws Exception {
        if (!seasons.existsById(YEAR)) {
            seasons.save(new HistoricSeason(YEAR, "Test " + YEAR, new BigDecimal("1"), new BigDecimal("6"), null, null));
        }
        String typo = "Zq Bådom " + System.nanoTime();
        String fixed = typo.replace("Bådom", "Bådin");
        for (String rater : new String[]{"R1", "R2", "R3"}) {
            ratings.save(new HistoricRating(YEAR, typo, null, rater, null, new BigDecimal("4"), BigDecimal.ONE, null));
        }
        ratings.save(new HistoricRating(YEAR, typo + " other", null, "R1", null, new BigDecimal("3"), BigDecimal.ONE, null));
        long before = ratings.count();

        Map<String, String> map = real();
        map.put(YEAR + "|" + typo, fixed);
        map.put(YEAR + "|No Such Beer", "Nothing");           // unknown row: ignored
        map.put("1888|" + typo, "Wrong year");                 // other season: ignored
        assertThat(importer.applyBeerNames(map)).isEqualTo(3);

        assertThat(ratings.count()).isEqualTo(before);         // nothing deleted or re-imported
        List<HistoricRating> fixedRows = season().stream().filter(r -> r.getBeerName().equals(fixed)).toList();
        assertThat(fixedRows).hasSize(3).allSatisfy(r -> assertThat(r.getBeerNameOriginal()).isEqualTo(typo));
        assertThat(season()).filteredOn(r -> r.getBeerName().equals(typo + " other"))
                .hasSize(1).allSatisfy(r -> assertThat(r.getBeerNameOriginal()).isNull());

        assertThat(importer.applyBeerNames(map)).as("second start changes nothing").isZero();
        assertThat(ratings.count()).isEqualTo(before);

        map.put(YEAR + "|" + typo, "Second Thought");           // a changed correction replaces the first
        assertThat(importer.applyBeerNames(map)).isEqualTo(3);
        assertThat(season()).filteredOn(r -> r.getBeerName().equals("Second Thought"))
                .hasSize(3).allSatisfy(r -> assertThat(r.getBeerNameOriginal()).isEqualTo(typo));

        map.remove(YEAR + "|" + typo);                          // row removed: the original comes back
        assertThat(importer.applyBeerNames(map)).isEqualTo(3);
        assertThat(season()).filteredOn(r -> r.getBeerName().equals(typo))
                .hasSize(3).allSatisfy(r -> assertThat(r.getBeerNameOriginal()).isNull());
    }

    @Test
    void theShippedCorrectionsAreAppliedAtStartup() {
        List<HistoricRating> rows = ratings.findBySeasonYearOrderByBeerNameAsc(2019);
        assertThat(rows).filteredOn(r -> "Ringnes julebokk".equals(r.getBeerNameOriginal()))
                .isNotEmpty().allSatisfy(r -> assertThat(r.getBeerName()).isEqualTo("Ringnes Julebokk"));
        assertThat(rows).noneMatch(r -> r.getBeerName().equals("Ringnes julebokk"));
    }

    @Test
    void theFileIsWellFormed() {
        Set<String> seen = new HashSet<>();
        Map<Integer, Set<String>> written = new HashMap<>();
        RepoData.csv(RepoData.DATA.resolve("votes.csv")).forEach(r ->
                written.computeIfAbsent(Integer.parseInt(r.get("year")), y -> new HashSet<>()).add(r.get("beer").trim()));
        RepoData.csv(RepoData.DATA.resolve("results.csv")).forEach(r ->
                written.computeIfAbsent(Integer.parseInt(r.get("year")), y -> new HashSet<>()).add(r.get("beer").trim()));
        // the raw votes, not the corrected ones: RepoData.csv is uncorrected
        List<Map<String, String>> rows = RepoData.csv(RepoData.BEER_NAMES);
        for (Map<String, String> r : rows) {
            String year = r.get("year").trim(), name = r.get("as_written").trim(), canonical = r.get("canonical").trim();
            assertThat(year).as("year of " + name).matches("\\d{4}");
            assertThat(canonical).as("canonical for " + year + " " + name).isNotEmpty();
            assertThat(seen.add(year + "|" + name)).as("duplicate " + year + " " + name).isTrue();
            assertThat(written.getOrDefault(Integer.parseInt(year), Set.of()))
                    .as(name + " is not written in " + year + "'s data").contains(name);
        }
    }
}
