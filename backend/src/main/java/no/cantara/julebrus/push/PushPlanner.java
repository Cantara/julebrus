package no.cantara.julebrus.push;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Pure: when a browser gets the daily nudge and the finale announcement. */
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

    /** The finale is announced once, at the reveal, and never to a browser that subscribed after it. */
    public static boolean finaleDue(Instant now, Instant revealAt, Instant subscribedAt, Instant finaleSentAt) {
        if (finaleSentAt != null) return false;
        if (now.isBefore(revealAt)) return false;
        return !subscribedAt.isAfter(revealAt);
    }
}
