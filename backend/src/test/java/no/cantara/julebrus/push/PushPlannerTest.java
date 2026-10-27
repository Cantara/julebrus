package no.cantara.julebrus.push;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class PushPlannerTest {

    LocalDate start = LocalDate.of(2026, 11, 1), end = LocalDate.of(2026, 12, 23);
    LocalTime at = LocalTime.of(17, 0);

    @Test
    void nudgeOncePerDayFromTheNudgeTimeToThoseWhoHaveNotTasted() {
        LocalDate dec5 = LocalDate.of(2026, 12, 5);
        assertThat(PushPlanner.nudgeDue(dec5, LocalTime.of(17, 0), at, start, end, null, false)).isTrue();
        assertThat(PushPlanner.nudgeDue(dec5, LocalTime.of(23, 59), at, start, end, dec5.minusDays(1), false)).isTrue();
        assertThat(PushPlanner.nudgeDue(dec5, LocalTime.of(16, 59), at, start, end, null, false)).isFalse();
        assertThat(PushPlanner.nudgeDue(dec5, LocalTime.of(18, 0), at, start, end, dec5, false)).isFalse();
        assertThat(PushPlanner.nudgeDue(dec5, LocalTime.of(18, 0), at, start, end, null, true)).isFalse();
        assertThat(PushPlanner.nudgeDue(LocalDate.of(2026, 10, 31), LocalTime.of(18, 0), at, start, end, null, false)).isFalse();
        assertThat(PushPlanner.nudgeDue(LocalDate.of(2026, 12, 24), LocalTime.of(18, 0), at, start, end, null, false)).isFalse();
    }

    @Test
    void eventReminderOnceAnHourBeforeAndNotOnShortNotice() {
        Instant starts = Instant.parse("2026-12-05T18:00:00Z");
        Instant planned = starts.minusSeconds(3 * 3600), hourBefore = starts.minusSeconds(3600);
        assertThat(PushPlanner.eventReminderDue(hourBefore.minusSeconds(1), starts, planned, null, false)).isFalse();
        assertThat(PushPlanner.eventReminderDue(hourBefore, starts, planned, null, false)).isTrue();
        assertThat(PushPlanner.eventReminderDue(starts.minusSeconds(60), starts, planned, null, false)).isTrue();
        assertThat(PushPlanner.eventReminderDue(starts, starts, planned, null, false)).isFalse();
        assertThat(PushPlanner.eventReminderDue(hourBefore, starts, planned, hourBefore, false)).isFalse();
        assertThat(PushPlanner.eventReminderDue(hourBefore, starts, planned, null, true)).isFalse();
        // Planned 40 minutes before the start: everyone just signed up, no "in an hour".
        assertThat(PushPlanner.eventReminderDue(starts.minusSeconds(1800), starts, starts.minusSeconds(2400), null, false)).isFalse();
    }

    @Test
    void finaleOnceAtTheRevealAndNotToLatecomers() {
        Instant reveal = Instant.parse("2026-12-23T17:00:00Z");
        Instant early = reveal.minusSeconds(3600), late = reveal.plusSeconds(3600);
        assertThat(PushPlanner.finaleDue(reveal.minusSeconds(1), reveal, early, null)).isFalse();
        assertThat(PushPlanner.finaleDue(reveal, reveal, early, null)).isTrue();
        assertThat(PushPlanner.finaleDue(late, reveal, early, reveal)).isFalse();
        assertThat(PushPlanner.finaleDue(late, reveal, late, null)).isFalse();
    }
}
