package no.cantara.julebrus.beer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/beers")
public class BeerController {

    /** {@code status} is PENDING for a beer someone added that an admin has not approved yet. */
    public record BeerView(UUID id, String name, String brewery, String style, BigDecimal abv,
                           String vinmonopoletId, String imageUrl, long ratingCount, Beer.Status status, boolean mine) {
        static BeerView of(Beer b, long ratingCount, UUID me) {
            return new BeerView(b.getId(), b.getName(), b.getBrewery(), b.getStyle(), b.getAbv(),
                    b.getVinmonopoletId(), b.getImageUrl(), ratingCount, b.getStatus(),
                    me != null && me.equals(b.getCreatedBy()));
        }
    }

    public record NewBeer(@NotBlank String name, String brewery, String style, BigDecimal abv,
                          String vinmonopoletId, String imageUrl) {
    }

    private final BeerRepository beers;
    private final TastingRepository tastings;
    private final CurrentUser currentUser;
    private final AuthProperties auth;

    public BeerController(BeerRepository beers, TastingRepository tastings, CurrentUser currentUser, AuthProperties auth) {
        this.beers = beers;
        this.tastings = tastings;
        this.currentUser = currentUser;
        this.auth = auth;
    }

    /** Approved and pending beers for everyone; a rejected beer only for the person who added it. */
    @GetMapping
    public List<BeerView> list(@RequestParam(required = false) String q) {
        UUID me = currentUser.get().getId();
        Map<UUID, Long> counts = tastings.countPerBeer().stream()
                .collect(Collectors.toMap(TastingRepository.BeerCount::beerId, TastingRepository.BeerCount::count));
        return beers.search(q).stream()
                .filter(b -> b.getStatus() != Beer.Status.REJECTED || me.equals(b.getCreatedBy()))
                .map(b -> BeerView.of(b, counts.getOrDefault(b.getId(), 0L), me))
                .toList();
    }

    /** The long tail: approved beers with the fewest ratings first, for the home screen. */
    @GetMapping("/least-tasted")
    public List<BeerView> leastTasted(@RequestParam(defaultValue = "5") int limit) {
        UUID me = currentUser.get().getId();
        Map<UUID, Long> counts = tastings.countPerBeer().stream()
                .collect(Collectors.toMap(TastingRepository.BeerCount::beerId, TastingRepository.BeerCount::count));
        return beers.findByStatus(Beer.Status.APPROVED).stream()
                .map(b -> BeerView.of(b, counts.getOrDefault(b.getId(), 0L), me))
                .sorted(java.util.Comparator.comparingLong(BeerView::ratingCount).thenComparing(BeerView::name))
                .limit(limit)
                .toList();
    }

    /** Anyone may add a beer; an admin's goes straight in, everyone else's waits for approval. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BeerView create(@Valid @RequestBody NewBeer in) {
        User me = currentUser.get();
        Beer.Status status = auth.isAdmin(me.getEmail()) ? Beer.Status.APPROVED : Beer.Status.PENDING;
        Beer saved = beers.save(new Beer(in.name().trim(), in.brewery(), in.style(), in.abv(),
                blankToNull(in.vinmonopoletId()), blankToNull(in.imageUrl()), me.getId(), status));
        return BeerView.of(saved, 0, me.getId());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
