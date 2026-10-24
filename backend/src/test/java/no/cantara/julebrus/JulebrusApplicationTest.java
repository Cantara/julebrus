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
        // Tests share one database, so compare within our own beers rather than claiming the top spot.
        String ranking = mvc.perform(get("/api/stats/beers")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Testøl 4')].ratings").value(1))
                .andExpect(jsonPath("$[?(@.name=='Testøl 4')].effectiveVotes").value(0.5))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(ranking.indexOf("Testøl 4")).isLessThan(ranking.indexOf("Testøl 0"));

        // The finale hides the podium until 23 Dec, except for an admin preview.
        mvc.perform(get("/api/finale")).andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.totals.tastings").value(greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.podium").doesNotExist());
        mvc.perform(get("/api/finale?preview=true").header("X-Dev-User", "nobody@cantara.no"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/finale?preview=true")).andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").value(true))
                .andExpect(jsonPath("$.podium", hasSize(3)))
                .andExpect(jsonPath("$.awards[?(@.id=='MOST_TASTINGS')].winnerName").value("Dev Tester"))
                .andExpect(jsonPath("$.awards[?(@.id=='CONTRARIAN')].detail").value("Ingen kvalifiserte i år"));

        // A muted rater is listed but moves nothing.
        mvc.perform(post("/api/admin/raters/override").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"dev@cantara.no\",\"weight\":0,\"reason\":\"test\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/stats/beers"))
                .andExpect(jsonPath("$[?(@.name=='Testøl 4')].ratings").value(1))
                .andExpect(jsonPath("$[?(@.name=='Testøl 4')].effectiveVotes").value(0.0));
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

    @Test
    void beersNeedApprovalAndAdminsLinkHistoricNames() throws Exception {
        String guest = "per@cantara.no";
        ConsentSupport.accept(mvc, null);
        ConsentSupport.accept(mvc, guest);

        // A non-admin's beer waits; it can be rated but stays out of the ranking until approved.
        String pending = mvc.perform(post("/api/beers").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Hjemmebrygg Nisseøl\",\"brewery\":\"Per\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/tastings").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\",\"score\":6}".formatted(pending)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/stats/beers").header("X-Dev-User", guest))
                .andExpect(jsonPath("$[?(@.beerId=='%s')]".formatted(pending)).doesNotExist());
        mvc.perform(get("/api/beers/least-tasted").header("X-Dev-User", guest))
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(pending)).doesNotExist());

        // Non-admins cannot decide; the admin approves and the beer enters the ranking.
        mvc.perform(post("/api/admin/beers/" + pending + "/decision").header("X-Dev-User", guest)
                        .contentType(APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/beers")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='%s')].addedBy".formatted(pending)).value("per"));
        mvc.perform(post("/api/admin/beers/" + pending + "/decision").contentType(APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get("/api/stats/beers"))
                .andExpect(jsonPath("$[?(@.beerId=='%s')].ratings".formatted(pending)).value(1));
        mvc.perform(post("/api/admin/beers/" + pending + "/decision").contentType(APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BEER_NOT_PENDING"));

        // An admin links the historic "Totto" to the guest's login; the guest then sees that history.
        mvc.perform(get("/api/history/me").header("X-Dev-User", guest)).andExpect(jsonPath("$", hasSize(0)));
        String guestId = mvc.perform(get("/api/me").header("X-Dev-User", guest)).andReturn().getResponse()
                .getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/admin/history/raters/link").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Totto\",\"userId\":\"%s\"}".formatted(guestId)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/history/raters/link").contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Totto\",\"userId\":\"%s\"}".formatted(guestId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linkedTo.email").value(guest));
        mvc.perform(get("/api/history/me").header("X-Dev-User", guest))
                .andExpect(jsonPath("$[?(@.year==1998 && @.beerName=='Aass')].score").value(3.0));
        mvc.perform(get("/api/history/me/summary").header("X-Dev-User", guest))
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(12)))
                .andExpect(jsonPath("$[0].year").value(1998));
        mvc.perform(get("/api/admin/history/raters"))
                .andExpect(jsonPath("$[?(@.raterKey=='Totto')].linkedTo.displayName").value("per"));

        // Unlink takes it back.
        mvc.perform(post("/api/admin/history/raters/unlink").contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Totto\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.linkedTo").doesNotExist());
        mvc.perform(get("/api/history/me").header("X-Dev-User", guest)).andExpect(jsonPath("$", hasSize(0)));

        // The overview carries every season with its winner.
        mvc.perform(get("/api/history/overview")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(12)))
                .andExpect(jsonPath("$[?(@.year==2019)].beers").value(26))
                .andExpect(jsonPath("$[?(@.year==2019)].winnerName").isNotEmpty());
    }

    @Test
    void perennialsAreSeededAndCsvImportPreviewsThenCreates() throws Exception {
        ConsentSupport.accept(mvc, null);
        ConsentSupport.accept(mvc, "guest@cantara.no");

        // The seed put the grocery shelf in place, approved.
        mvc.perform(get("/api/beers").param("q", "Aass Jule"))
                .andExpect(jsonPath("$[?(@.name=='Aass Juleøl')].status").value("APPROVED"));

        String csv = """
                name;brewery;style;abv;vinmonopolet_id
                Nøgne Ø God Jul;Nøgne Ø;Juleøl, sterk;8,5;1234501
                Aass Juleøl;Aass;Juleøl;4,7;
                ;Ukjent;;;
                Lervig Juleøl;Lervig;Juleøl;abc;
                """;
        mvc.perform(post("/api/admin/beers/import?dryRun=true").header("X-Dev-User", "guest@cantara.no")
                        .contentType("text/csv").content(csv))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/beers/import?dryRun=true").contentType("text/csv").content(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.rows", hasSize(4)))
                .andExpect(jsonPath("$.rows[0].action").value("NEW"))
                .andExpect(jsonPath("$.rows[0].abv").value(8.5))
                .andExpect(jsonPath("$.rows[1].action").value("DUPLICATE"))
                .andExpect(jsonPath("$.rows[2].action").value("INVALID"))
                .andExpect(jsonPath("$.rows[3].action").value("INVALID"));
        mvc.perform(get("/api/beers").param("q", "God Jul")).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/admin/beers/import?dryRun=false").contentType("text/csv").content(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
        mvc.perform(get("/api/beers").param("q", "God Jul"))
                .andExpect(jsonPath("$[0].name").value("Nøgne Ø God Jul"))
                .andExpect(jsonPath("$[0].vinmonopoletId").value("1234501"))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));

        // Running the same file again finds nothing new.
        mvc.perform(post("/api/admin/beers/import?dryRun=false").contentType("text/csv").content(csv))
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.rows[0].action").value("DUPLICATE"));
    }

    @Test
    void tasteTwinsFollowTheScores() throws Exception {
        String anna = "anna@cantara.no", bjorn = "bjorn@cantara.no";
        for (String who : new String[]{anna, bjorn}) ConsentSupport.accept(mvc, who);
        ConsentSupport.accept(mvc, null);

        String[] ids = new String[4];
        for (int i = 0; i < 4; i++) {
            ids[i] = mvc.perform(post("/api/beers").contentType(APPLICATION_JSON)
                            .content("{\"name\":\"Tvillingøl %d\"}".formatted(i)))
                    .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        }
        int[] annaScores = {6, 5, 2, 1}, bjornScores = {6, 5, 2, 1};
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/tastings").header("X-Dev-User", anna).contentType(APPLICATION_JSON)
                    .content("{\"beerId\":\"%s\",\"score\":%d,\"tags\":[\"%s\"]}".formatted(ids[i], annaScores[i], annaScores[i] >= 4 ? "roasted" : "thin")))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/tastings").header("X-Dev-User", bjorn).contentType(APPLICATION_JSON)
                    .content("{\"beerId\":\"%s\",\"score\":%d}".formatted(ids[i], bjornScores[i])))
                    .andExpect(status().isCreated());
        }

        mvc.perform(get("/api/stats/palate/me").header("X-Dev-User", anna)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tastings").value(4))
                .andExpect(jsonPath("$.twin.displayName").value("bjorn"))
                .andExpect(jsonPath("$.twin.common").value(4))
                .andExpect(jsonPath("$.twin.similarity").value(1.0))
                .andExpect(jsonPath("$.likes[0].tag").value("roasted"))
                .andExpect(jsonPath("$.dislikes[0].tag").value("thin"));
        mvc.perform(get("/api/stats/palate/pairs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.twins[0].similarity").value(1.0));
    }

    private static org.springframework.test.web.servlet.result.ContentResultMatchers content() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content();
    }
}
