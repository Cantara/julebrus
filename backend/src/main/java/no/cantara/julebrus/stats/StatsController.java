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
    private final no.cantara.julebrus.advent.Blackout blackout;
    private final no.cantara.julebrus.user.CurrentUser currentUser;

    public StatsController(RankingService ranking, TeamService teams, no.cantara.julebrus.advent.Blackout blackout,
                           no.cantara.julebrus.user.CurrentUser currentUser) {
        this.ranking = ranking;
        this.teams = teams;
        this.blackout = blackout;
        this.currentUser = currentUser;
    }

    /**
     * The official ranking: weighted 1 to 6 scores, shrunk towards the season mean. {@code team} (a slug) narrows it to that
     * team's raters. 423 BLACKOUT in the last week before the finale ({@code preview=true} lets an admin look).
     */
    @GetMapping("/beers")
    public List<RankingService.BeerRank> beerRanking(@RequestParam(required = false) String team,
                                                     @RequestParam(defaultValue = "false") boolean preview) {
        blackout.guard(currentUser.get(), preview);
        if (team == null || team.isBlank()) return ranking.beerRanking();
        Team t = teams.bySlug(team);
        return ranking.beerRanking(t.getMembers().stream().map(Team.Member::userId).collect(Collectors.toSet()));
    }

    /** The sidebar: pairwise duels, independent of how generous each rater is. */
    @GetMapping("/duels")
    public List<RankingService.DuelRank> duelRanking(@RequestParam(defaultValue = "false") boolean preview) {
        blackout.guard(currentUser.get(), preview);
        return ranking.duelRanking();
    }
}
