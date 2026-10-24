package no.cantara.julebrus.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * An earlier season as it was run, with its own scale and weighting. Scores are
 * normalised to 1–6 for display, but the original numbers are what we keep.
 */
@Entity
@Table(name = "historic_seasons")
public class HistoricSeason {

    @Id
    @Column(name = "season_year")
    private Integer year;

    @Column(nullable = false)
    private String title;

    @Column(name = "scale_min", nullable = false)
    private BigDecimal scaleMin;

    @Column(name = "scale_max", nullable = false)
    private BigDecimal scaleMax;

    @Column(name = "weighting_description")
    private String weightingDescription;

    @Column(name = "source_url")
    private String sourceUrl;

    protected HistoricSeason() {
    }

    public HistoricSeason(int year, String title, BigDecimal scaleMin, BigDecimal scaleMax,
                          String weightingDescription, String sourceUrl) {
        this.year = year;
        this.title = title;
        this.scaleMin = scaleMin;
        this.scaleMax = scaleMax;
        this.weightingDescription = weightingDescription;
        this.sourceUrl = sourceUrl;
    }

    /** Linear map from this season's scale onto 1–6. */
    public double normalise(BigDecimal score) {
        double min = scaleMin.doubleValue(), max = scaleMax.doubleValue();
        if (max <= min) return score.doubleValue();
        return 1 + (score.doubleValue() - min) / (max - min) * 5;
    }

    public Integer getYear() { return year; }
    public String getTitle() { return title; }
    public BigDecimal getScaleMin() { return scaleMin; }
    public BigDecimal getScaleMax() { return scaleMax; }
    public String getWeightingDescription() { return weightingDescription; }
    public String getSourceUrl() { return sourceUrl; }
}
