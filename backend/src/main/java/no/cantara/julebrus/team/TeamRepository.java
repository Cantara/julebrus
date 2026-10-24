package no.cantara.julebrus.team;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findBySlug(String slug);

    List<Team> findByEnabledTrueOrderByNameAsc();

    List<Team> findAllByOrderByNameAsc();
}
