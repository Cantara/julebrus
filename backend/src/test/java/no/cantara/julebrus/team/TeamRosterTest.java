package no.cantara.julebrus.team;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tap a team, see who plays for it: names and rolls, most active first, never an e-mail, and never a member without active consent. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:roster;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TeamRosterTest {

    static final String CANTARA = "00000000-0000-4000-8000-000000000001";

    @Autowired
    MockMvc mvc;

    @Test
    void theRosterListsMembersByRolls() throws Exception {
        String anna = "roster-anna@cantara.no", bo = "roster-bo@cantara.no", gone = "roster-gone@cantara.no", admin = "dev@cantara.no";
        for (String who : new String[]{anna, bo, gone, admin}) ConsentSupport.accept(mvc, who);
        for (String who : new String[]{anna, bo, gone}) {
            mvc.perform(put("/api/me/teams").header("X-Dev-User", who).contentType(APPLICATION_JSON)
                    .content("{\"teamIds\":[\"%s\"]}".formatted(CANTARA))).andExpect(status().isOk());
        }
        String beer = mvc.perform(post("/api/beers").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Lagøl\",\"abv\":6.5}")).andReturn().getResponse().getContentAsString()
                .replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(post("/api/tastings").header("X-Dev-User", bo).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\",\"score\":5}".formatted(beer))).andExpect(status().isCreated());
        // A member who withdrew consent (or sits on an older text) is not on the list until they accept again.
        mvc.perform(post("/api/consent/withdraw").header("X-Dev-User", gone)).andExpect(status().isOk());

        mvc.perform(get("/api/teams/cantara").header("X-Dev-User", anna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cantara"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.tastings").value(1))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0].displayName").value("roster-bo"))
                .andExpect(jsonPath("$.members[0].tastings").value(1))
                .andExpect(jsonPath("$.members[1].displayName").value("roster-anna"))
                .andExpect(jsonPath("$.members[1].me").value(true))
                .andExpect(jsonPath("$.members[0].email").doesNotExist());
        // Anyone logged in may look, also someone on no team; an unknown slug is 404.
        mvc.perform(get("/api/teams/cantara").header("X-Dev-User", admin)).andExpect(jsonPath("$.mine").value(false));
        mvc.perform(get("/api/teams/nope").header("X-Dev-User", admin)).andExpect(status().isNotFound());
    }
}
