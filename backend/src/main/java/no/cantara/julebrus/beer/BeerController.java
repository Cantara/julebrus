package no.cantara.julebrus.beer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import no.cantara.julebrus.tasting.TastingRepository;
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

    public record BeerView(UUID id, String name, String brewery, String style, BigDecimal abv,
                           String vinmonopoletId, String imageUrl, long ratingCount) {
        static BeerView of(Beer b, long ratingCount) {
            return new BeerView(b.getId(), b.getName(), b.getBrewery(), b.getStyle(), b.getAbv(),
                    b.getVinmonopoletId(), b.getImageUrl(), ratingCount);
        }
    }

    public record NewBeer(@NotBlank String name, String brewery, String style, BigDecimal abv,
                          String vinmonopoletId, String imageUrl) {
    }

    private final BeerRepository beers;
    private final TastingRepository tastings;

    public BeerController(BeerRepository beers, TastingRepository tastings) {
        this.beers = beers;
        this.tastings = tastings;
    }

    @GetMapping
    public List<BeerView> list(@RequestParam(required = false) String q) {
        Map<UUID, Long> counts = tastings.countPerBeer().stream()
                .collect(Collectors.toMap(TastingRepository.BeerCount::beerId, TastingRepository.BeerCount::count));
        return beers.search(q).stream()
                .map(b -> BeerView.of(b, counts.getOrDefault(b.getId(), 0L)))
                .toList();
    }

    /** The long tail: beers with the fewest ratings first, for the home screen. */
    @GetMapping("/least-tasted")
    public List<BeerView> leastTasted(@RequestParam(defaultValue = "5") int limit) {
        Map<UUID, Long> counts = tastings.countPerBeer().stream()
                .collect(Collectors.toMap(TastingRepository.BeerCount::beerId, TastingRepository.BeerCount::count));
        return beers.findAll().stream()
                .map(b -> BeerView.of(b, counts.getOrDefault(b.getId(), 0L)))
                .sorted(java.util.Comparator.comparingLong(BeerView::ratingCount).thenComparing(BeerView::name))
                .limit(limit)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BeerView create(@Valid @RequestBody NewBeer in) {
        Beer saved = beers.save(new Beer(in.name().trim(), in.brewery(), in.style(), in.abv(),
                blankToNull(in.vinmonopoletId()), blankToNull(in.imageUrl())));
        return BeerView.of(saved, 0);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
