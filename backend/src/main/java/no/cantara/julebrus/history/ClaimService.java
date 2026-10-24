package no.cantara.julebrus.history;

import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static no.cantara.julebrus.history.HistoricRaterClaim.Status.APPROVED;
import static no.cantara.julebrus.history.HistoricRaterClaim.Status.PENDING;
import static no.cantara.julebrus.history.HistoricRaterClaim.Status.REJECTED;

/**
 * Claim-your-history: a person asks to be linked to the historic rater names that are
 * theirs. Only an admin decision links anything, never a name match.
 */
@Service
public class ClaimService {

    public static final int MAX_PENDING_PER_USER = 5;
    /** Every claim created or reopened counts, whatever its status, so a rejection is not a free retry. */
    public static final int MAX_CLAIMS_PER_DAY = 10;

    /**
     * One historic person (key). Counts only, deliberately no scores or beers. {@code loginEmail}
     * is the lower-cased email raters.csv attached to the person, or null; it is used on the
     * server and never serialised.
     */
    public record Candidate(String raterKey, List<Integer> years, long ratingCount, String loginEmail) {
        public boolean linkedByEmail() {
            return loginEmail != null;
        }
    }

    private final HistoricRatingRepository ratings;
    private final HistoricRaterClaimRepository claims;
    private final UserRepository users;

    public ClaimService(HistoricRatingRepository ratings, HistoricRaterClaimRepository claims, UserRepository users) {
        this.ratings = ratings;
        this.claims = claims;
        this.users = users;
    }

    /** Every historic person (key) with years and rating count. */
    @Transactional(readOnly = true)
    public Map<String, Candidate> candidates() {
        Map<String, TreeSet<Integer>> years = new TreeMap<>();
        Map<String, Long> counts = new TreeMap<>();
        Map<String, String> emails = new TreeMap<>();
        for (Object[] row : ratings.countByKeyAndSeason()) {
            String key = (String) row[0];
            years.computeIfAbsent(key, k -> new TreeSet<>()).add(((Number) row[1]).intValue());
            counts.merge(key, ((Number) row[2]).longValue(), Long::sum);
            if (row[3] != null) emails.putIfAbsent(key, ((String) row[3]).toLowerCase(Locale.ROOT));
        }
        Map<String, Candidate> out = new TreeMap<>();
        years.forEach((key, ys) -> out.put(key, new Candidate(key, List.copyOf(ys), counts.get(key), emails.get(key))));
        return out;
    }

    @Transactional(readOnly = true)
    public List<HistoricRaterClaim> mine(UUID userId) {
        return claims.findByUserIdOrderByCreatedAtAsc(userId);
    }

    /** Keys that are approved for someone, mapped to that someone. */
    @Transactional(readOnly = true)
    public Map<String, UUID> approvedOwners() {
        return claims.findByStatusOrderByCreatedAtAsc(APPROVED).stream()
                .collect(Collectors.toMap(HistoricRaterClaim::getRaterKey, HistoricRaterClaim::getUserId));
    }

    @Transactional(readOnly = true)
    public Set<String> approvedKeysOf(UUID userId) {
        return claims.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(c -> c.getStatus() == APPROVED).map(HistoricRaterClaim::getRaterKey).collect(Collectors.toSet());
    }

    @Transactional
    public HistoricRaterClaim create(UUID userId, String userEmail, String rawKey) {
        String key = rawKey == null ? "" : rawKey.trim();
        Candidate c = candidates().get(key);
        if (c == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RATER_NOT_FOUND", "No earlier rater with that name");
        }
        if (c.linkedByEmail()) {
            // raters.csv already ties this person to a Google login; the email match covers it.
            throw new ApiException(HttpStatus.CONFLICT, "RATER_LINKED",
                    c.loginEmail().equalsIgnoreCase(userEmail) ? "Already linked to your login, no claim needed"
                            : "That person is already linked to a login");
        }
        var existing = claims.findByUserIdAndRaterKey(userId, key);
        if (existing.isPresent() && existing.get().getStatus() != REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_EXISTS", "You have already claimed that name");
        }
        if (claims.findByRaterKeyAndStatus(key, APPROVED).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "RATER_TAKEN", "That name is already linked to someone");
        }
        if (claims.countByUserIdAndStatus(userId, PENDING) >= MAX_PENDING_PER_USER) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CLAIM_LIMIT",
                    "At most " + MAX_PENDING_PER_USER + " claims can wait for approval at once");
        }
        if (claims.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(24, ChronoUnit.HOURS)) >= MAX_CLAIMS_PER_DAY) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CLAIM_LIMIT",
                    "At most " + MAX_CLAIMS_PER_DAY + " claims can be made in 24 hours");
        }
        if (existing.isPresent()) {
            // Asking again after a rejection reopens the old row (kept for audit).
            HistoricRaterClaim again = existing.get();
            again.reopen();
            return claims.save(again);
        }
        return claims.save(new HistoricRaterClaim(userId, key));
    }

    /** A person withdraws their own pending claim. Someone else's claim looks like it does not exist. */
    @Transactional
    public void withdraw(UUID userId, UUID claimId) {
        HistoricRaterClaim c = claims.findById(claimId).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "No such claim"));
        if (c.getStatus() != PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_NOT_PENDING", "Only a pending claim can be withdrawn");
        }
        claims.delete(c);
    }

    @Transactional(readOnly = true)
    public List<HistoricRaterClaim> byStatus(HistoricRaterClaim.Status status) {
        // Grouped by key so competing claimants for one person sit next to each other.
        return claims.findByStatusOrderByRaterKeyAscCreatedAtAsc(status);
    }

    @Transactional(readOnly = true)
    public Map<UUID, User> usersById(Set<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    /** An admin takes back an approval. Only APPROVED claims can be revoked. */
    @Transactional
    public HistoricRaterClaim revoke(UUID adminId, UUID claimId) {
        HistoricRaterClaim c = claims.findById(claimId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "No such claim"));
        if (c.getStatus() != APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_NOT_APPROVED", "Only an approved claim can be revoked");
        }
        c.revoke(adminId);
        return c;
    }

    @Transactional
    public HistoricRaterClaim decide(UUID adminId, UUID claimId, boolean approve) {
        HistoricRaterClaim c = claims.findById(claimId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "No such claim"));
        if (c.getStatus() != PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_NOT_PENDING", "That claim is already decided");
        }
        if (!approve) {
            c.reject(adminId);
            return c;
        }
        if (claims.findByRaterKeyAndStatus(c.getRaterKey(), APPROVED).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "RATER_TAKEN", "That name is already linked to someone else");
        }
        c.approve(adminId);
        try {
            // Flush now so the unique constraint on approved_key decides a race here, not at commit.
            claims.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "RATER_TAKEN", "That name is already linked to someone else");
        }
        return c;
    }
}
