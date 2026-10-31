package no.cantara.julebrus.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import no.cantara.julebrus.config.SeasonProperties;
import no.cantara.julebrus.finale.FinaleService;
import no.cantara.julebrus.history.HistoricRating;
import no.cantara.julebrus.history.HistoricRatingRepository;
import no.cantara.julebrus.history.HistoricSeason;
import no.cantara.julebrus.history.HistoricSeasonRepository;
import no.cantara.julebrus.history.HistoryController;
import no.cantara.julebrus.stats.RankingService;
import no.cantara.julebrus.tasting.Tasting;
import no.cantara.julebrus.tasting.TastingRepository;
import no.cantara.julebrus.team.Team;
import no.cantara.julebrus.team.TeamService;
import no.cantara.julebrus.tip.TipService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The read-only tools an agent gets over the tasting data. Aggregates and already
 * public history only: no per-person scores from the running season, no emails.
 */
@Component
public class McpTools {

    private final ObjectMapper json;
    private final HistoryController history;
    private final HistoricSeasonRepository seasons;
    private final HistoricRatingRepository ratings;
    private final RankingService ranking;
    private final FinaleService finale;
    private final TastingRepository tastings;
    private final SeasonProperties season;
    private final TeamService teams;
    private final TipService tips;
    private final Clock clock;
    private final no.cantara.julebrus.advent.Blackout blackout;

    public McpTools(ObjectMapper json, HistoryController history, HistoricSeasonRepository seasons,
                    HistoricRatingRepository ratings, RankingService ranking, FinaleService finale,
                    TastingRepository tastings, SeasonProperties season, TeamService teams, TipService tips, Clock clock,
                    no.cantara.julebrus.advent.Blackout blackout) {
        this.blackout = blackout;
        this.teams = teams;
        this.tips = tips;
        this.json = json;
        this.history = history;
        this.seasons = seasons;
        this.ratings = ratings;
        this.ranking = ranking;
        this.finale = finale;
        this.tastings = tastings;
        this.season = season;
        this.clock = clock;
    }

