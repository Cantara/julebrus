package no.cantara.julebrus.team;

import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TeamService {

    private final TeamRepository teams;
    private final UserRepository users;
    private final Clock clock;

    public TeamService(TeamRepository teams, UserRepository users, Clock clock) {
        this.teams = teams;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Team> enabled() {
        return teams.findByEnabledTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Team bySlug(String slug) {
        return teams.findBySlug(slug == null ? "" : slug.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(Team::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", "No such team"));
    }

    /** The enabled teams this person plays for, in name order. */
    @Transactional(readOnly = true)
    public List<Team> of(UUID userId) {
        return teams.findByEnabledTrueOrderByNameAsc().stream().filter(t -> t.isMember(userId)).toList();
    }

    /** userId -> slugs of the enabled teams they play for, for the given people. */
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> slugsOf(Collection<UUID> userIds) {
        Set<UUID> wanted = new HashSet<>(userIds);
        Map<UUID, List<String>> out = new HashMap<>();
        for (Team t : teams.findByEnabledTrueOrderByNameAsc()) {
            for (Team.Member m : t.getMembers()) {
                if (wanted.contains(m.userId())) out.computeIfAbsent(m.userId(), k -> new java.util.ArrayList<>()).add(t.getSlug());
            }
        }
        return out;
    }

    /**
     * Sets the person's teams to exactly these, among the enabled ones, and records that
     * the choice was made. An empty list is a valid choice ("later"): the page stops nagging.
     */
    @Transactional
    public List<Team> choose(UUID userId, Collection<UUID> teamIds) {
        Set<UUID> chosen = new HashSet<>(teamIds == null ? List.of() : teamIds);
        List<Team> enabled = teams.findByEnabledTrueOrderByNameAsc();
        Set<UUID> known = new HashSet<>();
        enabled.forEach(t -> known.add(t.getId()));
        if (!known.containsAll(chosen)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TEAM_NOT_FOUND", "Pick among the enabled teams");
        }
        for (Team t : enabled) {
            if (chosen.contains(t.getId())) t.join(userId, clock.instant());
            else t.leave(userId);
        }
        User u = users.findById(userId).orElseThrow();
        u.setTeamsChosenAt(clock.instant());
        return enabled.stream().filter(t -> chosen.contains(t.getId())).toList();
    }

    @Transactional
    public Team create(String name, String sponsorName, String sponsorUrl, String domains) {
        String slug = Team.slugOf(name);
        if (name == null || name.isBlank() || slug.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION", "A team needs a name");
        }
        if (teams.findBySlug(slug).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "TEAM_EXISTS", "There is already a team called " + name);
        }
        Team t = new Team(slug, name.trim(), clock.instant());
        t.setSponsorName(blankToNull(sponsorName));
        t.setSponsorUrl(blankToNull(sponsorUrl));
        t.setDomains(blankToNull(domains));
        return teams.save(t);
    }

    @Transactional
    public Team update(UUID id, String name, String sponsorName, String sponsorUrl, String domains, Boolean enabled) {
        Team t = teams.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", "No such team"));
        if (name != null && !name.isBlank()) t.setName(name.trim());
        if (sponsorName != null) t.setSponsorName(blankToNull(sponsorName));
        if (sponsorUrl != null) t.setSponsorUrl(blankToNull(sponsorUrl));
        if (domains != null) t.setDomains(blankToNull(domains));
        if (enabled != null) t.setEnabled(enabled);
        return t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
