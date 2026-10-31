package no.cantara.julebrus.advent;

import no.cantara.julebrus.beer.Beer;
import no.cantara.julebrus.beer.BeerRepository;
import no.cantara.julebrus.config.AdventProperties;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.history.HistoryController;
import no.cantara.julebrus.session.SessionRepository;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.stats.RankingService;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.team.Team;
import no.cantara.julebrus.team.TeamService;
import no.cantara.julebrus.user.User;
import no.cantara.julebrus.user.UserRepository;
import no.cantara.julebrus.web.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The advent calendar game. One door a day from the season's first day to the finale: open it by answering
 * the day's question, sober or not, and find a tasting tip, a shelf beer few have tried, and in the last week
 * a clue about the leader. A missed door opens late for free until {@code free-revival-until}, after that
 * with a nisse-key, earned one per day you tasted at a table with someone else. The streak counts doors,
 * not beers. The whole house lights the star together.
 */
@Service
public class AdventService {

    /** How many of the least-tasted shelf beers take turns as "dagens ukjente". */
    static final int SPOTLIGHT_POOL = 5;

    public enum DoorState { OPENED, TODAY, FREE_LATE, NEEDS_KEY }

    public record QuestionView(DoorQuiz.Kind kind, String text, List<String> options) {
    }

    public record Answer(Integer chosen, Boolean correct, int answer, String explanation) {
    }

    public record BeerRef(UUID id, String name, String brewery, long tastings) {
    }

    /** One door. Behind it (tip, spotlight, clue, answer) only once it is opened. */
    public record DoorView(LocalDate day, int number, DoorState state, QuestionView question, Answer answer,
                           String tip, BeerRef spotlight, String clue, boolean releaseDay, boolean finaleDay,
                           boolean late, boolean keyUsed, int keysAvailable) {
    }

    public record Opened(LocalDate day, Boolean correct, boolean late, boolean keyUsed) {
    }

    public record Keys(int earned, int spent, int available) {
    }

    public record House(long opened, int goal, long today, boolean unlocked) {
    }

    public record TeamRace(String slug, String name, int members, int openedToday) {
    }

    public record Overview(LocalDate today, LocalDate start, LocalDate end, boolean inSeason, List<Opened> opened,
                           int streak, int correct, Keys keys, LocalDate freeRevivalUntil, Instant releaseAt,
                           LocalDate blackoutFrom, boolean blackout, Instant revealAt, House house,
                           /** the current top ten in alphabetical order, once the house has lit the star */ List<BeerRef> topTen,
                           List<TeamRace> teams, String todayQuestion, boolean todayOpened) {
    }

    private final AdventDoorRepository doors;
    private final UserRepository users;
    private final TastingRepository tastings;
    private final SessionRepository sessions;
    private final BeerRepository beers;
    private final HistoryController history;
    private final RankingService ranking;
    private final TeamService teams;
    private final ConsentService consents;
    private final Blackout blackout;
    private final SeasonProperties season;
    private final AdventProperties advent;
    private final Clock clock;
    private final List<String> tips;
    private final Map<LocalDate, DoorQuiz.Question> questions = new ConcurrentHashMap<>();
    private volatile QuizData quizData;

    private record QuizData(List<DoorQuiz.AllTime> allTime, List<DoorQuiz.SeasonTop> seasons) {
    }

    public AdventService(AdventDoorRepository doors, UserRepository users, TastingRepository tastings, SessionRepository sessions,
                         BeerRepository beers, HistoryController history, RankingService ranking, TeamService teams,
                         ConsentService consents, Blackout blackout, SeasonProperties season, AdventProperties advent, Clock clock) {
        this.doors = doors;
        this.users = users;
        this.tastings = tastings;
        this.sessions = sessions;
        this.beers = beers;
        this.history = history;
        this.ranking = ranking;
        this.teams = teams;
        this.consents = consents;
        this.blackout = blackout;
        this.season = season;
        this.advent = advent;
        this.clock = clock;
        this.tips = loadTips();
    }

    public LocalDate today() {
        return clock.instant().atZone(season.zone()).toLocalDate();
    }

    // ---- reading

