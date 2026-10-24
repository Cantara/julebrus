package no.cantara.julebrus.blind;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BlindTallyTest {

    UUID session = UUID.randomUUID(), host = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
    UUID beer1 = UUID.randomUUID(), beer2 = UUID.randomUUID();
    Instant t0 = Instant.parse("2026-12-05T18:00:00Z");

    @Test
    void countsOnlyRevealedRoundsAndGradesAgainstTheAnswer() {
        BlindRound revealed = new BlindRound(session, host, beer1, List.of(beer1, beer2), t0);
        revealed.addGuess(a, beer1, t0);
        revealed.addGuess(b, beer2, t0);
        revealed.reveal(t0);

        BlindRound open = new BlindRound(session, host, beer2, List.of(beer1, beer2), t0);
        open.addGuess(a, beer2, t0);

        BlindRound second = new BlindRound(session, host, beer2, List.of(beer1, beer2), t0);
        second.addGuess(a, beer1, t0);
        second.reveal(t0);

        Map<UUID, BlindTally> tally = BlindTally.of(List.of(revealed, open, second));
        assertThat(tally.get(a)).isEqualTo(new BlindTally(2, 1));
        assertThat(tally.get(b)).isEqualTo(new BlindTally(1, 0));
        assertThat(tally).doesNotContainKey(host);
        assertThat(tally.get(a).accuracy()).isEqualTo(0.5);
        assertThat(BlindTally.NONE.accuracy()).isZero();
    }
}
