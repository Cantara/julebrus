package no.cantara.julebrus.vinmonopolet;

import no.cantara.julebrus.web.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The review of vote matches: listing with progress, decisions, the year-wide accept, and the overrides export. */
@Service
public class MatchService {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;
    public static final Set<String> STATUSES = Set.of("auto", "likely", "review", "none", "not-a-product");
    /** Bulk accept is offered only where the machine is confident; review/none rows need a person's eyes one by one. */
    public static final Set<String> BULK_STATUSES = Set.of("auto", "likely");
    public static final String OVERRIDES_HEADER = "year,vote_beer,product_article_no,product_name";

    public enum Action { CONFIRM, CHOOSE, NONE, NOT_A_PRODUCT, CLEAR }

    /** A candidate from the machine's list: either one article number or only a count of numbers under that name. */
    public record Candidate(String name, String articleNo, Integer articleCount, BigDecimal score) {
    }

    public record Machine(String status, String method, BigDecimal score, String articleNo, List<String> articleOptions,
                          String productName, String listYear) {
    }

    /** {@code decidedBy} is the deciding administrator's user id and nothing else about them. */
    public record Decided(VoteMatch.Decision kind, String articleNo, String productName, UUID decidedBy, Instant decidedAt) {
    }

    public record MatchView(UUID id, int year, String voteBeer, Machine machine, List<Candidate> candidates, Decided decision,
                            long version) {
    }

    public record StatusCount(long total, long undecided) {
    }

    public record Summary(long total, long decided, long undecided, Map<String, StatusCount> byStatus,
                          Map<String, Long> byDecision) {
    }

    public record MatchPage(List<MatchView> items, int page, int size, long totalItems, int totalPages, Summary summary) {
    }

    public record BulkResult(boolean dryRun, int year, String status, int count) {
    }

    private static final Pattern CANDIDATE = Pattern.compile("^(.*) \\[([^\\]]*)\\] (\\d+(?:\\.\\d+)?)$");
    private static final Pattern COUNT = Pattern.compile("^(\\d+) article no\\.$");

    private final VoteMatchRepository matches;
    private final VmpCatalogue catalogue;
    private final Clock clock;

