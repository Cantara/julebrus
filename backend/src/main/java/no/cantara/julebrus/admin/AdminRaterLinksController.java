package no.cantara.julebrus.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.history.ClaimService;
import no.cantara.julebrus.history.HistoricRaterClaim;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The mapping table: every historic name on one side, every login on the other, and an
 * admin joining them by hand. Sits beside the claim queue, which handles requests from
 * the people themselves.
 */
@RestController
@RequestMapping("/api/admin/history/raters")
public class AdminRaterLinksController {

    public record Login(UUID id, String displayName, String email) {
    }

    public record RaterRow(String raterKey, List<Integer> years, long ratingCount,
                           /** linked through raters.csv; cannot be changed from here */ boolean linkedByEmail,
                           String loginEmail,
                           /** the login an admin or an approved claim tied it to, else null */ Login linkedTo) {
    }

    public record LinkRequest(@NotBlank String raterKey, @NotNull UUID userId) {
    }

    public record UnlinkRequest(@NotBlank String raterKey) {
    }

    private final CurrentUser currentUser;
    private final AuthProperties auth;
    private final ClaimService claims;
    private final UserRepository users;
    private final ConsentService consents;

    public AdminRaterLinksController(CurrentUser currentUser, AuthProperties auth, ClaimService claims, UserRepository users,
                                     ConsentService consents) {
        this.consents = consents;
        this.currentUser = currentUser;
        this.auth = auth;
        this.claims = claims;
        this.users = users;
    }

    private User admin() {
        User me = currentUser.get();
        if (!auth.isAdmin(me.getEmail())) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admins only");
        return me;
    }

    @GetMapping
    public List<RaterRow> list() {
        admin();
        Map<UUID, User> userById = users.findAll().stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<String, UUID> owners = claims.approvedOwners();
        return claims.candidates().values().stream()
                .map(c -> {
                    User owner = owners.containsKey(c.raterKey()) ? userById.get(owners.get(c.raterKey())) : null;
                    return new RaterRow(c.raterKey(), c.years(), c.ratingCount(), c.linkedByEmail(), c.loginEmail(),
                            owner == null ? null : new Login(owner.getId(), owner.getDisplayName(), owner.getEmail()));
                })
                .sorted(Comparator.comparing(r -> r.raterKey().toLowerCase()))
                .toList();
    }

    /** Everyone with active consent, for the dropdown: nobody who never consented, declined or withdrew is listed. */
    @GetMapping("/logins")
    public List<Login> logins() {
        admin();
        var active = consents.activeUserIds();
        return users.findAll().stream()
                .filter(u -> active.contains(u.getId()))
                .map(u -> new Login(u.getId(), u.getDisplayName(), u.getEmail()))
                .sorted(Comparator.comparing(l -> l.displayName().toLowerCase()))
                .toList();
    }

    @PostMapping("/link")
    public RaterRow link(@Valid @RequestBody LinkRequest in) {
        User me = admin();
        if (!consents.activeUserIds().contains(in.userId())) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_ACTIVE_CONSENT", "That person has no active consent");
        }
        claims.adminLink(me.getId(), in.userId(), in.raterKey());
        return row(in.raterKey());
    }

    @PostMapping("/unlink")
    public RaterRow unlink(@Valid @RequestBody UnlinkRequest in) {
        User me = admin();
        HistoricRaterClaim c = claims.adminUnlink(me.getId(), in.raterKey());
        return row(c.getRaterKey());
    }

    private RaterRow row(String key) {
        return list().stream().filter(r -> r.raterKey().equals(key.trim())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RATER_NOT_FOUND", "No earlier rater with that name"));
    }
}
