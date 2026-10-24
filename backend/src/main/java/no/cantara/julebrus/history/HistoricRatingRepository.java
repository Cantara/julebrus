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

    /** Name, season and rating count only: no scores, no beers. Feeds the "find your name" list. */
    @org.springframework.data.jpa.repository.Query("select r.raterName, r.seasonYear, count(r) from HistoricRating r group by r.raterName, r.seasonYear")
    List<Object[]> countByRaterNameAndSeason();

    /** Rows matched on email, plus rows whose rater name the person has had approved (and that are not another person's email). */
    @org.springframework.data.jpa.repository.Query("""
            select r from HistoricRating r
            where lower(r.raterEmail) = lower(:email)
               or (r.raterName in :names and (r.raterEmail is null or lower(r.raterEmail) = lower(:email)))
            order by r.seasonYear desc, r.beerName asc""")
    List<HistoricRating> findMineByEmailOrApprovedNames(@org.springframework.data.repository.query.Param("email") String email,
                                                        @org.springframework.data.repository.query.Param("names") java.util.Collection<String> names);
}
