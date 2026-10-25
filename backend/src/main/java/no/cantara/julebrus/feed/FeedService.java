package no.cantara.julebrus.feed;

import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.blind.BlindRound;
import no.cantara.julebrus.blind.BlindRoundRepository;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.live.SessionEvents;
import no.cantara.julebrus.photo.BeerPhoto;
import no.cantara.julebrus.photo.BeerPhotoRepository;
import no.cantara.julebrus.session.SessionRepository;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * "I kveld": the day's wall for the whole house. Who tasted what, which tables are going,
 * which labels got approved, how a blind round went, and the cheers people gave each other.
 *
 * <p>What it never shows is a person's dice. Scores are the table's business until the
 * reveal and the ranking's business after it; the wall gives the day's average per beer
 * only once {@link #MIN_FOR_AVERAGE} people have rolled it, so no single roll can be read
 * off it. People who withdrew consent are left out entirely, as everywhere else.
 */
@Service
public class FeedService {

    /** The cheer first, then four more. A fixed set: no free text, nothing to moderate. */
    public static final List<String> EMOJIS = List.of("🍻", "🎅", "🔥", "😂", "👏");

    /** A beer's day average is shown only from this many rolls. */
    public static final int MIN_FOR_AVERAGE = 3;

    public enum Target { TASTING, TABLE, PHOTO, BLIND }

    public record ReactionView(String emoji, int count, boolean mine) {
    }

    /**
     * One line on the wall. {@code kind} says which of the optional fields are filled:
     * TASTING has the person and the beer (and the table when there was one), TABLE has the
     * table, PHOTO has the beer, BLIND has the table, the beer and the tally.
     */
    public record Entry(Target kind, UUID id, Instant at,
                        UUID userId, String displayName, boolean me,
                        UUID beerId, String beerName, String brewery,
                        UUID tableId, String tableHost, Integer tableSize, Boolean open, Integer beers,
                        Integer correct, Integer guesses,
                        List<ReactionView> reactions) {
    }

    public record OpenTable(UUID id, String hostName, int members, int beers, boolean mine) {
    }

    /** The day's rolls on one beer; {@code average} only from {@link #MIN_FOR_AVERAGE} rolls. */
    public record DayBeer(UUID beerId, String name, String brewery, int count, Double average) {
    }

    public record FeedView(LocalDate day, LocalDate today, int tastings, int people,
                           List<OpenTable> openTables, List<Entry> entries, List<DayBeer> beers, List<String> emojis) {
    }

    private final TastingRepository tastings;
    private final SessionRepository sessions;
    private final BeerRepository beers;
    private final BeerPhotoRepository photos;
    private final BlindRoundRepository rounds;
    private final UserRepository users;
    private final ReactionRepository reactions;
    private final ConsentService consents;
    private final SeasonProperties season;
    private final SessionEvents events;
    private final Clock clock;

    public FeedService(TastingRepository tastings, SessionRepository sessions, BeerRepository beers,
                       BeerPhotoRepository photos, BlindRoundRepository rounds, UserRepository users,
                       ReactionRepository reactions, ConsentService consents, SeasonProperties season,
                       SessionEvents events, Clock clock) {
        this.tastings = tastings;
        this.sessions = sessions;
        this.beers = beers;
        this.photos = photos;
        this.rounds = rounds;
        this.users = users;
        this.reactions = reactions;
        this.consents = consents;
        this.season = season;
        this.events = events;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), season.zone());
    }

    @Transactional(readOnly = true)
    public FeedView view(User me, LocalDate day) {
        LocalDate today = today();
        Instant from = day.atStartOfDay(season.zone()).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(season.zone()).toInstant();
        Set<UUID> active = consents.activeUserIds();

        List<Tasting> rolls = tastings.findByTastedDay(day).stream()
                .filter(t -> active.contains(t.getUserId())).toList();

        // Tables of the day: opened today, or rolled at today. Only real tables, with company.
        Map<UUID, TastingSession> tables = new LinkedHashMap<>();
        sessions.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to).forEach(s -> tables.put(s.getId(), s));
        rolls.stream().map(Tasting::getSessionId).filter(Objects::nonNull).distinct()
                .filter(id -> !tables.containsKey(id))
                .forEach(id -> sessions.findById(id).ifPresent(s -> tables.put(id, s)));
        tables.values().removeIf(TastingSession::isSolo);

        List<BeerPhoto> labels = photos.decidedBetween(BeerPhoto.Status.APPROVED, from, to).stream()
                .filter(p -> active.contains(p.getUserId())).toList();

        List<BlindRound> blinds = rounds.findByRevealedAtIsNotNull().stream()
                .filter(r -> !r.getRevealedAt().isBefore(from) && r.getRevealedAt().isBefore(to) && !r.getGuesses().isEmpty())
                .toList();

        // Names and beers in two lookups.
        Set<UUID> userIds = new HashSet<>();
        rolls.forEach(t -> userIds.add(t.getUserId()));
        tables.values().forEach(s -> userIds.add(s.getHostId()));
        blinds.forEach(r -> userIds.add(r.getHostId()));
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
        Set<UUID> beerIds = new HashSet<>();
        rolls.forEach(t -> beerIds.add(t.getBeerId()));
        labels.forEach(p -> beerIds.add(p.getBeerId()));
        blinds.forEach(r -> beerIds.add(r.getAnswerBeerId()));
        Map<UUID, Beer> beerById = beers.findAllById(beerIds).stream()
                .collect(Collectors.toMap(Beer::getId, Function.identity()));

        Set<UUID> targetIds = new HashSet<>();
        rolls.forEach(t -> targetIds.add(t.getId()));
        targetIds.addAll(tables.keySet());
        labels.forEach(p -> targetIds.add(p.getId()));
        blinds.forEach(r -> targetIds.add(r.getId()));
        Map<UUID, List<Reaction>> given = targetIds.isEmpty() ? Map.of()
                : reactions.findByTargetIdIn(targetIds).stream().collect(Collectors.groupingBy(Reaction::getTargetId));

        List<Entry> entries = new ArrayList<>();
        for (Tasting t : rolls) {
            Beer b = beerById.get(t.getBeerId());
            TastingSession s = t.getSessionId() == null ? null : tables.get(t.getSessionId());
            entries.add(new Entry(Target.TASTING, t.getId(), t.getTastedAt(),
                    t.getUserId(), names.getOrDefault(t.getUserId(), "?"), t.getUserId().equals(me.getId()),
                    t.getBeerId(), b == null ? "?" : b.getName(), b == null ? null : b.getBrewery(),
                    s == null ? null : s.getId(), s == null ? null : names.getOrDefault(s.getHostId(), "?"),
                    s == null ? null : s.getMembers().size(), null, null,
                    null, null, views(given.get(t.getId()), me.getId())));
        }
        for (TastingSession s : tables.values()) {
            entries.add(new Entry(Target.TABLE, s.getId(), s.getCreatedAt(),
                    s.getHostId(), names.getOrDefault(s.getHostId(), "?"), s.getHostId().equals(me.getId()),
                    null, null, null,
                    s.getId(), names.getOrDefault(s.getHostId(), "?"), s.getMembers().size(), s.isOpen(), s.getBeers().size(),
                    null, null, views(given.get(s.getId()), me.getId())));
        }
        for (BeerPhoto p : labels) {
            Beer b = beerById.get(p.getBeerId());
            entries.add(new Entry(Target.PHOTO, p.getId(), p.getDecidedAt(),
                    null, null, false,
                    p.getBeerId(), b == null ? "?" : b.getName(), b == null ? null : b.getBrewery(),
                    null, null, null, null, null,
                    null, null, views(given.get(p.getId()), me.getId())));
        }
        for (BlindRound r : blinds) {
            Beer b = beerById.get(r.getAnswerBeerId());
            int correct = (int) r.getGuesses().stream().filter(g -> g.beerId().equals(r.getAnswerBeerId())).count();
            Optional<TastingSession> s = Optional.ofNullable(tables.get(r.getSessionId())).or(() -> sessions.findById(r.getSessionId()));
            entries.add(new Entry(Target.BLIND, r.getId(), r.getRevealedAt(),
                    r.getHostId(), names.getOrDefault(r.getHostId(), "?"), r.getHostId().equals(me.getId()),
                    r.getAnswerBeerId(), b == null ? "?" : b.getName(), b == null ? null : b.getBrewery(),
                    r.getSessionId(), names.getOrDefault(r.getHostId(), "?"), s.map(x -> x.getMembers().size()).orElse(null), null, null,
                    correct, r.getGuesses().size(), views(given.get(r.getId()), me.getId())));
        }
        entries.sort(Comparator.comparing(Entry::at).reversed());

        List<OpenTable> open = tables.values().stream().filter(TastingSession::isOpen)
                .sorted(Comparator.comparing(TastingSession::getCreatedAt).reversed())
                .map(s -> new OpenTable(s.getId(), names.getOrDefault(s.getHostId(), "?"), s.getMembers().size(),
                        s.getBeers().size(), s.isMember(me.getId())))
                .toList();

        Map<UUID, List<Tasting>> perBeer = rolls.stream().collect(Collectors.groupingBy(Tasting::getBeerId));
        List<DayBeer> dayBeers = perBeer.entrySet().stream().map(e -> {
            Beer b = beerById.get(e.getKey());
            int n = e.getValue().size();
            Double avg = n >= MIN_FOR_AVERAGE
                    ? Math.round(e.getValue().stream().mapToInt(Tasting::getScore).average().orElse(0) * 10) / 10.0 : null;
            return new DayBeer(e.getKey(), b == null ? "?" : b.getName(), b == null ? null : b.getBrewery(), n, avg);
        }).sorted(Comparator.comparingInt(DayBeer::count).reversed().thenComparing(DayBeer::name)).toList();

        return new FeedView(day, today, rolls.size(), (int) rolls.stream().map(Tasting::getUserId).distinct().count(),
                open, entries, dayBeers, EMOJIS);
    }

    /** Gives the emoji, or takes it back when it was already given. Returns the target's new tally. */
    @Transactional
    public List<ReactionView> react(User me, Target kind, UUID targetId, String emoji) {
        if (kind == null || targetId == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FEED_TARGET", "Say what you cheer for");
        if (emoji == null || !EMOJIS.contains(emoji)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FEED_EMOJI", "Pick one of " + String.join(" ", EMOJIS));
        }
        if (!exists(kind, targetId)) throw new ApiException(HttpStatus.NOT_FOUND, "FEED_TARGET", "Nothing on the wall with that id");
        Optional<Reaction> already = reactions.findByTargetIdAndUserIdAndEmoji(targetId, me.getId(), emoji);
        if (already.isPresent()) {
            reactions.delete(already.get());
        } else {
            reactions.save(new Reaction(kind, targetId, me.getId(), emoji, clock.instant()));
        }
        reactions.flush();
        events.publishFeed(SessionEvents.Kind.REACTION);
        return views(reactions.findByTargetId(targetId), me.getId());
    }

    private boolean exists(Target kind, UUID id) {
        return switch (kind) {
            case TASTING -> tastings.existsById(id);
            case TABLE -> sessions.existsById(id);
            case PHOTO -> photos.findLive(id).map(p -> p.getStatus() == BeerPhoto.Status.APPROVED).orElse(false);
            case BLIND -> rounds.findById(id).map(BlindRound::isRevealed).orElse(false);
        };
    }

    static List<ReactionView> views(List<Reaction> given, UUID me) {
        if (given == null || given.isEmpty()) return List.of();
        Map<String, Integer> counts = new HashMap<>();
        Set<String> mine = new HashSet<>();
        for (Reaction r : given) {
            counts.merge(r.getEmoji(), 1, Integer::sum);
            if (r.getUserId().equals(me)) mine.add(r.getEmoji());
        }
        return EMOJIS.stream().filter(counts::containsKey)
                .map(e -> new ReactionView(e, counts.get(e), mine.contains(e))).toList();
    }
}
