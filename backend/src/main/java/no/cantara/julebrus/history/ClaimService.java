package no.cantara.julebrus.history;

import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.web.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static no.cantara.julebrus.history.HistoricRaterClaim.Status.APPROVED;
import static no.cantara.julebrus.history.HistoricRaterClaim.Status.PENDING;

/**
 * Claim-your-history: a person asks to be linked to the historic rater names that are
 * theirs. Only an admin decision links anything, never a name match.
 */
@Service
public class ClaimService {

    public static final int MAX_PENDING_PER_USER = 5;

    /** Where a historic rater name appears. Counts only, deliberately no scores or beers. */
    public record RaterInfo(String raterName, List<Integer> years, long ratingCount) {
    }

    private final HistoricRatingRepository ratings;
    private final HistoricRaterClaimRepository claims;
    private final UserRepository users;

    public ClaimService(HistoricRatingRepository ratings, HistoricRaterClaimRepository claims, UserRepository users) {
        this.ratings = ratings;
        this.claims = claims;
        this.users = users;
    }

    /** Every distinct historic rater name with its years and rating count, by name. */
    @Transactional(readOnly = true)
    public Map<String, RaterInfo> raters() {
        Map<String, TreeSet<Integer>> years = new TreeMap<>();
        Map<String, Long> counts = new TreeMap<>();
        for (Object[] row : ratings.countByRaterNameAndSeason()) {
            String name = (String) row[0];
            years.computeIfAbsent(name, k -> new TreeSet<>()).add(((Number) row[1]).intValue());
            counts.merge(name, ((Number) row[2]).longValue(), Long::sum);
        }
        Map<String, RaterInfo> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        years.forEach((name, ys) -> out.put(name, new RaterInfo(name, List.copyOf(ys), counts.get(name))));
        return out;
    }

    @Transactional(readOnly = true)
    public List<HistoricRaterClaim> mine(UUID userId) {
        return claims.findByUserIdOrderByCreatedAtAsc(userId);
    }

    /** Names that are approved for someone, mapped to that someone. */
    @Transactional(readOnly = true)
    public Map<String, UUID> approvedOwners() {
        return claims.findByStatusOrderByCreatedAtAsc(APPROVED).stream()
                .collect(Collectors.toMap(HistoricRaterClaim::getRaterName, HistoricRaterClaim::getUserId));
    }

    @Transactional(readOnly = true)
    public Set<String> approvedNamesOf(UUID userId) {
        return claims.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(c -> c.getStatus() == APPROVED).map(HistoricRaterClaim::getRaterName).collect(Collectors.toSet());
    }

    @Transactional
    public HistoricRaterClaim create(UUID userId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (!raters().containsKey(name)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RATER_NOT_FOUND", "No earlier rater with that name");
        }
        if (claims.findByUserIdAndRaterName(userId, name).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "CLAIM_EXISTS", "You have already claimed that name");
        }
        if (claims.findByRaterNameAndStatus(name, APPROVED).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "RATER_TAKEN", "That name is already linked to someone");
        }
        if (claims.countByUserIdAndStatus(userId, PENDING) >= MAX_PENDING_PER_USER) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CLAIM_LIMIT",
                    "At most " + MAX_PENDING_PER_USER + " claims can wait for approval at once");
        }
        return claims.save(new HistoricRaterClaim(userId, name));
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
        return claims.findByStatusOrderByCreatedAtAsc(status);
    }

    @Transactional(readOnly = true)
    public Map<UUID, User> usersById(Set<UUID> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
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
        if (claims.findByRaterNameAndStatus(c.getRaterName(), APPROVED).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "RATER_TAKEN", "That name is already linked to someone else");
        }
        c.approve(adminId);
        try {
            // Flush now so the unique constraint on approved_name decides a race here, not at commit.
            claims.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "RATER_TAKEN", "That name is already linked to someone else");
        }
        return c;
    }
}