    @Transactional(readOnly = true)
    public Overview overview(User me) {
        LocalDate today = today();
        List<AdventDoor> mine = doors.findByUserIdOrderByDoorDayAsc(me.getId());
        Set<LocalDate> days = mine.stream().map(AdventDoor::getDoorDay).collect(Collectors.toSet());
        Keys keys = keys(me.getId());
        Set<UUID> active = consents.activeUserIds();
        List<AdventDoor> all = doors.findAll().stream().filter(d -> active.contains(d.getUserId())).toList();
        long openedToday = all.stream().filter(d -> d.getDoorDay().equals(today)).count();
        boolean unlocked = all.size() >= advent.houseGoal();
        List<BeerRef> topTen = unlocked ? ranking.beerRanking().stream().limit(10)
                .map(r -> new BeerRef(r.beerId(), r.name(), r.brewery(), r.ratings()))
                .sorted(Comparator.comparing(b -> b.name().toLowerCase())).toList() : List.of();
        Set<UUID> todayOpeners = all.stream().filter(d -> d.getDoorDay().equals(today)).map(AdventDoor::getUserId).collect(Collectors.toSet());
        List<TeamRace> race = teams.enabled().stream().map(t -> {
            List<UUID> members = t.getMembers().stream().map(Team.Member::userId).filter(active::contains).toList();
            return new TeamRace(t.getSlug(), t.getName(), members.size(), (int) members.stream().filter(todayOpeners::contains).count());
        }).filter(r -> r.members() > 0).toList();
        boolean inSeason = !today.isBefore(season.start()) && !today.isAfter(season.end());
        DoorQuiz.Question q = inSeason ? question(today) : null;
        return new Overview(today, season.start(), season.end(), inSeason,
                mine.stream().map(d -> new Opened(d.getDoorDay(), d.getCorrect(), d.isLate(), d.isKeyUsed())).toList(),
                streak(days, today), (int) mine.stream().filter(d -> Boolean.TRUE.equals(d.getCorrect())).count(), keys,
                advent.freeRevivalUntil(), releaseAt(), advent.blackoutFrom(), blackout.active(), season.revealAt(),
                new House(all.size(), advent.houseGoal(), openedToday, unlocked), topTen, race,
                q == null ? null : q.text(), days.contains(today));
    }

    @Transactional(readOnly = true)
    public DoorView door(User me, LocalDate day) {
        requireOpenable(day);
        return view(me, day, doors.findByUserIdAndDoorDay(me.getId(), day).orElse(null));
    }

    /** True when the person has opened today's door (the daily push nudge skips them). */
    @Transactional(readOnly = true)
    public boolean openedToday(UUID userId) {
        return doors.existsByUserIdAndDoorDay(userId, today());
    }

    /** Today's question, for the push nudge; null outside the season or without an archive. */
    public String todayQuestion() {
        LocalDate today = today();
        if (today.isBefore(season.start()) || today.isAfter(season.end())) return null;
        DoorQuiz.Question q = question(today);
        return q == null ? null : q.text();
    }

    /** The days a person opened, for the Advent badge. */
    @Transactional(readOnly = true)
    public Set<LocalDate> openedDays(UUID userId) {
        return doors.findByUserIdOrderByDoorDayAsc(userId).stream().map(AdventDoor::getDoorDay).collect(Collectors.toSet());
    }

    // ---- opening

    @Transactional
    public DoorView open(User me, LocalDate day, Integer answer) {
        requireOpenable(day);
        users.lockById(me.getId());
        if (doors.existsByUserIdAndDoorDay(me.getId(), day)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOOR_OPENED", "You have opened that door already");
        }
        LocalDate today = today();
        boolean late = day.isBefore(today);
        boolean needsKey = late && (advent.freeRevivalUntil() == null || day.isAfter(advent.freeRevivalUntil()));
        if (needsKey && keys(me.getId()).available() < 1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_KEY", "A late door needs a nisse-key: taste at a table with someone to earn one");
        }
        DoorQuiz.Question q = question(day);
        Boolean correct = null;
        if (q != null) {
            if (answer == null || answer < 0 || answer >= q.options().size()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOOR_ANSWER", "Pick one of the options");
            }
            correct = answer == q.answer();
        }
        AdventDoor saved = doors.saveAndFlush(new AdventDoor(me.getId(), day, clock.instant(), q == null ? null : answer, correct, late, needsKey));
        return view(me, day, saved);
    }

    // ---- the parts

    private DoorView view(User me, LocalDate day, AdventDoor opened) {
        LocalDate today = today();
        DoorQuiz.Question q = question(day);
        DoorState state = opened != null ? DoorState.OPENED
                : day.equals(today) ? DoorState.TODAY
                : advent.freeRevivalUntil() != null && !day.isAfter(advent.freeRevivalUntil()) ? DoorState.FREE_LATE
                : DoorState.NEEDS_KEY;
        QuestionView question = q == null ? null : new QuestionView(q.kind(), q.text(), q.options());
        Answer answer = null;
        String tip = null, clue = null;
        BeerRef spotlight = null;
        if (opened != null) {
            if (q != null) answer = new Answer(opened.getAnswer(), opened.getCorrect(), q.answer(), q.explanation());
            tip = tips.isEmpty() ? null : tips.get(Math.floorMod((int) (day.toEpochDay() - season.start().toEpochDay()), tips.size()));
            spotlight = spotlight(day);
            clue = clue(day);
        }
        return new DoorView(day, (int) (day.toEpochDay() - season.start().toEpochDay()) + 1, state, question, answer, tip, spotlight, clue,
                day.equals(advent.releaseDay()), day.equals(season.end()),
                opened != null && opened.isLate(), opened != null && opened.isKeyUsed(), keys(me.getId()).available());
    }