    /** What the ranking tools answer in the last week before the finale, when the ranking is hidden for everyone. */
    private Map<String, Object> dark() {
        return Map.of("blackout", true, "revealAt", season.revealAt().toString(),
                "message", "The ranking is hidden until the finale reveal; see the finale tool afterwards.");
    }

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        return List.of(
                tool("list_seasons",
                        "Every recorded season of the Christmas beer tasting (the tasting began in 1992; votes are recorded from 1998 onwards): size, winner, weighting and source. Years with no surviving votes are absent.",
                        noArgs(), args -> history.overview()),
                tool("season_results",
                        "The ranked result list of one earlier season, weighted as it was that year. Scores are on each season's own scale (all 1-6 so far).",
                        schema(Map.of("year", Map.of("type", "integer", "description", "Season year, e.g. 2019")), List.of("year")),
                        args -> history.season(intArg(args, "year"))),
                tool("beer_history",
                        "How one beer has scored across the years, by season: mean on 1-6 and number of votes. Name match is exact, case-insensitive.",
                        schema(Map.of("name", Map.of("type", "string", "description", "Beer name as written in the results, e.g. 'Aass Juleøl'")), List.of("name")),
                        args -> beerHistory(stringArg(args, "name"))),
                tool("alltime_ranking",
                        "The all-time list across every recorded season: each beer's votes on 1-6, weighted as each year weighted them, shrunk towards the all-time mean (prior 5 votes), with its best year. Beers with at least `min` votes (default 5).",
                        schema(Map.of("min", Map.of("type", "integer", "description", "Minimum votes over the years, default 5")), List.of()),
                        args -> history.allTime(args.get("min") == null ? 5 : intArg(args, "min"))),
                tool("list_raters",
                        "Everyone who has tasted in the recorded seasons, by display name, with their years and vote count. Names only; no contact details.",
                        noArgs(), args -> history.raters()),
                tool("rater_history",
                        "One historic rater's seasons: votes cast, their mean and the season's weighted mean, both on 1-6.",
                        schema(Map.of("name", Map.of("type", "string", "description", "Rater display name as listed by list_raters")), List.of("name")),
                        args -> raterHistory(stringArg(args, "name"))),
                tool("current_ranking",
                        "The running ranking of this season: approved Vinmonopolet-strength beers with people count, effective (weighted) votes, weighted mean, Bayesian score and controversy. No per-person scores.",
                        noArgs(), args -> blackout.active() ? dark() : ranking.beerRanking()),
                tool("team_ranking",
                        "This season's ranking as seen by one team's raters only (same Bayesian method, the team's own mean as prior). Teams are listed by season_status.",
                        schema(Map.of("team", Map.of("type", "string", "description", "Team slug, e.g. 'cantara' or 'rebel'")), List.of("team")),
                        args -> {
                            if (blackout.active()) return dark();
                            Team t = teams.bySlug(stringArg(args, "team"));
                            return ranking.beerRanking(t.getMembers().stream().map(Team.Member::userId).collect(Collectors.toSet()));
                        }),
                tool("duel_ranking",
                        "This season's sidebar ranking from pairwise duels (Bradley-Terry strengths), independent of how generous each rater is.",
                        noArgs(), args -> blackout.active() ? dark() : ranking.duelRanking()),
                tool("season_status",
                        "Where this season stands: dates, days left, when the result is revealed, and totals so far.",
                        noArgs(), args -> status()),
                tool("finale",
                        "The final result of this season once it is revealed on the last day: podium, duel podium and the season awards. Before that, only the reveal time and totals.",
                        noArgs(), args -> finale.view(false))
        );
    }

    private Map<String, Object> status() {
        LocalDate today = clock.instant().atZone(season.zone()).toLocalDate();
        List<Tasting> all = tastings.findAll();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", season.year());
        out.put("start", season.start().toString());
        out.put("end", season.end().toString());
        out.put("today", today.toString());
        out.put("inSeason", !today.isBefore(season.start()) && !today.isAfter(season.end()));
        out.put("daysLeft", Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(today, season.end())));
        out.put("revealAt", season.revealAt().toString());
        out.put("tipDeadline", season.tipDeadline().toString());
        out.put("tipped", tips.count());
        out.put("rules", Map.of(
                "maxTastingsPerDay", season.maxTastingsPerDay(),
                "bigTableSize", season.bigTableSize(),
                "maxTastingsPerDayBigTable", season.maxTastingsPerDayBigTable(),
                "maxSoloDays", season.maxSoloDays(),
                "counts", "Vinmonopolet juleøl above 4.7 % ABV; homebrew only at a table of 6+ and with 4+ scores"));
        out.put("teams", teams.enabled().stream().map(t -> Map.of("slug", t.getSlug(), "name", t.getName(),
                "members", t.getMembers().size())).toList());
        out.put("totals", Map.of(
                "tastings", all.size(),
                "beers", all.stream().map(Tasting::getBeerId).distinct().count(),
                "people", all.stream().map(Tasting::getUserId).distinct().count(),
                "sessions", all.stream().map(Tasting::getSessionId).filter(Objects::nonNull).distinct().count()));
        return out;
    }

    private List<Map<String, Object>> beerHistory(String name) {
        Map<Integer, HistoricSeason> byYear = seasons.findAll().stream().collect(Collectors.toMap(HistoricSeason::getYear, Function.identity()));
        return ratings.findByBeerNameIgnoreCaseOrderBySeasonYearDesc(name).stream()
                .collect(Collectors.groupingBy(HistoricRating::getSeasonYear, java.util.TreeMap::new, Collectors.toList()))
                .entrySet().stream().map(e -> {
                    HistoricSeason s = byYear.get(e.getKey());
                    double mean = e.getValue().stream().mapToDouble(r -> s == null ? r.getScore().doubleValue() : s.normalise(r.getScore())).average().orElse(0);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("year", e.getKey());
                    row.put("votes", e.getValue().size());
                    row.put("mean", Math.round(mean * 100) / 100.0);
                    return row;
                }).toList();
    }

    private List<Map<String, Object>> raterHistory(String name) {
        Map<Integer, HistoricSeason> byYear = seasons.findAll().stream().collect(Collectors.toMap(HistoricSeason::getYear, Function.identity()));
        List<HistoricRating> all = ratings.findAll();
        Map<Integer, List<HistoricRating>> allByYear = all.stream().collect(Collectors.groupingBy(HistoricRating::getSeasonYear));
        return all.stream()
                .filter(r -> name.equalsIgnoreCase(r.getRaterKey() == null ? r.getRaterName() : r.getRaterKey()))
                .collect(Collectors.groupingBy(HistoricRating::getSeasonYear, java.util.TreeMap::new, Collectors.toList()))
                .entrySet().stream().map(e -> {
                    HistoricSeason s = byYear.get(e.getKey());
                    Function<HistoricRating, Double> norm = r -> s == null ? r.getScore().doubleValue() : s.normalise(r.getScore());
                    double mine = e.getValue().stream().mapToDouble(norm::apply).average().orElse(0);
                    List<HistoricRating> crowd = allByYear.getOrDefault(e.getKey(), List.of());
                    double w = crowd.stream().mapToDouble(r -> r.getWeight().doubleValue()).sum();
                    double crowdMean = w == 0 ? 0 : crowd.stream().mapToDouble(r -> norm.apply(r) * r.getWeight().doubleValue()).sum() / w;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("year", e.getKey());
                    row.put("votes", e.getValue().size());
                    row.put("mean", Math.round(mine * 100) / 100.0);
                    row.put("crowdMean", Math.round(crowdMean * 100) / 100.0);
                    return row;
                }).toList();
    }

    // ---- plumbing ----

    private McpServerFeatures.SyncToolSpecification tool(String name, String description, Map<String, Object> inputSchema,
                                                         Function<Map<String, Object>, Object> handler) {
        McpSchema.Tool tool = McpSchema.Tool.builder().name(name).description(description).inputSchema(inputSchema).build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> {
                    try {
                        Object result = handler.apply(request.arguments() == null ? Map.of() : request.arguments());
                        return McpSchema.CallToolResult.builder()
                                .addTextContent(json.writeValueAsString(result))
                                .structuredContent(result instanceof Map ? result : Map.of("result", result))
                                .build();
                    } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException
                             | no.cantara.julebrus.web.ApiException e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent(e.getMessage()).build();
                    } catch (org.springframework.web.server.ResponseStatusException e) {
                        return McpSchema.CallToolResult.builder().isError(true).addTextContent("Not found").build();
                    }
                })
                .build();
    }

    private static Map<String, Object> noArgs() {
        return schema(Map.of(), List.of());
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", "object");
        s.put("properties", properties);
        s.put("required", required);
        s.put("additionalProperties", false);
        return s;
    }

    private static int intArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignored) { /* fall through */ }
        }
        throw new IllegalArgumentException("Missing or invalid '" + key + "'");
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof String str && !str.isBlank()) return str.trim();
        throw new IllegalArgumentException("Missing '" + key + "'");
    }
}