    public MatchService(VoteMatchRepository matches, VmpCatalogue catalogue, Clock clock) {
        this.matches = matches;
        this.catalogue = catalogue;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MatchPage list(String status, Integer year, Boolean decided, Integer page, Integer size) {
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FILTER", "Unknown status: " + status);
        }
        int p = page == null || page < 0 ? 0 : Math.min(page, 100_000);
        int s = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Specification<VoteMatch> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("machineStatus"), status));
            if (year != null) ps.add(cb.equal(root.get("year"), year));
            if (decided != null) ps.add(decided ? cb.isNotNull(root.get("decision")) : cb.isNull(root.get("decision")));
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                // Undecided first (review, none, likely, auto, the rest), then what has been decided; then year and name.
                var undecidedRank = cb.<Integer>selectCase().when(cb.isNull(root.get("decision")), 0).otherwise(1);
                var statusRank = cb.<String, Integer>selectCase(root.<String>get("machineStatus"))
                        .when("review", 0).when("none", 1).when("likely", 2).when("auto", 3).otherwise(4);
                query.orderBy(cb.asc(undecidedRank), cb.asc(statusRank), cb.asc(root.get("year")), cb.asc(root.get("voteBeer")));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<VoteMatch> result = matches.findAll(spec, PageRequest.of(p, s));
        return new MatchPage(result.getContent().stream().map(MatchService::view).toList(), p, s,
                result.getTotalElements(), result.getTotalPages(), summary());
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        Map<String, StatusCount> byStatus = new LinkedHashMap<>();
        for (String st : List.of("review", "none", "likely", "auto", "not-a-product")) byStatus.put(st, new StatusCount(0, 0));
        long total = 0, undecided = 0;
        for (Object[] r : matches.countByStatus()) {
            long t = ((Number) r[1]).longValue();
            long u = r[2] == null ? 0 : ((Number) r[2]).longValue();
            byStatus.put((String) r[0], new StatusCount(t, u));
            total += t;
            undecided += u;
        }
        Map<String, Long> byDecision = new LinkedHashMap<>();
        for (VoteMatch.Decision d : VoteMatch.Decision.values()) byDecision.put(d.name(), 0L);
        for (Object[] r : matches.countByDecision()) byDecision.put(((VoteMatch.Decision) r[0]).name(), ((Number) r[1]).longValue());
        return new Summary(total, total - undecided, undecided, byStatus, byDecision);
    }

    @Transactional
    public MatchView decide(UUID id, Action action, long version, String articleNo, String productName, UUID adminId) {
        VoteMatch m = matches.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "No such match"));
        if (m.getVersion() != version) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "The entry changed since you loaded it; reload and try again");
        }
        Instant now = clock.instant();
        switch (action) {
            case CONFIRM -> {
                if (m.getMachineProductName() == null) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_MACHINE_GUESS", "There is no guess to accept");
                }
                m.decide(VoteMatch.Decision.CONFIRMED, m.getMachineArticleNo(), m.getMachineProductName(), adminId, now);
            }
            case CHOOSE -> choose(m, articleNo, productName, adminId, now);
            case NONE -> m.decide(VoteMatch.Decision.NONE, null, null, adminId, now);
            case NOT_A_PRODUCT -> m.decide(VoteMatch.Decision.NOT_A_PRODUCT, null, null, adminId, now);
            case CLEAR -> m.clearDecision();
        }
        return view(matches.saveAndFlush(m));
    }

    private void choose(VoteMatch m, String articleNo, String productName, UUID adminId, Instant now) {
        String art = articleNo == null || articleNo.isBlank() ? null : articleNo.trim();
        String name = productName == null || productName.isBlank() ? null : productName.trim();
        if (art == null && name == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CHOICE_REQUIRED", "Give an article number or a product name");
        }
        if (art != null && !catalogue.articleExists(art)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNKNOWN_PRODUCT", "No product has article number " + art);
        }
        if (name != null && !catalogue.nameExists(name)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNKNOWN_PRODUCT", "No product or listing is named " + name);
        }
        if (art != null && name != null && !catalogue.nameBelongsToArticle(name, art)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CHOICE_MISMATCH", "That name does not belong to that article number");
        }
        if (name == null) name = catalogue.productName(art);
        m.decide(VoteMatch.Decision.CHOSEN, art, name, adminId, now);
    }

    /**
     * Confirms the machine guess on every undecided row of one year and status that has a guess. {@code expectedCount},
     * when given, makes an apply fail with 409 if the number of rows is no longer what the person was shown.
     */
    @Transactional
    public BulkResult bulkConfirm(int year, String status, boolean dryRun, Integer expectedCount, UUID adminId) {
        if (status == null || !BULK_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BULK_STATUS",
                    "Bulk accept is only for status " + String.join(" or ", BULK_STATUSES.stream().sorted().toList()));
        }
        List<VoteMatch> rows = matches.findByYearAndMachineStatusAndDecisionIsNull(year, status).stream()
                .filter(m -> m.getMachineProductName() != null).toList();
        if (dryRun) return new BulkResult(true, year, status, rows.size());
        if (expectedCount != null && expectedCount != rows.size()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "The number of entries changed; run the preview again");
        }
        Instant now = clock.instant();
        for (VoteMatch m : rows) {
            m.decide(VoteMatch.Decision.CONFIRMED, m.getMachineArticleNo(), m.getMachineProductName(), adminId, now);
        }
        matches.saveAll(rows);
        return new BulkResult(false, year, status, rows.size());
    }

    /**
     * All decisions in the repo's data/vinmonopolet/vote_match_overrides.csv format. NONE and NOT_A_PRODUCT both become
     * {@code none} (the script has no separate not-a-product override); a chosen or confirmed row gives the article number
     * when known and always the product name.
     */
    @Transactional(readOnly = true)
    public String overridesCsv() {
        StringBuilder sb = new StringBuilder(OVERRIDES_HEADER).append('\n');
        for (VoteMatch m : matches.findDecided()) {
            String art;
            String name;
            if (m.getDecision() == VoteMatch.Decision.NONE || m.getDecision() == VoteMatch.Decision.NOT_A_PRODUCT) {
                art = "none";
                name = "";
            } else {
                art = m.getDecisionArticleNo() == null ? "" : m.getDecisionArticleNo();
                name = m.getDecisionProductName() == null ? "" : m.getDecisionProductName();
            }
            sb.append(m.getYear()).append(',').append(CsvTable.field(m.getVoteBeer())).append(',')
                    .append(CsvTable.field(art)).append(',').append(CsvTable.field(name)).append('\n');
        }
        return sb.toString();
    }

    static MatchView view(VoteMatch m) {
        List<String> options = m.getMachineArticleOptions() == null ? List.of() : List.of(m.getMachineArticleOptions().split(";"));
        Machine machine = new Machine(m.getMachineStatus(), m.getMachineMethod(), m.getMachineScore(), m.getMachineArticleNo(),
                options, m.getMachineProductName(), m.getMachineListYear());
        Decided d = m.getDecision() == null ? null
                : new Decided(m.getDecision(), m.getDecisionArticleNo(), m.getDecisionProductName(), m.getDecidedBy(), m.getDecidedAt());
        return new MatchView(m.getId(), m.getYear(), m.getVoteBeer(), machine, parseCandidates(m.getCandidates()), d, m.getVersion());
    }

    /** "Name [123] 0.88 | Other [2 article no.] 0.84" into candidates; text that does not parse is kept as a name. */
    static List<Candidate> parseCandidates(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<Candidate> out = new ArrayList<>();
        for (String part : text.split(" \\| ")) {
            Matcher m = CANDIDATE.matcher(part.trim());
            if (!m.matches()) {
                out.add(new Candidate(part.trim(), null, null, null));
                continue;
            }
            String ref = m.group(2).trim();
            Matcher c = COUNT.matcher(ref);
            if (c.matches()) out.add(new Candidate(m.group(1), null, Integer.valueOf(c.group(1)), new BigDecimal(m.group(3))));
            else out.add(new Candidate(m.group(1), ref.isEmpty() ? null : ref, ref.isEmpty() ? null : 1, new BigDecimal(m.group(3))));
        }
        return out;
    }
}
