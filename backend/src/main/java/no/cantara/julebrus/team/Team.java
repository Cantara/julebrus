package no.cantara.julebrus.team;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** A camp people play for: Cantara, Rebel, and whoever an admin enables next. */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "sponsor_name")
    private String sponsorName;

    @Column(name = "sponsor_url")
    private String sponsorUrl;

    /** Comma separated e-mail domains that suggest this team at signup. */
    private String domains;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "team_members", joinColumns = @JoinColumn(name = "team_id"))
    private List<Member> members = new ArrayList<>();

    protected Team() {
    }

    public Team(String slug, String name, Instant now) {
        this.id = UUID.randomUUID();
        this.slug = slug;
        this.name = name;
        this.createdAt = now;
    }

    /** Lower-case ascii letters, digits and dashes, so it reads in a URL and a query string. */
    public static String slugOf(String name) {
        String s = java.text.Normalizer.normalize(name == null ? "" : name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("ø", "o").replace("Ø", "o").replace("æ", "ae").replace("Æ", "ae")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    public boolean isMember(UUID userId) {
        return members.stream().anyMatch(m -> m.userId().equals(userId));
    }

    public void join(UUID userId, Instant now) {
        if (!isMember(userId)) members.add(new Member(userId, now));
    }

    public void leave(UUID userId) {
        members.removeIf(m -> m.userId().equals(userId));
    }

    public boolean suggestsFor(String email) {
        if (domains == null || email == null || email.indexOf('@') < 0) return false;
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(domains.split(",")).map(d -> d.trim().toLowerCase(Locale.ROOT))
                .anyMatch(d -> !d.isEmpty() && d.equals(domain));
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSponsorName() { return sponsorName; }
    public void setSponsorName(String sponsorName) { this.sponsorName = sponsorName; }
    public String getSponsorUrl() { return sponsorUrl; }
    public void setSponsorUrl(String sponsorUrl) { this.sponsorUrl = sponsorUrl; }
    public String getDomains() { return domains; }
    public void setDomains(String domains) { this.domains = domains; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Member> getMembers() { return List.copyOf(members); }

    @Embeddable
    public record Member(@Column(name = "user_id", nullable = false) UUID userId,
                         @Column(name = "joined_at", nullable = false) Instant joinedAt) {
    }
}
