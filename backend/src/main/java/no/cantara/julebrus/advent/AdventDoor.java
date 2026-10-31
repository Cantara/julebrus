package no.cantara.julebrus.advent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One door one person opened, and how they answered its question. */
@Entity
@Table(name = "advent_doors")
public class AdventDoor {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "door_day", nullable = false)
    private LocalDate doorDay;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    private Integer answer;

    private Boolean correct;

    @Column(nullable = false)
    private boolean late;

    @Column(name = "key_used", nullable = false)
    private boolean keyUsed;

    protected AdventDoor() {
    }

    public AdventDoor(UUID userId, LocalDate doorDay, Instant openedAt, Integer answer, Boolean correct, boolean late, boolean keyUsed) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.doorDay = doorDay;
        this.openedAt = openedAt;
        this.answer = answer;
        this.correct = correct;
        this.late = late;
        this.keyUsed = keyUsed;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public LocalDate getDoorDay() { return doorDay; }
    public Instant getOpenedAt() { return openedAt; }
    public Integer getAnswer() { return answer; }
    public Boolean getCorrect() { return correct; }
    public boolean isLate() { return late; }
    public boolean isKeyUsed() { return keyUsed; }
}
