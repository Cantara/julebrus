package no.cantara.julebrus.beer;

import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adding a beer: the per-person pending cap and duplicate check run under a lock on the person's row. */
@Service
public class BeerService {

    /** How many beers one person may have waiting for approval at once. */
    public static final int MAX_PENDING_PER_USER = 10;

    private final BeerRepository beers;
    private final UserRepository users;
    private final AuthProperties auth;

    public BeerService(BeerRepository beers, UserRepository users, AuthProperties auth) {
        this.beers = beers;
        this.users = users;
        this.auth = auth;
    }

    /** An admin's beer goes straight in, everyone else's waits for approval. */
    @Transactional
    public Beer create(User me, BeerController.NewBeer in) {
        // One person's additions run one at a time, so parallel requests cannot pass the cap or the duplicate check together.
        users.lockById(me.getId()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unknown user"));
        if (Beer.isGroceryStrength(in.abv())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BEER_GROCERY_STRENGTH",
                    "Only Vinmonopolet juleøl counts: above %s %%".formatted(Beer.GROCERY_MAX_ABV));
        }
        if (in.homebrew() && in.abv() == null) {
            // Homebrew has no Polet listing to vouch for its strength, so the brewer states it.
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BEER_HOMEBREW_NEEDS_ABV",
                    "State the ABV of a homebrew; it must be above %s %%".formatted(Beer.GROCERY_MAX_ABV));
        }
        String name = in.name().trim();
        String key = BeerCsv.key(name);
        if (beers.findByCreatedBy(me.getId()).stream().anyMatch(b -> BeerCsv.key(b.getName()).equals(key))) {
            throw new ApiException(HttpStatus.CONFLICT, "BEER_DUPLICATE", "You have already added a beer with that name");
        }
        boolean admin = auth.isAdmin(me.getEmail());
        if (!admin && beers.countByCreatedByAndStatus(me.getId(), Beer.Status.PENDING) >= MAX_PENDING_PER_USER) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BEER_PENDING_LIMIT",
                    "You can have at most " + MAX_PENDING_PER_USER + " beers waiting for approval");
        }
        return beers.save(new Beer(name, blankToNull(in.brewery()), blankToNull(in.style()), in.abv(),
                blankToNull(in.vinmonopoletId()), blankToNull(in.imageUrl()), me.getId(),
                admin ? Beer.Status.APPROVED : Beer.Status.PENDING, in.homebrew()));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
