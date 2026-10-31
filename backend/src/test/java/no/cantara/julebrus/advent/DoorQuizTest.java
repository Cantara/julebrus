package no.cantara.julebrus.advent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The daily question: same day, same question; the kinds take turns; the answer is always among the options. */
class DoorQuizTest {

    static List<DoorQuiz.AllTime> allTime() {
        List<DoorQuiz.AllTime> out = new ArrayList<>();
        for (int i = 1; i <= 30; i++) out.add(new DoorQuiz.AllTime(i, "Øl " + i, 20));
        return out;
    }

    static List<DoorQuiz.SeasonTop> seasons() {
        return List.of(
                new DoorQuiz.SeasonTop(2017, List.of("Aass Juleøl", "Nøgne Ø Underlig Jul", "Haandbryggeriet Nissefar", "Ringnes Juleøl"), 4.9),
                new DoorQuiz.SeasonTop(2018, List.of("Nøgne Ø Underlig Jul", "Aass Juleøl", "Kinn Julefred", "Mack God Jul"), 5.1),
                new DoorQuiz.SeasonTop(2019, List.of("Aass Juleøl", "Kinn Julefred", "Mack God Jul", "Ringnes Juleøl"), 4.7),
                new DoorQuiz.SeasonTop(2016, List.of("Kinn Julefred", "Aass Juleøl", "Mack God Jul"), 4.5));
    }

    @Test
    void sameDaySameQuestionAndTheKindsTakeTurns() {
        Set<DoorQuiz.Kind> seen = EnumSet.noneOf(DoorQuiz.Kind.class);
        LocalDate day = LocalDate.of(2026, 11, 1);
        for (int i = 0; i < 9; i++, day = day.plusDays(1)) {
            DoorQuiz.Question q = DoorQuiz.generate(day, allTime(), seasons());
            assertThat(q).isEqualTo(DoorQuiz.generate(day, allTime(), seasons()));
            assertThat(q.answer()).isBetween(0, q.options().size() - 1);
            assertThat(q.options()).doesNotHaveDuplicates();
            assertThat(q.explanation()).isNotBlank();
            seen.add(q.kind());
        }
        assertThat(seen).containsExactlyInAnyOrder(DoorQuiz.Kind.values());
    }

    @Test
    void higherOrLowerPicksBeersFarApartAndTheBetterOneWins() {
        for (LocalDate d = LocalDate.of(2026, 11, 1); d.isBefore(LocalDate.of(2026, 12, 24)); d = d.plusDays(1)) {
            DoorQuiz.Question q = DoorQuiz.generate(d, allTime(), List.of());
            assertThat(q.kind()).isEqualTo(DoorQuiz.Kind.HIGHER_LOWER);
            int a = Integer.parseInt(q.options().get(0).substring(3)), b = Integer.parseInt(q.options().get(1).substring(3));
            assertThat(Math.abs(a - b)).isGreaterThanOrEqualTo(DoorQuiz.MIN_RANK_GAP);
            assertThat(q.answer()).isEqualTo(a < b ? 0 : 1);
        }
    }

    @Test
    void winnerYearNeverOffersAnotherYearTheSameBeerWon() {
        for (LocalDate d = LocalDate.of(2026, 11, 1); d.isBefore(LocalDate.of(2026, 12, 24)); d = d.plusDays(1)) {
            DoorQuiz.Question q = DoorQuiz.generate(d, List.of(), seasons());
            if (q.kind() != DoorQuiz.Kind.WINNER_YEAR) continue;
            if (q.text().contains("Aass")) {
                // Aass won 2017 and 2019: only one of those may be an option, and it is the answer.
                long wonYears = q.options().stream().filter(o -> o.equals("2017") || o.equals("2019")).count();
                assertThat(wonYears).isEqualTo(1);
                assertThat(q.options().get(q.answer())).isIn("2017", "2019");
                assertThat(q.explanation()).contains("2017 og 2019");
            }
        }
    }

    @Test
    void yearWinnerHasTheWinnerAmongThreeOptions() {
        for (LocalDate d = LocalDate.of(2026, 11, 1); d.isBefore(LocalDate.of(2026, 11, 20)); d = d.plusDays(1)) {
            DoorQuiz.Question q = DoorQuiz.generate(d, List.of(), seasons());
            if (q.kind() != DoorQuiz.Kind.YEAR_WINNER) continue;
            int year = Integer.parseInt(q.text().replaceAll("\\D", ""));
            String winner = seasons().stream().filter(s -> s.year() == year).findFirst().orElseThrow().best().get(0);
            assertThat(q.options()).hasSize(3);
            assertThat(q.options().get(q.answer())).isEqualTo(winner);
            assertThat(q.explanation()).contains(",");
        }
    }

    @Test
    void anEmptyArchiveGivesNoQuestion() {
        assertThat(DoorQuiz.generate(LocalDate.of(2026, 11, 1), List.of(), List.of())).isNull();
    }

    @Test
    void cluesFallBackWhenTheBeerLacksAFact() {
        DoorClues.Leader full = new DoorClues.Leader("Aass Juleøl", "Aass", "Mørk lager", new BigDecimal("6.5"), 23);
        DoorClues.Leader bare = new DoorClues.Leader("Julebrus", null, null, null, 4);
        for (int n = 0; n < DoorClues.COUNT + 2; n++) {
            assertThat(DoorClues.clue(n, full)).isNotBlank();
            assertThat(DoorClues.clue(n, bare)).isNotBlank();
        }
        assertThat(DoorClues.clue(0, full)).isEqualTo("Lederen er under 7 %.");
        assertThat(DoorClues.clue(1, full)).isEqualTo("Lederen har fått 20 terningkast eller flere.");
        assertThat(DoorClues.clue(2, full)).isEqualTo("Lederen er en mørk lager.");
        assertThat(DoorClues.clue(3, full)).isEqualTo("Navnet har 2 ord.");
        assertThat(DoorClues.clue(4, full)).isEqualTo("Bryggeriet begynner på A.");
        assertThat(DoorClues.clue(5, full)).isEqualTo("Navnet begynner på A og slutter på l.");
        assertThat(DoorClues.clue(0, bare)).contains("4,7");
        assertThat(DoorClues.clue(4, bare)).isEqualTo("Navnet begynner på J.");
        assertThat(DoorClues.clue(0, null)).isNull();
    }

    @Test
    void theStreakCountsDoorsBackFromTodayOrYesterday() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        assertThat(AdventService.streak(Set.of(today, today.minusDays(1), today.minusDays(3)), today)).isEqualTo(2);
        assertThat(AdventService.streak(Set.of(today.minusDays(1), today.minusDays(2)), today)).isEqualTo(2);
        assertThat(AdventService.streak(Set.of(today.minusDays(2)), today)).isZero();
    }
}
