package no.cantara.julebrus.feed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    List<Reaction> findByTargetIdIn(Collection<UUID> targetIds);

    List<Reaction> findByTargetId(UUID targetId);

    Optional<Reaction> findByTargetIdAndUserIdAndEmoji(UUID targetId, UUID userId, String emoji);
}
