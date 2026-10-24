package no.cantara.julebrus.duel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.session.SessionService;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/duels")
public class DuelController {

    public record NewDuel(@NotNull UUID sessionId, @NotNull UUID winnerBeerId, @NotNull UUID loserBeerId) {
    }

    public record Pair(UUID beerA, UUID beerB) {
    }

    private final DuelRepository duels;
    private final SessionService sessions;
    private final TastingRepository tastings;
    private final BeerRepository beers;
    private final CurrentUser currentUser;

    private final no.cantara.julebrus.live.SessionEvents events;

    public DuelController(DuelRepository duels, SessionService sessions, TastingRepository tastings, BeerRepository beers,
                          CurrentUser currentUser, no.cantara.julebrus.live.SessionEvents events) {
        this.beers = beers;
        this.events = events;
        this.duels = duels;
        this.sessions = sessions;
        this.tastings = tastings;
        this.currentUser = currentUser;
    }

    /**
     * The pairs this member still owes an answer for: every pair of beers they have
     * rated in the session and not yet compared.
     */
    @GetMapping("/pending/{sessionId}")
    public List<Pair> pending(@PathVariable UUID sessionId) {
        UUID userId = currentUser.get().getId();
        TastingSession session = sessions.getForMember(sessionId, userId);
        List<UUID> ratedAll = tastings.findBySessionId(session.getId()).stream()
                .filter(t -> t.getUserId().equals(userId))
                .map(Tasting::getBeerId).toList();
        Set<UUID> rejected = rejectedAmong(ratedAll);
        List<UUID> rated = ratedAll.stream().filter(id -> !rejected.contains(id)).toList();
        Set<String> answered = duels.findByUserIdAndSessionId(userId, sessionId).stream()
                .map(d -> key(d.getWinnerBeerId(), d.getLoserBeerId()))
                .collect(Collectors.toSet());
        List<Pair> out = new ArrayList<>();
        for (int i = 0; i < rated.size(); i++) {
            for (int j = i + 1; j < rated.size(); j++) {
                if (!answered.contains(key(rated.get(i), rated.get(j)))) {
                    out.add(new Pair(rated.get(i), rated.get(j)));
                }
            }
        }
        return out;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void answer(@Valid @RequestBody NewDuel in) {
        UUID userId = currentUser.get().getId();
        TastingSession session = sessions.getForMember(in.sessionId(), userId);
        if (in.winnerBeerId().equals(in.loserBeerId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A beer cannot duel itself");
        }
        if (!session.hasBeer(in.winnerBeerId()) || !session.hasBeer(in.loserBeerId())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Both beers must be in the session");
        }
        if (!rejectedAmong(List.of(in.winnerBeerId(), in.loserBeerId())).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "A rejected beer cannot be compared");
        }
        boolean alreadyAnswered = duels.findByUserIdAndSessionId(userId, in.sessionId()).stream()
                .anyMatch(d -> key(d.getWinnerBeerId(), d.getLoserBeerId()).equals(key(in.winnerBeerId(), in.loserBeerId())));
        if (alreadyAnswered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already answered");
        }
        duels.save(new Duel(userId, in.sessionId(), in.winnerBeerId(), in.loserBeerId()));
        events.publish(in.sessionId(), no.cantara.julebrus.live.SessionEvents.Kind.DUEL);
    }

    /** Beers the organisers rejected, among these (also after they were added to a table). */
    private Set<UUID> rejectedAmong(List<UUID> ids) {
        return beers.findAllById(ids).stream().filter(b -> b.getStatus() == Beer.Status.REJECTED)
                .map(Beer::getId).collect(Collectors.toSet());
    }

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
}
