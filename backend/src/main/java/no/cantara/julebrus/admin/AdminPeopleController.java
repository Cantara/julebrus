package no.cantara.julebrus.admin;

import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.history.HistoricRaterClaimRepository;
import no.cantara.julebrus.history.HistoricRatingRepository;
import no.cantara.julebrus.history.HistoricRaterClaim;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.weight.RaterWeight;
import no.cantara.julebrus.weight.RaterWeightService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Everyone who has signed in, for the organiser to map to earlier seasons and to check the weights. The
 * consent rule holds: a person without active consent to the current version is listed by name and e-mail
 * only (the users row that exists anyway), with no weight, no scores and no earlier seasons, and gets no
 * weight row; {@code consentVersion} says whether they answered an older text and will be asked again.
 */
@RestController
@RequestMapping("/api/admin/people")
public class AdminPeopleController {

    public record Person(UUID id, String displayName, String email, Instant createdAt,
                         /** active consent to the current version */ boolean consented,
                         /** the newest version with an active consent, or null if none: an older one means "asked again at next visit" */
                         String consentVersion,
                         /** all scores given in this app; null without consent */ Integer scores,
                         /** this season's weight; null without consent */ Double weight,
                         String reason, boolean overridden, boolean flagged,
                         /** historic names an admin approved for this login */ List<String> linkedNames,
                         /** seasons with earlier scores, by e-mail or by an approved name */ List<Integer> historyYears,
                         /** the team question has been answered ("later" counts); false without consent */ boolean teamsChosen,
                         /** names of the enabled teams the person plays for */ List<String> teams) {
    }

    private final CurrentUser currentUser;
    private final AuthProperties auth;
    private final UserRepository users;
    private final ConsentService consents;
    private final RaterWeightService weights;
    private final TastingRepository tastings;
    private final HistoricRaterClaimRepository claims;
    private final HistoricRatingRepository history;
    private final no.cantara.julebrus.team.TeamService teamService;

    public AdminPeopleController(CurrentUser currentUser, AuthProperties auth, UserRepository users, ConsentService consents,
                                 RaterWeightService weights, TastingRepository tastings,
                                 HistoricRaterClaimRepository claims, HistoricRatingRepository history,
                                 no.cantara.julebrus.team.TeamService teamService) {
        this.teamService = teamService;
        this.currentUser = currentUser;
        this.auth = auth;
        this.users = users;
        this.consents = consents;
        this.weights = weights;
        this.tastings = tastings;
        this.claims = claims;
        this.history = history;
    }

    @GetMapping
    public List<Person> people() {
        if (!auth.isAdmin(currentUser.get().getEmail())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only");
        var active = consents.activeUserIds();
        Map<UUID, String> versions = consents.newestActiveVersions();
        Map<UUID, Long> scoreCount = tastings.findAll().stream().collect(Collectors.groupingBy(Tasting::getUserId, Collectors.counting()));
        Map<UUID, List<String>> keysByUser = new HashMap<>();
        for (HistoricRaterClaim c : claims.findByStatusOrderByCreatedAtAsc(HistoricRaterClaim.Status.APPROVED)) {
            keysByUser.computeIfAbsent(c.getUserId(), k -> new java.util.ArrayList<>()).add(c.getRaterKey());
        }
        List<User> all = users.findAll();
        Map<String, String> teamName = teamService.enabled().stream()
                .collect(Collectors.toMap(no.cantara.julebrus.team.Team::getSlug, no.cantara.julebrus.team.Team::getName));
        Map<UUID, List<String>> teamsOf = teamService.slugsOf(all.stream().map(User::getId).toList());
        return all.stream()
                .sorted(Comparator.comparing((User u) -> u.getDisplayName() == null ? "" : u.getDisplayName().toLowerCase()))
                .map(u -> {
                    if (!active.contains(u.getId())) {
                        return new Person(u.getId(), u.getDisplayName(), u.getEmail(), u.getCreatedAt(), false,
                                versions.get(u.getId()), null, null, null, false, false, List.of(), List.of(), false, List.of());
                    }
                    RaterWeight w = weights.current(u);
                    List<String> keys = keysByUser.getOrDefault(u.getId(), List.of());
                    TreeSet<Integer> years = new TreeSet<>(history.findSeasonYearsByEmail(u.getEmail()));
                    if (!keys.isEmpty()) years.addAll(history.findSeasonYearsByKeys(keys));
                    return new Person(u.getId(), u.getDisplayName(), u.getEmail(), u.getCreatedAt(), true,
                            ConsentService.CONSENT_VERSION, scoreCount.getOrDefault(u.getId(), 0L).intValue(), w.weight(), w.getReason(),
                            w.isOverridden(), w.isFlagged(), keys, List.copyOf(years), u.getTeamsChosenAt() != null,
                            teamsOf.getOrDefault(u.getId(), List.of()).stream().map(s -> teamName.getOrDefault(s, s)).toList());
                })
                .toList();
    }
}
