package no.cantara.julebrus.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata of a label photo. The {@code data} column of {@code beer_photos} is deliberately not mapped:
 * nothing that goes through JPA can load image bytes. {@link PhotoStore} writes the row and reads the
 * bytes with plain JDBC, and makes every change of the row, so this class is read-only.
 */
@Entity
@Table(name = "beer_photos")
public class BeerPhoto {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    private UUID id;

    @Column(name = "beer_id", nullable = false)
    private UUID beerId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    private int width;
    private int height;

    @Column(name = "size_bytes")
    private int sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected BeerPhoto() {
    }

    public UUID getId() { return id; }
    public UUID getBeerId() { return beerId; }
    public UUID getUserId() { return userId; }
    public Status getStatus() { return status; }
    public String getContentType() { return contentType; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
