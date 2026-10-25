package no.cantara.julebrus.history;

import no.cantara.julebrus.landing.RepoData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * beer_names.csv: applied to ratings already in the database, idempotent, reversible, and well formed. Rows marked
 * legacy correct names the source files no longer hold, for databases imported before the files were corrected.
 */
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

    /** The rows of the shipped beer_names.csv marked legacy (fourth column), as (year, as written, canonical). */
    static List<List<String>> legacyRows() {
        return RepoData.csv(RepoData.BEER_NAMES).stream().filter(r -> "legacy".equals(r.getOrDefault("note", "").trim()))
                .map(r -> List.of(r.get("year").trim(), r.get("as_written").trim(), r.get("canonical").trim())).toList();
    }

    @Test
    void legacyRowsRenameADatabaseImportedBeforeTheFilesWereCorrected() throws Exception {
        // A database imported from the old files (production) holds the old names. Put one rating per legacy row
        // into that row's real season under the old name, as the first import wrote it, and start again.
        List<List<String>> legacy = legacyRows();
        assertThat(legacy).hasSize(41);
        String rater = "Legacy Rater " + System.nanoTime();
        List<HistoricRating> added = new ArrayList<>();
        for (List<String> row : legacy) {
            added.add(ratings.save(new HistoricRating(Integer.parseInt(row.get(0)), row.get(1), null, rater, null,
                    new BigDecimal("4"), BigDecimal.ONE, null)));
        }
        try {
            long before = ratings.count();
            assertThat(importer.applyBeerNames(real())).isEqualTo(legacy.size());
            assertThat(ratings.count()).isEqualTo(before);
            for (int i = 0; i < legacy.size(); i++) {
                HistoricRating r = ratings.findById(added.get(i).getId()).orElseThrow();
                assertThat(r.getBeerName()).as(legacy.get(i).toString()).isEqualTo(legacy.get(i).get(2));
                assertThat(r.getBeerNameOriginal()).as(legacy.get(i).toString()).isEqualTo(legacy.get(i).get(1));
            }
            assertThat(importer.applyBeerNames(real())).as("second start changes nothing").isZero();
        } finally {
            ratings.deleteAllById(added.stream().map(HistoricRating::getId).toList());
        }
    }

    @Test
    void aDatabaseImportedFromTheCorrectedFilesIsNotTouchedByLegacyRows() throws Exception {
        // This context imported the current history/<year>.csv at startup and applied beer_names.csv.
        assertThat(importer.applyBeerNames(real())).as("idempotent").isZero();
        Set<String> legacyKeys = new HashSet<>();
        legacyRows().forEach(r -> legacyKeys.add(r.get(0) + "|" + r.get(1)));
        Set<String> certainKeys = new HashSet<>();
        RepoData.csv(RepoData.BEER_NAMES).stream().filter(r -> r.getOrDefault("note", "").isBlank())
                .forEach(r -> certainKeys.add(r.get("year").trim() + "|" + r.get("as_written").trim()));
        for (HistoricRating r : ratings.findAll()) {
            String key = r.getSeasonYear() + "|" + r.sourceBeerName().trim();
            assertThat(legacyKeys).as("legacy row matched a fresh import: " + key).doesNotContain(key);
            if (r.getBeerNameOriginal() != null) {
                assertThat(certainKeys).as("only the certain rows rename a fresh import: " + key).contains(key);
            }
        }
        // Grouping by beer name across seasons, as the app's beer history does.
        assertThat(ratings.findByBeerNameIgnoreCaseOrderBySeasonYearDesc("Dahls Juleøl"))
                .extracting(HistoricRating::getSeasonYear).contains(2011, 2012, 2013, 2014, 2015, 2017, 2018, 2019);
        assertThat(ratings.findByBeerNameIgnoreCaseOrderBySeasonYearDesc("Ringnes Julebokk"))
                .extracting(HistoricRating::getSeasonYear).contains(2014, 2015, 2019);
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
            String note = r.getOrDefault("note", "").trim();
            assertThat(year).as("year of " + name).matches("\\d{4}");
            assertThat(canonical).as("canonical for " + year + " " + name).isNotEmpty();
            assertThat(seen.add(year + "|" + name)).as("duplicate " + year + " " + name).isTrue();
            assertThat(note).as("note of " + year + " " + name).isIn("", "legacy");
            Set<String> names = written.getOrDefault(Integer.parseInt(year), Set.of());
            if (note.isEmpty()) {
                assertThat(names).as(name + " is not written in " + year + "'s data").contains(name);
            } else {
                // legacy: the files were corrected; the old name is gone and the new one is what they say now
                assertThat(names).as(name + " is still written in " + year + "'s data: not a legacy row").doesNotContain(name);
                assertThat(names).as(canonical + " (legacy target) is not written in " + year + "'s data").contains(canonical);
            }
        }
    }
}
