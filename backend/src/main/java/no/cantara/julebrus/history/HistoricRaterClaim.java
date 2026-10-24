package no.cantara.julebrus.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A person's request to be linked to a historic person (rater_key). Only an admin decision
 * turns it into a link; see {@code approved_key} in the V3 migration for how "one
 * key, one person" is enforced by the database.
 */
@Entity
@Table(name = "historic_rater_claims")
public class HistoricRaterClaim {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rater_key", nullable = false)
    private String raterKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "approved_key")
    private String approvedKey;

    protected HistoricRaterClaim() {
    }

    public HistoricRaterClaim(UUID userId, String raterKey) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.raterKey = raterKey;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void approve(UUID adminId) {
        this.status = Status.APPROVED;
        this.approvedKey = raterKey;
        decide(adminId);
    }

    public void reject(UUID adminId) {
        this.status = Status.REJECTED;
        decide(adminId);
    }

    /** An admin takes an approval back: the key becomes claimable again and the link disappears. */
    public void revoke(UUID adminId) {
        this.status = Status.REJECTED;
        this.approvedKey = null;
        decide(adminId);
    }

    /** A rejected claim asked for again: the old row is reused so the audit trail stays one row per (user, key). */
    public void reopen() {
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.decidedBy = null;
        this.decidedAt = null;
    }

    private void decide(UUID adminId) {
        this.decidedBy = adminId;
        this.decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getRaterKey() { return raterKey; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
}
