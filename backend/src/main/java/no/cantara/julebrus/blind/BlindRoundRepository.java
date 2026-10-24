package no.cantara.julebrus.blind;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BlindRoundRepository extends JpaRepository<BlindRound, UUID> {

    List<BlindRound> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    List<BlindRound> findBySessionIdAndRevealedAtIsNull(UUID sessionId);

    List<BlindRound> findByRevealedAtIsNotNull();
}
