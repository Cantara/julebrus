package no.cantara.julebrus.advent;

import no.cantara.julebrus.config.AdventProperties;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * "Mørkelegging": from the start of {@code julebrus.advent.blackout-from} until the reveal, the running
 * ranking is hidden everywhere it shows (results, team ranking, duels, a beer's rank and dice, the wall's
 * day average, the MCP ranking tools) and the doors drip clues about the leader instead. An admin can
 * still look with {@code preview=true} on the results endpoints, as on the finale.
 */
@Component
public class Blackout {

    private final AdventProperties advent;
    private final SeasonProperties season;
    private final AuthProperties auth;
    private final Clock clock;

    public Blackout(AdventProperties advent, SeasonProperties season, AuthProperties auth, Clock clock) {
        this.advent = advent;
        this.season = season;
        this.auth = auth;
        this.clock = clock;
    }

    public Instant startsAt() {
        return advent.blackoutFrom() == null ? null : advent.blackoutFrom().atStartOfDay(season.zone()).toInstant();
    }

    public boolean active() {
        Instant from = startsAt();
        if (from == null) return false;
        Instant now = clock.instant();
        return !now.isBefore(from) && now.isBefore(season.revealAt());
    }

    /** Throws 423 BLACKOUT unless the ranking may be shown to this person right now. */
    public void guard(User me, boolean preview) {
        if (active() && !(preview && auth.isAdmin(me.getEmail()))) {
            throw new ApiException(HttpStatus.LOCKED, "BLACKOUT", "The ranking is hidden until the finale");
        }
    }
}
