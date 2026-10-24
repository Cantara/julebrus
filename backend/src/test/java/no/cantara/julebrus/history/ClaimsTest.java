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
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Claim-your-history: lifecycle, rules, admin gate, and what the candidate list may reveal.
 * The H2 database is shared by every test, so each test seeds its own uniquely named people
 * and users ({@link Fx}) and does not depend on test order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ClaimsTest {

    static final String ADMIN = "dev@cantara.no"; // configured admin in the dev profile
    static final AtomicInteger COUNTER = new AtomicInteger();

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

    /** Names and logins that exist only for one test. */
    class Fx {
        final String s = "q" + Long.toString(System.nanoTime(), 36) + COUNTER.incrementAndGet();
        /** The dev user whose display name is "zq..."; its first name matches {@link #person}. */
        final String me = "zq" + s + "@cantara.no";
        final String person = "Zq" + s + " Litangen";     // three spellings, two seasons, one key
        final String solo = "Zq" + s + "-solo";
        final String pair = "Zq" + s + "-pair";
        final String linked = "Zq" + s + "-linked";
        final String linkedEmail = "zq" + s + "-linked@cantara.no";

        Fx() throws Exception {
            for (int year : new int[]{2001, 2002}) {
                if (!seasons.existsById(year)) {
                    seasons.save(new HistoricSeason(year, "Test " + year, new BigDecimal("1"), new BigDecimal("6"), null, null));
                }
            }
            row(2001, "Zq" + s, person, null, "5");
            row(2002, "Zq" + s + " L", person, null, "3");
            row(2002, person, person, null, "4");
            row(2001, solo, solo, null, "4");
            row(2001, pair, pair, null, "6");
            row(2001, pair, pair, null, "1");
            row(2001, linked, linked, linkedEmail, "2");
            login(me);
            login(linkedEmail);
        }

        /** A login that has accepted consent. */
        String login(String email) throws Exception {
            ConsentSupport.accept(mvc, email);
            return email;
        }

        String other(String tag) throws Exception {
            return login("zq" + s + "-" + tag + "@cantara.no");
        }

        void row(int year, String name, String key, String email, String score) {
            var r = new HistoricRating(year, "Claimtest Stout", "Testbryggeri", name, null, new BigDecimal(score),
                    BigDecimal.ONE, "secret note");
            r.applyMapping(key, email);
            ratings.save(r);
        }
    }

    @BeforeEach
    void admin() throws Exception {
        ConsentSupport.accept(mvc, ADMIN);
    }

    private String claim(String user, String raterKey) throws Exception {
        return mvc.perform(post("/api/history/claims").header("X-Dev-User", user).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"%s\"}".formatted(raterKey)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void claimExpecting(String user, String raterKey, int expected, String code) throws Exception {
        mvc.perform(post("/api/history/claims").header("X-Dev-User", user).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"%s\"}".formatted(raterKey)))
                .andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(code));
    }

    private void decide(String id, String decision, int expected) throws Exception {
        mvc.perform(post("/api/admin/history/claims/" + id + "/decision").header("X-Dev-User", ADMIN)
                .contentType(APPLICATION_JSON).content("{\"decision\":\"" + decision + "\"}")).andExpect(status().is(expected));
    }

    private void historySize(String user, int n) throws Exception {
        mvc.perform(get("/api/history/me").header("X-Dev-User", user)).andExpect(jsonPath("$", hasSize(n)));
    }

    @Test
    void lifecycleOneApprovalReturnsEverySpellingOfThePerson() throws Exception {
        Fx f = new Fx();
        historySize(f.me, 0);

        // Three spellings, one key: one candidate with both seasons and all three ratings.
        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", f.me))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')]".formatted(f.person), hasSize(1)))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].ratingCount".formatted(f.person)).value(3))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].years".formatted(f.person), contains(contains(2001, 2002))))
                .andExpect(jsonPath("$[?(@.raterKey=='Zq%s')]".formatted(f.s), hasSize(0)));

        String id = claim(f.me, f.person);
        mvc.perform(get("/api/history/claims/me").header("X-Dev-User", f.me)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].raterKey").value(f.person));
        historySize(f.me, 0); // pending links nothing

        mvc.perform(get("/api/admin/history/claims?status=PENDING").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$[?(@.id=='%s')].email".formatted(id)).value(f.me))
                .andExpect(jsonPath("$[?(@.id=='%s')].displayName".formatted(id)).value("zq" + f.s))
                .andExpect(jsonPath("$[?(@.id=='%s')].raterKey".formatted(id)).value(f.person))
                .andExpect(jsonPath("$[?(@.id=='%s')].ratingCount".formatted(id)).value(3))
                .andExpect(jsonPath("$[?(@.id=='%s')].years".formatted(id), contains(contains(2001, 2002))));

        decide(id, "APPROVE", 200);

        // Each rating once, from both seasons and all three spellings.
        mvc.perform(get("/api/history/me").header("X-Dev-User", f.me)).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].raterName", containsInAnyOrder("Zq" + f.s, "Zq" + f.s + " L", f.person)))
                .andExpect(jsonPath("$[*].raterKey", everyItem(is(f.person))))
                .andExpect(jsonPath("$[*].year", containsInAnyOrder(2001, 2002, 2002)));
        mvc.perform(get("/api/history/claims/me").header("X-Dev-User", f.me)).andExpect(jsonPath("$[0].status").value("APPROVED"));
        decide(id, "REJECT", 409); // deciding twice is refused
        mvc.perform(delete("/api/history/claims/" + id).header("X-Dev-User", f.me)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_NOT_PENDING"));
    }

    @Test
    void revokeTakesTheRatingsAwayAndFreesTheKey() throws Exception {
        Fx f = new Fx();
        String rival = f.other("rival");
        String id = claim(f.me, f.person);
        decide(id, "APPROVE", 200);
        historySize(f.me, 3);

        // Not for non-admins, and only for approved claims.
        mvc.perform(post("/api/admin/history/claims/" + id + "/revoke").header("X-Dev-User", f.me))
                .andExpect(status().isForbidden());
        historySize(f.me, 3);
        String pending = claim(rival, f.solo);
        mvc.perform(post("/api/admin/history/claims/" + pending + "/revoke").header("X-Dev-User", ADMIN))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLAIM_NOT_APPROVED"));
        mvc.perform(post("/api/admin/history/claims/" + java.util.UUID.randomUUID() + "/revoke").header("X-Dev-User", ADMIN))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/admin/history/claims/" + id + "/revoke").header("X-Dev-User", ADMIN))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        historySize(f.me, 0);
        mvc.perform(get("/api/history/claims/me").header("X-Dev-User", f.me)).andExpect(jsonPath("$[0].status").value("REJECTED"));
        // The key is claimable again, by someone else and approvable.
        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", rival))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].available".formatted(f.person)).value(true));
        decide(claim(rival, f.person), "APPROVE", 200);
        historySize(rival, 3);
        historySize(f.me, 0);
    }

    @Test
    void aRejectedKeyCanBeClaimedAgainAndRejectionsCountTowardsADailyLimit() throws Exception {
        Fx f = new Fx();
        String id = claim(f.me, f.solo);
        decide(id, "REJECT", 200);
        // Asking again reopens the same row.
        mvc.perform(post("/api/history/claims").header("X-Dev-User", f.me).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"%s\"}".formatted(f.solo)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/history/claims/me").header("X-Dev-User", f.me)).andExpect(jsonPath("$", hasSize(1)));
        decide(id, "REJECT", 200);

        // Nine more claims, each rejected, bring the day's count to ten: the eleventh is refused.
        for (int i = 0; i < 9; i++) {
            String key = "Zq" + f.s + "-bulk" + i;
            f.row(2001, key, key, null, "3");
            decide(claim(f.me, key), "REJECT", 200);
        }
        f.row(2001, "Zq" + f.s + "-bulk9", "Zq" + f.s + "-bulk9", null, "3");
        claimExpecting(f.me, "Zq" + f.s + "-bulk9", 422, "CLAIM_LIMIT");
        claimExpecting(f.me, f.solo, 422, "CLAIM_LIMIT"); // re-asking counts too
    }

    @Test
    void personLinkedByEmailIsNotClaimableAndNeedsNoClaim() throws Exception {
        Fx f = new Fx();
        claimExpecting(f.me, f.linked, 409, "RATER_LINKED");
        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", f.me))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].available".formatted(f.linked)).value(false))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].linkedByEmail".formatted(f.linked)).value(true))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].linkedToYou".formatted(f.linked)).value(false))
                .andExpect(content().string(not(containsString(f.linkedEmail))));
        // The owner is covered by the email match; the candidate says so and a claim is refused too.
        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", f.linkedEmail))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].linkedToYou".formatted(f.linked)).value(true));
        historySize(f.linkedEmail, 1);
        claimExpecting(f.linkedEmail, f.linked, 409, "RATER_LINKED");
    }

    @Test
    void duplicateUnknownAndTakenKeys() throws Exception {
        Fx f = new Fx();
        String b = f.other("b");
        String id = claim(f.me, f.solo);

        claimExpecting(f.me, f.solo, 409, "CLAIM_EXISTS");
        // A source spelling that raters.csv joined into a key is not a candidate of its own.
        claimExpecting(f.me, "Zq" + f.s + " L", 404, "RATER_NOT_FOUND");
        claimExpecting(f.me, "Nobody Ever Rated", 404, "RATER_NOT_FOUND");
        mvc.perform(post("/api/history/claims").header("X-Dev-User", f.me).contentType(APPLICATION_JSON)
                .content("{\"raterKey\":\"  \"}")).andExpect(status().isBadRequest());

        decide(id, "APPROVE", 200);

        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", b))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].available".formatted(f.solo)).value(false));
        mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", f.me))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].available".formatted(f.solo)).value(true))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].myClaim.status".formatted(f.solo)).value("APPROVED"));
        claimExpecting(b, f.solo, 409, "RATER_TAKEN");
    }

    @Test
    void competingClaimantsAreListedTogetherAndOnlyOneCanWin() throws Exception {
        Fx f = new Fx();
        String b = f.other("b"), c = f.other("c");
        String first = claim(f.me, f.pair);
        String middle = claim(c, f.solo);  // a different key, created between the two claims for pair
        String second = claim(b, f.pair);

        String body = mvc.perform(get("/api/admin/history/claims").header("X-Dev-User", ADMIN)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int iFirst = body.indexOf(first), iSecond = body.indexOf(second), iMiddle = body.indexOf(middle);
        org.assertj.core.api.Assertions.assertThat(iFirst).isPositive();
        org.assertj.core.api.Assertions.assertThat(iSecond).isPositive();
        // Same key side by side: the other key's claim is not between them.
        org.assertj.core.api.Assertions.assertThat(iMiddle < Math.min(iFirst, iSecond) || iMiddle > Math.max(iFirst, iSecond)).isTrue();
        org.assertj.core.api.Assertions.assertThat(body).contains(f.me).contains(b);

        decide(first, "APPROVE", 200);
        decide(second, "APPROVE", 409);
        decide(second, "REJECT", 200);
    }

    @Test
    void atMostFivePendingClaims() throws Exception {
        Fx f = new Fx();
        for (int i = 0; i < 5; i++) {
            String key = "Zq" + f.s + "-many" + i;
            f.row(2001, key, key, null, "3");
            claim(f.me, key);
        }
        f.row(2001, "Zq" + f.s + "-many5", "Zq" + f.s + "-many5", null, "3");
        claimExpecting(f.me, "Zq" + f.s + "-many5", 422, "CLAIM_LIMIT");

        // Withdrawing one frees a slot; nobody else can withdraw it.
        String mine = mvc.perform(get("/api/history/claims/me").header("X-Dev-User", f.me)).andReturn().getResponse().getContentAsString()
                .replaceAll("^\\[\\{\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(delete("/api/history/claims/" + mine).header("X-Dev-User", f.other("x"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/history/claims/" + mine).header("X-Dev-User", f.me)).andExpect(status().isNoContent());
        claim(f.me, "Zq" + f.s + "-many5");
    }

    @Test
    void adminEndpointsAreForAdminsOnly() throws Exception {
        Fx f = new Fx();
        String user = f.other("plain");
        String id = claim(f.me, f.pair);
        mvc.perform(get("/api/admin/history/claims").header("X-Dev-User", user)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/history/claims/" + id + "/decision").header("X-Dev-User", user)
                .contentType(APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/history/claims").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$[?(@.id=='%s')].status".formatted(id)).value("PENDING"));
        mvc.perform(get("/api/admin/history/claims?status=bogus").header("X-Dev-User", ADMIN)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/history/claims/" + id + "/decision").header("X-Dev-User", ADMIN)
                .contentType(APPLICATION_JSON).content("{\"decision\":\"MAYBE\"}")).andExpect(status().is4xxClientError());
    }

    @Test
    void claimEndpointsNeedConsent() throws Exception {
        Fx f = new Fx();
        String nobody = "zq" + f.s + "-noconsent@cantara.no";
        for (var req : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                get("/api/history/claim-candidates"), get("/api/history/claims/me"),
                post("/api/history/claims").contentType(APPLICATION_JSON).content("{\"raterKey\":\"" + f.solo + "\"}"),
                delete("/api/history/claims/" + java.util.UUID.randomUUID())}) {
            mvc.perform(req.header("X-Dev-User", nobody)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        }
    }

    @Test
    void aWithdrawnAdminIsBlockedFromTheAdminList() throws Exception {
        Fx f = new Fx();
        // dev@cantara.no is the admin; withdraw and check, then restore for the other tests.
        mvc.perform(post("/api/consent/withdraw")).andExpect(status().isOk());
        try {
            mvc.perform(get("/api/admin/history/claims")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
            mvc.perform(post("/api/admin/history/claims/" + java.util.UUID.randomUUID() + "/revoke")).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
        } finally {
            ConsentSupport.accept(mvc, null);
        }
    }

    @Test
    void candidateListSuggestsFirstAndLeaksOnlyKeyYearsAndCount() throws Exception {
        Fx f = new Fx();
        String body = mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", f.me))
                .andExpect(status().isOk())
                // Display name "zq<s>": the person whose key starts with it leads the list.
                .andExpect(jsonPath("$[0].raterKey").value(f.person))
                .andExpect(jsonPath("$[0].suggested").value(true))
                .andExpect(jsonPath("$[?(@.raterKey=='%s')].ratingCount".formatted(f.pair)).value(2))
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
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("@cantara.no").doesNotContain("score");
        // Main's own /api/history/raters is untouched.
        mvc.perform(get("/api/history/raters").header("X-Dev-User", f.me)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void databaseRefusesTwoApprovedClaimsForOneKey() throws Exception {
        Fx f = new Fx();
        var admin = users.upsert(ADMIN, "Dev Tester").getId();
        var one = new HistoricRaterClaim(users.upsert(f.other("db1"), "a").getId(), f.solo);
        var two = new HistoricRaterClaim(users.upsert(f.other("db2"), "b").getId(), f.solo);
        one.approve(admin);
        two.approve(admin);
        claimRepo.saveAndFlush(one);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> claimRepo.saveAndFlush(two))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
