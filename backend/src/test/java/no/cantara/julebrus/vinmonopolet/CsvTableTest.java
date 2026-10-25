package no.cantara.julebrus.vinmonopolet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTableTest {

    @Test
    void readsQuotesCommasLineBreaksAndBlankLines() {
        List<Map<String, String>> rows = CsvTable.parse("﻿a,b,c\r\n1,\"x, \"\"y\"\"\",\n\n2,\"two\nlines\",z\n3,last,");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("a", "1").containsEntry("b", "x, \"y\"").containsEntry("c", "");
        assertThat(rows.get(1)).containsEntry("b", "two\nlines").containsEntry("c", "z");
        assertThat(rows.get(2)).containsEntry("c", "");
    }

    @Test
    void aShortRowReadsMissingColumnsAsEmpty() {
        assertThat(CsvTable.parse("a,b\n1\n").get(0)).containsEntry("a", "1").containsEntry("b", "");
    }

    @Test
    void fieldQuotesOnlyWhenNeededAndRoundTrips() {
        assertThat(CsvTable.field("plain")).isEqualTo("plain");
        for (String v : List.of("a,b", "say \"hi\"", "line\nbreak", "\"", ",", "Hansddbrygggirier, nissefar")) {
            String csv = "k\n" + CsvTable.field(v) + "\n";
            assertThat(CsvTable.parse(csv).get(0).get("k")).as(v).isEqualTo(v);
        }
    }

    @Test
    void candidatesParseWithAnArticleNumberOrACount() {
        List<MatchService.Candidate> c = MatchService.parseCandidates(
                "Aass Juleøl [265502] 0.88 | Aass Juleøl Premium [3 article no.] 0.84 | Odd [Brackets] Beer [0 article no.] 0.7 | free text");
        assertThat(c).hasSize(4);
        assertThat(c.get(0).name()).isEqualTo("Aass Juleøl");
        assertThat(c.get(0).articleNo()).isEqualTo("265502");
        assertThat(c.get(0).score()).isEqualByComparingTo("0.88");
        assertThat(c.get(1).articleNo()).isNull();
        assertThat(c.get(1).articleCount()).isEqualTo(3);
        assertThat(c.get(2).name()).isEqualTo("Odd [Brackets] Beer");
        assertThat(c.get(3).name()).isEqualTo("free text");
        assertThat(c.get(3).score()).isNull();
        assertThat(MatchService.parseCandidates(null)).isEmpty();
        assertThat(MatchService.parseCandidates("")).isEmpty();
    }
}
