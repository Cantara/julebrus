package no.cantara.julebrus.beer;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** v0.5 review fixes: rejected beers, beer creation bounds and caps, admin login list and link consent. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BeerRulesTest {

    static final String ADMIN = "dev@cantara.no";
    static final AtomicInteger N = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    private String person() throws Exception {
        String email = "br" + System.nanoTime() + N.incrementAndGet() + "@cantara.no";
        ConsentSupport.accept(mvc, email);
        ConsentSupport.accept(mvc, ADMIN);
        return email;
    }

    private ResultActions addBeer(String user, String json) throws Exception {
        return mvc.perform(post("/api/beers").header("X-Dev-User", user).contentType(APPLICATION_JSON).content(json));
    }

    private String idOf(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    // ---- A1: raters/logins and link only for people with active consent

    @Test
    void loginListAndLinkCoverOnlyPeopleWithActiveConsent() throws Exception {
        String consented = person();
        String never = "never" + System.nanoTime() + "@cantara.no";
        mvc.perform(get("/api/me").header("X-Dev-User", never)).andExpect(status().isOk()); // users row only
        String withdrawn = person();
        mvc.perform(post("/api/consent/withdraw").header("X-Dev-User", withdrawn)).andExpect(status().is2xxSuccessful());

        mvc.perform(get("/api/admin/history/raters/logins").header("X-Dev-User", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].email", hasItem(consented)))
                .andExpect(jsonPath("$[*].email", not(hasItem(never))))
                .andExpect(jsonPath("$[*].email", not(hasItem(withdrawn))));

        for (String who : new String[]{never, withdrawn}) {
            String id = mvc.perform(get("/api/me").header("X-Dev-User", who)).andReturn().getResponse()
                    .getContentAsString().replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
            mvc.perform(post("/api/admin/history/raters/link").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                            .content("{\"raterKey\":\"Whoever\",\"userId\":\"%s\"}".formatted(id)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_ACTIVE_CONSENT"));
        }
    }

    @Test
    void loginListIsAdminOnly() throws Exception {
        String user = person();
        mvc.perform(get("/api/admin/history/raters/logins").header("X-Dev-User", user)).andExpect(status().isForbidden());
    }

    // ---- A2: a rejected beer is unusable, even for its creator

    @Test
    void rejectedBeerCannotBeAddedToASessionOrRatedButPendingCan() throws Exception {
        String guest = person();
        String pending = idOf(addBeer(guest, "{\"name\":\"Pending " + N.incrementAndGet() + "\"}").andExpect(status().isCreated()));
        String rejected = idOf(addBeer(guest, "{\"name\":\"Rejected " + N.incrementAndGet() + "\"}").andExpect(status().isCreated()));
        mvc.perform(post("/api/admin/beers/" + rejected + "/decision").header("X-Dev-User", ADMIN)
                .contentType(APPLICATION_JSON).content("{\"decision\":\"REJECT\"}")).andExpect(status().isOk());

        String session = idOf(mvc.perform(post("/api/sessions").header("X-Dev-User", guest)).andExpect(status().isCreated()));
        mvc.perform(post("/api/sessions/" + session + "/beers").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\"}".formatted(rejected)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BEER_REJECTED"));
        mvc.perform(post("/api/tastings").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\",\"score\":3}".formatted(rejected)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BEER_REJECTED"));

        mvc.perform(post("/api/sessions/" + session + "/beers").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\"}".formatted(pending))).andExpect(status().isOk());
        mvc.perform(post("/api/tastings").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\",\"score\":3,\"sessionId\":\"%s\"}".formatted(pending, session))).andExpect(status().isCreated());
    }

    // ---- A3: bounds, duplicates, pending cap

    @Test
    void oversizeAndMalformedFieldsAreRejectedWith400NeverAServerError() throws Exception {
        String user = person();
        String longName = "x".repeat(256);
        addBeer(user, "{\"name\":\"" + longName + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));
        addBeer(user, "{\"name\":\"ok\",\"brewery\":\"" + longName + "\"}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"ok\",\"style\":\"" + "y".repeat(101) + "\"}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"ok\",\"vinmonopoletId\":\"" + "1".repeat(33) + "\"}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"ok\",\"imageUrl\":\"https://x.no/" + "a".repeat(1100) + "\"}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"ok\",\"imageUrl\":\"javascript:alert(1)\"}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"   \"}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"bad\\u0000name\"}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"two\\nlines\"}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"bell\\u0007\"}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"ok\",\"abv\":-0.1}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"ok\",\"abv\":20.1}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"ok\",\"abv\":5.55}").andExpect(status().isBadRequest());
        addBeer(user, "{\"name\":\"ok\",\"abv\":1e400}").andExpect(status().is4xxClientError());
        addBeer(user, "{\"name\":\"" + "x".repeat(300_000) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void boundaryValuesAreAccepted() throws Exception {
        String user = person();
        addBeer(user, "{\"name\":\"" + "n".repeat(255) + "\",\"brewery\":\"" + "b".repeat(255) + "\",\"style\":\""
                + "s".repeat(100) + "\",\"abv\":20,\"vinmonopoletId\":\"" + "9".repeat(32) + "\"}").andExpect(status().isCreated());
        addBeer(user, "{\"name\":\"Zero abv " + N.incrementAndGet() + "\",\"abv\":0,\"brewery\":\"  \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.brewery").doesNotExist());
        addBeer(user, "{\"name\":\"Null abv " + N.incrementAndGet() + "\"}").andExpect(status().isCreated());
    }

    @Test
    void samePersonCannotAddTheSameNameTwiceButAnotherPersonCan() throws Exception {
        String a = person();
        String b = person();
        String name = "Dup Stout " + N.incrementAndGet();
        addBeer(a, "{\"name\":\"" + name + "\"}").andExpect(status().isCreated());
        addBeer(a, "{\"name\":\"  " + name.toUpperCase() + "  \"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BEER_DUPLICATE"));
        addBeer(a, "{\"name\":\"" + name.replace(" ", "   ") + "\"}").andExpect(status().isConflict());
        addBeer(b, "{\"name\":\"" + name + "\"}").andExpect(status().isCreated());
    }

    @Test
    void pendingBeersPerPersonAreCappedAndAdminsAreNot() throws Exception {
        String user = person();
        for (int i = 0; i < BeerService.MAX_PENDING_PER_USER; i++) {
            addBeer(user, "{\"name\":\"Cap beer " + N.incrementAndGet() + "\"}").andExpect(status().isCreated());
        }
        addBeer(user, "{\"name\":\"Cap beer overflow\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BEER_PENDING_LIMIT"));
        // Another person is unaffected.
        addBeer(person(), "{\"name\":\"Cap beer other " + N.incrementAndGet() + "\"}").andExpect(status().isCreated());
        // Deciding one frees a slot.
        String id = mvc.perform(get("/api/beers").param("q", "Cap beer").header("X-Dev-User", user)).andReturn().getResponse()
                .getContentAsString().replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/admin/beers/" + id + "/decision").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}")).andExpect(status().isOk());
        addBeer(user, "{\"name\":\"Cap beer after approval\"}").andExpect(status().isCreated());
    }

    @Test
    void parallelAdditionsCannotPassThePendingCap() throws Exception {
        String user = person();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 24; i++) {
                String json = "{\"name\":\"Race beer " + i + " " + N.incrementAndGet() + "\"}";
                futures.add(pool.submit(() -> addBeer(user, json).andReturn().getResponse().getStatus()));
            }
            int created = 0;
            for (var f : futures) if (f.get() == 201) created++;
            org.assertj.core.api.Assertions.assertThat(created).isEqualTo(BeerService.MAX_PENDING_PER_USER);
        } finally {
            pool.shutdownNow();
        }
    }
}
