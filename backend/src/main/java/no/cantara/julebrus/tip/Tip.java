package no.cantara.julebrus.tip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One person's tip for the podium: gold, silver, bronze. */
@Entity
@Table(name = "tips")
public class Tip {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "first_beer_id", nullable = false)
    private UUID firstBeerId;

    @Column(name = "second_beer_id", nullable = false)
    private UUID secondBeerId;

    @Column(name = "third_beer_id", nullable = false)
    private UUID thirdBeerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tip() {
    }

    public Tip(UUID userId, UUID first, UUID second, UUID third, Instant now) {
        this.userId = userId;
        this.createdAt = now;
        change(first, second, third, now);
    }

    public void change(UUID first, UUID second, UUID third, Instant now) {
        this.firstBeerId = first;
        this.secondBeerId = second;
        this.thirdBeerId = third;
        this.updatedAt = now;
    }

    /** Gold, silver, bronze, in that order. */
    public List<UUID> podium() {
        return List.of(firstBeerId, secondBeerId, thirdBeerId);
    }

    public UUID getUserId() { return userId; }
    public UUID getFirstBeerId() { return firstBeerId; }
    public UUID getSecondBeerId() { return secondBeerId; }
    public UUID getThirdBeerId() { return thirdBeerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
