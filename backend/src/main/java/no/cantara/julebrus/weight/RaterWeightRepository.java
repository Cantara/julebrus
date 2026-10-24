package no.cantara.julebrus.weight;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaterWeightRepository extends JpaRepository<RaterWeight, UUID> {
    Optional<RaterWeight> findBySeasonYearAndEmailIgnoreCase(int seasonYear, String email);

    List<RaterWeight> findBySeasonYear(int seasonYear);
}
