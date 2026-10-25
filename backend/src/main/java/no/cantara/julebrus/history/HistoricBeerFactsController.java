package no.cantara.julebrus.history;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** What Vinmonopolet's lists say about an archive beer, with how sure each match is. Public history, nothing personal. */
@RestController
@RequestMapping("/api/history/beers/facts")
public class HistoricBeerFactsController {

    private final HistoricBeerFacts facts;

    public HistoricBeerFactsController(HistoricBeerFacts facts) {
        this.facts = facts;
    }

    @GetMapping
    public List<HistoricBeerFacts.Fact> facts(@RequestParam String name) {
        return facts.forName(name);
    }
}
