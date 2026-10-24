package no.cantara.julebrus.history;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Claim your history: find the old rater names that are yours and ask an admin to link them. */
@RestController
@RequestMapping("/api/history")
public class ClaimController {

    public record MyClaim(UUID id, HistoricRaterClaim.Status status) {
    }

    /**
     * One historic rater name. Only the name, the years and a count: never scores or
     * beers, because anyone logged in can read this list.
     */
    public record RaterView(String raterName, List<Integer> years, long ratingCount,
                            /** false when the name is approved for another person */ boolean available,
                            /** looks like the caller's first name */ boolean suggested,
                            MyClaim myClaim) {
    }

    public record ClaimView(UUID id, String raterName, HistoricRaterClaim.Status status, Instant createdAt,
                            Instant decidedAt) {
        static ClaimView of(HistoricRaterClaim c) {
            return new ClaimView(c.getId(), c.getRaterName(), c.getStatus(), c.getCreatedAt(), c.getDecidedAt());
        }
    }

    public record ClaimRequest(@NotBlank @Size(max = 255) String raterName) {
    }

    private final ClaimService claims;
    private final CurrentUser currentUser;

    public ClaimController(ClaimService claims, CurrentUser currentUser) {
        this.claims = claims;
        this.currentUser = currentUser;
    }

    /** Suggested names first (exact first-name match before prefix match), then alphabetical. */
    @GetMapping("/raters")
    public List<RaterView> raters() {
        User me = currentUser.get();
        Map<String, UUID> owners = claims.approvedOwners();
        Map<String, HistoricRaterClaim> mine = claims.mine(me.getId()).stream()
                .collect(Collectors.toMap(HistoricRaterClaim::getRaterName, Function.identity()));
        return claims.raters().values().stream()
                .sorted(Comparator.<ClaimService.RaterInfo>comparingInt(r -> RaterNameSuggester.rank(me.getDisplayName(), r.raterName()))
                        .thenComparing(ClaimService.RaterInfo::raterName, String.CASE_INSENSITIVE_ORDER))
                .map(r -> {
                    UUID owner = owners.get(r.raterName());
                    HistoricRaterClaim c = mine.get(r.raterName());
                    return new RaterView(r.raterName(), r.years(), r.ratingCount(),
                            owner == null || owner.equals(me.getId()),
                            RaterNameSuggester.suggested(me.getDisplayName(), r.raterName()),
                            c == null ? null : new MyClaim(c.getId(), c.getStatus()));
                }).toList();
    }

    @PostMapping("/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimView create(@Valid @RequestBody ClaimRequest in) {
        return ClaimView.of(claims.create(currentUser.get().getId(), in.raterName()));
    }

    @GetMapping("/claims/me")
    public List<ClaimView> mine() {
        return claims.mine(currentUser.get().getId()).stream().map(ClaimView::of).toList();
    }

    @DeleteMapping("/claims/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID id) {
        claims.withdraw(currentUser.get().getId(), id);
    }
}
