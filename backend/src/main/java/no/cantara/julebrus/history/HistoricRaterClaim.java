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
 * A person's request to be linked to a historic rater name. Only an admin decision
 * turns it into a link; see {@code approved_name} in the V2 migration for how "one
 * name, one person" is enforced by the database.
 */
@Entity
@Table(name = "historic_rater_claims")
public class HistoricRaterClaim {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "rater_name", nullable = false)
    private String raterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "approved_name")
    private String approvedName;

    protected HistoricRaterClaim() {
    }

    public HistoricRaterClaim(UUID userId, String raterName) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.raterName = raterName;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    public void approve(UUID adminId) {
        this.status = Status.APPROVED;
        this.approvedName = raterName;
        decide(adminId);
    }

    public void reject(UUID adminId) {
        this.status = Status.REJECTED;
        decide(adminId);
    }

    private void decide(UUID adminId) {
        this.decidedBy = adminId;
        this.decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getRaterName() { return raterName; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public Instant getDecidedAt() { return decidedAt; }
}
