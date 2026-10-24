package no.cantara.julebrus.team;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TeamController {

    /** {@code suggested} follows the person's e-mail domain; {@code mine} is their current choice. */
    public record TeamView(UUID id, String slug, String name, String sponsorName, String sponsorUrl, long members,
                           boolean suggested, boolean mine) {
    }

    public record ChooseRequest(@NotNull List<UUID> teamIds) {
    }

    private final TeamService service;
    private final CurrentUser currentUser;

    public TeamController(TeamService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** The enabled teams, for the signup page and the profile. */
    @GetMapping("/api/teams")
    public List<TeamView> list() {
        User me = currentUser.get();
        return service.enabled().stream().map(t -> view(t, me)).toList();
    }

    /** Replaces the person's teams; an empty list means "later" and still counts as chosen. */
    @PutMapping("/api/me/teams")
    public List<TeamView> choose(@Valid @RequestBody ChooseRequest in) {
        User me = currentUser.get();
        return service.choose(me.getId(), in.teamIds()).stream().map(t -> view(t, me)).toList();
    }

    static TeamView view(Team t, User me) {
        return new TeamView(t.getId(), t.getSlug(), t.getName(), t.getSponsorName(), t.getSponsorUrl(),
                t.getMembers().size(), t.suggestsFor(me.getEmail()), t.isMember(me.getId()));
    }
}
