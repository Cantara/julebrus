package no.cantara.julebrus.beer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    /** Bounds match the columns in V1__init.sql (name/brewery 255, style 100, Polet number 32, image url 1024). */
    public record NewBeer(@NotBlank @Size(max = 255) @Pattern(regexp = NO_CONTROL, message = "must not contain control characters") String name,
                          @Size(max = 255) @Pattern(regexp = NO_CONTROL, message = "must not contain control characters") String brewery,
                          @Size(max = 100) @Pattern(regexp = NO_CONTROL, message = "must not contain control characters") String style,
                          @DecimalMin("0") @DecimalMax("20") @Digits(integer = 2, fraction = 1) BigDecimal abv,
                          @Size(max = 32) @Pattern(regexp = NO_CONTROL, message = "must not contain control characters") String vinmonopoletId,
                          @Size(max = 1024) @Pattern(regexp = "^(https?://\\S*)?$", message = "must be an http(s) link") String imageUrl) {
    }

    /** No Unicode control characters (C0, DEL, C1) and no line or paragraph separators. */
    static final String NO_CONTROL = "^[^\\p{Cc}\\p{Zl}\\p{Zp}]*$";

    private final BeerRepository beers;
    private final BeerService beerService;
    private final TastingRepository tastings;
    private final CurrentUser currentUser;

    public BeerController(BeerRepository beers, BeerService beerService, TastingRepository tastings, CurrentUser currentUser) {
        this.beers = beers;
        this.beerService = beerService;
        this.tastings = tastings;
        this.currentUser = currentUser;
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
        Beer saved = beerService.create(me, in);
        return BeerView.of(saved, 0, me.getId());
    }
}