    private void requireOpenable(LocalDate day) {
        if (day.isBefore(season.start()) || day.isAfter(season.end())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOOR_NOT_FOUND", "No door that day");
        }
        if (day.isAfter(today())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DOOR_LOCKED", "That door opens on its day");
        }
    }

    DoorQuiz.Question question(LocalDate day) {
        return questions.computeIfAbsent(day, d -> {
            QuizData data = quizData();
            return DoorQuiz.generate(d, data.allTime(), data.seasons());
        });
    }

    private QuizData quizData() {
        QuizData data = quizData;
        if (data == null) {
            List<DoorQuiz.AllTime> allTime = history.allTime(5).stream()
                    .map(r -> new DoorQuiz.AllTime(r.rank(), r.beerName(), r.ratings())).toList();
            List<DoorQuiz.SeasonTop> tops = history.overview().stream()
                    .map(o -> {
                        List<HistoryController.BeerSeasonView> rows = history.season(o.year());
                        return new DoorQuiz.SeasonTop(o.year(), rows.stream().map(HistoryController.BeerSeasonView::beerName).toList(),
                                rows.isEmpty() ? 0 : rows.get(0).normalisedMean());
                    }).toList();
            data = new QuizData(allTime, tops);
            if (!allTime.isEmpty()) quizData = data; // an empty archive (still importing) is tried again next time
        }
        return data;
    }

    private BeerRef spotlight(LocalDate day) {
        Map<UUID, Long> counts = new HashMap<>();
        tastings.countPerBeer().forEach(c -> counts.put(c.beerId(), c.count()));
        List<Beer> pool = beers.findAll().stream().filter(Beer::counts)
                .sorted(Comparator.comparingLong((Beer b) -> counts.getOrDefault(b.getId(), 0L)).thenComparing(b -> b.getName().toLowerCase()))
                .limit(SPOTLIGHT_POOL).toList();
        if (pool.isEmpty()) return null;
        Beer b = pool.get(Math.floorMod((int) day.toEpochDay(), pool.size()));
        return new BeerRef(b.getId(), b.getName(), b.getBrewery(), counts.getOrDefault(b.getId(), 0L));
    }

    /** The leader's clue, for doors in the blackout before the finale day. */
    private String clue(LocalDate day) {
        if (advent.blackoutFrom() == null || day.isBefore(advent.blackoutFrom()) || !day.isBefore(season.end())) return null;
        var top = ranking.beerRanking().stream().findFirst().orElse(null);
        if (top == null) return null;
        Beer b = beers.findById(top.beerId()).orElse(null);
        if (b == null) return null;
        return DoorClues.clue((int) (day.toEpochDay() - advent.blackoutFrom().toEpochDay()),
                new DoorClues.Leader(b.getName(), b.getBrewery(), b.getStyle(), b.getAbv(), top.ratings()));
    }

    /** One key per day with a roll at a table someone else sat at too; spent ones are the doors that used one. */
    Keys keys(UUID userId) {
        List<Tasting> atTables = tastings.findByUserIdOrderByTastedAtDesc(userId).stream()
                .filter(t -> t.getSessionId() != null).toList();
        Set<UUID> shared = new HashSet<>();
        for (TastingSession s : sessions.findAllById(atTables.stream().map(Tasting::getSessionId).collect(Collectors.toSet()))) {
            if (s.getMembers().size() >= 2) shared.add(s.getId());
        }
        int earned = (int) atTables.stream().filter(t -> shared.contains(t.getSessionId()))
                .map(Tasting::getTastedDay).filter(Objects::nonNull).distinct().count();
        int spent = (int) doors.countByUserIdAndKeyUsedTrue(userId);
        return new Keys(earned, spent, Math.max(0, earned - spent));
    }

    /** Doors in a row, counting back from today, or from yesterday while today's is still shut. */
    static int streak(Set<LocalDate> days, LocalDate today) {
        LocalDate d = days.contains(today) ? today : today.minusDays(1);
        int n = 0;
        while (days.contains(d)) {
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    private Instant releaseAt() {
        return advent.releaseDay() == null ? null : advent.releaseDay().atTime(advent.releaseTime()).atZone(season.zone()).toInstant();
    }

    private static List<String> loadTips() {
        try (var in = new ClassPathResource("advent/tips.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
