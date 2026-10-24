package no.cantara.julebrus.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HistoricRaterClaimRepository extends JpaRepository<HistoricRaterClaim, UUID> {
    List<HistoricRaterClaim> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<HistoricRaterClaim> findByStatusOrderByCreatedAtAsc(HistoricRaterClaim.Status status);

    Optional<HistoricRaterClaim> findByUserIdAndRaterKey(UUID userId, String raterName);

    Optional<HistoricRaterClaim> findByRaterKeyAndStatus(String raterKey, HistoricRaterClaim.Status status);

    long countByUserIdAndStatus(UUID userId, HistoricRaterClaim.Status status);
}
