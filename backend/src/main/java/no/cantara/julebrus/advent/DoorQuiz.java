package no.cantara.julebrus.advent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Pure: the question behind a day's door, made from the archive so nobody has to write 53 of them.
 * The same day always gives the same question (the day seeds the choice), the kinds take turns,
 * and a kind the archive cannot support falls through to the next.
 */
public final class DoorQuiz {

    public enum Kind { HIGHER_LOWER, YEAR_WINNER, WINNER_YEAR }

    /** A question as the door shows it; {@code answer} is the index of the right option. */
    public record Question(Kind kind, String text, List<String> options, int answer, String explanation) {
    }

    /** One beer on the all-time list (rank 1 is best). */
    public record AllTime(int rank, String name, long ratings) {
    }

    /** One season: its beers best first, with the winner's mean on 1-6. */
    public record SeasonTop(int year, List<String> best, double winnerMean) {
    }

    /** How far apart two beers must be on the all-time list to make a fair higher-or-lower. */
    static final int MIN_RANK_GAP = 5;
    private static final Locale NB = Locale.of("nb", "NO");

    private DoorQuiz() {
    }

    public static Question generate(LocalDate day, List<AllTime> allTime, List<SeasonTop> seasons) {
        long seed = day.toEpochDay();
        List<AllTime> beers = allTime.stream().sorted(Comparator.comparingInt(AllTime::rank)).toList();
        List<SeasonTop> years = seasons.stream().filter(s -> !s.best().isEmpty())
                .sorted(Comparator.comparingInt(SeasonTop::year)).toList();
        Kind[] kinds = Kind.values();
        for (int k = 0; k < kinds.length; k++) {
            Kind kind = kinds[(int) Math.floorMod(seed + k, kinds.length)];
            Random random = new Random(seed * 7919 + kind.ordinal());
            Question q = switch (kind) {
                case HIGHER_LOWER -> higherLower(random, beers);
                case YEAR_WINNER -> yearWinner(random, years);
                case WINNER_YEAR -> winnerYear(random, years);
            };
            if (q != null) return q;
        }
        return null;
    }

    private static Question higherLower(Random random, List<AllTime> beers) {
        if (beers.size() < MIN_RANK_GAP + 1) return null;
        for (int attempt = 0; attempt < 50; attempt++) {
            AllTime a = beers.get(random.nextInt(beers.size()));
            AllTime b = beers.get(random.nextInt(beers.size()));
            if (a.name().equalsIgnoreCase(b.name()) || Math.abs(a.rank() - b.rank()) < MIN_RANK_GAP) continue;
            AllTime better = a.rank() < b.rank() ? a : b;
            return new Question(Kind.HIGHER_LOWER, "Hvem står høyest på listen over alle år?",
                    List.of(a.name(), b.name()), better == a ? 0 : 1,
                    "%s er nr. %d på listen over alle år, %s nr. %d.".formatted(a.name(), a.rank(), b.name(), b.rank()));
        }
        return null;
    }

    private static Question yearWinner(Random random, List<SeasonTop> years) {
        List<SeasonTop> usable = years.stream().filter(s -> s.best().size() >= 3).toList();
        if (usable.isEmpty()) return null;
        SeasonTop s = usable.get(random.nextInt(usable.size()));
        String winner = s.best().get(0);
        List<String> others = new ArrayList<>(s.best().subList(1, Math.min(6, s.best().size())));
        others.removeIf(o -> o.equalsIgnoreCase(winner));
        if (others.size() < 2) return null;
        java.util.Collections.shuffle(others, random);
        List<String> options = new ArrayList<>(List.of(winner, others.get(0), others.get(1)));
        java.util.Collections.shuffle(options, random);
        return new Question(Kind.YEAR_WINNER, "Hva vant juleølsmakingen i %d?".formatted(s.year()), List.copyOf(options),
                options.indexOf(winner), "%s vant %d med %s på 1–6.".formatted(winner, s.year(), String.format(NB, "%.2f", s.winnerMean())));
    }

    private static Question winnerYear(Random random, List<SeasonTop> years) {
        if (years.size() < 3) return null;
        SeasonTop s = years.get(random.nextInt(years.size()));
        String beer = s.best().get(0);
        TreeSet<Integer> won = years.stream().filter(y -> y.best().get(0).equalsIgnoreCase(beer)).map(SeasonTop::year)
                .collect(Collectors.toCollection(TreeSet::new));
        List<Integer> others = new ArrayList<>(years.stream().map(SeasonTop::year).filter(y -> !won.contains(y)).toList());
        if (others.size() < 2) return null;
        java.util.Collections.shuffle(others, random);
        List<Integer> options = new ArrayList<>(List.of(s.year(), others.get(0), others.get(1)));
        options.sort(Integer::compare);
        String when = won.size() == 1 ? "i " + won.first()
                : "i " + won.stream().map(String::valueOf).collect(Collectors.joining(", ")).replaceFirst(", (\\d+)$", " og $1");
        return new Question(Kind.WINNER_YEAR,
                (won.size() == 1 ? "Hvilket år vant %s?" : "Hvilket av disse årene vant %s?").formatted(beer),
                options.stream().map(String::valueOf).toList(), options.indexOf(s.year()), "%s vant %s.".formatted(beer, when));
    }
}
