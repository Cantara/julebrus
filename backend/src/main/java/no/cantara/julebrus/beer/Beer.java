package no.cantara.julebrus.beer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beers")
public class Beer {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String brewery;
    private String style;
    private BigDecimal abv;

    @Column(name = "vinmonopolet_id", unique = true)
    private String vinmonopoletId;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Beer() {
    }

    public Beer(String name, String brewery, String style, BigDecimal abv, String vinmonopoletId, String imageUrl) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.brewery = brewery;
        this.style = style;
        this.abv = abv;
        this.vinmonopoletId = vinmonopoletId;
        this.imageUrl = imageUrl;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getBrewery() { return brewery; }
    public String getStyle() { return style; }
    public BigDecimal getAbv() { return abv; }
    public String getVinmonopoletId() { return vinmonopoletId; }
    public String getImageUrl() { return imageUrl; }
}
