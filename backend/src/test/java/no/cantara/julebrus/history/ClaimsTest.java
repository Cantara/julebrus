package no.cantara.julebrus.history;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Claim-your-history: lifecycle, rules, admin gate, and what the name list may reveal. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ClaimsTest {

    static final String ADMIN = "dev@cantara.no"; // configured admin in the dev profile

    @Autowired
    MockMvc mvc;
    @Autowired
    HistoricRatingRepository ratings;
    @Autowired
    HistoricSeasonRepository seasons;
    @Autowired
    HistoricRaterClaimRepository claimRepo;
    @Autowired
    no.cantara.julebrus.user.UserService users;

    @BeforeEach
    void history() throws Exception {
        if (!seasons.existsById(2001)) {
            seasons.save(new HistoricSeason(2001, "Test 2001", new BigDecimal("1"), new BigDecimal("6"), null, null));
        }
        // Rater names are unique to this test class so they cannot collide with the bundled history.
        if (ratings.findByBeerNameIgnoreCaseOrderBySeasonYearDesc("Claimtest Stout").isEmpty()) {
            row("Zqeirik", null, "5");
            row("Zqeirik L", null, "3");
            row("Zqtotto", null, "4");
            row("Zqola", "zqola@cantara.no", "2");
            row("Zqpair", null, "6");
            row("Zqpair", null, "1");
        }
        ConsentSupport.accept(mvc, ADMIN);
        for (String u : new String[]{"zqeirik@cantara.no", "zqkari@cantara.no", "zqola@cantara.no", "zqmany@cantara.no"}) {
            ConsentSupport.accept(mvc, u);
        }
    }

    private void row(String rater, String email, String score) {
        ratings.save(new HistoricRating(2001, "Claimtest Stout", "Testbryggeri", rater, email, new BigDecimal(score),
                BigDecimal.ONE, "secret note"));
    }

    private String claim(String user, String raterName) throws Exception {
        return mvc.perform(post("/api/history/claims").header("X-Dev-User", user).contentType(APPLICATION_JSON)
                        .content("{\"raterName\":\"%s\"}".formatted(raterName)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void decide(String id, String decision, int expected) throws Exception {
        mvc.perform(post("/api/admin/history/claims/" + id + "/decision").header("X-Dev-User", ADMIN)
                .contentType(APPLICATION_JSON).content("{\"decision\":\"" + decision + "\"}")).andExpect(status().is(expected));
    }

    @Test
    void lifecycleApprovedClaimAddsRatingsToHistoryMe() throws Exception {
        String me = "zqeirik@cantara.no";
        mvc.perform(get("/api/history/me").header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(0)));

        String id = claim(me, "Zqeirik");
        mvc.perform(get("/api/history/claims/me").header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING")).andExpect(jsonPath("$[0].raterName").value("Zqeirik"));
        // Pending links nothing.
        mvc.perform(get("/api/history/me").header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/admin/history/claims?status=PENDING").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$[?(@.id=='%s')].email".formatted(id)).value(me))
                .andExpect(jsonPath("$[?(@.id=='%s')].displayName".formatted(id)).value("zqeirik"))
                .andExpect(jsonPath("$[?(@.id=='%s')].ratingCount".formatted(id)).value(1))
                .andExpect(jsonPath("$[?(@.id=='%s')].years[0]".formatted(id)).value(2001));

        decide(id, "APPROVE", 200);

        mvc.perform(get("/api/history/me").header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].beerName").value("Claimtest Stout"));
        mvc.perform(get("/api/history/claims/me").header("X-Dev-User", me)).andExpect(jsonPath("$[0].status").value("APPROVED"));
        // Deciding twice is refused; an approved claim cannot be withdrawn.
        decide(id, "REJECT", 409);
        mvc.perform(delete("/api/history/claims/" + id).header("X-Dev-User", me)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_PENDING"));
    }

    @Test
    void emailMatchAndApprovedNameGiveNoDuplicates() throws Exception {
        // zqola has one row by email under "Zqola"; claiming the same name must not double it.
        String me = "zqola@cantara.no";
        mvc.perform(get("/api/history/me").header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(1)));
        decide(claim(me, "Zqola"), "APPROVE", 200);
        mvc.perform(get("/api/history/me").header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void duplicateUnknownAndTakenNames() throws Exception {
        String a = "zqkari@cantara.no", b = "zqeirik@cantara.no";
        String id = claim(a, "Zqtotto");

        mvc.perform(post("/api/history/claims").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                        .content("{\"raterName\":\"Zqtotto\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_EXISTS"));
        mvc.perform(post("/api/history/claims").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                        .content("{\"raterName\":\"Nobody Ever Rated\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RATER_NOT_FOUND"));
        mvc.perform(post("/api/history/claims").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                        .content("{\"raterName\":\"  \"}"))
                .andExpect(status().isBadRequest());

        decide(id, "APPROVE", 200);

        // Approved for a: unavailable to b in the list, and refused when b tries.
        mvc.perform(get("/api/history/raters").header("X-Dev-User", b))
                .andExpect(jsonPath("$[?(@.raterName=='Zqtotto')].available").value(false));
        mvc.perform(get("/api/history/raters").header("X-Dev-User", a))
                .andExpect(jsonPath("$[?(@.raterName=='Zqtotto')].available").value(true))
                .andExpect(jsonPath("$[?(@.raterName=='Zqtotto')].myClaim.status").value("APPROVED"));
        mvc.perform(post("/api/history/claims").header("X-Dev-User", b).contentType(APPLICATION_JSON)
                        .content("{\"raterName\":\"Zqtotto\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RATER_TAKEN"));
    }

    @Test
    void secondPendingClaimForTheSameNameCannotBeApprovedOnceTaken() throws Exception {
        String a = "zqkari@cantara.no", b = "zqeirik@cantara.no";
        String first = claim(a, "Zqpair");
        String second = claim(b, "Zqpair"); // both pending: allowed
        decide(first, "APPROVE", 200);
        decide(second, "APPROVE", 409);
        decide(second, "REJECT", 200);
    }

    @Test
    void atMostFivePendingClaims() throws Exception {
        String me = "zqmany@cantara.no";
        for (int i = 0; i < 5; i++) {
            String name = "Zqmany" + i;
            row(name, null, "3");
            claim(me, name);
        }
        row("Zqmany5", null, "3");
        mvc.perform(post("/api/history/claims").header("X-Dev-User", me).contentType(APPLICATION_JSON)
                        .content("{\"raterName\":\"Zqmany5\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CLAIM_LIMIT"));

        // Withdrawing one frees a slot; nobody else can withdraw it.
        String mine = mvc.perform(get("/api/history/claims/me").header("X-Dev-User", me)).andReturn().getResponse().getContentAsString()
                .replaceAll("^\\[\\{\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(delete("/api/history/claims/" + mine).header("X-Dev-User", "zqkari@cantara.no"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/history/claims/" + mine).header("X-Dev-User", me)).andExpect(status().isNoContent());
        claim(me, "Zqmany5");
    }

    @Test
    void adminEndpointsAreForAdminsOnly() throws Exception {
        String user = "zqkari@cantara.no";
        String id = claim("zqola@cantara.no", "Zqeirik L");
        mvc.perform(get("/api/admin/history/claims").header("X-Dev-User", user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/history/claims/" + id + "/decision").header("X-Dev-User", user)
                .contentType(APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}")).andExpect(status().isForbidden());
        // Still pending: the forbidden call changed nothing.
        mvc.perform(get("/api/admin/history/claims").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$[?(@.id=='%s')].status".formatted(id)).value("PENDING"));
        mvc.perform(get("/api/admin/history/claims?status=bogus").header("X-Dev-User", ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/history/claims/" + id + "/decision").header("X-Dev-User", ADMIN)
                .contentType(APPLICATION_JSON).content("{\"decision\":\"MAYBE\"}")).andExpect(status().is4xxClientError());
    }

    @Test
    void nameListSuggestsFirstAndLeaksNoScoresOrBeers() throws Exception {
        String body = mvc.perform(get("/api/history/raters").header("X-Dev-User", "zqeirik@cantara.no"))
                .andExpect(status().isOk())
                // Display name of this dev user is "zqeirik": the exact name, then the prefix match, lead the list.
                .andExpect(jsonPath("$[0].raterName").value("Zqeirik"))
                .andExpect(jsonPath("$[0].suggested").value(true))
                .andExpect(jsonPath("$[1].raterName").value("Zqeirik L"))
                .andExpect(jsonPath("$[1].suggested").value(true))
                .andExpect(jsonPath("$[2].suggested").value(false))
                .andExpect(jsonPath("$[?(@.raterName=='Zqpair')].ratingCount").value(2))
                .andExpect(content().string(not(containsString("Claimtest Stout"))))
                .andExpect(content().string(not(containsString("Testbryggeri"))))
                .andExpect(content().string(not(containsString("secret note"))))
                .andReturn().getResponse().getContentAsString();
        // Only these keys may ever appear per entry.
        org.assertj.core.api.Assertions.assertThat(body.replaceAll("\"[a-zA-Z]+\":", "\n$0").lines()
                        .map(String::trim).filter(l -> l.startsWith("\"")).map(l -> l.substring(0, l.indexOf(':')))
                        .collect(java.util.stream.Collectors.toSet()))
                .containsOnly("\"raterName\"", "\"years\"", "\"ratingCount\"", "\"available\"", "\"suggested\"", "\"myClaim\"",
                        "\"id\"", "\"status\"");
        // The email of a historic rater is not exposed either.
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("zqola@cantara.no").doesNotContain("score");
    }

    @Test
    void databaseRefusesTwoApprovedClaimsForOneName() {
        var admin = users.upsert(ADMIN, "Dev Tester").getId();
        var one = new HistoricRaterClaim(users.upsert("zqdb1@cantara.no", "a").getId(), "Zqdbname");
        var two = new HistoricRaterClaim(users.upsert("zqdb2@cantara.no", "b").getId(), "Zqdbname");
        one.approve(admin);
        two.approve(admin);
        claimRepo.saveAndFlush(one);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> claimRepo.saveAndFlush(two))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
