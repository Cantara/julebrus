package no.cantara.julebrus.feed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A cheer or an emoji one person gave one thing on the day's wall. */
@Entity
@Table(name = "feed_reactions")
public class Reaction {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_kind", nullable = false)
    private FeedService.Target targetKind;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String emoji;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Reaction() {
    }

    public Reaction(FeedService.Target targetKind, UUID targetId, UUID userId, String emoji, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.targetKind = targetKind;
        this.targetId = targetId;
        this.userId = userId;
        this.emoji = emoji;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public FeedService.Target getTargetKind() { return targetKind; }
    public UUID getTargetId() { return targetId; }
    public UUID getUserId() { return userId; }
    public String getEmoji() { return emoji; }
    public Instant getCreatedAt() { return createdAt; }
}
