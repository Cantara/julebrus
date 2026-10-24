package no.cantara.julebrus.web;

import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.tasting.TastingService;
import no.cantara.julebrus.user.CurrentUser;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.weight.RaterWeight;
import no.cantara.julebrus.weight.RaterWeightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class MeController {

    public record Season(int year, LocalDate start, LocalDate end, int maxTastingsPerDay, int maxSoloDays) {
    }

    public record Weight(double weight, String reason) {
    }

    public record Me(UUID id, String email, String displayName, TastingService.Status status, Season season,
                     Weight weight, boolean admin, boolean consentRequired, String consentVersion) {
    }

    private final CurrentUser currentUser;
    private final TastingService tastings;
    private final SeasonProperties season;
    private final RaterWeightService weights;
    private final no.cantara.julebrus.config.AuthProperties auth;
    private final ConsentService consents;

    public MeController(CurrentUser currentUser, TastingService tastings, SeasonProperties season,
                        RaterWeightService weights, no.cantara.julebrus.config.AuthProperties auth,
                        ConsentService consents) {
        this.currentUser = currentUser;
        this.tastings = tastings;
        this.season = season;
        this.weights = weights;
        this.auth = auth;
        this.consents = consents;
    }

    @GetMapping("/api/me")
    public Me me() {
        User u = currentUser.get();
        RaterWeight w = weights.current(u);
        return new Me(u.getId(), u.getEmail(), u.getDisplayName(), tastings.status(u.getId()),
                new Season(season.year(), season.start(), season.end(), season.maxTastingsPerDay(), season.maxSoloDays()),
                new Weight(w.weight(), w.getReason()), auth.isAdmin(u.getEmail()),
                !consents.hasAccepted(u.getId()), ConsentService.CONSENT_VERSION);
    }
}
