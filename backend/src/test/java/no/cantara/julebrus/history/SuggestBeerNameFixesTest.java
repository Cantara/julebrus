package no.cantara.julebrus.history;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs tools/suggest_beer_name_fixes.py on a small fixture (src/test/resources/beer-names). */
class SuggestBeerNameFixesTest {

    static List<String> run(Path out) throws Exception {
        Path fixture = Path.of("src", "test", "resources", "beer-names");
        Process p;
        try {
            p = new ProcessBuilder("python3", "-I", Path.of("..", "tools", "suggest_beer_name_fixes.py").toString(),
                    "--data", fixture.resolve("data").toString(), "--app-history", fixture.resolve("history").toString(),
                    "--out", out.toString()).redirectErrorStream(true).start();
        } catch (java.io.IOException e) {
            Assumptions.abort("python3 is not available");
            return List.of();
        }
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor()).as(output).isZero();
        return Files.readAllLines(out, StandardCharsets.UTF_8);
    }

    @Test
    void suggestsNormalisedFuzzyVariantAndNoSuggestionRows(@TempDir Path dir) throws Exception {
        List<String> lines = run(dir.resolve("out.csv"));
        assertThat(lines.get(0)).isEqualTo("year,as_written,vote_count,suggested_name,source,similarity,note,decision");
        // case only, against the listing of the same year: exact after normalisation
        assertThat(lines).anyMatch(l -> l.startsWith("2015,hansa Juleøl,1,Hansa Juleøl,vinmonopolet-year,1.00,"));
        // a typo, against the listing of the same year
        assertThat(lines).anyMatch(l -> l.startsWith("2014,Rignes Julebokk,1,Ringnes Julebokk,vinmonopolet-year,0.97,"));
        // a typo in a year without a listing, found in another year's listing
        assertThat(lines).anyMatch(l -> l.startsWith("2017,Bådom Tussvatnet,1,Bådin Tussvatnet Juleøl,vinmonopolet-other-year,"));
        // the same beer written with and without "Juleøl"
        assertThat(lines).anyMatch(l -> l.startsWith("2016,Hansa,1,Hansa Juleøl,variant-of,"));
        // nothing close anywhere
        assertThat(lines).anyMatch(l -> l.startsWith("2016,Zzyzx Weird Brew,1,,no-suggestion,"));
        // correct names are not listed, and the decision column is empty
        assertThat(lines).noneMatch(l -> l.contains(",Ringnes Juleøl,1,") || l.startsWith("2019,Ringnes Juleøl"));
        assertThat(lines).noneMatch(l -> l.startsWith("2015,Ringnes Julebokk,"));
        assertThat(lines.subList(1, lines.size())).allMatch(l -> l.endsWith(","));
    }
}
