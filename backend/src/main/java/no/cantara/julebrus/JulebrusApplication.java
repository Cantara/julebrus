package no.cantara.julebrus;

import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.config.DevUserProperties;
import no.cantara.julebrus.config.SeasonProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({SeasonProperties.class, AuthProperties.class, DevUserProperties.class})
public class JulebrusApplication {

    public static void main(String[] args) {
        SpringApplication.run(JulebrusApplication.class, args);
    }
}
