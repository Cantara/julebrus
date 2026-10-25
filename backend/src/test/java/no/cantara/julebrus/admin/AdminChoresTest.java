package no.cantara.julebrus.admin;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An organiser's /api/me counts what waits: claims, unmatched logins, pending beers and photos. Nobody else sees it. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:chores;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminChoresTest {

    static final String ADMIN = "dev@cantara.no";

    @Autowired
    MockMvc mvc;

    @Test
    void theOrganiserSeesWhatWaits() throws Exception {
        String pia = "chores-pia@cantara.no";
        ConsentSupport.accept(mvc, ADMIN);
        ConsentSupport.accept(mvc, pia);
        // Two logins with consent, neither tied to a historic name yet; nothing else in the queues.
        mvc.perform(get("/api/me").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$.chores.unmatched").value(2))
                .andExpect(jsonPath("$.chores.claims").value(0))
                .andExpect(jsonPath("$.chores.beers").value(0))
                .andExpect(jsonPath("$.chores.photos").value(0))
                .andExpect(jsonPath("$.chores.total").value(2));
        mvc.perform(get("/api/me").header("X-Dev-User", pia)).andExpect(jsonPath("$.chores").doesNotExist());

        // Pia adds a beer and asks to be linked to a historic name: two more chores.
        mvc.perform(post("/api/beers").header("X-Dev-User", pia).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Pias øl\",\"abv\":6.5}")).andExpect(status().isCreated());
        String key = mvc.perform(get("/api/history/claim-candidates").header("X-Dev-User", pia)).andReturn().getResponse()
                .getContentAsString().replaceAll(".*?\"raterKey\":\"([^\"]+)\".*", "$1");
        String claim = mvc.perform(post("/api/history/claims").header("X-Dev-User", pia).contentType(APPLICATION_JSON)
                        .content("{\"raterKey\":\"%s\"}".formatted(key))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/me").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$.chores.claims").value(1))
                .andExpect(jsonPath("$.chores.beers").value(1))
                .andExpect(jsonPath("$.chores.total").value(4));

        // Approving the claim matches Pia: one claim fewer, one unmatched login fewer.
        mvc.perform(post("/api/admin/history/claims/" + claim + "/decision").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$.chores.claims").value(0))
                .andExpect(jsonPath("$.chores.unmatched").value(1))
                .andExpect(jsonPath("$.chores.total").value(2));
    }
}
