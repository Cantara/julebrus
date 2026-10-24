package no.cantara.julebrus.photo;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The only code that writes {@code beer_photos} or reads its {@code data} column. Plain JDBC, so the
 * image bytes stay out of the JPA entity and out of every list query. The caller provides the transaction.
 */
@Component
public class PhotoStore {

    private final JdbcTemplate jdbc;

    public PhotoStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static OffsetDateTime ts(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    public void insert(UUID id, UUID beerId, UUID userId, BeerPhoto.Status status, int width, int height, byte[] jpeg,
                       Instant now, UUID decidedBy) {
        jdbc.update("""
                insert into beer_photos (id, beer_id, user_id, status, content_type, width, height, size_bytes, data,
                                         created_at, decided_by, decided_at)
                values (?, ?, ?, ?, 'image/jpeg', ?, ?, ?, ?, ?, ?, ?)
                """, id, beerId, userId, status.name(), width, height, jpeg.length, jpeg, ts(now), decidedBy,
                decidedBy == null ? null : ts(now));
    }

    /** The image bytes of a live (not deleted) photo. */
    public Optional<byte[]> data(UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("select data from beer_photos where id = ? and deleted_at is null",
                    (rs, n) -> rs.getBytes(1), id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** PENDING to APPROVED or REJECTED in one conditional update: of two parallel decisions only one changes a row. */
    public boolean decide(UUID id, BeerPhoto.Status to, UUID adminId, Instant now) {
        return jdbc.update("update beer_photos set status = ?, decided_by = ?, decided_at = ? "
                + "where id = ? and status = 'PENDING' and deleted_at is null", to.name(), adminId, ts(now), id) == 1;
    }

    /** Wipes the bytes and marks the row deleted; the empty row stays as the quota ledger. */
    public boolean delete(UUID id, Instant now) {
        return jdbc.update("update beer_photos set data = ?, size_bytes = 0, deleted_at = ? where id = ? and deleted_at is null",
                new byte[0], ts(now), id) == 1;
    }

    /** For tests and diagnostics: the bytes whatever the state of the row. */
    public List<byte[]> rawData(UUID id) {
        return jdbc.query("select data from beer_photos where id = ?", (rs, n) -> rs.getBytes(1), id);
    }
}
