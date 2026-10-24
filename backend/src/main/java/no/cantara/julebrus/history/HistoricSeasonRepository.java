package no.cantara.julebrus.history;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricSeasonRepository extends JpaRepository<HistoricSeason, Integer> {
}
