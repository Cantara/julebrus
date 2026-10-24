package no.cantara.julebrus.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<TastingSession, UUID> {
    Optional<TastingSession> findByJoinCode(String joinCode);

    /** Every table this person sits at, newest first. */
    @org.springframework.data.jpa.repository.Query("select s from TastingSession s join s.members m where m.userId = :userId order by s.createdAt desc")
    java.util.List<TastingSession> findByMember(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
