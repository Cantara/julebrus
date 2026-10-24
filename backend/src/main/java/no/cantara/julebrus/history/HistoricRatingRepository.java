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

    /** Seasons of the people (keys) whose claims an admin approved for someone. */
    @org.springframework.data.jpa.repository.Query("select distinct r.seasonYear from HistoricRating r where coalesce(r.raterKey, r.raterName) in :keys")
    List<Integer> findSeasonYearsByKeys(@org.springframework.data.repository.query.Param("keys") java.util.Collection<String> keys);

    @org.springframework.data.jpa.repository.Query("select count(r) from HistoricRating r where r.seasonYear = :year and coalesce(r.raterKey, r.raterName) in :keys")
    int countBySeasonYearAndKeys(@org.springframework.data.repository.query.Param("year") int year,
                                 @org.springframework.data.repository.query.Param("keys") java.util.Collection<String> keys);

    /**
     * Per person (key) and season: the rating count and one rater_email if any row has one. Only
     * feeds the claim-candidate list, so no scores, beers, breweries or notes are selected.
     * A row without a key counts under its source name, as the main history views do.
     */
    @org.springframework.data.jpa.repository.Query("""
            select coalesce(r.raterKey, r.raterName), r.seasonYear, count(r), min(r.raterEmail)
            from HistoricRating r group by coalesce(r.raterKey, r.raterName), r.seasonYear""")
    List<Object[]> countByKeyAndSeason();

    /** Rows matched on email, plus every row of a person (key) whose claim an admin approved. */
    @org.springframework.data.jpa.repository.Query("""
            select r from HistoricRating r
            where lower(r.raterEmail) = lower(:email) or coalesce(r.raterKey, r.raterName) in :keys
            order by r.seasonYear desc, r.beerName asc""")
    List<HistoricRating> findMineByEmailOrApprovedKeys(@org.springframework.data.repository.query.Param("email") String email,
                                                       @org.springframework.data.repository.query.Param("keys") java.util.Collection<String> keys);
}
