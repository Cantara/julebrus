package no.cantara.julebrus.blind;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The blind pour at a table of three: the host pours, two guests guess, the answer
 * comes out with the last guess. Who may do what, and what leaks before the reveal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BlindRoundTest {

    @Autowired
    MockMvc mvc;

    static String idOf(String json) {
        return json.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void hostPoursGuestsGuessAndTheLastGuessReveals() throws Exception {
        String host = "blind-host@cantara.no", a = "blind-a@cantara.no", b = "blind-b@cantara.no", nosy = "blind-nosy@cantara.no";
        for (String who : new String[]{host, a, b, nosy, "dev@cantara.no"}) ConsentSupport.accept(mvc, who);

        String beer1 = idOf(mvc.perform(post("/api/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Blindøl 1\",\"abv\":6.5}")).andReturn().getResponse().getContentAsString());
        String beer2 = idOf(mvc.perform(post("/api/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Blindøl 2\",\"abv\":7.0}")).andReturn().getResponse().getContentAsString());
        String beer3 = idOf(mvc.perform(post("/api/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Blindøl 3\",\"abv\":8.0}")).andReturn().getResponse().getContentAsString());

        String session = mvc.perform(post("/api/sessions").header("X-Dev-User", host)).andReturn().getResponse().getContentAsString();
        String sid = idOf(session);
        String code = session.replaceAll(".*\"joinCode\":\"([^\"]+)\".*", "$1");
        String blind = "/api/sessions/" + sid + "/blind";

        // Alone at the table: nobody to guess.
        mvc.perform(post("/api/sessions/" + sid + "/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\"}".formatted(beer1))).andExpect(status().isOk());
        mvc.perform(post("/api/sessions/" + sid + "/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\"}".formatted(beer2))).andExpect(status().isOk());
        String start = "{\"answerBeerId\":\"%s\",\"optionBeerIds\":[\"%s\",\"%s\"]}".formatted(beer1, beer1, beer2);
        mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON).content(start))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_NEEDS_GUESTS"));

        for (String who : new String[]{a, b}) {
            mvc.perform(post("/api/sessions/join").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                    .content("{\"code\":\"%s\"}".formatted(code))).andExpect(status().isOk());
        }

        // Only the host pours; options must be on the table and the answer among them.
        mvc.perform(post(blind).header("X-Dev-User", a).contentType(APPLICATION_JSON).content(start))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("BLIND_NOT_HOST"));
        mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"answerBeerId\":\"%s\",\"optionBeerIds\":[\"%s\"]}".formatted(beer1, beer1)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_OPTIONS"));
        mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"answerBeerId\":\"%s\",\"optionBeerIds\":[\"%s\",\"%s\"]}".formatted(beer1, beer1, beer3)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_OPTIONS"));
        mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"answerBeerId\":\"%s\",\"optionBeerIds\":[\"%s\",\"%s\"]}".formatted(beer3, beer1, beer2)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_ANSWER"));

        String round = idOf(mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON).content(start))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.answerBeerId").doesNotExist())
                .andExpect(jsonPath("$.options", hasSize(2)))
                .andReturn().getResponse().getContentAsString());

        // One open round at a time.
        mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON).content(start))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_ROUND_OPEN"));

        // The host does not guess, outsiders see nothing, guesses must be an option.
        String guess1 = "{\"beerId\":\"%s\"}".formatted(beer1);
        mvc.perform(post(blind + "/" + round + "/guess").header("X-Dev-User", host).contentType(APPLICATION_JSON).content(guess1))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_HOST_GUESS"));
        mvc.perform(get(blind).header("X-Dev-User", nosy)).andExpect(status().isForbidden());
        mvc.perform(post(blind + "/" + round + "/guess").header("X-Dev-User", a).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\"}".formatted(beer3)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_NOT_OPTION"));

        // Nothing to reveal before anyone has guessed.
        mvc.perform(post(blind + "/" + round + "/reveal").header("X-Dev-User", host))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_NO_GUESSES"));

        mvc.perform(post(blind + "/" + round + "/guess").header("X-Dev-User", a).contentType(APPLICATION_JSON).content(guess1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.myGuess").value(beer1))
                .andExpect(jsonPath("$.guessedBy", hasSize(1)))
                .andExpect(jsonPath("$.guesses").doesNotExist());
        mvc.perform(post(blind + "/" + round + "/guess").header("X-Dev-User", a).contentType(APPLICATION_JSON).content(guess1))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_ALREADY_GUESSED"));

        // The other guest sees who has guessed but not what.
        mvc.perform(get(blind).header("X-Dev-User", b))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].guessedBy", hasSize(1)))
                .andExpect(jsonPath("$[0].myGuess").doesNotExist())
                .andExpect(jsonPath("$[0].answerBeerId").doesNotExist());

        // The last guest's guess reveals the round.
        String aId = mvc.perform(get("/api/me").header("X-Dev-User", a)).andReturn().getResponse().getContentAsString()
                .replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(post(blind + "/" + round + "/guess").header("X-Dev-User", b).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\"}".formatted(beer2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.answerBeerId").value(beer1))
                .andExpect(jsonPath("$.correct", contains(aId)))
                .andExpect(jsonPath("$.guesses." + aId).value(beer1));
        mvc.perform(post(blind + "/" + round + "/guess").header("X-Dev-User", b).contentType(APPLICATION_JSON).content(guess1))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_REVEALED"));

        // Round two: the host reveals early, and closing the table ends round three.
        String round2 = idOf(mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON).content(start))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post(blind + "/" + round2 + "/guess").header("X-Dev-User", a).contentType(APPLICATION_JSON).content(guess1))
                .andExpect(status().isOk());
        mvc.perform(post(blind + "/" + round2 + "/reveal").header("X-Dev-User", a)).andExpect(status().isForbidden());
        mvc.perform(post(blind + "/" + round2 + "/reveal").header("X-Dev-User", host)).andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true)).andExpect(jsonPath("$.correct", contains(aId)));

        String round3 = idOf(mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON).content(start))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post(blind + "/" + round3 + "/guess").header("X-Dev-User", a).contentType(APPLICATION_JSON).content(guess1))
                .andExpect(status().isOk());
        mvc.perform(post("/api/sessions/" + sid + "/close").header("X-Dev-User", host)).andExpect(status().isOk());
        mvc.perform(get(blind).header("X-Dev-User", a))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].revealed", containsInAnyOrder(true, true, true)));

        // Three hits: the Blindsmaker bauble lights up for a, not for b.
        mvc.perform(get("/api/badges/me").header("X-Dev-User", a))
                .andExpect(jsonPath("$[?(@.id=='BLIND_TASTER')].earned").value(true));
        mvc.perform(get("/api/badges/me").header("X-Dev-User", b))
                .andExpect(jsonPath("$[?(@.id=='BLIND_TASTER')].earned").value(false));
        // ...and the finale award names a (admin preview shows awards before the reveal).
        mvc.perform(get("/api/finale").param("preview", "true").header("X-Dev-User", "dev@cantara.no"))
                .andExpect(jsonPath("$.awards[?(@.id=='BLIND_TASTER')].winnerName").value("blind-a"))
                .andExpect(jsonPath("$.awards[?(@.id=='BLIND_TASTER')].detail").value("3 av 3 riktig"));
    }

    @Test
    void aBeerRejectedAfterItWasAddedCannotBePouredNorIsAPendingOneRefused() throws Exception {
        String host = "blind-rej-host@cantara.no", guest = "blind-rej-guest@cantara.no", admin = "dev@cantara.no";
        for (String who : new String[]{host, guest, admin}) ConsentSupport.accept(mvc, who);
        String[] b = new String[3];
        for (int i = 0; i < 3; i++) {
            b[i] = idOf(mvc.perform(post("/api/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                    .content("{\"name\":\"Rejblind " + i + "\",\"abv\":5.5}")).andReturn().getResponse().getContentAsString());
        }
        String session = mvc.perform(post("/api/sessions").header("X-Dev-User", host)).andReturn().getResponse().getContentAsString();
        String sid = idOf(session);
        String code = session.replaceAll(".*\"joinCode\":\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/sessions/join").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))).andExpect(status().isOk());
        for (String id : b) {
            mvc.perform(post("/api/sessions/" + sid + "/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                    .content("{\"beerId\":\"%s\"}".formatted(id))).andExpect(status().isOk());
        }
        // rejected after it was added to the table
        mvc.perform(post("/api/admin/beers/" + b[0] + "/decision").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}")).andExpect(status().isOk());
        String blind = "/api/sessions/" + sid + "/blind";
        // as the answer, and as a plain option
        mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"answerBeerId\":\"%s\",\"optionBeerIds\":[\"%s\",\"%s\"]}".formatted(b[0], b[0], b[1])))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_OPTIONS"));
        mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"answerBeerId\":\"%s\",\"optionBeerIds\":[\"%s\",\"%s\"]}".formatted(b[1], b[0], b[1])))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("BLIND_OPTIONS"));
        // the other two are still pending (not rejected) and work
        mvc.perform(post(blind).header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"answerBeerId\":\"%s\",\"optionBeerIds\":[\"%s\",\"%s\"]}".formatted(b[1], b[1], b[2])))
                .andExpect(status().isCreated());
    }
}
