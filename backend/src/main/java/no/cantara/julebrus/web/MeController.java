package no.cantara.julebrus.web;

import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.tasting.TastingService;
import no.cantara.julebrus.team.Team;
import no.cantara.julebrus.team.TeamService;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.weight.RaterWeight;
import no.cantara.julebrus.weight.RaterWeightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class MeController {

    public record Season(int year, LocalDate start, LocalDate end, int maxTastingsPerDay, int maxSoloDays,
                         int bigTableSize, int maxTastingsPerDayBigTable) {
    }

    public record Weight(double weight, String reason) {
    }

    public record TeamRef(UUID id, String slug, String name) {
    }

    /** The newest open table this person sits at, with how many beers still wait for their roll. */
    public record OpenTable(UUID id, String hostName, boolean host, int members, int beers, int pendingForMe) {
    }

    public record Me(UUID id, String email, String displayName, TastingService.Status status, Season season,
                     /** null until the person has consented */ Weight weight, boolean admin, boolean consentRequired, String consentVersion,
                     /** the enabled teams the person plays for; empty before the choice and after "later" */
                     java.util.List<TeamRef> teams,
                     /** false until the team page has been answered, "later" included */
                     boolean teamsChosen,
                     /** null when no table is open */
                     OpenTable openTable) {
    }

    private final CurrentUser currentUser;
    private final TastingService tastings;
    private final SeasonProperties season;
    private final RaterWeightService weights;
    private final no.cantara.julebrus.config.AuthProperties auth;
    private final ConsentService consents;
    private final TeamService teams;
    private final no.cantara.julebrus.session.SessionService sessions;
    private final no.cantara.julebrus.tasting.TastingRepository tastingRows;
    private final no.cantara.julebrus.user.UserRepository users;

    public MeController(CurrentUser currentUser, TastingService tastings, SeasonProperties season,
                        RaterWeightService weights, no.cantara.julebrus.config.AuthProperties auth,
                        ConsentService consents, TeamService teams, no.cantara.julebrus.session.SessionService sessions,
                        no.cantara.julebrus.tasting.TastingRepository tastingRows, no.cantara.julebrus.user.UserRepository users) {
        this.sessions = sessions;
        this.tastingRows = tastingRows;
        this.users = users;
        this.currentUser = currentUser;
        this.tastings = tastings;
        this.season = season;
        this.weights = weights;
        this.auth = auth;
        this.consents = consents;
        this.teams = teams;
    }

    @GetMapping("/api/me")
    public Me me() {
        User u = currentUser.get();
        boolean consented = consents.hasAccepted(u.getId());
        // Before consent only the users row exists: no weight is computed or stored, no history is read.
        Weight weight = null;
        java.util.List<TeamRef> mine = java.util.List.of();
        OpenTable open = null;
        if (consented) {
            RaterWeight w = weights.current(u);
            weight = new Weight(w.weight(), w.getReason());
            mine = teams.of(u.getId()).stream().map(t -> new TeamRef(t.getId(), t.getSlug(), t.getName())).toList();
            open = sessions.mine(u.getId()).stream().filter(no.cantara.julebrus.session.TastingSession::isOpen).findFirst()
                    .map(s -> {
                        long rated = tastingRows.findBySessionId(s.getId()).stream()
                                .filter(t -> t.getUserId().equals(u.getId())).map(no.cantara.julebrus.tasting.Tasting::getBeerId).distinct().count();
                        String host = users.findById(s.getHostId()).map(User::getDisplayName).orElse("?");
                        return new OpenTable(s.getId(), host, s.getHostId().equals(u.getId()), s.getMembers().size(),
                                s.getBeers().size(), (int) Math.max(0, s.getBeers().size() - rated));
                    }).orElse(null);
        }
        return new Me(u.getId(), u.getEmail(), u.getDisplayName(), tastings.status(u.getId()),
                new Season(season.year(), season.start(), season.end(), season.maxTastingsPerDay(), season.maxSoloDays(),
                        season.bigTableSize(), season.maxTastingsPerDayBigTable()),
                weight, auth.isAdmin(u.getEmail()),
                !consented, ConsentService.CONSENT_VERSION, mine, u.getTeamsChosenAt() != null, open);
    }
}
