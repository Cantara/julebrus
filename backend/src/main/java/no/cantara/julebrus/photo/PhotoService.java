package no.cantara.julebrus.photo;

import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.config.AuthProperties;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Label photos: upload (sanitised, quota-limited), who may see which, delete, and the admin decision.
 * The quotas are checked once before the expensive decoding (so a person over the limit costs nothing) and
 * again inside a transaction that holds a row lock on the person, so parallel requests cannot all pass.
 */
@Service
public class PhotoService {

    public static final int MAX_PER_DAY = 5;
    public static final int MAX_PER_SEASON = 30;
    /** Pictures decoded at the same time. Each is bounded in memory by sub-sampling; this bounds the CPU too. */
    public static final int MAX_CONCURRENT_DECODES = 3;

    /** What a person sees of a photo row. */
    public record PhotoView(UUID id, UUID beerId, BeerPhoto.Status status, int width, int height, Instant createdAt) {
        static PhotoView of(BeerPhoto p) {
            return new PhotoView(p.getId(), p.getBeerId(), p.getStatus(), p.getWidth(), p.getHeight(), p.getCreatedAt());
        }
    }

    /** The bytes to serve. */
    public record Image(UUID id, byte[] jpeg) {
    }

    /** Per-caller photo facts for the beer list: which beers have a cover for them, and their own newest photo per beer. */
    public record Covers(Set<UUID> beersWithCover, Map<UUID, BeerPhoto> mine) {
    }

    private final BeerPhotoRepository photos;
    private final PhotoStore store;
    private final BeerRepository beers;
    private final UserRepository users;
    private final AuthProperties auth;
    private final SeasonProperties season;
    private final ConsentService consents;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Semaphore decoding = new Semaphore(MAX_CONCURRENT_DECODES);
    /** People with an upload being processed right now: one at a time each. */
    private final Set<UUID> uploading = ConcurrentHashMap.newKeySet();

    public PhotoService(BeerPhotoRepository photos, PhotoStore store, BeerRepository beers, UserRepository users,
                        AuthProperties auth, SeasonProperties season, ConsentService consents, Clock clock,
                        PlatformTransactionManager txm) {
        this.photos = photos;
        this.store = store;
        this.beers = beers;
        this.users = users;
        this.auth = auth;
        this.season = season;
        this.consents = consents;
        this.clock = clock;
        this.tx = new TransactionTemplate(txm);
    }

    // ---- upload

    /** Claims the person's single upload slot; false if one of their uploads is already in flight. */
    boolean beginUpload(UUID userId) {
        return uploading.add(userId);
    }

    void endUpload(UUID userId) {
        uploading.remove(userId);
    }

    public PhotoView upload(User me, UUID beerId, byte[] bytes) {
        if (!beginUpload(me.getId())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PHOTO_IN_PROGRESS", "Another picture from you is still being processed");
        }
        try {
            return doUpload(me, beerId, bytes);
        } finally {
            endUpload(me.getId());
        }
    }

    private PhotoView doUpload(User me, UUID beerId, byte[] bytes) {
        Beer beer = beers.findById(beerId).orElseThrow(PhotoService::noSuchBeer);
        if (beer.getStatus() == Beer.Status.REJECTED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BEER_REJECTED", "That beer was rejected by the organisers");
        }
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_MISSING", "No picture was sent");
        }
        boolean admin = auth.isAdmin(me.getEmail());
        checkQuota(me.getId(), beerId, admin); // cheap refusal before any decoding

