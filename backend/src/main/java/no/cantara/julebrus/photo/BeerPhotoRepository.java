package no.cantara.julebrus.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Metadata only: no method here reads the image bytes (see {@link BeerPhoto}). */
public interface BeerPhotoRepository extends JpaRepository<BeerPhoto, UUID> {

    /** Row of the admin queue. */
    record AdminRow(UUID id, UUID beerId, String beerName, UUID userId, String uploaderName, String uploaderEmail,
                    BeerPhoto.Status status, Instant createdAt) {
    }

    @Query("select p from BeerPhoto p where p.id = :id and p.deletedAt is null")
    Optional<BeerPhoto> findLive(@Param("id") UUID id);

    /** Beers this person can see a cover for: an approved photo exists, or they have a pending one of their own. */
    @Query("""
            select distinct p.beerId from BeerPhoto p
            where p.deletedAt is null
              and (p.status = no.cantara.julebrus.photo.BeerPhoto.Status.APPROVED
                   or (p.userId = :me and p.status = no.cantara.julebrus.photo.BeerPhoto.Status.PENDING))
            """)
    Set<UUID> coverBeerIds(@Param("me") UUID me);

    /** The oldest approved photo of the beer, else the caller's own newest pending one. */
    @Query("""
            select p from BeerPhoto p
            where p.beerId = :beerId and p.deletedAt is null
              and (p.status = no.cantara.julebrus.photo.BeerPhoto.Status.APPROVED
                   or (p.userId = :me and p.status = no.cantara.julebrus.photo.BeerPhoto.Status.PENDING))
            order by case when p.status = no.cantara.julebrus.photo.BeerPhoto.Status.APPROVED then 0 else 1 end,
                     p.createdAt asc, p.id asc
            """)
    List<BeerPhoto> coverCandidates(@Param("beerId") UUID beerId, @Param("me") UUID me);

    List<BeerPhoto> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /** Uploads since a point in time, deleted ones included: the quota ledger. */
    long countByUserIdAndCreatedAtGreaterThanEqual(UUID userId, Instant since);

    long countByUserIdAndBeerIdAndStatusAndDeletedAtIsNull(UUID userId, UUID beerId, BeerPhoto.Status status);

    @Query("""
            select new no.cantara.julebrus.photo.BeerPhotoRepository$AdminRow(p.id, b.id, b.name, u.id, u.displayName, u.email, p.status, p.createdAt)
            from BeerPhoto p, Beer b, no.cantara.julebrus.user.User u
            where p.beerId = b.id and p.userId = u.id and p.deletedAt is null and p.status = :status
            order by p.createdAt asc
            """)
    List<AdminRow> adminQueue(@Param("status") BeerPhoto.Status status);
}
