package no.cantara.julebrus.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    /** Everything that starts after the given moment and is not cancelled, soonest first. */
    List<Event> findByStartsAtGreaterThanEqualAndCancelledAtIsNullOrderByStartsAtAsc(Instant from);
}
