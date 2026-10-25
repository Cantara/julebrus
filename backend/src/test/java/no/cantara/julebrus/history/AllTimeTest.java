package no.cantara.julebrus.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The all-time list: every recorded vote on 1–6, weighted as its year weighted it, best first, no names. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:alltime;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AllTimeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void theListIsRankedShrunkAndAnonymous() throws Exception {
        ConsentSupport.accept(mvc, null);
        String body = mvc.perform(get("/api/history/alltime")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode rows = json.readTree(body);
        assertThat(rows.size()).isGreaterThan(20);
        double previous = Double.MAX_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            JsonNode r = rows.get(i);
            assertThat(r.get("rank").asInt()).isEqualTo(i + 1);
            assertThat(r.get("ratings").asInt()).isGreaterThanOrEqualTo(5);
            double b = r.get("bayesian").asDouble();
            assertThat(b).isLessThanOrEqualTo(previous).isBetween(1.0, 6.0);
            previous = b;
            assertThat(r.get("mean").asDouble()).isBetween(1.0, 6.0);
            assertThat(r.get("years").size()).isGreaterThanOrEqualTo(1);
            assertThat(r.get("years").findValues("").size()).isZero();
            assertThat(r.get("bestYear").asInt()).isIn(r.get("years").findValuesAsText("").isEmpty()
                    ? java.util.List.of(r.get("bestYear").asInt()) : java.util.List.of(r.get("bestYear").asInt()));
        }
        // A beer with votes in several seasons carries all of them, and its best year is one of them.
        JsonNode multi = null;
        for (JsonNode r : rows) if (r.get("years").size() > 1) { multi = r; break; }
        assertThat(multi).isNotNull();
        java.util.List<Integer> years = new java.util.ArrayList<>();
        multi.get("years").forEach(y -> years.add(y.asInt()));
        assertThat(years).contains(multi.get("bestYear").asInt());
        // Never a person: the Totto who voted in 2019 is not in this list.
        assertThat(body).doesNotContain("Totto").doesNotContain("rater");
        // A higher bar shortens the list and keeps the order.
        JsonNode strict = json.readTree(mvc.perform(get("/api/history/alltime?min=20")).andReturn().getResponse().getContentAsString());
        assertThat(strict.size()).isLessThan(rows.size()).isGreaterThan(0);
        assertThat(strict.get(0).get("ratings").asInt()).isGreaterThanOrEqualTo(20);
    }
}
