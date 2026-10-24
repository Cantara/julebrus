package no.cantara.julebrus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What people wrote with their roll comes out with the scores, and not a second earlier. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:revealnotes;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RevealNotesTest {

    @Autowired
    MockMvc mvc;

    static String idOf(String json) {
        return json.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void notesAndTagsShowOnlyAtTheReveal() throws Exception {
        String host = "notes-vert@cantara.no", guest = "notes-gjest@cantara.no", admin = "dev@cantara.no";
        for (String who : new String[]{host, guest, admin}) ConsentSupport.accept(mvc, who);
        String beer = idOf(mvc.perform(post("/api/beers").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Notatøl\",\"abv\":6.5}")).andReturn().getResponse().getContentAsString());
        String session = mvc.perform(post("/api/sessions").header("X-Dev-User", host)).andReturn().getResponse().getContentAsString();
        String sid = idOf(session);
        mvc.perform(post("/api/sessions/join").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(session.replaceAll(".*\"joinCode\":\"([^\"]+)\".*", "$1")))).andExpect(status().isOk());
        mvc.perform(post("/api/sessions/" + sid + "/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\"}".formatted(beer))).andExpect(status().isOk());
        String hostId = mvc.perform(get("/api/me").header("X-Dev-User", host)).andReturn().getResponse().getContentAsString()
                .replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");

        mvc.perform(post("/api/tastings").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\",\"sessionId\":\"%s\",\"score\":5,\"notes\":\"Mykt og rundt\",\"tags\":[\"karamell\"]}".formatted(beer, sid)))
                .andExpect(status().isCreated());
        // Before the reveal: the guest sees that the host has rolled, nothing more.
        mvc.perform(get("/api/sessions/" + sid).header("X-Dev-User", guest))
                .andExpect(jsonPath("$.beers[0].revealed").value(false))
                .andExpect(jsonPath("$.beers[0].notes").doesNotExist());
        // The guest rolls without a word: revealed, and only the host appears under notes.
        mvc.perform(post("/api/tastings").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                        .content("{\"beerId\":\"%s\",\"sessionId\":\"%s\",\"score\":3}".formatted(beer, sid)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/sessions/" + sid).header("X-Dev-User", guest))
                .andExpect(jsonPath("$.beers[0].revealed").value(true))
                .andExpect(jsonPath("$.beers[0].notes." + hostId + ".notes").value("Mykt og rundt"))
                .andExpect(jsonPath("$.beers[0].notes." + hostId + ".tags", contains("karamell")))
                .andExpect(jsonPath("$.beers[0].notes.length()").value(1));
    }
}