        ImageSanitizer.Clean clean;
        try {
            if (!decoding.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PHOTO_BUSY", "Busy right now, try again in a moment");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PHOTO_BUSY", "Busy right now, try again in a moment");
        }
        try {
            clean = ImageSanitizer.sanitize(bytes);
        } finally {
            decoding.release();
        }

        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            // One person's uploads run one at a time from here to the commit.
            users.lockById(me.getId()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Unknown user"));
            checkQuota(me.getId(), beerId, admin);
            Instant now = clock.instant();
            // An admin's photo is approved at once, by themselves.
            store.insert(id, beerId, me.getId(), admin ? BeerPhoto.Status.APPROVED : BeerPhoto.Status.PENDING,
                    clean.width(), clean.height(), clean.jpeg(), now, admin ? me.getId() : null);
        });
        return PhotoView.of(photos.findById(id).orElseThrow());
    }

    private void checkQuota(UUID userId, UUID beerId, boolean admin) {
        if (photos.countByUserIdAndBeerIdAndStatusAndDeletedAtIsNull(userId, beerId, BeerPhoto.Status.PENDING) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PHOTO_PENDING_EXISTS",
                    "Your earlier picture of this beer is still waiting for approval");
        }
        if (admin) return; // organisers photograph whole shelves
        var zone = season.zone();
        Instant dayStart = LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
        if (photos.countByUserIdAndCreatedAtGreaterThanEqual(userId, dayStart) >= MAX_PER_DAY) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_DAILY_LIMIT",
                    "At most " + MAX_PER_DAY + " pictures a day");
        }
        Instant seasonStart = LocalDate.of(season.year(), 1, 1).atStartOfDay(zone).toInstant();
        if (photos.countByUserIdAndCreatedAtGreaterThanEqual(userId, seasonStart) >= MAX_PER_SEASON) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PHOTO_SEASON_LIMIT",
                    "At most " + MAX_PER_SEASON + " pictures per season");
        }
    }

    // ---- viewing

    private static ApiException noSuchBeer() {
        return new ApiException(HttpStatus.NOT_FOUND, "BEER_NOT_FOUND", "No such beer");
    }

    private static ApiException noSuchPhoto() {
        return new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "No such picture");
    }

    /** A rejected beer is only visible to the person who added it (and admins), as in the beer list. */
    private boolean beerVisible(Beer b, User me) {
        return b.getStatus() != Beer.Status.REJECTED || me.getId().equals(b.getCreatedBy()) || auth.isAdmin(me.getEmail());
    }

    /** The cover of a beer for this caller. Empty (404) for everything they may not see, including unknown beers. */
    public Optional<Image> cover(UUID beerId, User me) {
        Optional<Beer> beer = beers.findById(beerId).filter(b -> beerVisible(b, me));
        if (beer.isEmpty()) return Optional.empty();
        return photos.coverCandidates(beerId, me.getId(), ConsentService.CONSENT_VERSION).stream().findFirst().flatMap(this::image);
    }

    /**
     * A specific photo: the admin (for moderation), the uploader, or anyone once it is approved (and its beer is
     * visible). Others see it only while the uploader's consent is active; admins and the uploader are not held to that.
     */
    public Optional<Image> image(UUID photoId, User me) {
        Optional<BeerPhoto> p = photos.findLive(photoId);
        if (p.isEmpty()) return Optional.empty();
        BeerPhoto photo = p.get();
        boolean own = me.getId().equals(photo.getUserId());
        boolean admin = auth.isAdmin(me.getEmail());
        boolean shown = photo.getStatus() == BeerPhoto.Status.APPROVED
                && consents.hasAccepted(photo.getUserId())
                && beers.findById(photo.getBeerId()).filter(b -> beerVisible(b, me)).isPresent();
        if (!own && !admin && !shown) return Optional.empty();
        return image(photo);
    }

    private Optional<Image> image(BeerPhoto p) {
        return store.data(p.getId()).filter(d -> d.length > 0).map(d -> new Image(p.getId(), d));
    }

    /** For the beer list: no image bytes are read. */
    public Covers covers(User me) {
        Set<UUID> with = photos.coverBeerIds(me.getId(), ConsentService.CONSENT_VERSION);
        Map<UUID, BeerPhoto> mine = new HashMap<>();
        for (BeerPhoto p : photos.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(me.getId())) {
            mine.putIfAbsent(p.getBeerId(), p); // newest first
        }
        return new Covers(with, mine);
    }

    // ---- delete

    /** The uploader (any status) or an admin. Anything else, or an unknown id, is a 404. */
    public void delete(UUID photoId, User me) {
        BeerPhoto p = photos.findLive(photoId).orElseThrow(PhotoService::noSuchPhoto);
        if (!me.getId().equals(p.getUserId()) && !auth.isAdmin(me.getEmail())) throw noSuchPhoto();
        tx.executeWithoutResult(s -> {
            if (!store.delete(photoId, clock.instant())) throw noSuchPhoto();
        });
    }

    // ---- admin

    public List<BeerPhotoRepository.AdminRow> queue(BeerPhoto.Status status) {
        return photos.adminQueue(status);
    }

    public enum Choice { APPROVE, REJECT }

    /** One conditional update decides: a second, parallel decision finds the row no longer pending and gets 409. */
    public BeerPhotoRepository.AdminRow decide(UUID photoId, Choice choice, User admin) {
        BeerPhoto p = photos.findLive(photoId).orElseThrow(PhotoService::noSuchPhoto);
        // Someone who withdrew consent: nothing about them is shown or decided, and no name or email goes back.
        if (!consents.hasAccepted(p.getUserId())) throw noSuchPhoto();
        boolean won = Boolean.TRUE.equals(tx.execute(s -> store.decide(photoId,
                choice == Choice.APPROVE ? BeerPhoto.Status.APPROVED : BeerPhoto.Status.REJECTED, admin.getId(), clock.instant())));
        if (!won) throw new ApiException(HttpStatus.CONFLICT, "PHOTO_NOT_PENDING", "That picture is already decided");
        BeerPhoto after = photos.findLive(photoId).orElseThrow(PhotoService::noSuchPhoto);
        User up = users.findById(after.getUserId()).orElse(null);
        Beer beer = beers.findById(after.getBeerId()).orElse(null);
        return new BeerPhotoRepository.AdminRow(after.getId(), after.getBeerId(), beer == null ? "?" : beer.getName(),
                after.getUserId(), up == null ? "?" : up.getDisplayName(), up == null ? null : up.getEmail(),
                after.getStatus(), p.getCreatedAt());
    }
}
