package no.cantara.julebrus.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.history.ClaimService;
import no.cantara.julebrus.history.HistoricRaterClaim;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin side of claim-your-history. An admin sees who asks for which old name and
 * decides. Nothing here is automatic, not even for an exact name match.
 */
@RestController
@RequestMapping("/api/admin/history/claims")
public class AdminClaimsController {

    public record Decision(@NotNull Choice decision) {
    }

    public enum Choice { APPROVE, REJECT }

    public record AdminClaimView(UUID id, HistoricRaterClaim.Status status, String raterKey, List<Integer> years,
                                 long ratingCount, /** raters.csv ties this person to another login: approving is refused */ boolean linkedByEmail,
                                 UUID userId, String displayName, String email, Instant createdAt,
                                 Instant decidedAt) {
    }

    private final CurrentUser currentUser;
    private final AuthProperties auth;
    private final ClaimService claims;

    public AdminClaimsController(CurrentUser currentUser, AuthProperties auth, ClaimService claims) {
        this.currentUser = currentUser;
        this.auth = auth;
        this.claims = claims;
    }

    private User requireAdmin() {
        User u = currentUser.get();
        if (!auth.isAdmin(u.getEmail())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admins only");
        }
        return u;
    }

    @GetMapping
    public List<AdminClaimView> list(@RequestParam(defaultValue = "PENDING") String status) {
        requireAdmin();
        HistoricRaterClaim.Status s;
        try {
            s = HistoricRaterClaim.Status.valueOf(status.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "status must be PENDING, APPROVED, REJECTED, REVOKED or WITHDRAWN");
        }
        List<HistoricRaterClaim> list = claims.byStatus(s);
        return views(list);
    }

    @PostMapping("/{id}/decision")
    public AdminClaimView decide(@PathVariable UUID id, @Valid @RequestBody Decision in) {
        User admin = requireAdmin();
        HistoricRaterClaim c = claims.decide(admin.getId(), id, in.decision() == Choice.APPROVE);
        return views(List.of(c)).get(0);
    }

    /** Takes an approval back: the ratings leave the person's history and the key can be claimed again. */
    @PostMapping("/{id}/revoke")
    public AdminClaimView revoke(@PathVariable UUID id) {
        User admin = requireAdmin();
        return views(List.of(claims.revoke(admin.getId(), id))).get(0);
    }

    private List<AdminClaimView> views(List<HistoricRaterClaim> list) {
        Map<UUID, User> byId = claims.usersById(list.stream().map(HistoricRaterClaim::getUserId).collect(Collectors.toSet()));
        Map<String, ClaimService.Candidate> raters = claims.candidates();
        return list.stream().map(c -> {
            User u = byId.get(c.getUserId());
            ClaimService.Candidate r = raters.get(c.getRaterKey());
            return new AdminClaimView(c.getId(), c.getStatus(), c.getRaterKey(),
                    r == null ? List.of() : r.years(), r == null ? 0 : r.ratingCount(),
                    r != null && r.linkedByEmail() && (u == null || !r.loginEmail().equalsIgnoreCase(u.getEmail())), c.getUserId(),
                    u == null ? null : u.getDisplayName(), u == null ? null : u.getEmail(), c.getCreatedAt(), c.getDecidedAt());
        }).toList();
    }
}
