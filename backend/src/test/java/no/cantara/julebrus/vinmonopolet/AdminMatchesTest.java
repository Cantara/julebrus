package no.cantara.julebrus.vinmonopolet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.cantara.julebrus.ConsentSupport;
import no.cantara.julebrus.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin API for reviewing vote matches. The consent gate and CSRF are tested on the production chain in {@link MatchesProdChainTest}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminMatchesTest {

    static final String ADMIN = "dev@cantara.no"; // configured admin in the dev profile
    static final String PLAIN = "plain-matches@cantara.no";
    static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    VoteMatchRepository repo;
    @Autowired
    UserRepository users;

    @BeforeEach
    void freshState() throws Exception {
        ConsentSupport.accept(mvc, ADMIN);
        ConsentSupport.accept(mvc, PLAIN);
        jdbc.update("update vote_matches set decision = null, decision_article_no = null, decision_product_name = null, "
                + "decided_by = null, decided_at = null");
    }

    // --- helpers

    static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder admin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) {
        return b.header("X-Dev-User", ADMIN);
    }

    JsonNode page(String query) throws Exception {
        String body = mvc.perform(admin(get("/api/admin/matches" + query))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    ResultActions decide(JsonNode row, String json) throws Exception {
        return mvc.perform(admin(post("/api/admin/matches/" + row.get("id").asText() + "/decision"))
                .contentType(APPLICATION_JSON).content(json));
    }

    String act(String action, JsonNode row) {
        return "{\"action\":\"%s\",\"version\":%d}".formatted(action, row.get("version").asLong());
    }

    /** First row of the given machine status that has (or lacks) a machine product name. */
    JsonNode firstRow(String status, boolean withGuess) throws Exception {
        for (JsonNode r : page("?status=" + status + "&size=100").get("items")) {
            if (r.get("machine").get("productName").isNull() != withGuess) return r;
        }
        throw new AssertionError("no " + status + " row with guess=" + withGuess);
    }

    /** The row as the API shows it now. */
    JsonNode reload(JsonNode row) throws Exception {
        String id = row.get("id").asText();
        int year = repo.findById(UUID.fromString(id)).orElseThrow().getYear();
        for (JsonNode r : page("?year=" + year + "&size=100").get("items")) {
            if (r.get("id").asText().equals(id)) return r;
        }
        throw new AssertionError("row vanished: " + id);
    }

    // --- authorisation

    @Test
    void everyEndpointIsForAdminsOnly() throws Exception {
        String id = UUID.randomUUID().toString();
        String body = "{\"action\":\"NONE\",\"version\":0}";
        var calls = List.of(
                get("/api/admin/matches"),
                get("/api/admin/matches?status=review&year=1998&decided=false&page=0&size=5"),
                post("/api/admin/matches/" + id + "/decision").contentType(APPLICATION_JSON).content(body),
                post("/api/admin/matches/bulk").contentType(APPLICATION_JSON).content("{\"action\":\"CONFIRM\",\"year\":1998,\"status\":\"likely\"}"),
                get("/api/admin/matches/overrides.csv"),
                get("/api/admin/products/search?q=jule"),
                post("/api/admin/vinmonopolet/import"));
        for (var call : calls) {
            mvc.perform(call.header("X-Dev-User", PLAIN)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        // and nothing changed
        assertThat(repo.findDecided()).isEmpty();
    }

    // --- listing, ordering, summary, paging

    @Test
    void summaryCountsStatusesAndDecisions() throws Exception {
        JsonNode s = page("").get("summary");
        assertThat(s.get("total").asInt()).isEqualTo(400);
        assertThat(s.get("decided").asInt()).isZero();
        assertThat(s.get("undecided").asInt()).isEqualTo(400);
        int sum = 0;
        for (String st : List.of("review", "none", "likely", "auto", "not-a-product")) {
            JsonNode c = s.get("byStatus").get(st);
            long expected = repo.findAll().stream().filter(m -> m.getMachineStatus().equals(st)).count();
            assertThat(c.get("total").asLong()).as(st).isEqualTo(expected);
            assertThat(c.get("undecided").asLong()).as(st).isEqualTo(expected);
            sum += c.get("total").asInt();
        }
        assertThat(sum).isEqualTo(400);

        JsonNode row = firstRow("likely", true);
        decide(row, act("CONFIRM", row)).andExpect(status().isOk());
        JsonNode row2 = firstRow("none", false);
        decide(row2, act("NONE", row2)).andExpect(status().isOk());
        s = page("").get("summary");
        assertThat(s.get("decided").asInt()).isEqualTo(2);
        assertThat(s.get("undecided").asInt()).isEqualTo(398);
        assertThat(s.get("byDecision").get("CONFIRMED").asInt()).isEqualTo(1);
        assertThat(s.get("byDecision").get("NONE").asInt()).isEqualTo(1);
        assertThat(s.get("byDecision").get("CHOSEN").asInt()).isZero();
        assertThat(s.get("byStatus").get("likely").get("undecided").asLong())
                .isEqualTo(s.get("byStatus").get("likely").get("total").asLong() - 1);
    }

    @Test
    void undecidedReviewFirstThenNoneLikelyAutoAndDecidedLast() throws Exception {
        JsonNode first = page("?size=1").get("items").get(0);
        decide(first, act("NONE", first)).andExpect(status().isOk());

        List<JsonNode> all = new ArrayList<>();
        for (int p = 0; p < 5; p++) page("?size=100&page=" + p).get("items").forEach(all::add);
        assertThat(all).hasSize(400);
        List<String> order = List.of("review", "none", "likely", "auto", "not-a-product");
        int lastRank = -1;
        boolean seenDecided = false;
        for (JsonNode r : all) {
            boolean decided = !r.get("decision").isNull();
            if (decided) seenDecided = true;
            else assertThat(seenDecided).as("undecided row after a decided one").isFalse();
            if (decided) continue;
            int rank = order.indexOf(r.get("machine").get("status").asText());
            assertThat(rank).as("status order").isGreaterThanOrEqualTo(lastRank);
            lastRank = rank;
        }
        assertThat(all.get(0).get("machine").get("status").asText()).isEqualTo("review");
        assertThat(all.get(399).get("decision").isNull()).isFalse();
        // the same row is never on two pages
        assertThat(all.stream().map(r -> r.get("id").asText()).distinct().count()).isEqualTo(400);
    }

    @Test
    void filtersAndPaging() throws Exception {
        JsonNode p = page("?status=review&year=1998&decided=false");
        assertThat(p.get("items")).isNotEmpty();
        for (JsonNode r : p.get("items")) {
            assertThat(r.get("machine").get("status").asText()).isEqualTo("review");
            assertThat(r.get("year").asInt()).isEqualTo(1998);
        }
        assertThat(p.get("size").asInt()).isEqualTo(25);
        assertThat(page("?decided=true").get("totalItems").asInt()).isZero();
        assertThat(page("").get("totalPages").asInt()).isEqualTo(16); // 400 / 25
        assertThat(page("?page=1").get("page").asInt()).isEqualTo(1);
        assertThat(page("?page=9999").get("items")).isEmpty();
    }

    @Test
    void pageSizeIsCappedAndOddPagingIsClamped() throws Exception {
        assertThat(page("?size=100000").get("items")).hasSize(100);
        assertThat(page("?size=100000").get("size").asInt()).isEqualTo(100);
        assertThat(page("?size=0").get("size").asInt()).isEqualTo(1);
        assertThat(page("?size=-5&page=-3").get("page").asInt()).isZero();
        assertThat(page("?size=-5&page=-3").get("items")).hasSize(1);
    }

    @Test
    void badFiltersAnswer4xxWithACodeNeverA500() throws Exception {
        mvc.perform(admin(get("/api/admin/matches?status=bogus"))).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
        mvc.perform(admin(get("/api/admin/matches?year=abc"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        mvc.perform(admin(get("/api/admin/matches?decided=maybe"))).andExpect(status().isBadRequest());
        mvc.perform(admin(get("/api/admin/matches?size=1e9"))).andExpect(status().isBadRequest());
        mvc.perform(admin(get("/api/admin/matches?status=%27%20or%201=1--"))).andExpect(status().isUnprocessableEntity());
        mvc.perform(admin(post("/api/admin/matches/not-a-uuid/decision")).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(admin(post("/api/admin/matches/" + UUID.randomUUID() + "/decision")).contentType(APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
        mvc.perform(admin(post("/api/admin/matches/" + UUID.randomUUID() + "/decision")).contentType(APPLICATION_JSON)
                .content("{\"action\":\"EXPLODE\",\"version\":0}")).andExpect(status().isBadRequest());
        mvc.perform(admin(post("/api/admin/matches/" + UUID.randomUUID() + "/decision")).contentType(APPLICATION_JSON)
                .content("{\"version\":0}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
        mvc.perform(admin(post("/api/admin/matches/" + UUID.randomUUID() + "/decision")).contentType(APPLICATION_JSON)
                .content("{\"action\":\"NONE\",\"version\":0}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"));
        mvc.perform(admin(post("/api/admin/matches/bulk")).contentType(APPLICATION_JSON).content("{\"action\":\"CONFIRM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void candidatesAreParsedIntoAList() throws Exception {
        JsonNode r = null;
        for (JsonNode x : page("?status=review&size=100").get("items")) {
            if (x.get("candidates").size() >= 3) { r = x; break; }
        }
        assertThat(r).isNotNull();
        JsonNode c = r.get("candidates").get(0);
        assertThat(c.get("name").asText()).isNotBlank();
        assertThat(c.get("score").decimalValue().doubleValue()).isBetween(0.0, 1.0);
        assertThat(c.has("articleNo")).isTrue();
        assertThat(c.has("articleCount")).isTrue();
    }

    // --- decisions

    @Test
    void confirmAcceptsTheMachineGuessAndRecordsWhoAndWhen() throws Exception {
        JsonNode row = firstRow("likely", true);
        Instant before = Instant.now().minusSeconds(2);
        decide(row, act("CONFIRM", row)).andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.kind").value("CONFIRMED"))
                .andExpect(jsonPath("$.decision.productName").value(row.get("machine").get("productName").asText()))
                .andExpect(jsonPath("$.version").value(row.get("version").asLong() + 1));
        VoteMatch m = repo.findById(UUID.fromString(row.get("id").asText())).orElseThrow();
        assertThat(m.getDecision()).isEqualTo(VoteMatch.Decision.CONFIRMED);
        assertThat(m.getDecisionArticleNo()).isEqualTo(m.getMachineArticleNo());
        assertThat(m.getDecidedBy()).isEqualTo(users.findByEmailIgnoreCase(ADMIN).orElseThrow().getId());
        assertThat(m.getDecidedAt()).isAfter(before);
    }

    @Test
    void confirmWithoutAGuessIsRefused() throws Exception {
        JsonNode row = firstRow("none", false);
        decide(row, act("CONFIRM", row)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_MACHINE_GUESS"));
        assertThat(repo.findDecided()).isEmpty();
    }

    @Test
    void noneAndNotAProductAndClearHaveTheirOwnLifecycle() throws Exception {
        JsonNode row = firstRow("review", true);
        decide(row, act("NONE", row)).andExpect(status().isOk()).andExpect(jsonPath("$.decision.kind").value("NONE"))
                .andExpect(jsonPath("$.decision.productName").doesNotExist());
        VoteMatch m = repo.findById(UUID.fromString(row.get("id").asText())).orElseThrow();
        assertThat(m.getDecidedBy()).isNotNull();
        assertThat(m.getDecidedAt()).isNotNull();

        JsonNode again = reload(row);
        decide(again, act("NOT_A_PRODUCT", again)).andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.kind").value("NOT_A_PRODUCT"));

        again = reload(row);
        decide(again, act("CLEAR", again)).andExpect(status().isOk()).andExpect(jsonPath("$.decision").doesNotExist());
        m = repo.findById(m.getId()).orElseThrow();
        assertThat(m.getDecision()).isNull();
        assertThat(m.getDecidedBy()).isNull();
        assertThat(m.getDecidedAt()).isNull();
        assertThat(m.getDecisionProductName()).isNull();
        assertThat(m.getMachineProductName()).isEqualTo(row.get("machine").get("productName").asText()); // machine side untouched
    }

    @Test
    void chooseByArticleNumberFillsTheName() throws Exception {
        JsonNode row = firstRow("review", true);
        String art = jdbc.queryForObject("select article_no from vmp_products where name = 'Aass Juleøl'", String.class);
        decide(row, "{\"action\":\"CHOOSE\",\"version\":%d,\"articleNo\":\"%s\"}".formatted(row.get("version").asLong(), art))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision.kind").value("CHOSEN"))
                .andExpect(jsonPath("$.decision.articleNo").value(art))
                .andExpect(jsonPath("$.decision.productName").value("Aass Juleøl"));
    }

    @Test
    void chooseByNameAloneOrWithItsArticleNumber() throws Exception {
        JsonNode row = firstRow("review", true);
        decide(row, "{\"action\":\"CHOOSE\",\"version\":%d,\"productName\":\"Gouden Carolus Christmas\"}".formatted(row.get("version").asLong()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decision.articleNo").doesNotExist())
                .andExpect(jsonPath("$.decision.productName").value("Gouden Carolus Christmas"));
        String art = jdbc.queryForObject("select article_no from vmp_products where name = 'Aass Juleøl'", String.class);
        JsonNode again = reload(row);
        decide(again, "{\"action\":\"CHOOSE\",\"version\":%d,\"articleNo\":\"%s\",\"productName\":\"Aass Juleøl\"}"
                .formatted(again.get("version").asLong(), art)).andExpect(status().isOk())
                .andExpect(jsonPath("$.decision.articleNo").value(art));
    }

    @Test
    void chooseRejectsUnknownMismatchedAndEmptyChoices() throws Exception {
        JsonNode row = firstRow("review", true);
        long v = row.get("version").asLong();
        String art = jdbc.queryForObject("select article_no from vmp_products where name = 'Aass Juleøl'", String.class);
        decide(row, "{\"action\":\"CHOOSE\",\"version\":%d,\"articleNo\":\"0000\"}".formatted(v))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"));
        decide(row, "{\"action\":\"CHOOSE\",\"version\":%d,\"productName\":\"No Such Beer\"}".formatted(v))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("UNKNOWN_PRODUCT"));
        decide(row, "{\"action\":\"CHOOSE\",\"version\":%d,\"productName\":\"' or 1=1 --\"}".formatted(v))
                .andExpect(status().isUnprocessableEntity());
        decide(row, "{\"action\":\"CHOOSE\",\"version\":%d,\"articleNo\":\"%s\",\"productName\":\"Gouden Carolus Christmas\"}".formatted(v, art))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CHOICE_MISMATCH"));
        decide(row, "{\"action\":\"CHOOSE\",\"version\":%d}".formatted(v))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CHOICE_REQUIRED"));
        decide(row, "{\"action\":\"CHOOSE\",\"version\":%d,\"articleNo\":\"  \",\"productName\":\"\"}".formatted(v))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CHOICE_REQUIRED"));
        assertThat(repo.findDecided()).isEmpty();
    }

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() throws Exception {
        JsonNode row = firstRow("review", true);
        decide(row, act("NONE", row)).andExpect(status().isOk());
        decide(row, act("NOT_A_PRODUCT", row)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        assertThat(repo.findById(UUID.fromString(row.get("id").asText())).orElseThrow().getDecision()).isEqualTo(VoteMatch.Decision.NONE);
    }

    @Test
    void aReimportBetweenLoadAndDecideMakesTheOldPageConflict() throws Exception {
        JsonNode row = firstRow("likely", true);
        VoteMatch m = repo.findById(UUID.fromString(row.get("id").asText())).orElseThrow();
        // the importer changed the machine side meanwhile: bumps the version
        m.applyMachine("review", m.getMachineMethod(), m.getMachineScore(), m.getMachineArticleNo(), m.getMachineArticleOptions(),
                m.getMachineProductName(), m.getMachineListYear(), m.getCandidates());
        repo.saveAndFlush(m);
        try {
            decide(row, act("CONFIRM", row)).andExpect(status().isConflict());
        } finally {
            jdbc.update("update vote_matches set machine_status = 'likely' where id = ?", m.getId());
        }
    }

    // --- bulk

    @Test
    void bulkConfirmDryRunByDefaultThenAppliesToExactlyTheRightRows() throws Exception {
        int year = repo.findAll().stream().filter(m -> m.getMachineStatus().equals("likely")).mapToInt(VoteMatch::getYear)
                .boxed().collect(java.util.stream.Collectors.groupingBy(y -> y, java.util.stream.Collectors.counting()))
                .entrySet().stream().max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey();
        List<VoteMatch> target = repo.findAll().stream()
                .filter(m -> m.getYear() == year && m.getMachineStatus().equals("likely") && m.getMachineProductName() != null).toList();
        assertThat(target.size()).isGreaterThan(2);

        // one of them is already decided by a person: it must keep that decision
        JsonNode pre = null;
        for (JsonNode r : page("?year=" + year + "&status=likely&size=100").get("items")) {
            if (r.get("id").asText().equals(target.get(0).getId().toString())) pre = r;
        }
        decide(pre, act("NONE", pre)).andExpect(status().isOk());
        int expected = target.size() - 1;

        String req = "{\"action\":\"CONFIRM\",\"year\":%d,\"status\":\"likely\"%s}";
        mvc.perform(admin(post("/api/admin/matches/bulk")).contentType(APPLICATION_JSON).content(req.formatted(year, "")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(true)).andExpect(jsonPath("$.count").value(expected));
        assertThat(repo.findDecided()).hasSize(1); // the dry run changed nothing

        mvc.perform(admin(post("/api/admin/matches/bulk")).contentType(APPLICATION_JSON)
                        .content(req.formatted(year, ",\"dryRun\":false,\"expectedCount\":" + (expected + 3))))
                .andExpect(status().isConflict());
        assertThat(repo.findDecided()).hasSize(1);

        mvc.perform(admin(post("/api/admin/matches/bulk")).contentType(APPLICATION_JSON)
                        .content(req.formatted(year, ",\"dryRun\":false,\"expectedCount\":" + expected)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(false)).andExpect(jsonPath("$.count").value(expected));
        List<VoteMatch> decided = repo.findDecided();
        assertThat(decided).hasSize(expected + 1);
        UUID adminId = users.findByEmailIgnoreCase(ADMIN).orElseThrow().getId();
        for (VoteMatch m : decided) {
            if (m.getId().equals(target.get(0).getId())) {
                assertThat(m.getDecision()).isEqualTo(VoteMatch.Decision.NONE);
            } else {
                assertThat(m.getYear()).isEqualTo(year);
                assertThat(m.getMachineStatus()).isEqualTo("likely");
                assertThat(m.getDecision()).isEqualTo(VoteMatch.Decision.CONFIRMED);
                assertThat(m.getDecisionProductName()).isEqualTo(m.getMachineProductName());
                assertThat(m.getDecidedBy()).isEqualTo(adminId);
            }
        }
        // a second apply finds nothing left
        mvc.perform(admin(post("/api/admin/matches/bulk")).contentType(APPLICATION_JSON).content(req.formatted(year, ",\"dryRun\":false")))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void bulkIsOnlyForAutoAndLikely() throws Exception {
        for (String st : List.of("review", "none", "not-a-product", "bogus")) {
            mvc.perform(admin(post("/api/admin/matches/bulk")).contentType(APPLICATION_JSON)
                            .content("{\"action\":\"CONFIRM\",\"year\":1998,\"status\":\"%s\",\"dryRun\":false}".formatted(st)))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BULK_STATUS"));
        }
        mvc.perform(admin(post("/api/admin/matches/bulk")).contentType(APPLICATION_JSON)
                        .content("{\"action\":\"DELETE\",\"year\":1998,\"status\":\"likely\"}"))
                .andExpect(status().isBadRequest());
        assertThat(repo.findDecided()).isEmpty();
    }

    // --- overrides.csv

    @Test
    void overridesCsvHasTheRepoFormatAndRoundTrips() throws Exception {
        // Odd names the real data may hold: a comma, quotes, a line break.
        VoteMatch odd = new VoteMatch(1999, "Odd \"Quoted\", Beer\nLine two");
        odd.applyMachine("review", "m", null, null, null, null, null, null);
        odd.decide(VoteMatch.Decision.CHOSEN, "12345", "Name, with \"quotes\"", null, Instant.now());
        repo.saveAndFlush(odd);
        try {
            JsonNode a = firstRow("likely", true);
            decide(a, act("CONFIRM", a)).andExpect(status().isOk());
            JsonNode b = firstRow("none", false);
            decide(b, act("NONE", b)).andExpect(status().isOk());
            JsonNode c = firstRow("not-a-product", false);
            decide(c, act("NOT_A_PRODUCT", c)).andExpect(status().isOk());

            String csv = mvc.perform(admin(get("/api/admin/matches/overrides.csv"))).andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", startsWith("text/csv")))
                    .andExpect(header().string("Content-Disposition", containsString("vote_match_overrides.csv")))
                    .andExpect(content().encoding("UTF-8"))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(csv).startsWith("year,vote_beer,product_article_no,product_name\n");

            List<java.util.Map<String, String>> rows = CsvTable.parse(csv);
            assertThat(rows).hasSize(4);
            assertThat(rows.get(0).keySet()).containsExactlyInAnyOrder("year", "vote_beer", "product_article_no", "product_name");
            java.util.Map<String, java.util.Map<String, String>> byBeer = new java.util.HashMap<>();
            rows.forEach(r -> byBeer.put(r.get("year") + "|" + r.get("vote_beer"), r));

            var oddRow = byBeer.get("1999|Odd \"Quoted\", Beer\nLine two");
            assertThat(oddRow.get("product_article_no")).isEqualTo("12345");
            assertThat(oddRow.get("product_name")).isEqualTo("Name, with \"quotes\"");

            VoteMatch confirmed = repo.findById(UUID.fromString(a.get("id").asText())).orElseThrow();
            var confirmedRow = byBeer.get(confirmed.getYear() + "|" + confirmed.getVoteBeer());
            assertThat(confirmedRow.get("product_name")).isEqualTo(confirmed.getMachineProductName());
            assertThat(confirmedRow.get("product_article_no")).isEqualTo(confirmed.getMachineArticleNo() == null ? "" : confirmed.getMachineArticleNo());

            for (JsonNode x : List.of(b, c)) {
                VoteMatch m = repo.findById(UUID.fromString(x.get("id").asText())).orElseThrow();
                var r = byBeer.get(m.getYear() + "|" + m.getVoteBeer());
                assertThat(r.get("product_article_no")).isEqualTo("none");
                assertThat(r.get("product_name")).isEmpty();
            }
            // sorted by year then beer
            List<Integer> years = rows.stream().map(r -> Integer.parseInt(r.get("year"))).toList();
            assertThat(years).isSorted();
        } finally {
            repo.delete(odd);
        }
    }

    @Test
    void overridesCsvIsJustTheHeaderWhenNothingIsDecided() throws Exception {
        mvc.perform(admin(get("/api/admin/matches/overrides.csv"))).andExpect(status().isOk())
                .andExpect(content().string("year,vote_beer,product_article_no,product_name\n"));
    }

    // --- search

    @Test
    void searchFindsProductsAndListingNamesAndIgnoresCaseAndNordicLetters() throws Exception {
        String body = mvc.perform(admin(get("/api/admin/products/search").param("q", "AASS juleol"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode hits = JSON.readTree(body);
        assertThat(hits).isNotEmpty();
        assertThat(hits.findValuesAsText("name")).contains("Aass Juleøl");
        for (JsonNode h : hits) {
            assertThat(h.get("source").asText()).isIn("PRODUCT", "LISTING");
            if (h.get("source").asText().equals("PRODUCT")) assertThat(h.get("articleNo").asText()).isNotBlank();
        }
        // a name that exists only as a listing without article number (2014-2020 lists)
        JsonNode listing = JSON.readTree(mvc.perform(admin(get("/api/admin/products/search").param("q", "Gouden Carolus Christmas")))
                .andReturn().getResponse().getContentAsString());
        assertThat(listing.findValuesAsText("name")).contains("Gouden Carolus Christmas");
    }

    @Test
    void searchIsLimitedAndHarmlessOnOddInput() throws Exception {
        assertThat(JSON.readTree(mvc.perform(admin(get("/api/admin/products/search").param("q", "a"))).andReturn().getResponse().getContentAsString()))
                .hasSize(20);
        for (String q : List.of("", "%", "_", "' or 1=1--", "x".repeat(5000), "\u0000", "\\")) {
            mvc.perform(admin(get("/api/admin/products/search").param("q", q))).andExpect(status().isOk());
        }
        mvc.perform(admin(get("/api/admin/products/search"))).andExpect(status().isOk()).andExpect(content().json("[]"));
        // '%' and '_' are not wildcards
        mvc.perform(admin(get("/api/admin/products/search").param("q", "%"))).andExpect(content().json("[]"));
        mvc.perform(admin(get("/api/admin/products/search").param("q", "_"))).andExpect(content().json("[]"));
    }

    // --- import endpoint and privacy

    @Test
    void theImportEndpointIsIdempotentAndKeepsDecisions() throws Exception {
        JsonNode row = firstRow("likely", true);
        decide(row, act("CONFIRM", row)).andExpect(status().isOk());
        mvc.perform(admin(post("/api/admin/vinmonopolet/import"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.products.inserted").value(0))
                .andExpect(jsonPath("$.listings.inserted").value(0))
                .andExpect(jsonPath("$.voteMatches.inserted").value(0))
                .andExpect(jsonPath("$.voteMatches.updated").value(0));
        assertThat(repo.findDecided()).hasSize(1);
    }

    @Test
    void responsesCarryNoPersonalDataBeyondTheAdminsOwnId() throws Exception {
        JsonNode row = firstRow("review", true);
        String decided = decide(row, act("NONE", row)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String list = mvc.perform(admin(get("/api/admin/matches?size=100"))).andReturn().getResponse().getContentAsString();
        String csv = mvc.perform(admin(get("/api/admin/matches/overrides.csv"))).andReturn().getResponse().getContentAsString();
        for (String s : List.of(decided, list, csv)) {
            assertThat(s).doesNotContain("@").doesNotContain("Dev Tester").doesNotContainIgnoringCase("email");
        }
        UUID adminId = users.findByEmailIgnoreCase(ADMIN).orElseThrow().getId();
        assertThat(JSON.readTree(decided).get("decision").get("decidedBy").asText()).isEqualTo(adminId.toString());
    }
}
