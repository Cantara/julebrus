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
import static no.cantara.julebrus.history.HistoricRaterClaim.Status.WITHDRAWN;

/**
 * Claim-your-history: a person asks to be linked to the historic rater names that are
 * theirs. Only an admin decision links anything, never a name match.
 */
@Service
public class ClaimService {

    public static final int MAX_PENDING_PER_USER = 5;
    /** Every claim created or reopened counts, whatever happens to it afterwards. */
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
    private final ClaimEventRepository events;

    public ClaimService(HistoricRatingRepository ratings, HistoricRaterClaimRepository claims, UserRepository users,
                        ClaimEventRepository events) {
        this.ratings = ratings;
        this.claims = claims;
        this.users = users;
        this.events = events;
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

    /** The person's claims as they see them: a withdrawn claim is gone from their list (the row is kept). */
    @Transactional(readOnly = true)
    public List<HistoricRaterClaim> mine(UUID userId) {
        return claims.findByUserIdOrderByCreatedAtAsc(userId).stream().filter(c -> c.getStatus() != WITHDRAWN).toList();
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

    /**
     * Creates or reopens a claim. Everything runs under a row lock on the person's users row, so
     * two requests from one person cannot both pass the pending or daily count (check, then insert).
     */
    @Transactional
    public HistoricRaterClaim create(UUID userId, String userEmail, String rawKey) {
        users.lockById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unknown user"));
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
        if (existing.isPresent()) {
            switch (existing.get().getStatus()) {
                case PENDING, APPROVED ->
                        throw new ApiException(HttpStatus.CONFLICT, "CLAIM_EXISTS", "You have already claimed that name");
                case REVOKED ->
                        throw new ApiException(HttpStatus.CONFLICT, "CLAIM_REVOKED",
                                "An administrator took this link back; ask the organisers");
                default -> { /* REJECTED or WITHDRAWN: asking again is allowed */ }
            }
        }
        if (claims.findByRaterKeyAndStatus(key, APPROVED).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "RATER_TAKEN", "That name is already linked to someone");
        }
        if (claims.countByUserIdAndStatus(userId, PENDING) >= MAX_PENDING_PER_USER) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CLAIM_LIMIT",
                    "You can have at most " + MAX_PENDING_PER_USER + " claims waiting for approval");
        }
        // The daily limit counts events, not rows, so withdrawing and asking again does not reset it.
        if (events.countByActorUserIdAndKindInAndAtAfter(userId, List.of(ClaimEvent.Kind.CREATE, ClaimEvent.Kind.REOPEN),
                Instant.now().minus(24, ChronoUnit.HOURS)) >= MAX_CLAIMS_PER_DAY) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CLAIM_DAILY_LIMIT",
                    "You can make at most " + MAX_CLAIMS_PER_DAY + " claims in 24 hours");
        }
        HistoricRaterClaim claim;
        if (existing.isPresent()) {
            claim = existing.get();
            claim.reopen();
            claim = claims.saveAndFlush(claim);
            events.save(new ClaimEvent(claim.getId(), ClaimEvent.Kind.REOPEN, userId));
        } else {
            claim = claims.saveAndFlush(new HistoricRaterClaim(userId, key));
            events.save(new ClaimEvent(claim.getId(), ClaimEvent.Kind.CREATE, userId));
        }
        return claim;
    }

    /** A person withdraws their own pending claim. The row stays (it counts towards the daily limit). */
    @Transactional
    public void withdraw(UUID userId, UUID claimId) {
        users.lockById(userId);
        HistoricRaterClaim c = claims.findById(claimId).filter(x -> x.getUserId().equals(userId))
                .filter(x -> x.getStatus() != WITHDRAWN)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "No such claim"));
        if (c.getStatus() != PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_NOT_PENDING", "Only a pending claim can be withdrawn");
        }
        c.withdraw();
        claims.saveAndFlush(c);
        events.save(new ClaimEvent(c.getId(), ClaimEvent.Kind.WITHDRAW, userId));
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

    /** An admin takes back an approval. Only APPROVED claims can be revoked, and a revoked claim stays revoked. */
    @Transactional
    public HistoricRaterClaim revoke(UUID adminId, UUID claimId) {
        HistoricRaterClaim c = claims.findById(claimId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLAIM_NOT_FOUND", "No such claim"));
        if (c.getStatus() != APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_NOT_APPROVED", "Only an approved claim can be revoked");
        }
        c.revoke(adminId);
        claims.saveAndFlush(c);
        events.save(new ClaimEvent(c.getId(), ClaimEvent.Kind.REVOKE, adminId));
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
            claims.saveAndFlush(c);
            events.save(new ClaimEvent(c.getId(), ClaimEvent.Kind.REJECT, adminId));
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
        events.save(new ClaimEvent(c.getId(), ClaimEvent.Kind.APPROVE, adminId));
        return c;
    }
}
