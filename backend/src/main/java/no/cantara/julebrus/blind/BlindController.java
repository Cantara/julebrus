package no.cantara.julebrus.blind;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sessions/{sessionId}/blind")
public class BlindController {

    public record OptionView(UUID beerId, String name, String brewery) {
    }

    /**
     * Who has guessed is always visible; what they guessed, and the answer, only once
     * revealed. {@code myGuess} lets a guesser see their own pick while waiting.
     */
    public record RoundView(UUID id, UUID hostId, Instant createdAt, Instant revealedAt, boolean revealed,
                            List<OptionView> options, List<UUID> guessedBy, UUID myGuess,
                            UUID answerBeerId, Map<UUID, UUID> guesses, List<UUID> correct) {
    }

    public record StartRequest(@NotNull UUID answerBeerId, @NotNull List<UUID> optionBeerIds) {
    }

    public record GuessRequest(@NotNull UUID beerId) {
    }

    private final BlindService service;
    private final CurrentUser currentUser;
    private final BeerRepository beers;

    public BlindController(BlindService service, CurrentUser currentUser, BeerRepository beers) {
        this.service = service;
        this.currentUser = currentUser;
        this.beers = beers;
    }

    @GetMapping
    public List<RoundView> list(@PathVariable UUID sessionId) {
        UUID me = currentUser.get().getId();
        return service.list(sessionId, me).stream().map(r -> view(r, me)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoundView start(@PathVariable UUID sessionId, @Valid @RequestBody StartRequest in) {
        UUID me = currentUser.get().getId();
        return view(service.start(sessionId, me, in.answerBeerId(), in.optionBeerIds()), me);
    }

    @PostMapping("/{roundId}/guess")
    public RoundView guess(@PathVariable UUID sessionId, @PathVariable UUID roundId, @Valid @RequestBody GuessRequest in) {
        UUID me = currentUser.get().getId();
        return view(service.guess(sessionId, roundId, me, in.beerId()), me);
    }

    @PostMapping("/{roundId}/reveal")
    public RoundView reveal(@PathVariable UUID sessionId, @PathVariable UUID roundId) {
        UUID me = currentUser.get().getId();
        return view(service.reveal(sessionId, roundId, me), me);
    }

    private RoundView view(BlindRound r, UUID me) {
        Map<UUID, Beer> beerById = beers.findAllById(r.getOptions()).stream()
                .collect(Collectors.toMap(Beer::getId, Function.identity()));
        List<OptionView> options = r.getOptions().stream().map(id -> {
            Beer b = beerById.get(id);
            return new OptionView(id, b == null ? "?" : b.getName(), b == null ? null : b.getBrewery());
        }).toList();
        List<UUID> guessedBy = r.getGuesses().stream().map(BlindRound.Guess::userId).toList();
        UUID myGuess = r.guessOf(me).map(BlindRound.Guess::beerId).orElse(null);
        boolean revealed = r.isRevealed();
        Map<UUID, UUID> guesses = null;
        List<UUID> correct = null;
        if (revealed) {
            guesses = new LinkedHashMap<>();
            for (BlindRound.Guess g : r.getGuesses()) guesses.put(g.userId(), g.beerId());
            correct = r.getGuesses().stream().filter(r::isCorrect).map(BlindRound.Guess::userId).toList();
        }
        return new RoundView(r.getId(), r.getHostId(), r.getCreatedAt(), r.getRevealedAt(), revealed, options,
                guessedBy, myGuess, revealed ? r.getAnswerBeerId() : null, guesses, correct);
    }
}
