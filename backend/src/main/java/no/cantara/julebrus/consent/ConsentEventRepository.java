package no.cantara.julebrus.consent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsentEventRepository extends JpaRepository<ConsentEvent, UUID> {
    List<ConsentEvent> findByUserIdOrderByAtAsc(UUID userId);
}
