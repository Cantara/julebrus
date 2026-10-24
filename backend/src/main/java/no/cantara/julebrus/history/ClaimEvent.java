package no.cantara.julebrus.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Append-only: one row per thing that happened to a claim, and who did it. */
@Entity
@Table(name = "claim_events")
public class ClaimEvent {

    public enum Kind { CREATE, REOPEN, WITHDRAW, APPROVE, REJECT, REVOKE }

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false)
    private Kind kind;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "at", nullable = false)
    private Instant at;

    protected ClaimEvent() {
    }

    public ClaimEvent(UUID claimId, Kind kind, UUID actorUserId) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.kind = kind;
        this.actorUserId = actorUserId;
        this.at = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public Kind getKind() { return kind; }
    public UUID getActorUserId() { return actorUserId; }
    public Instant getAt() { return at; }
}
