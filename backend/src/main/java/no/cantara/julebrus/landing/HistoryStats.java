package no.cantara.julebrus.landing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * The aggregates of the juleøl history that the public landing page shows, read from
 * {@code landing/history-stats.json} on the classpath. That file is written by
 * {@code tools/to_landing_stats.py} from {@code data/} and holds counts and per-beer numbers only:
 * no rater names, no per-person scores. The landing page reads nothing else, and never the database.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HistoryStats(int minVotesForTop, List<Season> seasons, Totals totals, Map<String, Integer> dice,
                           int nonIntegerScores, List<Regular> regulars, List<Listing> vinmonopolet) {

    public static final String RESOURCE = "/landing/history-stats.json";

    /** {@code source} is "votes" (single votes survive) or "aggregate" (2016: the sheet's ranked list only). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Season(int year, String source, int beers, int voters, int votes, Double meanScore, List<Top> top) {
        public boolean aggregate() {
            return "aggregate".equals(source);
        }
    }

    /** {@code votes} is null where the source does not state it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Top(String beer, double mean, Integer votes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Totals(int seasonsWithVotes, int seasonsRecorded, int firstYear, int lastYear, int votes,
                         int votesIncludingAggregate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Regular(String beer, int seasons, int votes, List<Integer> years) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Listing(int year, int listings) {
    }

    public static HistoryStats load() {
        try (InputStream in = HistoryStats.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is missing; run tools/to_landing_stats.py");
            return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(in, HistoryStats.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
