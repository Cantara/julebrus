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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
        for (int year : new int[]{2001, 2002}) {
            if (!seasons.existsById(year)) {
                seasons.save(new HistoricSeason(year, "Test " + year, new BigDecimal("1"), new BigDecimal("6"), null, null));
            }
        }
        // Keys are unique to this test class so they cannot collide with the real history.
        if (ratings.findByBeerNameIgnoreCaseOrderBySeasonYearDesc("Claimtest Stout").isEmpty()) {
            // One person, three spellings in two seasons (what raters.csv joins).
            row(2001, "Zqeirik", "Zqeirik Litangen", null, "5");
            row(2002, "Zqeirik L", "Zqeirik Litangen", null, "3");
            row(2002, "Zqeirik Litangen", "Zqeirik Litangen", null, "4");
            row(2001, "Zqtotto", "Zqtotto", null, "4");
            row(2001, "Zqpair", "Zqpair", null, "6");
            row(2001, "Zqpair", "Zqpair", null, "1");
            // raters.csv already tied this person to a login.
            row(2001, "Zqlinked", "Zqlinked Person", "zqlinked@cantara.no", "2");
        }
        ConsentSupport.accept(mvc, ADMIN);
        for (String u : new String[]{"zqeirik@cantara.no", "zqkari@cantara.no", "zqola@cantara.no", "zqmany@cantara.no",
                "zqlinked@cantara.no"}) {
            ConsentSupport.accept(mvc, u);
        }
    }

    private void row(int year, String name, String key, String email, String score) {
        var r = new HistoricRating(year, "Claimtest Stout", "Testbryggeri", name, null, new BigDecimal(score),
                BigDecimal.ONE, "secret note");
        r.applyMapping(key, email);
        ratings.save(r);
    }

    private String claim(String user, String raterKey) throws Exception {
        return mvc.perform(post("/api/history/claims").header("X-Dev-User", user).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"%s\"}".formatted(raterKey)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void decide(String id, String decision, int expected) throws Exception {
        mvc.perform(post("/api/admin/history/claims/" + id + "/decision").header("X-Dev-User", ADMIN)
                .contentType(APPLICATION_JSON).content("{\"decision\":\"" + decision + "\"}")).andExpect(status().is(expected));
    }

    @Test
    void lifecycleOneApprovalReturnsEverySpellingOfThePerson() throws Exception {
        String me = "zqeirik@cantara.no";
        mvc.perform(get("/api/history/me").header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(0)));

        // Three spellings, one key: one candidate with both seasons and all three ratings.
        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", me))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqeirik Litangen')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqeirik Litangen')].ratingCount").value(3))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqeirik Litangen')].years", contains(contains(2001, 2002))))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqeirik')]", hasSize(0)));

        String id = claim(me, "Zqeirik Litangen");
        mvc.perform(get("/api/history/claims/me").header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].raterKey").value("Zqeirik Litangen"));
        // Pending links nothing.
        mvc.perform(get("/api/history/me").header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/admin/history/claims?status=PENDING").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$[?(@.id=='%s')].email".formatted(id)).value(me))
                .andExpect(jsonPath("$[?(@.id=='%s')].displayName".formatted(id)).value("zqeirik"))
                .andExpect(jsonPath("$[?(@.id=='%s')].raterKey".formatted(id)).value("Zqeirik Litangen"))
                .andExpect(jsonPath("$[?(@.id=='%s')].ratingCount".formatted(id)).value(3))
                .andExpect(jsonPath("$[?(@.id=='%s')].years".formatted(id), contains(contains(2001, 2002))));

        decide(id, "APPROVE", 200);

        // Each rating once, from both seasons and all three spellings.
        mvc.perform(get("/api/history/me").header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].raterName", containsInAnyOrder("Zqeirik", "Zqeirik L", "Zqeirik Litangen")))
                .andExpect(jsonPath("$[*].raterKey", everyItem(is("Zqeirik Litangen"))))
                .andExpect(jsonPath("$[*].year", containsInAnyOrder(2001, 2002, 2002)));
        mvc.perform(get("/api/history/claims/me").header("X-Dev-User", me)).andExpect(jsonPath("$[0].status").value("APPROVED"));
        // Deciding twice is refused; an approved claim cannot be withdrawn.
        decide(id, "REJECT", 409);
        mvc.perform(delete("/api/history/claims/" + id).header("X-Dev-User", me)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_PENDING"));
    }

    @Test
    void personLinkedByEmailIsNotClaimableAndNeedsNoClaim() throws Exception {
        String other = "zqkari@cantara.no", owner = "zqlinked@cantara.no";
        mvc.perform(post("/api/history/claims").header("X-Dev-User", other).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Zqlinked Person\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RATER_LINKED"));
        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", other))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqlinked Person')].available").value(false))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqlinked Person')].linkedByEmail").value(true))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqlinked Person')].linkedToYou").value(false))
                .andExpect(content().string(not(containsString(owner))));
        // The owner is covered by the email match; the candidate says so and a claim is refused too.
        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", owner))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqlinked Person')].linkedToYou").value(true));
        mvc.perform(get("/api/history/me").header("X-Dev-User", owner)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/history/claims").header("X-Dev-User", owner).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Zqlinked Person\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RATER_LINKED"));
    }

    @Test
    void duplicateUnknownAndTakenKeys() throws Exception {
        String a = "zqkari@cantara.no", b = "zqeirik@cantara.no";
        String id = claim(a, "Zqtotto");

        mvc.perform(post("/api/history/claims").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Zqtotto\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_EXISTS"));
        // A source spelling that raters.csv joined into a key is not a candidate of its own.
        mvc.perform(post("/api/history/claims").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Zqeirik L\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RATER_NOT_FOUND"));
        mvc.perform(post("/api/history/claims").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Nobody Ever Rated\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RATER_NOT_FOUND"));
        mvc.perform(post("/api/history/claims").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"  \"}"))
                .andExpect(status().isBadRequest());

        decide(id, "APPROVE", 200);

        // Approved for a: unavailable to b in the list, and refused when b tries.
        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", b))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqtotto')].available").value(false));
        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", a))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqtotto')].available").value(true))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqtotto')].myClaim.status").value("APPROVED"));
        mvc.perform(post("/api/history/claims").header("X-Dev-User", b).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Zqtotto\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RATER_TAKEN"));
    }

    @Test
    void secondPendingClaimForTheSameKeyCannotBeApprovedOnceTaken() throws Exception {
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
            String key = "Zqmany" + i;
            row(2001, key, key, null, "3");
            claim(me, key);
        }
        row(2001, "Zqmany5", "Zqmany5", null, "3");
        mvc.perform(post("/api/history/claims").header("X-Dev-User", me).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"Zqmany5\"}"))
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
        String id = claim("zqola@cantara.no", "Zqpair");
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
    void candidateListSuggestsFirstAndLeaksOnlyKeyYearsAndCount() throws Exception {
        String me = "zqeirik@cantara.no";
        String body = mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", me))
                .andExpect(status().isOk())
                // The dev user's display name is "zqeirik": "Zqeirik Litangen" leads the list.
                .andExpect(jsonPath("$[0].raterKey").value("Zqeirik Litangen"))
                .andExpect(jsonPath("$[0].suggested").value(true))
                .andExpect(jsonPath("$[1].suggested").value(false))
                .andExpect(jsonPath("$[?(@.raterKey=='Zqpair')].ratingCount").value(2))
                .andExpect(content().string(not(containsString("Claimtest Stout"))))
                .andExpect(content().string(not(containsString("Testbryggeri"))))
                .andExpect(content().string(not(containsString("secret note"))))
                .andReturn().getResponse().getContentAsString();
        // Only these keys may ever appear.
        org.assertj.core.api.Assertions.assertThat(body.replaceAll("\"[a-zA-Z]+\":", "\n$0").lines()
                        .map(String::trim).filter(l -> l.startsWith("\"")).map(l -> l.substring(0, l.indexOf(':')))
                        .collect(java.util.stream.Collectors.toSet()))
                .isSubsetOf("\"raterKey\"", "\"years\"", "\"ratingCount\"", "\"available\"", "\"linkedByEmail\"",
                        "\"linkedToYou\"", "\"suggested\"", "\"myClaim\"", "\"id\"", "\"status\"");
        // No email of a linked person, no scores. Main's own /api/history/raters is untouched.
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("@cantara.no").doesNotContain("score");
        mvc.perform(get("/api/history/raters").header("X-Dev-User", me)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void databaseRefusesTwoApprovedClaimsForOneName() {
        var admin = users.upsert(ADMIN, "Dev Tester").getId();
        var one = new HistoricRaterClaim(users.upsert("zqdb1@cantara.no", "a").getId(), "Zqdbkey");
        var two = new HistoricRaterClaim(users.upsert("zqdb2@cantara.no", "b").getId(), "Zqdbkey");
        one.approve(admin);
        two.approve(admin);
        claimRepo.saveAndFlush(one);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> claimRepo.saveAndFlush(two))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
