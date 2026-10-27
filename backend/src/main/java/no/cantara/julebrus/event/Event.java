package no.cantara.julebrus.event;

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
import java.util.List;
import java.util.UUID;

/** A planned tasting: one host, a time, a place or "digitalt", and who is coming. */
@Entity
@Table(name = "events")
public class Event {

    @Id
    private UUID id;

    @Column(name = "host_id", nullable = false)
    private UUID hostId;

    @Column(nullable = false)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private boolean online;

    private String place;

    private String note;

    private Integer capacity;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_attendees", joinColumns = @JoinColumn(name = "event_id"))
    private List<Attendee> attendees = new ArrayList<>();

    protected Event() {
    }

    public Event(UUID hostId, String title, Instant startsAt, boolean online, String place, String note, Integer capacity, Instant now) {
        this.id = UUID.randomUUID();
        this.hostId = hostId;
        this.title = title;
        this.startsAt = startsAt;
        this.online = online;
        this.place = place;
        this.note = note;
        this.capacity = capacity;
        this.createdAt = now;
        this.attendees.add(new Attendee(hostId, now));
    }

    public boolean isAttending(UUID userId) {
        return attendees.stream().anyMatch(a -> a.userId().equals(userId));
    }

    public void join(UUID userId, Instant now) {
        if (!isAttending(userId)) attendees.add(new Attendee(userId, now));
    }

    public void leave(UUID userId) {
        attendees.removeIf(a -> a.userId().equals(userId));
    }

    public boolean isCancelled() { return cancelledAt != null; }
    public void cancel(Instant now) { if (cancelledAt == null) cancelledAt = now; }
    public void attachTable(UUID sessionId) { this.sessionId = sessionId; }

    public UUID getId() { return id; }
    public UUID getHostId() { return hostId; }
    public String getTitle() { return title; }
    public Instant getStartsAt() { return startsAt; }
    public boolean isOnline() { return online; }
    public String getPlace() { return place; }
    public String getNote() { return note; }
    public Integer getCapacity() { return capacity; }
    public UUID getSessionId() { return sessionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public List<Attendee> getAttendees() { return List.copyOf(attendees); }

    @Embeddable
    public record Attendee(@Column(name = "user_id", nullable = false) UUID userId,
                           @Column(name = "joined_at", nullable = false) Instant joinedAt) {
    }
}
