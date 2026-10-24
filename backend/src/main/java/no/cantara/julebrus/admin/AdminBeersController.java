package no.cantara.julebrus.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerCsv;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Beers people added, waiting for a yes or no. */
@RestController
@RequestMapping("/api/admin/beers")
public class AdminBeersController {

    public record PendingBeer(UUID id, String name, String brewery, String style, BigDecimal abv, String vinmonopoletId,
                              Beer.Status status, Instant createdAt, String addedBy) {
    }

    public enum Choice { APPROVE, REJECT }

    public record Decision(@NotNull Choice decision) {
    }

    private final CurrentUser currentUser;
    private final AuthProperties auth;
    private final BeerRepository beers;
    private final UserRepository users;
    private final Clock clock;

    public AdminBeersController(CurrentUser currentUser, AuthProperties auth, BeerRepository beers, UserRepository users, Clock clock) {
        this.currentUser = currentUser;
        this.auth = auth;
        this.beers = beers;
        this.users = users;
        this.clock = clock;
    }

    private User admin() {
        User me = currentUser.get();
        if (!auth.isAdmin(me.getEmail())) throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Admins only");
        return me;
    }

    @GetMapping
    public List<PendingBeer> list(@RequestParam(defaultValue = "PENDING") Beer.Status status) {
        admin();
        return beers.findByStatusOrderByCreatedAtAsc(status).stream().map(this::view).toList();
    }

    @PostMapping("/{id}/decision")
    @Transactional
    public PendingBeer decide(@PathVariable UUID id, @Valid @RequestBody Decision in) {
        User me = admin();
        Beer b = beers.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BEER_NOT_FOUND", "No such beer"));
        if (b.getStatus() != Beer.Status.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "BEER_NOT_PENDING", "That beer is already decided");
        }
        b.decide(in.decision() == Choice.APPROVE ? Beer.Status.APPROVED : Beer.Status.REJECTED, me.getId(), clock.instant());
        return view(beers.save(b));
    }

    public enum ImportAction { NEW, DUPLICATE, INVALID }

    public record ImportRow(int line, String name, String brewery, String style, BigDecimal abv, String vinmonopoletId,
                            ImportAction action, String reason) {
    }

    public record ImportResult(boolean dryRun, int created, List<ImportRow> rows) {
    }

    /**
     * CSV import for the Vinmonopolet drop or any other list. {@code dryRun=true} previews;
     * {@code dryRun=false} creates the NEW rows as approved beers. Duplicates are matched on
     * Vinmonopolet product number first, then on the normalised name.
     */
    @PostMapping(value = "/import", consumes = {"text/csv", "text/plain", "application/octet-stream"})
    @Transactional
    public ImportResult importCsv(@RequestParam(defaultValue = "true") boolean dryRun, @RequestBody String csv) {
        User me = admin();
        List<Beer> all = beers.findAll();
        Set<String> names = all.stream().map(b -> BeerCsv.key(b.getName())).collect(Collectors.toCollection(HashSet::new));
        Set<String> polet = all.stream().map(Beer::getVinmonopoletId).filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        List<ImportRow> rows = new ArrayList<>();
        int created = 0;
        for (BeerCsv.Row r : BeerCsv.parse(csv)) {
            ImportAction action;
            String reason = null;
            if (!r.valid()) {
                action = ImportAction.INVALID;
                reason = r.error();
            } else if (Beer.isGroceryStrength(r.abv())) {
                action = ImportAction.INVALID;
                reason = "Butikkstyrke (%s %% eller under), teller ikke".formatted(Beer.GROCERY_MAX_ABV);
            } else if (r.vinmonopoletId() != null && polet.contains(r.vinmonopoletId())) {
                action = ImportAction.DUPLICATE;
                reason = "Samme Polet-nummer finnes";
            } else if (names.contains(BeerCsv.key(r.name()))) {
                action = ImportAction.DUPLICATE;
                reason = "Samme navn finnes";
            } else {
                action = ImportAction.NEW;
                names.add(BeerCsv.key(r.name()));
                if (r.vinmonopoletId() != null) polet.add(r.vinmonopoletId());
                if (!dryRun) {
                    beers.save(new Beer(r.name(), r.brewery(), r.style(), r.abv(), r.vinmonopoletId(), null, me.getId(), Beer.Status.APPROVED));
                    created++;
                }
            }
            rows.add(new ImportRow(r.line(), r.name(), r.brewery(), r.style(), r.abv(), r.vinmonopoletId(), action, reason));
        }
        return new ImportResult(dryRun, created, rows);
    }

    private PendingBeer view(Beer b) {
        String addedBy = b.getCreatedBy() == null ? null
                : users.findById(b.getCreatedBy()).map(User::getDisplayName).orElse("?");
        return new PendingBeer(b.getId(), b.getName(), b.getBrewery(), b.getStyle(), b.getAbv(), b.getVinmonopoletId(),
                b.getStatus(), b.getCreatedAt(), addedBy);
    }
}
