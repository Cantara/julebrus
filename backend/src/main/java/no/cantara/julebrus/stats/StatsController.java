package no.cantara.julebrus.stats;

import no.cantara.julebrus.team.Team;
import no.cantara.julebrus.team.TeamService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final RankingService ranking;
    private final TeamService teams;

    public StatsController(RankingService ranking, TeamService teams) {
        this.ranking = ranking;
        this.teams = teams;
    }

    /** The official ranking: weighted 1 to 6 scores, shrunk towards the season mean. {@code team} (a slug) narrows it to that team's raters. */
    @GetMapping("/beers")
    public List<RankingService.BeerRank> beerRanking(@RequestParam(required = false) String team) {
        if (team == null || team.isBlank()) return ranking.beerRanking();
        Team t = teams.bySlug(team);
        return ranking.beerRanking(t.getMembers().stream().map(Team.Member::userId).collect(Collectors.toSet()));
    }

    /** The sidebar: pairwise duels, independent of how generous each rater is. */
    @GetMapping("/duels")
    public List<RankingService.DuelRank> duelRanking() {
        return ranking.duelRanking();
    }
}
