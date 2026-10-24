package no.cantara.julebrus.team;

import no.cantara.julebrus.ConsentSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teams: the seeded two, the choice at signup ("later" included), the e-mail suggestion,
 * admin-created teams, and what a team changes: the filtered ranking, the pills around the
 * table and the finale. Runs on its own H2 database: its tastings would otherwise tie the
 * finale awards that the shared-database tests assert on.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:teams;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TeamTest {

    static final String CANTARA = "00000000-0000-4000-8000-000000000001";
    static final String REBEL = "00000000-0000-4000-8000-000000000002";

    @Autowired
    MockMvc mvc;

    static String idOf(String json) {
        return json.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void slugsAreUrlSafe() {
        assertThat(Team.slugOf("Team Blå & Grønn")).isEqualTo("team-bla-gronn");
        assertThat(Team.slugOf("  Rebel  ")).isEqualTo("rebel");
        assertThat(Team.slugOf("Ølbryggerne")).isEqualTo("olbryggerne");
    }

    @Test
    void choosingTeamsOnceAndWhatItChanges() throws Exception {
        String anna = "anna@cantara.no", bo = "bo@example.org", admin = "dev@cantara.no";
        for (String who : new String[]{anna, bo, admin}) ConsentSupport.accept(mvc, who);

        // Fresh people have the team page ahead of them; the e-mail domain suggests Cantara to Anna only.
        mvc.perform(get("/api/me").header("X-Dev-User", anna))
                .andExpect(jsonPath("$.teamsChosen").value(false)).andExpect(jsonPath("$.teams", hasSize(0)));
        mvc.perform(get("/api/teams").header("X-Dev-User", anna))
                .andExpect(jsonPath("$[?(@.slug=='cantara')].suggested").value(true))
                .andExpect(jsonPath("$[?(@.slug=='rebel')].suggested").value(false));
        mvc.perform(get("/api/teams").header("X-Dev-User", bo))
                .andExpect(jsonPath("$[?(@.slug=='cantara')].suggested").value(false));

        // Anna joins Cantara, Bo chooses "later": both have answered, only Anna has a team.
        mvc.perform(put("/api/me/teams").header("X-Dev-User", anna).contentType(APPLICATION_JSON)
                        .content("{\"teamIds\":[\"%s\"]}".formatted(CANTARA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].slug").value("cantara")).andExpect(jsonPath("$[0].mine").value(true));
        mvc.perform(put("/api/me/teams").header("X-Dev-User", bo).contentType(APPLICATION_JSON).content("{\"teamIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/me").header("X-Dev-User", anna))
                .andExpect(jsonPath("$.teamsChosen").value(true)).andExpect(jsonPath("$.teams[0].slug").value("cantara"));
        mvc.perform(get("/api/me").header("X-Dev-User", bo))
                .andExpect(jsonPath("$.teamsChosen").value(true)).andExpect(jsonPath("$.teams", hasSize(0)));

        // Both camps at once is allowed; the pills around a table show it.
        mvc.perform(put("/api/me/teams").header("X-Dev-User", anna).contentType(APPLICATION_JSON)
                        .content("{\"teamIds\":[\"%s\",\"%s\"]}".formatted(CANTARA, REBEL)))
                .andExpect(jsonPath("$", hasSize(2)));
        String session = mvc.perform(post("/api/sessions").header("X-Dev-User", anna)).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/sessions/" + idOf(session)).header("X-Dev-User", anna))
                .andExpect(jsonPath("$.members[0].teams", contains("cantara", "rebel")));
        mvc.perform(put("/api/me/teams").header("X-Dev-User", anna).contentType(APPLICATION_JSON)
                .content("{\"teamIds\":[\"%s\"]}".formatted(CANTARA)));

        // A team ranking only sees its own raters: Anna (Cantara) gives 6, Bo (no team) gives 1.
        String beer = idOf(mvc.perform(post("/api/beers").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Lagøl\",\"abv\":7.0}")).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/tastings").header("X-Dev-User", anna).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\",\"score\":6}".formatted(beer))).andExpect(status().isCreated());
        mvc.perform(post("/api/tastings").header("X-Dev-User", bo).contentType(APPLICATION_JSON)
                .content("{\"beerId\":\"%s\",\"score\":1}".formatted(beer))).andExpect(status().isCreated());
        mvc.perform(get("/api/stats/beers").header("X-Dev-User", anna))
                .andExpect(jsonPath("$[?(@.name=='Lagøl')].ratings").value(2))
                .andExpect(jsonPath("$[?(@.name=='Lagøl')].mean").value(3.5));
        mvc.perform(get("/api/stats/beers").param("team", "cantara").header("X-Dev-User", anna))
                .andExpect(jsonPath("$[?(@.name=='Lagøl')].ratings").value(1))
                .andExpect(jsonPath("$[?(@.name=='Lagøl')].mean").value(6.0));
        mvc.perform(get("/api/stats/beers").param("team", "nope").header("X-Dev-User", anna))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));

        // Admins enable more teams; others may not. A disabled team cannot be chosen and is not listed.
        mvc.perform(post("/api/admin/teams").header("X-Dev-User", anna).contentType(APPLICATION_JSON)
                .content("{\"name\":\"Team Blå\"}")).andExpect(status().isForbidden());
        String bla = idOf(mvc.perform(post("/api/admin/teams").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Team Blå\",\"sponsorName\":\"Blå AS\",\"domains\":\"bla.no\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value("team-bla"))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/admin/teams").header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                .content("{\"name\":\"team blå\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/teams").header("X-Dev-User", bo)).andExpect(jsonPath("$[*].slug", hasItem("team-bla")));
        mvc.perform(post("/api/admin/teams/" + bla).header("X-Dev-User", admin).contentType(APPLICATION_JSON)
                .content("{\"enabled\":false}")).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/api/teams").header("X-Dev-User", bo)).andExpect(jsonPath("$[*].slug", not(hasItem("team-bla"))));
        mvc.perform(put("/api/me/teams").header("X-Dev-User", bo).contentType(APPLICATION_JSON)
                        .content("{\"teamIds\":[\"%s\"]}".formatted(bla)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));

        // The finale names each team's own winner and the team of the year.
        mvc.perform(get("/api/finale").param("preview", "true").header("X-Dev-User", admin))
                .andExpect(jsonPath("$.teamWinners[?(@.slug=='cantara')].beerName").value("Lagøl"))
                .andExpect(jsonPath("$.awards[?(@.id=='TEAM')].winnerName").exists());
    }
}
