package no.cantara.julebrus.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<TastingSession, UUID> {
    Optional<TastingSession> findByJoinCode(String joinCode);
}
