package no.cantara.julebrus.tip;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.user.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tips")
public class TipController {

    public record Pick(UUID beerId, String name, String brewery) {
    }

    /** Gold, silver, bronze. */
    public record TipView(List<Pick> podium, Instant createdAt, Instant updatedAt) {
    }

    /** The person's own tip (null before they tip), when tipping closes, and how many have tipped. */
    public record MyTip(TipView tip, Instant deadline, boolean closed, long tipped) {
    }

    public record PlaceRequest(@NotNull UUID firstBeerId, @NotNull UUID secondBeerId, @NotNull UUID thirdBeerId) {
    }

    private final TipService service;
    private final CurrentUser currentUser;
    private final BeerRepository beers;
    private final SeasonProperties season;

    public TipController(TipService service, CurrentUser currentUser, BeerRepository beers, SeasonProperties season) {
        this.service = service;
        this.currentUser = currentUser;
        this.beers = beers;
        this.season = season;
    }

    @GetMapping("/me")
    public MyTip mine() {
        UUID me = currentUser.get().getId();
        return new MyTip(service.mine(me).map(this::view).orElse(null), season.tipDeadline(), service.closed(), service.count());
    }

    @PutMapping
    public MyTip place(@Valid @RequestBody PlaceRequest in) {
        UUID me = currentUser.get().getId();
        Tip tip = service.place(me, in.firstBeerId(), in.secondBeerId(), in.thirdBeerId());
        return new MyTip(view(tip), season.tipDeadline(), service.closed(), service.count());
    }

    @DeleteMapping
    public MyTip withdraw() {
        service.withdraw(currentUser.get().getId());
        return new MyTip(null, season.tipDeadline(), service.closed(), service.count());
    }

    TipView view(Tip t) {
        Map<UUID, Beer> byId = beers.findAllById(t.podium()).stream().collect(Collectors.toMap(Beer::getId, Function.identity()));
        List<Pick> picks = t.podium().stream().map(id -> {
            Beer b = byId.get(id);
            return new Pick(id, b == null ? "?" : b.getName(), b == null ? null : b.getBrewery());
        }).toList();
        return new TipView(picks, t.getCreatedAt(), t.getUpdatedAt());
    }
}
