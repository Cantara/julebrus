package no.cantara.julebrus.tasting;

import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.web.ApiException;
import no.cantara.julebrus.session.SessionRepository;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class TastingService {

    public record Status(LocalDate today, int tastingsToday, int maxPerDay, int soloDaysUsed, int maxSoloDays,
                         boolean inSeason) {
    }

    private final TastingRepository tastings;
    private final SessionRepository sessions;
    private final BeerRepository beers;
    private final TastingRules rules;
    private final SeasonProperties season;
    private final Clock clock;

    public TastingService(TastingRepository tastings, SessionRepository sessions, BeerRepository beers,
                          SeasonProperties season, Clock clock) {
        this.tastings = tastings;
        this.sessions = sessions;
        this.beers = beers;
        this.season = season;
        this.rules = new TastingRules(season);
        this.clock = clock;
    }

    @Transactional
    public Tasting rate(UUID userId, UUID beerId, UUID sessionId, int score, BigDecimal prediction, String notes,
                        Tasting.Notes structured, Set<String> tags) {
        Beer beer = beers.findById(beerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown beer"));
        if (beer.getStatus() == Beer.Status.REJECTED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BEER_REJECTED", "That beer was rejected by the organisers");
        }
        boolean solo = true;
        int tableSize = 1;
        if (sessionId != null) {
            TastingSession session = sessions.findById(sessionId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown session"));
            if (!session.isMember(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member");
            if (!session.isOpen()) throw new ResponseStatusException(HttpStatus.GONE, "Session is closed");
            if (!session.hasBeer(beerId)) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Beer is not in this session");
            solo = session.isSolo();
            tableSize = session.getMembers().size();
        }
        // Homebrew is rated only at a table of more than five, so nobody scores their own in a quiet corner.
        if (beer.isHomebrew() && tableSize < Beer.HOMEBREW_MIN_GROUP) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "HOMEBREW_NEEDS_GROUP",
                    "Homebrew is rated only in a group of at least %d people".formatted(Beer.HOMEBREW_MIN_GROUP));
        }

        Instant now = clock.instant();
        LocalDate day = rules.dayOf(now);
        TastingRules.Ledger ledger = new TastingRules.Ledger(
                tastings.countByUserIdAndTastedDay(userId, day),
                new HashSet<>(tastings.findSoloDays(userId)),
                tastings.existsByUserIdAndBeerId(userId, beerId));
        rules.check(now, solo, ledger);

        return tastings.save(new Tasting(userId, beerId, sessionId, score, prediction, notes, structured, tags, now, day));
    }

    @Transactional(readOnly = true)
    public Status status(UUID userId) {
        LocalDate today = rules.dayOf(clock.instant());
        return new Status(today,
                tastings.countByUserIdAndTastedDay(userId, today), season.maxTastingsPerDay(),
                tastings.findSoloDays(userId).size(), season.maxSoloDays(),
                rules.inSeason(today));
    }
}
