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

/** Claim your history: find the historic people (rater keys) that are you and ask an admin to link them. */
@RestController
@RequestMapping("/api/history")
public class ClaimController {

    public record MyClaim(UUID id, HistoricRaterClaim.Status status) {
    }

    /**
     * One historic person (key). Only the key, the years and a count: never scores, beers,
     * breweries, notes or emails, because anyone logged in can read this list.
     */
    public record CandidateView(String raterKey, List<Integer> years, long ratingCount,
                                /** false when approved for another person or linked to a login */ boolean available,
                                /** raters.csv already ties this person to a login: no claim possible or needed */ boolean linkedByEmail,
                                /** the linked login is the caller's own */ boolean linkedToYou,
                                /** looks like the caller's first name */ boolean suggested,
                                MyClaim myClaim) {
    }

    public record ClaimView(UUID id, String raterKey, HistoricRaterClaim.Status status, Instant createdAt,
                            Instant decidedAt) {
        static ClaimView of(HistoricRaterClaim c) {
            return new ClaimView(c.getId(), c.getRaterKey(), c.getStatus(), c.getCreatedAt(), c.getDecidedAt());
        }
    }

    public record ClaimRequest(@NotBlank @Size(max = 255) String raterKey) {
    }

    private final ClaimService claims;
    private final CurrentUser currentUser;

    public ClaimController(ClaimService claims, CurrentUser currentUser) {
        this.claims = claims;
        this.currentUser = currentUser;
    }

    /** Suggested people first (exact first-name match before prefix match), then alphabetical. */
    @GetMapping("/claim-candidates")
    public List<CandidateView> candidates() {
        User me = currentUser.get();
        Map<String, UUID> owners = claims.approvedOwners();
        Map<String, HistoricRaterClaim> mine = claims.mine(me.getId()).stream()
                .collect(Collectors.toMap(HistoricRaterClaim::getRaterKey, Function.identity()));
        return claims.candidates().values().stream()
                .sorted(Comparator.<ClaimService.Candidate>comparingInt(r -> RaterNameSuggester.rank(me.getDisplayName(), r.raterKey()))
                        .thenComparing(ClaimService.Candidate::raterKey, String.CASE_INSENSITIVE_ORDER))
                .map(r -> {
                    UUID owner = owners.get(r.raterKey());
                    HistoricRaterClaim c = mine.get(r.raterKey());
                    boolean toYou = r.linkedByEmail() && r.loginEmail().equalsIgnoreCase(me.getEmail());
                    return new CandidateView(r.raterKey(), r.years(), r.ratingCount(),
                            !r.linkedByEmail() && (owner == null || owner.equals(me.getId())),
                            r.linkedByEmail(), toYou,
                            !r.linkedByEmail() && RaterNameSuggester.suggested(me.getDisplayName(), r.raterKey()),
                            c == null ? null : new MyClaim(c.getId(), c.getStatus()));
                }).toList();
    }

    @PostMapping("/claims")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimView create(@Valid @RequestBody ClaimRequest in) {
        User me = currentUser.get();
        return ClaimView.of(claims.create(me.getId(), me.getEmail(), in.raterKey()));
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
