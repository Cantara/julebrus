package no.cantara.julebrus.beer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BeerRepository extends JpaRepository<Beer, UUID> {

    @Query("""
            select b from Beer b
            where :q is null or :q = ''
               or lower(b.name) like lower(concat('%', :q, '%'))
               or lower(coalesce(b.brewery, '')) like lower(concat('%', :q, '%'))
            order by b.name
            """)
    List<Beer> search(@Param("q") String q);
}
