package no.cantara.julebrus.advent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdventDoorRepository extends JpaRepository<AdventDoor, UUID> {

    List<AdventDoor> findByUserIdOrderByDoorDayAsc(UUID userId);

    Optional<AdventDoor> findByUserIdAndDoorDay(UUID userId, LocalDate doorDay);

    boolean existsByUserIdAndDoorDay(UUID userId, LocalDate doorDay);

    List<AdventDoor> findByDoorDay(LocalDate doorDay);

    long countByUserIdAndKeyUsedTrue(UUID userId);
}
