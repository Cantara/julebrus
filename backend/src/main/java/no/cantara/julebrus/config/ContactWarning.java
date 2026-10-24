package no.cantara.julebrus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** The consent text tells people where to ask for deletion; outside dev a blank address is a gap worth a log line. */
@Component
public class ContactWarning implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContactWarning.class);

    private final ContactProperties contact;
    private final Environment env;

    public ContactWarning(ContactProperties contact, Environment env) {
        this.contact = contact;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!contact.present() && !env.acceptsProfiles(Profiles.of("dev"))) {
            log.warn("julebrus.contact.email (JULEBRUS_CONTACT_EMAIL) is blank: the consent text says "
                    + "\"contact the organisers\" without an address");
        }
    }
}
