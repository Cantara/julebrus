package no.cantara.julebrus.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "historic_ratings")
public class HistoricRating {

    @Id
    private UUID id;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(name = "beer_name", nullable = false)
    private String beerName;

    private String brewery;

    @Column(name = "beer_id")
    private UUID beerId;

    @Column(name = "rater_name", nullable = false)
    private String raterName;

    @Column(name = "rater_email")
    private String raterEmail;

    @Column(nullable = false)
    private BigDecimal score;

    @Column(nullable = false)
    private BigDecimal weight;

    private String notes;

    protected HistoricRating() {
    }

    public HistoricRating(int seasonYear, String beerName, String brewery, String raterName, String raterEmail,
                          BigDecimal score, BigDecimal weight, String notes) {
        this.id = UUID.randomUUID();
        this.seasonYear = seasonYear;
        this.beerName = beerName;
        this.brewery = brewery;
        this.raterName = raterName;
        this.raterEmail = raterEmail;
        this.score = score;
        this.weight = weight == null ? BigDecimal.ONE : weight;
        this.notes = notes;
    }

    public UUID getId() { return id; }
    public int getSeasonYear() { return seasonYear; }
    public String getBeerName() { return beerName; }
    public String getBrewery() { return brewery; }
    public UUID getBeerId() { return beerId; }
    public void setBeerId(UUID beerId) { this.beerId = beerId; }
    public String getRaterName() { return raterName; }
    public String getRaterEmail() { return raterEmail; }
    public BigDecimal getScore() { return score; }
    public BigDecimal getWeight() { return weight; }
    public String getNotes() { return notes; }
}
