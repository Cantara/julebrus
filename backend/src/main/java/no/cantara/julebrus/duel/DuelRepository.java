package no.cantara.julebrus.duel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DuelRepository extends JpaRepository<Duel, UUID> {
    List<Duel> findByUserIdAndSessionId(UUID userId, UUID sessionId);
}
