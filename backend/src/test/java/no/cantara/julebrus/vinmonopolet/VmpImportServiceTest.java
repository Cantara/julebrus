package no.cantara.julebrus.vinmonopolet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The Vinmonopolet importer: what is on the classpath, idempotency, and that a human decision is never touched. */
@SpringBootTest
@ActiveProfiles("dev")
class VmpImportServiceTest {

    @Autowired
    VmpImportService importer;
    @Autowired
    VoteMatchRepository matches;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    VmpCatalogue catalogue;

    @TempDir
    Path tmp;

    @BeforeEach
    void noDecisions() {
        jdbc.update("update vote_matches set decision = null, decision_article_no = null, decision_product_name = null, "
                + "decided_by = null, decided_at = null");
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    @Test
    void theDataFilesAreOnTheClasspathButTheReadmeIsNot() {
        for (String f : List.of("products", "listings", "vote_matches", "2013", "2025")) {
            assertThat(new ClassPathResource("vinmonopolet/" + f + ".csv").exists()).as(f).isTrue();
        }
        assertThat(new ClassPathResource("vinmonopolet/README.md").exists()).isFalse();
    }

    @Test
    void startupLoadedTheTables() {
        assertThat(count("vmp_products")).isEqualTo(662);
        assertThat(count("vmp_listings")).isEqualTo(2518);
        // 401 entries in the file, one of them (2018 CB Juleøl) listed twice
        assertThat(count("vote_matches")).isEqualTo(400);
    }

    @Test
    void aSecondRunChangesNothing() throws Exception {
        long p = count("vmp_products"), l = count("vmp_listings"), v = count("vote_matches");
        long versionSum = jdbc.queryForObject("select sum(version) from vote_matches", Long.class);

        VmpImportService.Result r = importer.importClasspath();
        assertThat(r.products().inserted()).isZero();
        assertThat(r.listings().inserted()).isZero();
        assertThat(r.voteMatches().inserted()).isZero();
        assertThat(r.voteMatches().updated()).isZero();
        assertThat(r.voteMatches().unchanged()).isEqualTo(400);
        assertThat(r.voteMatches().duplicates()).isEqualTo(1);
        assertThat(count("vmp_products")).isEqualTo(p);
        assertThat(count("vmp_listings")).isEqualTo(l);
        assertThat(count("vote_matches")).isEqualTo(v);
        assertThat(jdbc.queryForObject("select sum(version) from vote_matches", Long.class)).isEqualTo(versionSum);
    }

    @Test
    void aDecisionSurvivesAReimport() throws Exception {
        VoteMatch m = matches.findAll().stream().filter(x -> x.getMachineProductName() != null).findFirst().orElseThrow();
        m.decide(VoteMatch.Decision.CHOSEN, null, m.getMachineProductName(), null, java.time.Instant.parse("2026-10-24T10:00:00Z"));
        matches.saveAndFlush(m);

        importer.importClasspath();
        importer.importClasspath();

        VoteMatch after = matches.findById(m.getId()).orElseThrow();
        assertThat(after.getDecision()).isEqualTo(VoteMatch.Decision.CHOSEN);
        assertThat(after.getDecisionProductName()).isEqualTo(m.getMachineProductName());
        assertThat(after.getDecidedAt()).isEqualTo(java.time.Instant.parse("2026-10-24T10:00:00Z"));
    }

    @Test
    void changedMachineFieldsUpdateButTheDecisionStays() throws Exception {
        VoteMatch m = matches.findAll().stream().filter(x -> "likely".equals(x.getMachineStatus()) && x.getMachineProductName() != null)
                .findFirst().orElseThrow();
        m.decide(VoteMatch.Decision.CONFIRMED, m.getMachineArticleNo(), m.getMachineProductName(), null,
                java.time.Instant.parse("2026-10-24T10:00:00Z"));
        matches.saveAndFlush(m);
        long versionBefore = matches.findById(m.getId()).orElseThrow().getVersion();
        String originalName = m.getMachineProductName();

        // A newer vote_matches.csv: the same entry now reads "review" with another guess and a new candidate list,
        // plus one entry the table has never seen.
        List<Map<String, String>> rows = CsvTable.read(new ClassPathResource("vinmonopolet/vote_matches.csv").getInputStream());
        StringBuilder csv = new StringBuilder("year,vote_beer,status,method,score,product_article_no,article_options,product_name,list_year,candidates\n");
        for (Map<String, String> r : rows) {
            boolean target = r.get("year").equals(String.valueOf(m.getYear())) && r.get("vote_beer").equals(m.getVoteBeer());
            csv.append(r.get("year")).append(',').append(CsvTable.field(r.get("vote_beer"))).append(',')
                    .append(target ? "review" : r.get("status")).append(',').append(r.get("method")).append(',')
                    .append(target ? "0.55" : r.get("score")).append(',').append(r.get("product_article_no")).append(',')
                    .append(r.get("article_options")).append(',')
                    .append(CsvTable.field(target ? "Some Other Beer" : r.get("product_name"))).append(',').append(r.get("list_year")).append(',')
                    .append(CsvTable.field(target ? "Some Other Beer [123] 0.55" : r.get("candidates"))).append('\n');
        }
        csv.append("2019,").append(CsvTable.field("A New \"Quoted\" Beer, really")).append(",none,,,,,,,\n");
        Path changed = tmp.resolve("vote_matches.csv");
        Files.writeString(changed, csv.toString(), StandardCharsets.UTF_8);
        VmpImportService.Sources src = new VmpImportService.Sources(new ClassPathResource("vinmonopolet/products.csv"),
                new ClassPathResource("vinmonopolet/listings.csv"), new FileSystemResource(changed));

        try {
            VmpImportService.Result r = importer.importFrom(src);
            assertThat(r.voteMatches().updated()).isEqualTo(1);
            assertThat(r.voteMatches().inserted()).isEqualTo(1);
            assertThat(r.products().inserted()).isZero();

            VoteMatch after = matches.findById(m.getId()).orElseThrow();
            assertThat(after.getMachineStatus()).isEqualTo("review");
            assertThat(after.getMachineProductName()).isEqualTo("Some Other Beer");
            assertThat(after.getCandidates()).isEqualTo("Some Other Beer [123] 0.55");
            assertThat(after.getMachineScore()).isEqualByComparingTo("0.55");
            assertThat(after.getDecision()).isEqualTo(VoteMatch.Decision.CONFIRMED);
            assertThat(after.getDecisionProductName()).isEqualTo(originalName);
            assertThat(after.getVersion()).isGreaterThan(versionBefore); // a person with the old page gets a 409
            assertThat(matches.findAll().stream().anyMatch(x -> x.getVoteBeer().equals("A New \"Quoted\" Beer, really"))).isTrue();
            assertThat(count("vote_matches")).isEqualTo(401);
        } finally {
            jdbc.update("delete from vote_matches where vote_beer = ?", "A New \"Quoted\" Beer, really");
            importer.importClasspath(); // put the machine side back
        }
        VoteMatch back = matches.findById(m.getId()).orElseThrow();
        assertThat(back.getMachineStatus()).isEqualTo("likely");
        assertThat(back.getMachineProductName()).isEqualTo(originalName);
        assertThat(back.getDecision()).isEqualTo(VoteMatch.Decision.CONFIRMED);
    }

    @Test
    void aRowMissingFromTheFileIsNotDeleted() throws Exception {
        Path empty = tmp.resolve("empty.csv");
        Files.writeString(empty, "year,vote_beer,status,method,score,product_article_no,article_options,product_name,list_year,candidates\n");
        VmpImportService.Result r = importer.importFrom(new VmpImportService.Sources(new FileSystemResource(empty),
                new FileSystemResource(empty), new FileSystemResource(empty)));
        assertThat(r.voteMatches().read()).isZero();
        assertThat(count("vote_matches")).isEqualTo(400);
        assertThat(count("vmp_products")).isEqualTo(662);
    }

    @Test
    void aMissingFileIsSkippedNotFatal() throws Exception {
        VmpImportService.Result r = importer.importFrom(new VmpImportService.Sources(new FileSystemResource(tmp.resolve("nope.csv")),
                new FileSystemResource(tmp.resolve("nope2.csv")), new FileSystemResource(tmp.resolve("nope3.csv"))));
        assertThat(r.voteMatches().read()).isZero();
        assertThat(count("vote_matches")).isEqualTo(400);
    }

    @Test
    void listingsAreMatchedAsAMultiset() throws Exception {
        // The lists repeat a name within a year (39 times). Deleting one of the repeats must restore exactly one on import.
        Long id = jdbc.queryForObject("select min(id) from vmp_listings where list_year = 2014 and name = 'Gouden Carolus Christmas'", Long.class);
        long before = count("vmp_listings");
        jdbc.update("delete from vmp_listings where id = ?", id);
        VmpImportService.Result r = importer.importClasspath();
        assertThat(r.listings().inserted()).isEqualTo(1);
        assertThat(count("vmp_listings")).isEqualTo(before);
    }

    @Test
    void everyMachineGuessAndCandidateNamesAThingTheAdminCanChoose() {
        for (VoteMatch m : matches.findAll()) {
            if (m.getMachineProductName() != null) {
                assertThat(catalogue.nameExists(m.getMachineProductName())).as(m.getVoteBeer() + " guess").isTrue();
            }
            for (MatchService.Candidate c : MatchService.parseCandidates(m.getCandidates())) {
                assertThat(catalogue.nameExists(c.name())).as(m.getVoteBeer() + " candidate " + c.name()).isTrue();
                if (c.articleNo() != null) assertThat(catalogue.articleExists(c.articleNo())).as(c.name()).isTrue();
            }
        }
    }
}
