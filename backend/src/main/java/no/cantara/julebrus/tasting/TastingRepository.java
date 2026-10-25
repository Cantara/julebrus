package no.cantara.julebrus.tasting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TastingRepository extends JpaRepository<Tasting, UUID> {

    record BeerCount(UUID beerId, long count) {
    }

    record UserCount(UUID userId, long count) {
    }

    @Query("select new no.cantara.julebrus.tasting.TastingRepository$UserCount(t.userId, count(t)) from Tasting t group by t.userId")
    List<UserCount> countPerUser();

    int countByUserIdAndTastedDay(UUID userId, LocalDate day);

    /** Everyone's rolls on one Oslo day, for the day's feed. */
    List<Tasting> findByTastedDay(LocalDate day);

    int countByUserIdAndTastedDayBetween(UUID userId, LocalDate from, LocalDate to);

    @Query("select distinct year(t.tastedDay) from Tasting t where t.userId = :userId")
    List<Integer> findSeasonYears(@Param("userId") UUID userId);

    boolean existsByUserIdAndBeerId(UUID userId, UUID beerId);

    List<Tasting> findBySessionId(UUID sessionId);

    List<Tasting> findByUserIdOrderByTastedAtDesc(UUID userId);

    List<Tasting> findByBeerId(UUID beerId);

    /** Days on which the user rated outside any group, i.e. alone or in a one-member session. */
    @Query("""
            select distinct t.tastedDay from Tasting t
            where t.userId = :userId
              and (t.sessionId is null
                   or (select count(m) from TastingSession s join s.members m where s.id = t.sessionId) <= 1)
            """)
    List<LocalDate> findSoloDays(@Param("userId") UUID userId);

    @Query("select new no.cantara.julebrus.tasting.TastingRepository$BeerCount(t.beerId, count(t)) from Tasting t group by t.beerId")
    List<BeerCount> countPerBeer();

}
