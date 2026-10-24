package no.cantara.julebrus.tasting;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tastings")
public class Tasting {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "beer_id", nullable = false)
    private UUID beerId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(nullable = false)
    private int score;

    private BigDecimal prediction;

    private String notes;

    /** Structured notes for the professionals. */
    private String appearance;
    private String aroma;
    private String taste;
    private String finish;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tasting_tags", joinColumns = @JoinColumn(name = "tasting_id"))
    @Column(name = "tag", nullable = false)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "tasted_at", nullable = false)
    private Instant tastedAt;

    @Column(name = "tasted_day", nullable = false)
    private LocalDate tastedDay;

    protected Tasting() {
    }

    public Tasting(UUID userId, UUID beerId, UUID sessionId, int score, BigDecimal prediction, String notes,
                   Notes structured, Set<String> tags, Instant tastedAt, LocalDate tastedDay) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.beerId = beerId;
        this.sessionId = sessionId;
        this.score = score;
        this.prediction = prediction;
        this.notes = notes;
        if (structured != null) {
            this.appearance = structured.appearance();
            this.aroma = structured.aroma();
            this.taste = structured.taste();
            this.finish = structured.finish();
        }
        if (tags != null) this.tags.addAll(tags);
        this.tastedAt = tastedAt;
        this.tastedDay = tastedDay;
    }

    public record Notes(String appearance, String aroma, String taste, String finish) {
        @com.fasterxml.jackson.annotation.JsonIgnore
        public boolean isEmpty() {
            return isBlank(appearance) && isBlank(aroma) && isBlank(taste) && isBlank(finish);
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getBeerId() { return beerId; }
    public UUID getSessionId() { return sessionId; }
    public int getScore() { return score; }
    public BigDecimal getPrediction() { return prediction; }
    public String getNotes() { return notes; }
    public Notes getStructuredNotes() { return new Notes(appearance, aroma, taste, finish); }
    public Set<String> getTags() { return Set.copyOf(tags); }
    public Instant getTastedAt() { return tastedAt; }
    public LocalDate getTastedDay() { return tastedDay; }
}
