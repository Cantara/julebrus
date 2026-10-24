package no.cantara.julebrus.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Without JULEBRUS_ADMINS nobody is an admin (the dev profile sets its own). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admindefault;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.security.oauth2.client.registration.google.client-id=fake",
        "spring.security.oauth2.client.registration.google.client-secret=fake"})
class AdminsDefaultTest {

    @Autowired
    AuthProperties auth;

    @Test
    void noAdminsByDefault() {
        assertThat(auth.admins()).isEmpty();
        assertThat(auth.isAdmin("totto@exoreaction.com")).isFalse();
    }
}
