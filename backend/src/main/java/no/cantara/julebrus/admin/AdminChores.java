package no.cantara.julebrus.admin;

import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.history.ClaimService;
import no.cantara.julebrus.history.HistoricRaterClaim;
import no.cantara.julebrus.photo.BeerPhoto;
import no.cantara.julebrus.photo.BeerPhotoRepository;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What waits for an organiser: the same four queues the admin page shows, counted so the
 * home screen and the tab bar can say "noe venter" without opening it. Counted only for
 * people with active consent, like the queues themselves.
 */
@Service
public class AdminChores {

    /**
     * @param claims    history claims waiting for a decision
     * @param unmatched logins with active consent that no historic name is tied to, by claim, admin link or raters.csv
     * @param beers     beers someone added that wait for approval
     * @param photos    label photos that wait for approval
     * @param noConsent    people who signed in but never gave, or withdrew, a consent: nothing of theirs counts until they do
     * @param staleConsent people whose active consent is to an older text; asked again at their next visit, and until then
     *                     treated like noConsent everywhere data is shown
     * @param noTeam       people with consent who have not answered the team question yet
     */
    public record Chores(int claims, int unmatched, int beers, int photos, int noConsent, int staleConsent, int noTeam, int total) {
        static Chores of(int claims, int unmatched, int beers, int photos, int noConsent, int staleConsent, int noTeam) {
            return new Chores(claims, unmatched, beers, photos, noConsent, staleConsent, noTeam,
                    claims + unmatched + beers + photos + noConsent + staleConsent + noTeam);
        }
    }

    private final ClaimService claims;
    private final BeerRepository beers;
    private final BeerPhotoRepository photos;
    private final UserRepository users;
    private final ConsentService consents;

    public AdminChores(ClaimService claims, BeerRepository beers, BeerPhotoRepository photos, UserRepository users,
                       ConsentService consents) {
        this.claims = claims;
        this.beers = beers;
        this.photos = photos;
        this.users = users;
        this.consents = consents;
    }

    @Transactional(readOnly = true)
    public Chores count() {
        Set<UUID> active = consents.activeUserIds();
        int pendingClaims = (int) claims.byStatus(HistoricRaterClaim.Status.PENDING).stream()
                .filter(c -> active.contains(c.getUserId())).count();
        Collection<UUID> owners = claims.approvedOwners().values();
        Set<String> emailsInRatersCsv = claims.candidates().values().stream()
                .map(ClaimService.Candidate::loginEmail).filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<User> all = users.findAll();
        Set<UUID> answered = consents.newestActiveVersions().keySet();
        int noConsent = (int) all.stream().filter(u -> !answered.contains(u.getId())).count();
        int staleConsent = (int) all.stream().filter(u -> answered.contains(u.getId()) && !active.contains(u.getId())).count();
        int noTeam = (int) all.stream().filter(u -> active.contains(u.getId()) && u.getTeamsChosenAt() == null).count();
        int unmatched = (int) all.stream()
                .filter(u -> active.contains(u.getId()))
                .filter(u -> !owners.contains(u.getId()))
                .filter(u -> !emailsInRatersCsv.contains(u.getEmail().toLowerCase(Locale.ROOT)))
                .map(User::getId).count();
        int pendingBeers = beers.findByStatus(Beer.Status.PENDING).size();
        int pendingPhotos = (int) photos.adminQueue(BeerPhoto.Status.PENDING).stream()
                .filter(r -> active.contains(r.userId())).count();
        return Chores.of(pendingClaims, unmatched, pendingBeers, pendingPhotos, noConsent, staleConsent, noTeam);
    }
}
