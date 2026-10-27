package no.cantara.julebrus.push;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Pure: when a browser gets the daily nudge, the finale announcement and the reminder before a planned tasting. */
public final class PushPlanner {

    private PushPlanner() {
    }

    /**
     * The nudge goes once a day, from the nudge time on, during the season, to people
     * who have not opened today's door yet. The window closes at midnight: no nudges for yesterday.
     */
    public static boolean nudgeDue(LocalDate today, LocalTime now, LocalTime nudgeTime, LocalDate seasonStart,
                                   LocalDate seasonEnd, LocalDate lastNudgeDay, boolean tastedToday) {
        if (today.isBefore(seasonStart) || today.isAfter(seasonEnd)) return false;
        if (now.isBefore(nudgeTime)) return false;
        if (today.equals(lastNudgeDay)) return false;
        return !tastedToday;
    }

    /** How long before a planned tasting its attendees are reminded. */
    public static final Duration EVENT_LEAD = Duration.ofHours(1);

    /**
     * A planned tasting is announced once, {@link #EVENT_LEAD} before it starts, to the people signed up
     * at that moment. Never for a cancelled one, never after the start, and never for one planned on
     * shorter notice than the lead (its attendees just signed up; "in an hour" would be wrong).
     */
    public static boolean eventReminderDue(Instant now, Instant startsAt, Instant createdAt, Instant reminderSentAt, boolean cancelled) {
        if (cancelled || reminderSentAt != null) return false;
        Instant remindAt = startsAt.minus(EVENT_LEAD);
        if (createdAt.isAfter(remindAt)) return false;
        return !now.isBefore(remindAt) && now.isBefore(startsAt);
    }

    /** The finale is announced once, at the reveal, and never to a browser that subscribed after it. */
    public static boolean finaleDue(Instant now, Instant revealAt, Instant subscribedAt, Instant finaleSentAt) {
        if (finaleSentAt != null) return false;
        if (now.isBefore(revealAt)) return false;
        return !subscribedAt.isAfter(revealAt);
    }
}
