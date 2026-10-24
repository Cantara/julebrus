package no.cantara.julebrus.vinmonopolet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Runs the Vinmonopolet import once at startup. A failure is logged, not fatal: the app works without the review screen. */
@Component
public class VmpImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VmpImportRunner.class);

    private final VmpImportService service;

    public VmpImportRunner(VmpImportService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            service.importClasspath();
        } catch (Exception e) {
            log.error("Vinmonopolet import failed; vote matches are not (re)loaded", e);
        }
    }
}
