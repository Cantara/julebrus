package no.cantara.julebrus.duel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One pairwise answer: in this session, this person preferred winner over loser. */
@Entity
@Table(name = "duels")
public class Duel {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "winner_beer_id", nullable = false)
    private UUID winnerBeerId;

    @Column(name = "loser_beer_id", nullable = false)
    private UUID loserBeerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Duel() {
    }

    public Duel(UUID userId, UUID sessionId, UUID winnerBeerId, UUID loserBeerId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.sessionId = sessionId;
        this.winnerBeerId = winnerBeerId;
        this.loserBeerId = loserBeerId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getSessionId() { return sessionId; }
    public UUID getWinnerBeerId() { return winnerBeerId; }
    public UUID getLoserBeerId() { return loserBeerId; }
    public Instant getCreatedAt() { return createdAt; }
}
