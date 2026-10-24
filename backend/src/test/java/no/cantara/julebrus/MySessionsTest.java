package no.cantara.julebrus;

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

/** "Dine bord" and the open-table card: a guest can find their way back to the table. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mysessions;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MySessionsTest {

    @Autowired
    MockMvc mvc;

    static String idOf(String json) {
        return json.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void theOpenTableIsOnMeAndInMyList() throws Exception {
        String host = "bord-vert@cantara.no", guest = "bord-gjest@cantara.no", admin = "dev@cantara.no";
        for (String who : new String[]{host, guest, admin}) ConsentSupport.accept(mvc, who);

        mvc.perform(get("/api/me").header("X-Dev-User", guest)).andExpect(jsonPath("$.openTable").doesNotExist());
        mvc.perform(get("/api/sessions/mine").header("X-Dev-User", guest)).andExpect(jsonPath("$", hasSize(0)));

        String beer = idOf(mvc.perform(post("/api/beers").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Bordøl\",\"abv\":6.5}")).andReturn().getResponse().getContentAsString());
        String session = mvc.perform(post("/api/sessions").header("X-Dev-User", host)).andReturn().getResponse().getContentAsString();
        String sid = idOf(session);
        String code = session.replaceAll(".*\"joinCode\":\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/sessions/join").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code))).andExpect(status().isOk());
        mvc.perform(post("/api/sessions/" + sid + "/beers").header("X-Dev-User", host).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\"}".formatted(beer))).andExpect(status().isOk());

        // The guest sees the host's table with one beer waiting; the host sees it as their own.
        mvc.perform(get("/api/me").header("X-Dev-User", guest))
                .andExpect(jsonPath("$.openTable.id").value(sid))
                .andExpect(jsonPath("$.openTable.hostName").value("bord-vert"))
                .andExpect(jsonPath("$.openTable.host").value(false))
                .andExpect(jsonPath("$.openTable.members").value(2))
                .andExpect(jsonPath("$.openTable.pendingForMe").value(1));
        mvc.perform(get("/api/me").header("X-Dev-User", host)).andExpect(jsonPath("$.openTable.host").value(true));

        // After the roll nothing waits; after the host closes, the table leaves "me" but stays in the list.
        mvc.perform(post("/api/tastings").header("X-Dev-User", guest).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\",\"sessionId\":\"%s\",\"score\":4}".formatted(beer, sid))).andExpect(status().isCreated());
        mvc.perform(get("/api/me").header("X-Dev-User", guest)).andExpect(jsonPath("$.openTable.pendingForMe").value(0));
        mvc.perform(post("/api/sessions/" + sid + "/close").header("X-Dev-User", host)).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("X-Dev-User", guest)).andExpect(jsonPath("$.openTable").doesNotExist());
        mvc.perform(get("/api/sessions/mine").header("X-Dev-User", guest))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(sid))
                .andExpect(jsonPath("$[0].closedAt").exists())
                .andExpect(jsonPath("$[0].beers").value(1));
        // An outsider's list is empty.
        mvc.perform(get("/api/sessions/mine").header("X-Dev-User", admin)).andExpect(jsonPath("$", hasSize(0)));
    }
}
