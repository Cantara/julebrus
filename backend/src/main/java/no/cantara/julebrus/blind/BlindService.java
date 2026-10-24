package no.cantara.julebrus.blind;

import no.cantara.julebrus.session.SessionService;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The blind pour rules. The host pours one of the beers on the table without saying
 * which; everyone else guesses. One open round per table, the host never guesses,
 * one guess each, and the answer comes out when the last guest has guessed or the
 * host reveals it.
 */
@Service
public class BlindService {

    private final BlindRoundRepository rounds;
    private final SessionService sessions;
    private final Clock clock;

    public BlindService(BlindRoundRepository rounds, SessionService sessions, Clock clock) {
        this.rounds = rounds;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BlindRound> list(UUID sessionId, UUID userId) {
        sessions.getForMember(sessionId, userId);
        return rounds.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    @Transactional
    public BlindRound start(UUID sessionId, UUID hostId, UUID answerBeerId, List<UUID> optionBeerIds) {
        TastingSession session = sessions.getForMember(sessionId, hostId);
        if (!session.getHostId().equals(hostId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BLIND_NOT_HOST", "Only the host pours blind");
        }
        if (!session.isOpen()) throw new ApiException(HttpStatus.GONE, "SESSION_CLOSED", "That session is closed");
        if (session.isSolo()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_NEEDS_GUESTS", "Nobody at the table to guess");
        }
        if (!rounds.findBySessionIdAndRevealedAtIsNull(sessionId).isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_ROUND_OPEN", "Reveal the open round first");
        }
        Set<UUID> options = new LinkedHashSet<>(optionBeerIds == null ? List.of() : optionBeerIds);
        if (options.size() < BlindRound.MIN_OPTIONS || options.size() > BlindRound.MAX_OPTIONS) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_OPTIONS",
                    "Pick between %d and %d beers".formatted(BlindRound.MIN_OPTIONS, BlindRound.MAX_OPTIONS));
        }
        if (!options.stream().allMatch(session::hasBeer)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_OPTIONS", "Every option must be on the table");
        }
        if (answerBeerId == null || !options.contains(answerBeerId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_ANSWER", "The poured beer must be one of the options");
        }
        return rounds.save(new BlindRound(sessionId, hostId, answerBeerId, List.copyOf(options), clock.instant()));
    }

    @Transactional
    public BlindRound guess(UUID sessionId, UUID roundId, UUID userId, UUID beerId) {
        TastingSession session = sessions.getForMember(sessionId, userId);
        BlindRound round = roundOf(session, roundId);
        if (round.isRevealed()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_REVEALED", "That round is already revealed");
        }
        if (round.getHostId().equals(userId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_HOST_GUESS", "The one who poured does not guess");
        }
        if (round.guessOf(userId).isPresent()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_ALREADY_GUESSED", "One guess each");
        }
        if (beerId == null || !round.isOption(beerId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_NOT_OPTION", "Guess one of the options");
        }
        round.addGuess(userId, beerId, clock.instant());
        if (everyGuestGuessed(session, round)) round.reveal(clock.instant());
        return round;
    }

    @Transactional
    public BlindRound reveal(UUID sessionId, UUID roundId, UUID userId) {
        TastingSession session = sessions.getForMember(sessionId, userId);
        BlindRound round = roundOf(session, roundId);
        if (!round.getHostId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BLIND_NOT_HOST", "Only the host reveals");
        }
        if (round.getGuesses().isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BLIND_NO_GUESSES", "Nobody has guessed yet");
        }
        round.reveal(clock.instant());
        return round;
    }

    /** Closing the table ends its open rounds; nobody is left to guess. */
    @Transactional
    public void revealAllOpen(UUID sessionId) {
        rounds.findBySessionIdAndRevealedAtIsNull(sessionId).forEach(r -> r.reveal(clock.instant()));
    }

    /** Correct blind guesses per person over the whole season, for badges and the finale. */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, BlindTally> tally() {
        return BlindTally.of(rounds.findByRevealedAtIsNotNull());
    }

    private static boolean everyGuestGuessed(TastingSession session, BlindRound round) {
        return session.getMembers().stream().map(TastingSession.Member::userId)
                .filter(id -> !id.equals(round.getHostId()))
                .allMatch(id -> round.guessOf(id).isPresent());
    }

    private BlindRound roundOf(TastingSession session, UUID roundId) {
        BlindRound round = rounds.findById(roundId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BLIND_NOT_FOUND", "No such round"));
        if (!round.getSessionId().equals(session.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BLIND_NOT_FOUND", "No such round at this table");
        }
        return round;
    }
}
