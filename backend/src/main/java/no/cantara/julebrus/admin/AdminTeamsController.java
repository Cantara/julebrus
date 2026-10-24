package no.cantara.julebrus.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.team.Team;
import no.cantara.julebrus.team.TeamRepository;
import no.cantara.julebrus.team.TeamService;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Teams are enabled by an organiser, typically after a word about a contribution to the tasting. */
@RestController
@RequestMapping("/api/admin/teams")
public class AdminTeamsController {

    public record AdminTeamView(UUID id, String slug, String name, String sponsorName, String sponsorUrl, String domains,
                                boolean enabled, long members) {
        static AdminTeamView of(Team t) {
            return new AdminTeamView(t.getId(), t.getSlug(), t.getName(), t.getSponsorName(), t.getSponsorUrl(),
                    t.getDomains(), t.isEnabled(), t.getMembers().size());
        }
    }

    public record CreateRequest(@NotBlank String name, String sponsorName, String sponsorUrl, String domains) {
    }

    public record UpdateRequest(String name, String sponsorName, String sponsorUrl, String domains, Boolean enabled) {
    }

    private final TeamService service;
    private final TeamRepository teams;
    private final CurrentUser currentUser;
    private final AuthProperties auth;

    public AdminTeamsController(TeamService service, TeamRepository teams, CurrentUser currentUser, AuthProperties auth) {
        this.service = service;
        this.teams = teams;
        this.currentUser = currentUser;
        this.auth = auth;
    }

    @GetMapping
    public List<AdminTeamView> list() {
        requireAdmin();
        return teams.findAllByOrderByNameAsc().stream().map(AdminTeamView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminTeamView create(@Valid @RequestBody CreateRequest in) {
        requireAdmin();
        return AdminTeamView.of(service.create(in.name(), in.sponsorName(), in.sponsorUrl(), in.domains()));
    }

    @PostMapping("/{id}")
    public AdminTeamView update(@PathVariable UUID id, @RequestBody UpdateRequest in) {
        requireAdmin();
        return AdminTeamView.of(service.update(id, in.name(), in.sponsorName(), in.sponsorUrl(), in.domains(), in.enabled()));
    }

    private void requireAdmin() {
        if (!auth.isAdmin(currentUser.get().getEmail())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admins only");
        }
    }
}
