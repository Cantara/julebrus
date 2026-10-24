package no.cantara.julebrus.tasting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import no.cantara.julebrus.user.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tastings")
public class TastingController {

    /** Suggested descriptors; free text tags are allowed too. */
    public static final List<String> SUGGESTED_TAGS = List.of(
            "malty", "roasted", "caramel", "toffee", "chocolate", "coffee", "liquorice",
            "dried fruit", "raisin", "fig", "orange peel", "spice", "clove", "cinnamon",
            "hoppy", "floral", "piney", "boozy", "sweet", "dry", "thin", "full-bodied", "balanced");

    public record NewTasting(
            @NotNull UUID beerId,
            UUID sessionId,
            @Min(1) @Max(6) int score,
            @DecimalMin("1.0") @DecimalMax("6.0") BigDecimal prediction,
            @Size(max = 2000) String notes,
            @Size(max = 1000) String appearance,
            @Size(max = 1000) String aroma,
            @Size(max = 1000) String taste,
            @Size(max = 1000) String finish,
            Set<@Size(max = 40) String> tags) {
    }

    public record TastingView(UUID id, UUID beerId, UUID sessionId, int score, BigDecimal prediction, String notes,
                              Tasting.Notes structuredNotes, Set<String> tags, Instant tastedAt,
                              /** Oslo calendar day, so the advent calendar never has to guess the zone. */
                              java.time.LocalDate tastedDay) {
        static TastingView of(Tasting t) {
            return new TastingView(t.getId(), t.getBeerId(), t.getSessionId(), t.getScore(), t.getPrediction(),
                    t.getNotes(), t.getStructuredNotes(), t.getTags(), t.getTastedAt(), t.getTastedDay());
        }
    }

    private final TastingService service;
    private final TastingRepository tastings;
    private final CurrentUser currentUser;

    public TastingController(TastingService service, TastingRepository tastings, CurrentUser currentUser) {
        this.service = service;
        this.tastings = tastings;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TastingView rate(@Valid @RequestBody NewTasting in) {
        Set<String> tags = in.tags() == null ? Set.of() : in.tags().stream()
                .map(t -> t.trim().toLowerCase(Locale.ROOT)).filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Tasting.Notes structured = new Tasting.Notes(in.appearance(), in.aroma(), in.taste(), in.finish());
        Tasting saved = service.rate(currentUser.get().getId(), in.beerId(), in.sessionId(), in.score(),
                in.prediction(), in.notes(), structured.isEmpty() ? null : structured, tags);
        return TastingView.of(saved);
    }

    @GetMapping("/mine")
    public List<TastingView> mine() {
        return tastings.findByUserIdOrderByTastedAtDesc(currentUser.get().getId()).stream()
                .map(TastingView::of).toList();
    }

    @GetMapping("/tags")
    public List<String> suggestedTags() {
        return SUGGESTED_TAGS;
    }
}
