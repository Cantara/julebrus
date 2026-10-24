package no.cantara.julebrus.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import no.cantara.julebrus.beer.Beer;
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
import java.util.List;
import java.util.UUID;

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

    private PendingBeer view(Beer b) {
        String addedBy = b.getCreatedBy() == null ? null
                : users.findById(b.getCreatedBy()).map(User::getDisplayName).orElse("?");
        return new PendingBeer(b.getId(), b.getName(), b.getBrewery(), b.getStyle(), b.getAbv(), b.getVinmonopoletId(),
                b.getStatus(), b.getCreatedAt(), addedBy);
    }
}
