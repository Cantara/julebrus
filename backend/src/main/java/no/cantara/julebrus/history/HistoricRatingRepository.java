package no.cantara.julebrus.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoricRatingRepository extends JpaRepository<HistoricRating, UUID> {
    List<HistoricRating> findByRaterEmailIgnoreCaseOrderBySeasonYearDescBeerNameAsc(String email);

    List<HistoricRating> findByBeerNameIgnoreCaseOrderBySeasonYearDesc(String beerName);

    List<HistoricRating> findBySeasonYearOrderByBeerNameAsc(int year);

    boolean existsBySeasonYear(int year);

    record RaterSummary(String raterKey, String raterEmail, long ratings, int firstYear, int lastYear) {
    }

    @org.springframework.data.jpa.repository.Query("""
            select new no.cantara.julebrus.history.HistoricRatingRepository$RaterSummary(
                r.raterKey, min(r.raterEmail), count(r), min(r.seasonYear), max(r.seasonYear))
            from HistoricRating r group by r.raterKey order by count(r) desc
            """)
    List<RaterSummary> summariseRaters();

    int countBySeasonYearAndRaterEmailIgnoreCase(int year, String email);

    @org.springframework.data.jpa.repository.Query("select distinct r.seasonYear from HistoricRating r where lower(r.raterEmail) = lower(:email)")
    List<Integer> findSeasonYearsByEmail(@org.springframework.data.repository.query.Param("email") String email);
}
