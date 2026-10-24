package no.cantara.julebrus.duel;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A beer the organisers rejected after it reached a table is not offered for duels, and a duel with it is refused. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DuelRejectedBeerTest {

    @Autowired
    MockMvc mvc;

    private static String idOf(String json) {
        return json.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
    }

    private String beer(String who, String name) throws Exception {
        return idOf(mvc.perform(post("/api/beers").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"abv\":5.5}")).andReturn().getResponse().getContentAsString());
    }

    @Test
    void rejectedBeerIsNeitherOfferedNorAccepted() throws Exception {
        String me = "duel-rej@cantara.no", admin = "dev@cantara.no";
        ConsentSupport.accept(mvc, me);
        ConsentSupport.accept(mvc, admin);
        String b1 = beer(me, "Duelrej 1"), b2 = beer(me, "Duelrej 2"), b3 = beer(me, "Duelrej 3");
        String sid = idOf(mvc.perform(post("/api/sessions").header("X-Dev-User", me)).andReturn().getResponse().getContentAsString());
        for (String b : new String[]{b1, b2, b3}) {
            mvc.perform(post("/api/sessions/" + sid + "/beers").header("X-Dev-User", me).contentType(APPLICATION_JSON)
                    .content("{\"beerId\":\"%s\"}".formatted(b))).andExpect(status().isOk());
            mvc.perform(post("/api/tastings").header("X-Dev-User", me).contentType(APPLICATION_JSON)
                    .content("{\"beerId\":\"%s\",\"sessionId\":\"%s\",\"score\":4}".formatted(b, sid))).andExpect(status().isCreated());
        }
        mvc.perform(get("/api/duels/pending/" + sid).header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(3)));

        mvc.perform(post("/api/admin/beers/" + b1 + "/decision").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\"}")).andExpect(status().isOk());

        mvc.perform(get("/api/duels/pending/" + sid).header("X-Dev-User", me)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/duels").header("X-Dev-User", me).contentType(APPLICATION_JSON)
                .content("{\"sessionId\":\"%s\",\"winnerBeerId\":\"%s\",\"loserBeerId\":\"%s\"}".formatted(sid, b1, b2)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/duels").header("X-Dev-User", me).contentType(APPLICATION_JSON)
                .content("{\"sessionId\":\"%s\",\"winnerBeerId\":\"%s\",\"loserBeerId\":\"%s\"}".formatted(sid, b2, b3)))
                .andExpect(status().isCreated());
    }
}
