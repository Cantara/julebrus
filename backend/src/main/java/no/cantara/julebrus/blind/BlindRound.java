package no.cantara.julebrus.blind;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One blind pour at a table: the host's secret answer, the beers it could be, and
 * who guessed what. The answer is only served once {@link #isRevealed()}.
 */
@Entity
@Table(name = "blind_rounds")
public class BlindRound {

    /** Fewer and it is a coin toss; more and nobody can keep the glasses apart. */
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 6;

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "answer_beer_id", nullable = false)
    private UUID answerBeerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revealed_at")
    private Instant revealedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "blind_round_options", joinColumns = @JoinColumn(name = "round_id"))
    @OrderColumn(name = "position")
    @Column(name = "beer_id", nullable = false)
    private List<UUID> options = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "blind_guesses", joinColumns = @JoinColumn(name = "round_id"))
    private List<Guess> guesses = new ArrayList<>();

    protected BlindRound() {
    }

    public BlindRound(UUID sessionId, UUID hostId, UUID answerBeerId, List<UUID> options, Instant now) {
        this.id = UUID.randomUUID();
        this.sessionId = sessionId;
        this.hostId = hostId;
        this.answerBeerId = answerBeerId;
        this.options = new ArrayList<>(options);
        this.createdAt = now;
    }

    public boolean isRevealed() {
        return revealedAt != null;
    }

    public void reveal(Instant now) {
        if (revealedAt == null) revealedAt = now;
    }

    public boolean isOption(UUID beerId) {
        return options.contains(beerId);
    }

    public Optional<Guess> guessOf(UUID userId) {
        return guesses.stream().filter(g -> g.userId().equals(userId)).findFirst();
    }

    public void addGuess(UUID userId, UUID beerId, Instant now) {
        guesses.add(new Guess(userId, beerId, now));
    }

    public boolean isCorrect(Guess g) {
        return g.beerId().equals(answerBeerId);
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getHostId() { return hostId; }
    public UUID getAnswerBeerId() { return answerBeerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevealedAt() { return revealedAt; }
    public List<UUID> getOptions() { return List.copyOf(options); }
    public List<Guess> getGuesses() { return List.copyOf(guesses); }

    @Embeddable
    public record Guess(@Column(name = "user_id", nullable = false) UUID userId,
                        @Column(name = "beer_id", nullable = false) UUID beerId,
                        @Column(name = "guessed_at", nullable = false) Instant guessedAt) {
    }
}
