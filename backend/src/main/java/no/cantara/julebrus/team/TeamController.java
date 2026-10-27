package no.cantara.julebrus.team;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** One person on a team: display name and how many rolls this season. Never an e-mail. */
    public record Mate(UUID userId, String displayName, long tastings, java.time.Instant joinedAt, boolean me) {
    }

    /** A team's roster, most active first. */
    public record Roster(UUID id, String slug, String name, String sponsorName, String sponsorUrl, long tastings,
                         boolean mine, List<Mate> members) {
    }

    private final TeamService service;
    private final CurrentUser currentUser;
    private final no.cantara.julebrus.user.UserRepository users;
    private final no.cantara.julebrus.tasting.TastingRepository tastings;
    private final no.cantara.julebrus.consent.ConsentService consents;

    public TeamController(TeamService service, CurrentUser currentUser, no.cantara.julebrus.user.UserRepository users,
                          no.cantara.julebrus.tasting.TastingRepository tastings, no.cantara.julebrus.consent.ConsentService consents) {
        this.service = service;
        this.currentUser = currentUser;
        this.users = users;
        this.tastings = tastings;
        this.consents = consents;
    }

    /**
     * Who plays for a team, and how much they have rolled. Members only see names, as around a table, and
     * only members with active consent are listed at all, like on the wall: a person on an older consent
     * text is back on the list once they have accepted the current one.
     */
    @GetMapping("/api/teams/{slug}")
    public Roster roster(@PathVariable String slug) {
        User me = currentUser.get();
        Team t = service.bySlug(slug);
        java.util.Map<UUID, Long> rolls = new java.util.HashMap<>();
        tastings.countPerUser().forEach(c -> rolls.put(c.userId(), c.count()));
        java.util.Set<UUID> consented = consents.activeUserIds();
        List<UUID> ids = t.getMembers().stream().map(Team.Member::userId).filter(consented::contains).toList();
        java.util.Map<UUID, User> byId = users.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, java.util.function.Function.identity()));
        List<Mate> mates = t.getMembers().stream()
                .filter(m -> consented.contains(m.userId()))
                .map(m -> new Mate(m.userId(), byId.containsKey(m.userId()) ? byId.get(m.userId()).getDisplayName() : "?",
                        rolls.getOrDefault(m.userId(), 0L), m.joinedAt(), m.userId().equals(me.getId())))
                .sorted(java.util.Comparator.comparingLong(Mate::tastings).reversed().thenComparing(Mate::displayName))
                .toList();
        long total = mates.stream().mapToLong(Mate::tastings).sum();
        return new Roster(t.getId(), t.getSlug(), t.getName(), t.getSponsorName(), t.getSponsorUrl(), total,
                t.isMember(me.getId()), mates);
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
