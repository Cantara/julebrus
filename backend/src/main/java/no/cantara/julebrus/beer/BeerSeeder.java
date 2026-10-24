package no.cantara.julebrus.beer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/** Puts the perennial grocery juleøl in the catalogue at startup, once per name. */
@Component
public class BeerSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BeerSeeder.class);

    private final BeerRepository beers;

    public BeerSeeder(BeerRepository beers) {
        this.beers = beers;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        ClassPathResource csv = new ClassPathResource("beers/seed.csv");
        if (!csv.exists()) return;
        Set<String> existing = beers.findAll().stream().map(b -> BeerCsv.key(b.getName())).collect(Collectors.toSet());
        int added = 0;
        for (BeerCsv.Row r : BeerCsv.parse(csv.getContentAsString(StandardCharsets.UTF_8))) {
            if (!r.valid() || existing.contains(BeerCsv.key(r.name()))) continue;
            beers.save(new Beer(r.name(), r.brewery(), r.style(), r.abv(), r.vinmonopoletId(), null, null, Beer.Status.APPROVED));
            existing.add(BeerCsv.key(r.name()));
            added++;
        }
        if (added > 0) log.info("Seeded {} beers from beers/seed.csv", added);
    }
}
