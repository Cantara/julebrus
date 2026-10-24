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

    public enum Status { PENDING, APPROVED, REJECTED }

    /**
     * What counts as a Norwegian Christmas beer here: sold at Vinmonopolet, which means
     * stronger than the grocery-shelf limit. Anything at or under this ABV is butikkøl.
     */
    public static final BigDecimal GROCERY_MAX_ABV = new BigDecimal("4.7");

    public static boolean isGroceryStrength(BigDecimal abv) {
        return abv != null && abv.compareTo(GROCERY_MAX_ABV) <= 0;
    }

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.APPROVED;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected Beer() {
    }

    public Beer(String name, String brewery, String style, BigDecimal abv, String vinmonopoletId, String imageUrl) {
        this(name, brewery, style, abv, vinmonopoletId, imageUrl, null, Status.APPROVED);
    }

    public Beer(String name, String brewery, String style, BigDecimal abv, String vinmonopoletId, String imageUrl,
                UUID createdBy, Status status) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.brewery = brewery;
        this.style = style;
        this.abv = abv;
        this.vinmonopoletId = vinmonopoletId;
        this.imageUrl = imageUrl;
        this.createdAt = Instant.now();
        this.createdBy = createdBy;
        this.status = status;
    }

    public void decide(Status status, UUID adminId, Instant now) {
        this.status = status;
        this.decidedBy = adminId;
        this.decidedAt = now;
    }

    public Instant getCreatedAt() { return createdAt; }
    public boolean isApproved() { return status == Status.APPROVED; }
    /** Approved and not grocery strength: the only beers the rankings count. */
    public boolean counts() { return isApproved() && !isGroceryStrength(abv); }
    public Status getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getDecidedAt() { return decidedAt; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getBrewery() { return brewery; }
    public String getStyle() { return style; }
    public BigDecimal getAbv() { return abv; }
    public String getVinmonopoletId() { return vinmonopoletId; }
    public String getImageUrl() { return imageUrl; }
}
