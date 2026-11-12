package no.cantara.julebrus.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logins outlive a restart: a session is a row in the database, so a session saved there (as by the server before a
 * release) logs the person in on its cookie alone. The cookie is persistent and locked down, the session lasts a month
 * of use, and it is named by e-mail so the deletion recipe can find it. Logging out removes the row.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sessions;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.security.oauth2.client.registration.google.client-id=fake",
        "spring.security.oauth2.client.registration.google.client-secret=fake"})
@AutoConfigureMockMvc
class SessionPersistenceTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcIndexedSessionRepository sessions;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ClientRegistrationRepository registrations;

    @Test
    void aLoginStoredInTheDatabaseLogsThePersonInOnItsCookieAlone() throws Exception {
        String who = "husket-" + System.nanoTime() + "@cantara.no";
        Instant now = Instant.now();
        OidcIdToken token = new OidcIdToken("token", now, now.plusSeconds(3600),
                Map.of("sub", "google-" + who, "email", who, "name", "Husket Person"));
        DefaultOidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), token, "email");
        Stored session = store(sessions, new SecurityContextImpl(new OAuth2AuthenticationToken(user, user.getAuthorities(), "google")));

        assertThat(session.maxInactive()).isEqualTo(Duration.ofDays(30));
        assertThat(jdbc.queryForObject("select principal_name from spring_session where session_id = ?", String.class, session.id()))
                .isEqualTo(who);

        Cookie cookie = new Cookie("SESSION", Base64.getEncoder().encodeToString(session.id().getBytes(StandardCharsets.UTF_8)));
        mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(who));
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());

        // Logging out ends it for good.
        MvcResult csrf = mvc.perform(get("/api/me").cookie(cookie)).andReturn();
        Cookie xsrf = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        mvc.perform(post("/api/logout").cookie(cookie, xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("select count(*) from spring_session where session_id = ?", Integer.class, session.id())).isZero();
        mvc.perform(get("/api/me").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    record Stored(String id, Duration maxInactive) {
    }

    /** What the server does when a login completes: the security context goes into a new session, saved to the database. */
    static <S extends org.springframework.session.Session> Stored store(org.springframework.session.SessionRepository<S> repo, Object context) {
        S s = repo.createSession();
        s.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        repo.save(s);
        return new Stored(s.getId(), s.getMaxInactiveInterval());
    }

    @Test
    void theSessionCookieIsPersistentAndLockedDown() throws Exception {
        // Starting the Google login creates the session that will carry the login.
        String setCookie = mvc.perform(get("/oauth2/authorization/google")).andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("SESSION=")).findFirst().orElseThrow();
        assertThat(setCookie).contains("Max-Age=31536000").contains("Secure").contains("HttpOnly").contains("SameSite=Lax");
    }

    @Test
    void googleLoginsAreNamedByEmail() {
        assertThat(registrations.findByRegistrationId("google").getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                .isEqualTo("email");
    }
}
