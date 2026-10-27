package no.cantara.julebrus.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Planned tastings: anyone hosts, others join until full, the host cannot leave but can
 * cancel, a time in the past is refused, and on the night the host opens the table from the
 * event so attendees get the join code and nobody else does.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:events;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EventTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void planJoinFillCancelAndOpenTheTable() throws Exception {
        String kari = "ev-kari@cantara.no", ola = "ev-ola@cantara.no", per = "ev-per@cantara.no", nosy = "ev-nosy@cantara.no";
        for (String who : new String[]{kari, ola, per, nosy}) ConsentSupport.accept(mvc, who);
        String soon = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES).toString();

        // A time that has passed is refused; a blank title too.
        mvc.perform(post("/api/events").header("X-Dev-User", kari).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "I går", "startsAt", Instant.now().minus(1, ChronoUnit.HOURS).toString()))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("EVENT_PAST"));
        mvc.perform(post("/api/events").header("X-Dev-User", kari).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", " ", "startsAt", soon))))
                .andExpect(status().isBadRequest());

        // Kari plans a physical evening for two. She attends her own event.
        JsonNode e = json.readTree(mvc.perform(post("/api/events").header("X-Dev-User", kari).contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "Juleøl hos Kari", "startsAt", soon, "online", false,
                                "place", "Kjøkkenet", "note", "Ta med ett øl hver", "capacity", 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mine").value(true)).andExpect(jsonPath("$.joined").value(true))
                .andExpect(jsonPath("$.attendees", hasSize(1))).andExpect(jsonPath("$.hostName").value("ev-kari"))
                .andExpect(jsonPath("$.joinCode").doesNotExist()).andExpect(jsonPath("$.tableOpen").value(false))
                .andReturn().getResponse().getContentAsString());
        String id = e.get("id").asText();

        // Everyone with consent sees it; Ola joins, Per finds it full; joining twice is harmless.
        mvc.perform(get("/api/events").header("X-Dev-User", nosy)).andExpect(jsonPath("$[0].title").value("Juleøl hos Kari"))
                .andExpect(jsonPath("$[0].joined").value(false)).andExpect(jsonPath("$[0].mine").value(false));
        mvc.perform(post("/api/events/" + id + "/join").header("X-Dev-User", ola)).andExpect(status().isOk())
                .andExpect(jsonPath("$.attendees", hasSize(2))).andExpect(jsonPath("$.joined").value(true));
        mvc.perform(post("/api/events/" + id + "/join").header("X-Dev-User", ola)).andExpect(jsonPath("$.attendees", hasSize(2)));
        mvc.perform(post("/api/events/" + id + "/join").header("X-Dev-User", per))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("EVENT_FULL"));
        // The host stays; a guest may leave and come back.
        mvc.perform(delete("/api/events/" + id + "/join").header("X-Dev-User", kari))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("EVENT_HOST_STAYS"));
        mvc.perform(delete("/api/events/" + id + "/join").header("X-Dev-User", ola)).andExpect(jsonPath("$.attendees", hasSize(1)));
        mvc.perform(post("/api/events/" + id + "/join").header("X-Dev-User", ola)).andExpect(jsonPath("$.attendees", hasSize(2)));

        // Only the host opens the table or cancels. Once open, attendees get the code, outsiders do not.
        mvc.perform(post("/api/events/" + id + "/table").header("X-Dev-User", ola))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EVENT_NOT_HOST"));
        JsonNode opened = json.readTree(mvc.perform(post("/api/events/" + id + "/table").header("X-Dev-User", kari))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tableOpen").value(true))
                .andReturn().getResponse().getContentAsString());
        String code = opened.get("joinCode").asText();
        assertThat(code).hasSize(6);
        String again = mvc.perform(post("/api/events/" + id + "/table").header("X-Dev-User", kari)).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(again).get("sessionId").asText()).isEqualTo(opened.get("sessionId").asText());
        String body = mvc.perform(get("/api/events").header("X-Dev-User", nosy)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(code).doesNotContain("@cantara.no");
        mvc.perform(get("/api/events").header("X-Dev-User", ola)).andExpect(jsonPath("$[0].joinCode").value(code));
        mvc.perform(post("/api/sessions/join").header("X-Dev-User", ola).contentType(APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}")).andExpect(status().isOk());

        // Cancelled: gone from the list, and nobody can join it.
        mvc.perform(post("/api/events/" + id + "/cancel").header("X-Dev-User", per)).andExpect(status().isForbidden());
        mvc.perform(post("/api/events/" + id + "/cancel").header("X-Dev-User", kari)).andExpect(jsonPath("$.cancelled").value(true));
        mvc.perform(get("/api/events").header("X-Dev-User", ola)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/events/" + id + "/join").header("X-Dev-User", per))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("EVENT_CANCELLED"));
        mvc.perform(post("/api/events/00000000-0000-4000-8000-00000000dead/join").header("X-Dev-User", per))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }
}
