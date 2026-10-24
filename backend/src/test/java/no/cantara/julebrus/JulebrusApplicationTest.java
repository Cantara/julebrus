package no.cantara.julebrus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Boots the dev profile (H2, dev user) and walks the main flow end to end. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class JulebrusApplicationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void soloRatingFlowWithRuleEnforcement() throws Exception {
        ConsentSupport.accept(mvc, null);
        ConsentSupport.accept(mvc, "nobody@cantara.no");
        mvc.perform(get("/api/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dev@cantara.no"))
                .andExpect(jsonPath("$.status.maxPerDay").value(5))
                .andExpect(jsonPath("$.weight.weight").value(0.5))
                .andExpect(jsonPath("$.admin").value(true));

        String[] beerIds = new String[6];
        for (int i = 0; i < 6; i++) {
            String body = mvc.perform(post("/api/beers").contentType(APPLICATION_JSON)
                            .content("{\"name\":\"Testøl %d\",\"brewery\":\"Cantara\"}".formatted(i)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            beerIds[i] = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        }

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/tastings").contentType(APPLICATION_JSON)
                            .content("""
                                    {"beerId":"%s","score":%d,"aroma":"raisin, clove","tags":["malty"," Raisin "]}
                                    """.formatted(beerIds[i], 1 + i)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tags", hasSize(2)))
                    .andExpect(jsonPath("$.structuredNotes.aroma").value("raisin, clove"));
        }

        // Sixth of the day is refused with a machine-readable code.
        mvc.perform(post("/api/tastings").contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\",\"score\":6}".formatted(beerIds[5])))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT"));

        // Rating the same beer twice is refused too, before the daily cap is even checked.
        mvc.perform(post("/api/tastings").contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\",\"score\":6}".formatted(beerIds[0])))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALREADY_RATED"));

        mvc.perform(get("/api/me")).andExpect(jsonPath("$.status.tastingsToday").value(5))
                .andExpect(jsonPath("$.status.soloDaysUsed").value(1));
        mvc.perform(get("/api/stats/beers")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Testøl 4"))
                .andExpect(jsonPath("$[0].ratings").value(1))
                .andExpect(jsonPath("$[0].effectiveVotes").value(0.5));

        // The finale hides the podium until 23 Dec, except for an admin preview.
        mvc.perform(get("/api/finale")).andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.totals.tastings").value(5))
                .andExpect(jsonPath("$.podium").doesNotExist());
        mvc.perform(get("/api/finale?preview=true").header("X-Dev-User", "nobody@cantara.no"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/finale?preview=true")).andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").value(true))
                .andExpect(jsonPath("$.podium[0].name").value("Testøl 4"))
                .andExpect(jsonPath("$.awards[?(@.id=='MOST_TASTINGS')].winnerName").value("Dev Tester"))
                .andExpect(jsonPath("$.awards[?(@.id=='CONTRARIAN')].detail").value("Ingen kvalifiserte i år"));

        // A muted rater is listed but moves nothing.
        mvc.perform(post("/api/admin/raters/override").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"dev@cantara.no\",\"weight\":0,\"reason\":\"test\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/stats/beers"))
                .andExpect(jsonPath("$[0].ratings").value(1))
                .andExpect(jsonPath("$[0].effectiveVotes").value(0.0))
                .andExpect(jsonPath("$[0].bayesian").value(3.5));
        mvc.perform(get("/api/admin/raters").header("X-Dev-User", "nobody@cantara.no")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/raters")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='dev@cantara.no')].scores").value(5));
        mvc.perform(get("/api/badges/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='FIRST_BLOOD')].earned").value(true));
    }

    @Test
    void groupSessionHidesScoresUntilEveryoneHasRated() throws Exception {
        // Two people who have not tasted anything yet today.
        String host = "ola@cantara.no", guest = "kari@cantara.no";
        for (String who : new String[]{host, guest, "nosy@cantara.no"}) ConsentSupport.accept(mvc, who);

        String beer = mvc.perform(post("/api/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Gruppeøl\"}")).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        String session = mvc.perform(post("/api/sessions").header("X-Dev-User", host)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String sessionId = session.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        String code = session.replaceAll(".*\"joinCode\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/sessions/join").header("X-Dev-User", guest)
                        .contentType(APPLICATION_JSON).content("{\"code\":\"%s\"}".formatted(code.toLowerCase())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.solo").value(false));

        mvc.perform(post("/api/sessions/" + sessionId + "/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\"}".formatted(beer))).andExpect(status().isOk());

        mvc.perform(post("/api/tastings").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\",\"sessionId\":\"%s\",\"score\":5}".formatted(beer, sessionId)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/sessions/" + sessionId).header("X-Dev-User", host))
                .andExpect(jsonPath("$.beers[0].revealed").value(false))
                .andExpect(jsonPath("$.beers[0].ratedBy", hasSize(1)))
                .andExpect(jsonPath("$.beers[0].scores").doesNotExist());

        mvc.perform(post("/api/tastings").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\",\"sessionId\":\"%s\",\"score\":3}".formatted(beer, sessionId)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/sessions/" + sessionId).header("X-Dev-User", guest))
                .andExpect(jsonPath("$.beers[0].revealed").value(true))
                .andExpect(jsonPath("$.beers[0].average").value(4.0));

        // A group tasting does not burn a solo day.
        mvc.perform(get("/api/me").header("X-Dev-User", host)).andExpect(jsonPath("$.status.soloDaysUsed").value(0));

        // Outsiders cannot peek.
        mvc.perform(get("/api/sessions/" + sessionId).header("X-Dev-User", "nosy@cantara.no"))
                .andExpect(status().isForbidden());

        // The bundled history (history/*.csv) is imported at startup: newest season first.
        ConsentSupport.accept(mvc, null);
        mvc.perform(get("/api/history/seasons")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(12)))
                .andExpect(jsonPath("$[0].year").value(2019));
        // ...and so are the ratings: 2019 has 39 votes over 26 beers in the bundled file.
        mvc.perform(get("/api/history/seasons/2019")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(26));
        // raters.csv joins "Eirik" (1998) into Eirik Litangen; unmapped names keep their own.
        mvc.perform(get("/api/history/beers?name=Aass"))
                .andExpect(jsonPath("$[?(@.year==1998 && @.raterName=='Eirik')].raterKey").value("Eirik Litangen"))
                .andExpect(jsonPath("$[?(@.year==1998 && @.raterName=='Totto')].raterKey").value("Totto"));
        mvc.perform(get("/api/history/raters")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Eirik Litangen')].lastYear").value(2017));
        mvc.perform(get("/api/tastings/tags")).andExpect(content().string(containsString("liquorice")));
    }

    private static org.springframework.test.web.servlet.result.ContentResultMatchers content() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content();
    }
}
