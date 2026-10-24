package no.cantara.julebrus.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final RankingService ranking;

    public StatsController(RankingService ranking) {
        this.ranking = ranking;
    }

    /** The official ranking: weighted 1 to 6 scores, shrunk towards the season mean. */
    @GetMapping("/beers")
    public List<RankingService.BeerRank> beerRanking() {
        return ranking.beerRanking();
    }

    /** The sidebar: pairwise duels, independent of how generous each rater is. */
    @GetMapping("/duels")
    public List<RankingService.DuelRank> duelRanking() {
        return ranking.duelRanking();
    }
}
