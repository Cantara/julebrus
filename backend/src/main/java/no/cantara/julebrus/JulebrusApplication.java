package no.cantara.julebrus;

import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.config.ContactProperties;
import no.cantara.julebrus.config.DevUserProperties;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.config.SponsorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
@EnableConfigurationProperties({no.cantara.julebrus.push.PushProperties.class, SeasonProperties.class, AuthProperties.class, DevUserProperties.class,
        SponsorProperties.class, ContactProperties.class})
public class JulebrusApplication {

    public static void main(String[] args) {
        SpringApplication.run(JulebrusApplication.class, args);
    }
}
