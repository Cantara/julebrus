package no.cantara.julebrus.weight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "rater_weights")
public class RaterWeight {

    @Id
    private UUID id;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private BigDecimal weight;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "flag_note")
    private String flagNote;

    protected RaterWeight() {
    }

    public RaterWeight(int seasonYear, String email, double weight, String reason) {
        this.id = UUID.randomUUID();
        this.seasonYear = seasonYear;
        this.email = email.toLowerCase();
        this.weight = BigDecimal.valueOf(weight);
        this.reason = reason;
    }

    public void override(double weight, String reason) {
        this.weight = BigDecimal.valueOf(weight);
        this.reason = reason;
    }

    public void flag(boolean flagged, String note) {
        this.flagged = flagged;
        this.flagNote = note;
    }

    public UUID getId() { return id; }
    public int getSeasonYear() { return seasonYear; }
    public String getEmail() { return email; }
    public BigDecimal getWeight() { return weight; }
    public double weight() { return weight.doubleValue(); }
    public String getReason() { return reason; }
    public boolean isFlagged() { return flagged; }
    public String getFlagNote() { return flagNote; }
}
