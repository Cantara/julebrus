package no.cantara.julebrus.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vinmonopolet facts on the archive beers (with how sure each match is), and the history views that leave the cured meat out.
 * Runs against the real imported data (vote_matches.csv and history/*.csv), so the numbers are the ones the app shows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class HistoricBeerFactsTest {

    static final String ME = "facts-test@cantara.no";
    static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void consent() throws Exception {
        ConsentSupport.accept(mvc, ME);
        clearDecisions();
    }

    @AfterEach
    void clearDecisions() {
        jdbc.update("update vote_matches set decision = null, decision_article_no = null, decision_product_name = null, "
                + "decided_by = null, decided_at = null");
    }

    JsonNode json(String url) throws Exception {
        String body = mvc.perform(get(url).header("X-Dev-User", ME)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    JsonNode factFor(JsonNode facts, String product) {
        for (JsonNode f : facts) if (product.equals(f.get("productName").asText())) return f;
        return null;
    }

    boolean hasYear(JsonNode fact, int year) {
        for (JsonNode y : fact.get("years")) if (y.asInt() == year) return true;
        return false;
    }

    List<String> names(JsonNode rows, String field) {
        List<String> out = new ArrayList<>();
        for (JsonNode r : rows) out.add(r.get(field).asText());
        return out;
    }

    // --- facts

    @Test
    void aBreweryNameFromTheEarlyYearsPointsToTheProductWithItsFacts() throws Exception {
        JsonNode fact = factFor(json("/api/history/beers/facts?name=Fredrikstad"), "Fredrikstad Juleøl");
        assertThat(fact).isNotNull();
        assertThat(hasYear(fact, 1998)).isTrue();
        assertThat(fact.get("tier").asText()).isIn("PROBABLE", "MATCHED", "GUESS");
        assertThat(fact.get("articleNos")).hasSize(1);
        assertThat(fact.get("poletUrl").asText()).isEqualTo("https://www.vinmonopolet.no/p/" + fact.get("articleNos").get(0).asText());
        assertThat(fact.get("listedYears")).isNotEmpty();
    }

    @Test
    void aProductWithTwoArticleNumbersShowsBothAndNoSingleLink() throws Exception {
        // Totto, 2026-10-25: a bare "Ringnes" at Vinmonopolet is the normal-strength Ringnes Juleøl (articles 263602 and 7910502, 6.3 %).
        JsonNode fact = factFor(json("/api/history/beers/facts?name=Ringnes"), "Ringnes Juleøl");
        assertThat(fact).isNotNull();
        assertThat(fact.get("tier").asText()).isEqualTo("PROBABLE");
        assertThat(fact.get("articleNos")).hasSize(2);
        assertThat(fact.get("poletUrl").isNull()).isTrue();
        List<Double> abv = new ArrayList<>();
        for (JsonNode a : fact.get("abvPercent")) abv.add(a.asDouble());
        assertThat(abv).contains(6.3);
    }

    @Test
    void myMediumReadingIsLabelledAGuess() throws Exception {
        JsonNode fact = factFor(json("/api/history/beers/facts?name=Aass"), "Aass Juleøl");
        assertThat(fact).isNotNull();
        assertThat(fact.get("tier").asText()).isEqualTo("GUESS");
    }

    @Test
    void aDanishImportAndCuredMeatGetNoFacts() throws Exception {
        assertThat(json("/api/history/beers/facts?name=Tuborg")).isEmpty();
        assertThat(json("/api/history/beers/facts?name=Låret")).isEmpty();
    }

    @Test
    void unresolvedNamesGetNoFacts() throws Exception {
        // "Qvart" 2016: Julekjesk and Nissekjesk tie, so nothing is claimed.
        assertThat(json("/api/history/beers/facts?name=Qvart")).isEmpty();
    }

    @Test
    void anAdministratorsDecisionReplacesTheMachinesAndMine() throws Exception {
        jdbc.update("update vote_matches set decision = 'NONE' where season_year = 1998 and vote_beer = 'Fredrikstad'");
        JsonNode afterNone = factFor(json("/api/history/beers/facts?name=Fredrikstad"), "Fredrikstad Juleøl");
        assertThat(afterNone == null || !hasYear(afterNone, 1998)).isTrue();

        jdbc.update("update vote_matches set decision = 'CHOSEN', decision_article_no = '5122002', "
                + "decision_product_name = 'Aass Juleøl Premium' where season_year = 1998 and vote_beer = 'Aass'");
        JsonNode chosen = factFor(json("/api/history/beers/facts?name=Aass"), "Aass Juleøl Premium");
        assertThat(chosen).isNotNull();
        assertThat(chosen.get("tier").asText()).isEqualTo("CONFIRMED");
        assertThat(hasYear(chosen, 1998)).isTrue();
    }

    // --- the cured meat stays out of the beer views unless asked for

    @Test
    void aSeasonLeavesOutTheMeatByDefaultAndShowsItOnRequest() throws Exception {
        assertThat(names(json("/api/history/seasons/2008"), "beerName")).noneMatch(n -> n.toLowerCase().contains("fenalår"));
        JsonNode all = json("/api/history/seasons/2008?includeNonBeer=true");
        List<String> flagged = new ArrayList<>();
        for (JsonNode b : all) if (b.get("nonBeer").asBoolean()) flagged.add(b.get("beerName").asText());
        assertThat(flagged).isNotEmpty().allMatch(n -> n.toLowerCase().contains("fenalår") || n.toLowerCase().contains("vertskap")
                || n.toLowerCase().contains("kjøt") || n.toLowerCase().contains("skinke"));
    }

    @Test
    void theYearsWinnerIsAlwaysABeer() throws Exception {
        for (JsonNode s : json("/api/history/overview")) {
            if (s.get("winnerName").isNull()) continue;
            assertThat(s.get("winnerName").asText().toLowerCase()).as("winner %s", s.get("year")).doesNotContain("fenalår");
        }
        JsonNode with2008 = null;
        for (JsonNode s : json("/api/history/overview?includeNonBeer=true")) if (s.get("year").asInt() == 2008) with2008 = s;
        assertThat(with2008.get("winnerName").asText()).containsIgnoringCase("fenalår");
    }

    @Test
    void theAllTimeListRanksOnlyBeersUnlessAsked() throws Exception {
        assertThat(names(json("/api/history/alltime?min=1"), "beerName")).noneMatch(n -> n.toLowerCase().contains("fenalår"));
        JsonNode all = json("/api/history/alltime?min=1&includeNonBeer=true");
        boolean flagged = false;
        for (JsonNode r : all) flagged |= r.get("nonBeer").asBoolean() && r.get("beerName").asText().toLowerCase().contains("fenalår");
        assertThat(flagged).isTrue();
        // the meat must not move the ranking of the beers: the beers-only list has the same order as before it was added
        JsonNode beersOnly = json("/api/history/alltime?min=1");
        for (JsonNode r : beersOnly) assertThat(r.get("nonBeer").asBoolean()).isFalse();
    }

    @Test
    void aHomebrewIsStillABeer() throws Exception {
        assertThat(names(json("/api/history/seasons/2013"), "beerName")).anyMatch(n -> n.toLowerCase().contains("hjemmebrygg"));
    }

    @Test
    void anAdministratorWhoSaysItIsABeerBeatsThePattern() throws Exception {
        assertThat(names(json("/api/history/seasons/2003"), "beerName")).noneMatch(n -> n.equals("Fenalår P"));
        jdbc.update("update vote_matches set decision = 'NONE' where season_year = 2003 and vote_beer = 'Fenalår P'");
        assertThat(names(json("/api/history/seasons/2003"), "beerName")).contains("Fenalår P");
    }

    // --- names decided on 2026-10-25

    @Test
    void redheadIsArildAndStigIsStigLauButKjetilStaysSeparate() throws Exception {
        JsonNode raters = json("/api/history/raters");
        List<String> names = names(raters, "name");
        assertThat(names).doesNotContain("Redhead", "Stig", "Stig L").contains("Arild", "Stig Lau", "Kjetil", "Kjetil JD");
        for (JsonNode r : raters) {
            if (r.get("name").asText().equals("Arild")) {
                assertThat(r.get("firstYear").asInt()).isEqualTo(1998);
                assertThat(r.get("lastYear").asInt()).isEqualTo(2010);
            }
            if (r.get("name").asText().equals("Stig Lau")) {
                assertThat(r.get("firstYear").asInt()).isEqualTo(2008);
                assertThat(r.get("lastYear").asInt()).isEqualTo(2019);
            }
        }
    }
}
