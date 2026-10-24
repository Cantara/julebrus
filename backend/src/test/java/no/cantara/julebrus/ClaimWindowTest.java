package no.cantara.julebrus;

import no.cantara.julebrus.history.HistoricRating;
import no.cantara.julebrus.history.HistoricRatingRepository;
import no.cantara.julebrus.history.HistoricSeason;
import no.cantara.julebrus.history.HistoricSeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The 24-hour claim window follows the injected Clock. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ClaimWindowTest {

    /** A clock the test can move. */
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-10T12:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    MutableClock clock;
    @Autowired
    HistoricRatingRepository ratings;
    @Autowired
    HistoricSeasonRepository seasons;

    @Test
    void claimsOlderThanADayNoLongerCountTowardsTheDailyLimit() throws Exception {
        if (!seasons.existsById(2001)) {
            seasons.save(new HistoricSeason(2001, "Test 2001", new BigDecimal("1"), new BigDecimal("6"), null, null));
        }
        String who = "window@cantara.no";
        ConsentSupport.accept(mvc, who);
        String tag = Long.toString(System.nanoTime(), 36);
        for (int i = 0; i < 21; i++) {
            var r = new HistoricRating(2001, "Window Beer", null, "Zw" + tag + i, null, new BigDecimal("3"), BigDecimal.ONE, null);
            r.applyMapping("Zw" + tag + i, null);
            ratings.save(r);
        }
        Instant start = clock.now;
        try {
            // Ten claims 25 hours ago, each withdrawn: they used up that day.
            clock.now = start.minusSeconds(25 * 3600);
            for (int i = 0; i < 10; i++) claimAndWithdraw(who, "Zw" + tag + i);
            // Now: they are outside the 24 hour window, so ten more are allowed ...
            clock.now = start;
            for (int i = 10; i < 20; i++) claimAndWithdraw(who, "Zw" + tag + i);
            // ... and the next one is over the limit.
            mvc.perform(post("/api/history/claims").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                            .content("{\"raterKey\":\"Zw%s20\"}".formatted(tag)))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CLAIM_DAILY_LIMIT"));
            // 25 hours after those ten, none of them is inside the window any more.
            clock.now = start.plusSeconds(25 * 3600);
            mvc.perform(post("/api/history/claims").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                            .content("{\"raterKey\":\"Zw%s20\"}".formatted(tag)))
                    .andExpect(status().isCreated());
        } finally {
            clock.now = start;
        }
    }

    private void claimAndWithdraw(String who, String key) throws Exception {
        String id = mvc.perform(post("/api/history/claims").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"%s\"}".formatted(key)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(delete("/api/history/claims/" + id).header("X-Dev-User", who)).andExpect(status().isNoContent());
    }
}
