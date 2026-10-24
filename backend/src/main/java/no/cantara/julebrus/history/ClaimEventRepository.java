package no.cantara.julebrus.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ClaimEventRepository extends JpaRepository<ClaimEvent, UUID> {
    List<ClaimEvent> findByClaimIdOrderByAtAsc(UUID claimId);

    /** How many claims the person made or reopened since then: the base of the daily limit. */
    long countByActorUserIdAndKindInAndAtAfter(UUID actorUserId, Collection<ClaimEvent.Kind> kinds, Instant since);
}
