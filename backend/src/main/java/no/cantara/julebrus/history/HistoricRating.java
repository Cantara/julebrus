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

    /** The name as the source wrote it, kept when beer_names.csv has changed {@link #beerName}; null otherwise. */
    @Column(name = "beer_name_original")
    private String beerNameOriginal;

    private String brewery;

    @Column(name = "beer_id")
    private UUID beerId;

    @Column(name = "rater_name", nullable = false)
    private String raterName;

    @Column(name = "rater_email")
    private String raterEmail;

    /** Canonical name from raters.csv, or the source name when unmapped. */
    @Column(name = "rater_key")
    private String raterKey;

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
    public String getBeerNameOriginal() { return beerNameOriginal; }

    /** The name as written in the source, whether or not a correction is applied. */
    public String sourceBeerName() { return beerNameOriginal != null ? beerNameOriginal : beerName; }

    /**
     * Applies a beer_names.csv correction (null = none, which undoes an earlier one). The source name is kept in
     * beer_name_original. Returns true when anything changed, so a second run changes nothing.
     */
    public boolean applyBeerName(String canonical) {
        String source = sourceBeerName();
        String target = canonical == null || canonical.isBlank() ? source : canonical.trim();
        String original = target.equals(source) ? null : source;
        boolean changed = !target.equals(beerName) || !java.util.Objects.equals(original, beerNameOriginal);
        beerName = target;
        beerNameOriginal = original;
        return changed;
    }

    public String getBrewery() { return brewery; }
    public UUID getBeerId() { return beerId; }
    public void setBeerId(UUID beerId) { this.beerId = beerId; }
    public String getRaterName() { return raterName; }
    public String getRaterEmail() { return raterEmail; }
    public String getRaterKey() { return raterKey; }

    /** Applies a raters.csv row. Returns true when anything changed. */
    public boolean applyMapping(String canonicalName, String email) {
        String key = (canonicalName == null || canonicalName.isBlank()) ? raterName : canonicalName.trim();
        String mail = (email == null || email.isBlank()) ? raterEmail : email.trim().toLowerCase();
        boolean changed = !key.equals(raterKey) || !java.util.Objects.equals(mail, raterEmail);
        raterKey = key;
        raterEmail = mail;
        return changed;
    }
    public BigDecimal getScore() { return score; }
    public BigDecimal getWeight() { return weight; }
    public String getNotes() { return notes; }
}
