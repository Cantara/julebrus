package no.cantara.julebrus.blind;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure: how many blind guesses each person made over revealed rounds, and how many hit. */
public record BlindTally(int guesses, int correct) {

    public static final BlindTally NONE = new BlindTally(0, 0);

    public BlindTally plus(boolean hit) {
        return new BlindTally(guesses + 1, hit ? correct + 1 : correct);
    }

    public double accuracy() {
        return guesses == 0 ? 0 : (double) correct / guesses;
    }

    /** Only revealed rounds count: an open round's guesses are not graded yet. */
    public static Map<UUID, BlindTally> of(List<BlindRound> rounds) {
        Map<UUID, BlindTally> out = new HashMap<>();
        for (BlindRound r : rounds) {
            if (!r.isRevealed()) continue;
            for (BlindRound.Guess g : r.getGuesses()) {
                out.put(g.userId(), out.getOrDefault(g.userId(), NONE).plus(r.isCorrect(g)));
            }
        }
        return out;
    }
}
