package no.cantara.julebrus.beer;

import no.cantara.julebrus.consent.ConsentService;
import no.cantara.julebrus.duel.Duel;
import no.cantara.julebrus.duel.DuelRepository;
import no.cantara.julebrus.history.HistoricRating;
import no.cantara.julebrus.history.HistoricRatingRepository;
import no.cantara.julebrus.history.HistoricSeason;
import no.cantara.julebrus.history.HistoricSeasonRepository;
import no.cantara.julebrus.photo.PhotoService;
import no.cantara.julebrus.session.SessionRepository;
import no.cantara.julebrus.session.TastingSession;
import no.cantara.julebrus.stats.RankingService;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One beer's page: the facts, this season's numbers, its duel record, what the room wrote
 * about it, your own roll, and what earlier years said.
 *
 * <p>Two guards keep the page from becoming a crib sheet or a leak. Anything derived from
 * other people's rolls (the histogram, the tag cloud, the notes) appears only from
 * {@link #MIN_SHARED} rolls, so no single roll can be read off it. And the notes, which are
 * the most personal part, open only after the reader has rolled the beer themselves: taste
 * first, read afterwards. Notes are anonymous (no name, no time, shuffled), only the
 * structured fields are shared (the free comment stays at the table), and a note from an
 * open table that has not revealed yet stays at that table too.
 */
@Service
public class BeerPageService {

    /** Histogram, tags and notes appear only from this many rolls. */
    public static final int MIN_SHARED = 3;

    public record TagCount(String tag, int count) {
    }

    /** What one person wrote, without the person. */
    public record SharedNote(String appearance, String aroma, String taste, String finish, List<String> tags) {
    }

    public record MyTasting(int score, BigDecimal prediction, String notes, String appearance, String aroma, String taste,
                            String finish, List<String> tags, LocalDate tastedDay, UUID sessionId) {
    }

    public record Season(int tastings, int people, Integer rank, Integer rankedOf, Double bayesian, Double mean,
                         /** counts of 1..6, null until MIN_SHARED rolls */ int[] histogram) {
    }

    public record Duels(int wins, int losses, Integer rank, Double strength) {
    }

    public record HistoryYear(int year, int ratings, double mean) {
    }

    public record Page(BeerController.BeerView beer, Season season, Duels duels, MyTasting mine,
                       /** null until MIN_SHARED rolls */ List<TagCount> tags,
                       /** true when the notes may be read: the reader has rolled the beer and MIN_SHARED have */
                       boolean notesOpen,
                       /** why the notes are closed: NEED_ROLL, NEED_MORE, or null when open */ String notesLocked,
                       List<SharedNote> notes, int minShared, List<HistoryYear> history) {
    }

    private final BeerRepository beers;
    private final TastingRepository tastings;
    private final SessionRepository sessions;
    private final DuelRepository duels;
    private final RankingService ranking;
    private final PhotoService photos;
    private final ConsentService consents;
    private final HistoricRatingRepository historic;
    private final HistoricSeasonRepository seasons;

    public BeerPageService(BeerRepository beers, TastingRepository tastings, SessionRepository sessions, DuelRepository duels,
                           RankingService ranking, PhotoService photos, ConsentService consents,
                           HistoricRatingRepository historic, HistoricSeasonRepository seasons) {
        this.beers = beers;
        this.tastings = tastings;
        this.sessions = sessions;
        this.duels = duels;
        this.ranking = ranking;
        this.photos = photos;
        this.consents = consents;
        this.historic = historic;
        this.seasons = seasons;
    }

    @Transactional(readOnly = true)
    public Page page(UUID beerId, User me) {
        Beer beer = beers.findById(beerId)
                .filter(b -> b.getStatus() != Beer.Status.REJECTED || me.getId().equals(b.getCreatedBy()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown beer"));
        Set<UUID> active = consents.activeUserIds();
        List<Tasting> rolls = tastings.findByBeerId(beerId).stream().filter(t -> active.contains(t.getUserId())).toList();
        Tasting own = rolls.stream().filter(t -> t.getUserId().equals(me.getId())).findFirst().orElse(null);
        boolean enough = rolls.size() >= MIN_SHARED;

        // Season numbers from the same ranking the results page shows.
        RankingService.BeerRank rank = null;
        List<RankingService.BeerRank> ranked = ranking.beerRanking();
        for (RankingService.BeerRank r : ranked) if (r.beerId().equals(beerId)) rank = r;
        int[] histogram = null;
        if (enough) {
            histogram = new int[6];
            for (Tasting t : rolls) histogram[Math.min(6, Math.max(1, t.getScore())) - 1]++;
        }
        Season season = new Season(rolls.size(), (int) rolls.stream().map(Tasting::getUserId).distinct().count(),
                rank == null ? null : rank.rank(), rank == null ? null : ranked.size(),
                rank == null ? null : rank.bayesian(), rank == null ? null : rank.mean(), histogram);

        // Duels: how often it won and lost, and its place in the sidebar.
        int wins = 0, losses = 0;
        for (Duel d : duels.findAll()) {
            if (beerId.equals(d.getWinnerBeerId())) wins++;
            else if (beerId.equals(d.getLoserBeerId())) losses++;
        }
        RankingService.DuelRank duelRank = ranking.duelRanking().stream().filter(r -> r.beerId().equals(beerId)).findFirst().orElse(null);
        Duels duelView = new Duels(wins, losses, duelRank == null ? null : duelRank.rank(), duelRank == null ? null : duelRank.strength());

        // The room's words. Tags at MIN_SHARED; notes also only once the reader has rolled, and never
        // from a table that has not revealed the beer yet.
        List<TagCount> tags = null;
        if (enough) {
            Map<String, Integer> counts = new HashMap<>();
            for (Tasting t : rolls) for (String tag : t.getTags()) counts.merge(tag, 1, Integer::sum);
            tags = counts.entrySet().stream().map(e -> new TagCount(e.getKey(), e.getValue()))
                    .sorted(Comparator.comparingInt(TagCount::count).reversed().thenComparing(TagCount::tag)).toList();
        }
        boolean notesOpen = enough && own != null;
        String locked = notesOpen ? null : own == null ? "NEED_ROLL" : "NEED_MORE";
        List<SharedNote> notes = List.of();
        if (notesOpen) {
            Map<UUID, Boolean> revealedBySession = new HashMap<>();
            List<SharedNote> shared = new ArrayList<>();
            for (Tasting t : rolls) {
                if (t.getUserId().equals(me.getId())) continue;
                Tasting.Notes n = t.getStructuredNotes();
                if (n.isEmpty() && t.getTags().isEmpty()) continue;
                if (t.getSessionId() != null && !revealedBySession.computeIfAbsent(t.getSessionId(), sid -> revealed(sid, beerId, rolls))) continue;
                shared.add(new SharedNote(n.appearance(), n.aroma(), n.taste(), n.finish(), List.copyOf(t.getTags())));
            }
            Collections.shuffle(shared);
            notes = List.copyOf(shared);
        }

        MyTasting mine = own == null ? null : new MyTasting(own.getScore(), own.getPrediction(), own.getNotes(),
                own.getStructuredNotes().appearance(), own.getStructuredNotes().aroma(), own.getStructuredNotes().taste(),
                own.getStructuredNotes().finish(), List.copyOf(own.getTags()), own.getTastedDay(), own.getSessionId());

        BeerController.BeerView view = BeerController.BeerView.of(beer, rolls.size(), me.getId(), photos.covers(me));
        return new Page(view, season, duelView, mine, tags, notesOpen, locked, notes, MIN_SHARED, history(beer.getName()));
    }

    /** The table's own rule: everyone at the table has rolled it, or the table is closed. */
    private boolean revealed(UUID sessionId, UUID beerId, List<Tasting> rolls) {
        TastingSession s = sessions.findById(sessionId).orElse(null);
        if (s == null || !s.isOpen()) return true;
        Set<UUID> ratedBy = rolls.stream().filter(t -> sessionId.equals(t.getSessionId())).map(Tasting::getUserId).collect(Collectors.toSet());
        return s.getMembers().stream().map(TastingSession.Member::userId).allMatch(ratedBy::contains);
    }

    /** What earlier years said, matched on name: one line per season, weighted mean on 1–6. */
    private List<HistoryYear> history(String name) {
        Map<Integer, HistoricSeason> byYear = seasons.findAll().stream().collect(Collectors.toMap(HistoricSeason::getYear, s -> s));
        TreeMap<Integer, List<HistoricRating>> perYear = historic.findByBeerNameIgnoreCaseOrderBySeasonYearDesc(name).stream()
                .collect(Collectors.groupingBy(HistoricRating::getSeasonYear, TreeMap::new, Collectors.toList()));
        List<HistoryYear> out = new ArrayList<>();
        perYear.descendingMap().forEach((year, rows) -> {
            HistoricSeason s = byYear.get(year);
            double w = rows.stream().mapToDouble(r -> r.getWeight().doubleValue()).sum();
            double mean = w == 0 ? 0 : rows.stream()
                    .mapToDouble(r -> (s == null ? r.getScore().doubleValue() : s.normalise(r.getScore())) * r.getWeight().doubleValue()).sum() / w;
            out.add(new HistoryYear(year, rows.size(), Math.round(mean * 100) / 100.0));
        });
        return out;
    }
}
