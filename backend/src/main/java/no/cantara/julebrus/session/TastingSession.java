package no.cantara.julebrus.session;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A sitting: one host, members who joined by QR or code, and the beers on the table.
 */
@Entity
@Table(name = "sessions")
public class TastingSession {

    @Id
    private UUID id;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(name = "join_code", nullable = false, unique = true)
    private String joinCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_members", joinColumns = @JoinColumn(name = "session_id"))
    private List<Member> members = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_beers", joinColumns = @JoinColumn(name = "session_id"))
    private List<SessionBeer> beers = new ArrayList<>();

    protected TastingSession() {
    }

    public TastingSession(UUID hostId, String joinCode) {
        this.id = UUID.randomUUID();
        this.hostId = hostId;
        this.joinCode = joinCode;
        this.createdAt = Instant.now();
        this.members.add(new Member(hostId, createdAt));
    }

    public boolean isMember(UUID userId) {
        return members.stream().anyMatch(m -> m.userId().equals(userId));
    }

    public void join(UUID userId) {
        if (!isMember(userId)) members.add(new Member(userId, Instant.now()));
    }

    public boolean hasBeer(UUID beerId) {
        return beers.stream().anyMatch(b -> b.beerId().equals(beerId));
    }

    public void addBeer(UUID beerId) {
        if (!hasBeer(beerId)) beers.add(new SessionBeer(beerId, Instant.now()));
    }

    public boolean isOpen() {
        return closedAt == null;
    }

    public void close() {
        if (closedAt == null) closedAt = Instant.now();
    }

    /** Solo means nobody else at the table. */
    public boolean isSolo() {
        return members.size() <= 1;
    }

    public UUID getId() { return id; }
    public UUID getHostId() { return hostId; }
    public String getJoinCode() { return joinCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
    public List<Member> getMembers() { return List.copyOf(members); }
    public List<SessionBeer> getBeers() { return List.copyOf(beers); }

    @jakarta.persistence.Embeddable
    public record Member(@Column(name = "user_id", nullable = false) UUID userId,
                         @Column(name = "joined_at", nullable = false) Instant joinedAt) {
    }

    @jakarta.persistence.Embeddable
    public record SessionBeer(@Column(name = "beer_id", nullable = false) UUID beerId,
                              @Column(name = "added_at", nullable = false) Instant addedAt) {
    }
}
