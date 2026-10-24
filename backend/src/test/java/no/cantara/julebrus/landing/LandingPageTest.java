package no.cantara.julebrus.landing;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * "/" under the real production security chain: a visitor who is not signed in gets the landing
 * page (strict CSP, no person's name, nothing read from the database); a signed-in person still
 * goes straight into the app.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:landing;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "julebrus.contact.email=kontakt@example.org",
        "spring.security.oauth2.client.registration.google.client-id=fake",
        "spring.security.oauth2.client.registration.google.client-secret=fake"})
@AutoConfigureMockMvc
class LandingPageTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManagerFactory emf;

    static RequestPostProcessor login(String email) {
        return oidcLogin().idToken(t -> t.claim("email", email).claim("name", "Test " + email).subject(email));
    }

    MockHttpServletResponse fetch(MockHttpServletRequestBuilder req) throws Exception {
        MockHttpServletResponse res = mvc.perform(req).andReturn().getResponse();
        res.setCharacterEncoding("UTF-8");
        return res;
    }

    @Test
    void anonymousRootIsTheLandingPageWithAStrictPolicy() throws Exception {
        for (MockHttpServletRequestBuilder req : new MockHttpServletRequestBuilder[]{get("/"), get("/").param("lang", "en"),
                get("/velkommen"), get("/velkommen/").param("lang", "en"), get("/welcome"), get("/welcome/").param("lang", "en"),
                get("/landing"), get("/landing/").param("lang", "en")}) {
            MockHttpServletResponse res = fetch(req);
            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(res.getForwardedUrl()).isNull();
            assertThat(res.getContentType()).startsWith("text/html");
            assertThat(res.getHeader("Content-Security-Policy")).isEqualTo(LandingController.CSP)
                    .isEqualTo("default-src 'none'; style-src 'unsafe-inline'; img-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
            assertThat(res.getHeader("Cache-Control")).contains("no-store");
            assertThat(res.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
            String html = res.getContentAsString();
            assertThat(html).startsWith("<!doctype html>").contains("Cantaras juleøl")
                    .contains("href=\"/oauth2/authorization/google\"").contains("href=\"/privacy")
                    .contains("eXOReaction").contains("mailto:kontakt@example.org");
            // Nothing that would load or run: no scripts, no external sources, no web fonts, no event handlers.
            assertThat(html).doesNotContain("<script").doesNotContain("src=\"http").doesNotContain("url(http")
                    .doesNotContain("@import").doesNotContain("@font-face").doesNotContain("<img").doesNotContain("<iframe")
                    .doesNotContain("href=\"http://").doesNotContainPattern("\\son[a-z]+=");
            assertThat(res.getContentAsByteArray().length).as("page weight").isLessThan(150_000);
            // The season's features, in the page's own language; push is off here (no VAPID keys), so not offered.
            boolean en = html.contains("<html lang=\"en\">");
            assertThat(html).contains(en ? "Cantara against Rebel" : "Cantara mot Rebel").contains(en ? "Tip the podium" : "Tipp pallen")
                    .contains(en ? "A photo of the label" : "Bilde av etiketten").contains(en ? "in the same second" : "i samme sekund")
                    .doesNotContain(en ? "A nudge from the calendar" : "Et dytt fra kalenderen");
        }
        assertThat(fetch(get("/")).getContentAsString()).contains("<html lang=\"nb\">").contains("Logg inn med Google").contains("Støttet av");
        assertThat(fetch(get("/").param("lang", "en")).getContentAsString()).contains("<html lang=\"en\">").contains("Sign in with Google").contains("Sponsored by");
    }

    @Test
    void noPersonsNameAppearsInEitherLanguage() throws Exception {
        Set<String> names = RepoData.personNames();
        assertThat(names).contains("Totto").hasSizeGreaterThan(80);
        for (MockHttpServletRequestBuilder req : new MockHttpServletRequestBuilder[]{get("/"), get("/").param("lang", "en"),
                get("/velkommen"), get("/velkommen").param("lang", "en"), get("/welcome"), get("/welcome").param("lang", "en"),
                get("/landing"), get("/landing").param("lang", "en")}) {
            String html = new String(fetch(req).getContentAsByteArray(), StandardCharsets.UTF_8);
            assertThat(RepoData.namesIn(html, names)).as("names in the page").isEmpty();
        }
    }

    @Test
    void welcomeAndLandingAreTheSamePageAsVelkommen() throws Exception {
        for (String path : new String[]{"/welcome", "/landing"}) {
            for (String lang : new String[]{null, "en"}) {
                String velkommen = lang == null ? fetch(get("/velkommen")).getContentAsString() : fetch(get("/velkommen").param("lang", lang)).getContentAsString();
                MockHttpServletResponse res = lang == null ? fetch(get(path)) : fetch(get(path).param("lang", lang));
                String html = res.getContentAsString();
                // Same page; only the language switch and hreflang links point at the path itself.
                assertThat(html.replace("\"" + path, "\"/velkommen")).isEqualTo(velkommen);
                assertThat(html).contains(lang == null ? "href=\"" + path + "?lang=en\"" : "href=\"" + path + "\" hreflang=\"nb\"");
                assertThat(html).contains(lang == null ? "<html lang=\"nb\">" : "<html lang=\"en\">");
            }
            // Signed in, too: it is the landing page, not the app.
            MockHttpServletResponse in = fetch(get(path).with(login("landing-" + path.substring(1) + "-" + System.nanoTime() + "@x.no")));
            assertThat(in.getForwardedUrl()).isNull();
            assertThat(in.getHeader("Content-Security-Policy")).isEqualTo(LandingController.CSP);
        }
        // /velkommen is unchanged: Norwegian by default, its own path in the switch.
        assertThat(fetch(get("/velkommen")).getContentAsString()).contains("<html lang=\"nb\">").contains("href=\"/velkommen?lang=en\"");
    }

    @Test
    void signedInPeopleStillGoStraightIntoTheApp() throws Exception {
        MockHttpServletResponse res = fetch(get("/").with(login("landing-in-" + System.nanoTime() + "@x.no")));
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getForwardedUrl()).isEqualTo("/index.html");
        assertThat(res.getHeader("Content-Security-Policy")).isNull();
        // The landing page itself stays reachable for them.
        MockHttpServletResponse welcome = fetch(get("/velkommen").with(login("landing-in2-" + System.nanoTime() + "@x.no")));
        assertThat(welcome.getForwardedUrl()).isNull();
        assertThat(welcome.getContentAsString()).contains("Cantaras juleøl");
        // Other app paths are unchanged.
        assertThat(fetch(get("/beers")).getForwardedUrl()).isEqualTo("/index.html");
    }

    @Test
    void theLandingPageReadsNothingFromTheDatabase() throws Exception {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        assertThat(stats.isStatisticsEnabled()).isTrue();
        stats.clear();
        fetch(get("/"));
        fetch(get("/").param("lang", "en"));
        fetch(get("/velkommen"));
        assertThat(stats.getPrepareStatementCount()).as("JDBC statements").isZero();
        assertThat(stats.getQueryExecutionCount()).as("queries").isZero();
        assertThat(stats.getEntityLoadCount()).as("entities loaded").isZero();
        assertThat(stats.getSessionOpenCount()).as("sessions opened").isZero();
    }

    @Test
    void everyBadgeHasNorwegianCopy() {
        assertThat(LandingPage.everyBadgeHasCopy()).isTrue();
    }
}
