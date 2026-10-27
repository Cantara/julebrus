package no.cantara.julebrus.event;

import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.session.SessionService;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Planned tastings. Anyone with consent hosts one; the others join until it is full,
 * cancelled or long over; the host (or an admin) cancels; the host opens the table from
 * it on the night, after which attendees get the join code. An event stays listed until
 * {@link #GRACE} after it started, so a late table can still be opened from it.
 */
@Service
public class EventService {

    /** How long after the start an event is still "upcoming" for the list and for joining. */
    public static final Duration GRACE = Duration.ofHours(6);
    public static final int MAX_CAPACITY = 60;

    private final EventRepository events;
    private final SessionService sessions;
    private final AuthProperties auth;
    private final Clock clock;

    public EventService(EventRepository events, SessionService sessions, AuthProperties auth, Clock clock) {
        this.events = events;
        this.sessions = sessions;
        this.auth = auth;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Event> upcoming() {
        return events.findByStartsAtGreaterThanEqualAndCancelledAtIsNullOrderByStartsAtAsc(clock.instant().minus(GRACE));
    }

    @Transactional(readOnly = true)
    public Event get(UUID id) {
        return events.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "No such tasting"));
    }

    @Transactional
    public Event create(User host, String title, Instant startsAt, boolean online, String place, String note, Integer capacity) {
        Instant now = clock.instant();
        if (startsAt == null || startsAt.isBefore(now)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVENT_PAST", "Pick a time that has not passed");
        if (capacity != null && (capacity < 2 || capacity > MAX_CAPACITY)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION", "Capacity is 2 to " + MAX_CAPACITY + ", or empty");
        }
        return events.save(new Event(host.getId(), title.trim(), startsAt, online, blankToNull(place), blankToNull(note), capacity, now));
    }

    @Transactional
    public Event join(UUID eventId, User me) {
        Event e = get(eventId);
        Instant now = clock.instant();
        if (e.isCancelled()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVENT_CANCELLED", "That tasting was cancelled");
        if (e.getStartsAt().plus(GRACE).isBefore(now)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVENT_PAST", "That tasting is over");
        if (!e.isAttending(me.getId()) && e.getCapacity() != null && e.getAttendees().size() >= e.getCapacity()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVENT_FULL", "That tasting is full");
        }
        e.join(me.getId(), now);
        return e;
    }

    @Transactional
    public Event leave(UUID eventId, User me) {
        Event e = get(eventId);
        if (e.getHostId().equals(me.getId())) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVENT_HOST_STAYS", "The host stays; cancel the tasting instead");
        e.leave(me.getId());
        return e;
    }

    @Transactional
    public Event cancel(UUID eventId, User me) {
        Event e = get(eventId);
        requireHost(e, me);
        e.cancel(clock.instant());
        return e;
    }

    /** The host opens the table on the night; attendees then see the join code. Idempotent. */
    @Transactional
    public Event openTable(UUID eventId, User me) {
        Event e = get(eventId);
        requireHost(e, me);
        if (e.isCancelled()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVENT_CANCELLED", "That tasting was cancelled");
        if (e.getSessionId() == null) {
            TastingSession s = sessions.open(me.getId());
            e.attachTable(s.getId());
        }
        return e;
    }

    private void requireHost(Event e, User me) {
        if (!e.getHostId().equals(me.getId()) && !auth.isAdmin(me.getEmail())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "EVENT_NOT_HOST", "Only the host can do that");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
