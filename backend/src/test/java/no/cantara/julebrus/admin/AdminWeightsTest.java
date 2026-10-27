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

/**
 * The archive has holes, so an organiser can set a weight by hand with a reason the person
 * sees, and hand it back to the policy later. Nobody else may look at or touch the list.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:adminweights;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminWeightsTest {

    static final String ADMIN = "dev@cantara.no";

    @Autowired
    MockMvc mvc;

    @Test
    void anOrganiserSetsAWeightByHandAndCanHandItBack() throws Exception {
        String vera = "weights-vera@cantara.no";
        ConsentSupport.accept(mvc, ADMIN);
        ConsentSupport.accept(mvc, vera);
        // A newcomer to the app with no archive link starts at 0.5, and the list says so.
        mvc.perform(get("/api/me").header("X-Dev-User", vera)).andExpect(jsonPath("$.weight.weight").value(0.5));
        mvc.perform(get("/api/admin/raters").header("X-Dev-User", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '%s')].weight".formatted(vera)).value(0.5))
                .andExpect(jsonPath("$[?(@.email == '%s')].overridden".formatted(vera)).value(false));
        mvc.perform(get("/api/admin/raters").header("X-Dev-User", vera)).andExpect(status().isForbidden());

        // The organiser knows her from years the archive lacks: full weight, with the reason she will see.
        mvc.perform(post("/api/admin/raters/override").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"weight\":1.0,\"reason\":\"Kjent fra tidligere år\"}".formatted(vera)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overridden").value(true));
        mvc.perform(get("/api/me").header("X-Dev-User", vera))
                .andExpect(jsonPath("$.weight.weight").value(1.0))
                .andExpect(jsonPath("$.weight.reason").value("Kjent fra tidligere år"));
        mvc.perform(get("/api/admin/raters").header("X-Dev-User", ADMIN))
                .andExpect(jsonPath("$[?(@.email == '%s')].overridden".formatted(vera)).value(true));

        // Handed back to the policy, she is a newcomer again.
        mvc.perform(post("/api/admin/raters/reset").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(vera)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overridden").value(false)).andExpect(jsonPath("$.weight").value(0.5));
        mvc.perform(get("/api/me").header("X-Dev-User", vera)).andExpect(jsonPath("$.weight.weight").value(0.5));
        mvc.perform(post("/api/admin/raters/reset").header("X-Dev-User", vera).contentType(APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(vera))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/raters/reset").header("X-Dev-User", ADMIN).contentType(APPLICATION_JSON)
                .content("{\"email\":\"nobody@cantara.no\"}")).andExpect(status().isNotFound());
    }
}
