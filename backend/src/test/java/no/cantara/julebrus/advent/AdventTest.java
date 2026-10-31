package no.cantara.julebrus.advent;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The advent calendar on a clock the test moves: today's door opens with an answer, a future door is shut,
 * an early door opens late for free, a later one needs a nisse-key earned at a table, the streak and the house
 * count doors, and in the last week the ranking goes dark while the door gives a clue.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:advent;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdventTest {

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

    static final String ADMIN = "dev@cantara.no", KARI = "adv-kari@cantara.no", OLA = "adv-ola@cantara.no";

    ResultActions open(String who, String day, String body) throws Exception {
        return mvc.perform(post("/api/advent/doors/" + day + "/open").header("X-Dev-User", who)
                .contentType(APPLICATION_JSON).content(body));
    }

    static String idOf(String json) {
        return json.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void doorsKeysStreakAndTheBlackout() throws Exception {
        for (String who : new String[]{ADMIN, KARI, OLA}) ConsentSupport.accept(mvc, who);

        // Outside the season there is no door; tomorrow's is shut.
        mvc.perform(get("/api/advent/doors/2026-09-30").header("X-Dev-User", KARI))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DOOR_NOT_FOUND"));
        mvc.perform(get("/api/advent/doors/2026-11-11").header("X-Dev-User", KARI))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DOOR_LOCKED"));

        // Today's door shows its question, but nothing behind it yet.
        mvc.perform(get("/api/advent/doors/2026-11-10").header("X-Dev-User", KARI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("TODAY"))
                .andExpect(jsonPath("$.question.text").isString())
                .andExpect(jsonPath("$.answer").value(nullValue()))
                .andExpect(jsonPath("$.tip").value(nullValue()));
        open(KARI, "2026-11-10", "{\"answer\":99}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOOR_ANSWER"));
        open(KARI, "2026-11-10", "{\"answer\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPENED"))
                .andExpect(jsonPath("$.answer.answer").isNumber())
                .andExpect(jsonPath("$.answer.explanation").isString())
                .andExpect(jsonPath("$.tip").isString())
                .andExpect(jsonPath("$.late").value(false));
        open(KARI, "2026-11-10", "{\"answer\":0}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOOR_OPENED"));

        // An early door opens late for free; a later missed one needs a key.
        mvc.perform(get("/api/advent/doors/2026-11-03").header("X-Dev-User", KARI)).andExpect(jsonPath("$.state").value("FREE_LATE"));
        open(KARI, "2026-11-03", "{\"answer\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.late").value(true)).andExpect(jsonPath("$.keyUsed").value(false));
        mvc.perform(get("/api/advent/doors/2026-11-08").header("X-Dev-User", KARI)).andExpect(jsonPath("$.state").value("NEEDS_KEY"));
        open(KARI, "2026-11-08", "{\"answer\":0}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_KEY"));

        // Kari tastes at Ola's table: a key. Tasting alone would not give one.
        String beer = idOf(mvc.perform(post("/api/beers").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Luketest juleøl\",\"abv\":6.5,\"style\":\"Mørk lager\",\"brewery\":\"Aass\"}"))
                .andReturn().getResponse().getContentAsString());
        String session = mvc.perform(post("/api/sessions").header("X-Dev-User", OLA)).andReturn().getResponse().getContentAsString();
        String sid = idOf(session);
        mvc.perform(post("/api/sessions/join").header("X-Dev-User", KARI).contentType(APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(session.replaceAll(".*\"joinCode\":\"([^\"]+)\".*", "$1")))).andExpect(status().isOk());
        mvc.perform(post("/api/sessions/" + sid + "/beers").header("X-Dev-User", OLA).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\"}".formatted(beer))).andExpect(status().isOk());
        mvc.perform(post("/api/tastings").header("X-Dev-User", KARI).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\",\"sessionId\":\"%s\",\"score\":5}".formatted(beer, sid))).andExpect(status().isCreated());

        mvc.perform(get("/api/advent").header("X-Dev-User", KARI))
                .andExpect(jsonPath("$.keys.earned").value(1)).andExpect(jsonPath("$.keys.available").value(1));
        open(KARI, "2026-11-08", "{\"answer\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.keyUsed").value(true)).andExpect(jsonPath("$.keysAvailable").value(0));

        // Doors on the 3rd, 8th and 10th: a streak of one (the 9th is shut), three opened, the house counts them.
        mvc.perform(get("/api/advent").header("X-Dev-User", KARI))
                .andExpect(jsonPath("$.opened.length()").value(3))
                .andExpect(jsonPath("$.streak").value(1))
                .andExpect(jsonPath("$.todayOpened").value(true))
                .andExpect(jsonPath("$.todayQuestion").isString())
                .andExpect(jsonPath("$.keys.spent").value(1))
                .andExpect(jsonPath("$.house.opened", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.house.today", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.house.unlocked").value(false))
                .andExpect(jsonPath("$.blackout").value(false))
                .andExpect(jsonPath("$.releaseAt").value("2026-11-04T09:00:00Z"));
        mvc.perform(get("/api/advent").header("X-Dev-User", OLA)).andExpect(jsonPath("$.todayOpened").value(false))
                .andExpect(jsonPath("$.streak").value(0));

        // The last week: the ranking goes dark for everyone but an admin who asks, and the door gives a clue.
        clock.now = Instant.parse("2026-12-18T12:00:00Z");
        mvc.perform(get("/api/stats/beers").header("X-Dev-User", KARI))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("BLACKOUT"));
        mvc.perform(get("/api/stats/duels").header("X-Dev-User", KARI)).andExpect(status().isLocked());
        mvc.perform(get("/api/stats/beers?preview=true").header("X-Dev-User", KARI)).andExpect(status().isLocked());
        mvc.perform(get("/api/stats/beers?preview=true").header("X-Dev-User", ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/api/beers/" + beer).header("X-Dev-User", KARI))
                .andExpect(jsonPath("$.season.blackout").value(true))
                .andExpect(jsonPath("$.season.rank").value(nullValue()))
                .andExpect(jsonPath("$.season.mean").value(nullValue()));
        open(KARI, "2026-12-18", "{\"answer\":0}").andExpect(status().isOk())
                .andExpect(jsonPath("$.clue").value(notNullValue()));
        mvc.perform(get("/api/advent").header("X-Dev-User", KARI)).andExpect(jsonPath("$.blackout").value(true));

        // After the reveal the ranking is back.
        clock.now = Instant.parse("2026-12-23T17:00:01Z");
        mvc.perform(get("/api/stats/beers").header("X-Dev-User", KARI)).andExpect(status().isOk());
    }
}
