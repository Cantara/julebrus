package no.cantara.julebrus.vinmonopolet;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RFC 4180 reader keyed on the header row: quoted fields may hold commas, doubled quotes and line breaks,
 * and a UTF-8 byte-order mark is ignored. Rows are maps from column name to value; a missing column reads as "".
 */
public final class CsvTable {

    private CsvTable() {
    }

    public static List<Map<String, String>> read(InputStream in) throws IOException {
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(r);
        }
    }

    public static List<Map<String, String>> parse(String text) {
        try {
            return parse(new java.io.StringReader(text));
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Rows as lists of fields, header included. Blank lines are skipped. */
    public static List<List<String>> records(Reader in) throws IOException {
        List<List<String>> out = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        int c = in.read();
        if (c == 0xFEFF) c = in.read();
        while (c != -1) {
            if (quoted) {
                if (c == '"') {
                    int next = in.read();
                    if (next == '"') {
                        cur.append('"');
                        c = in.read();
                    } else {
                        quoted = false;
                        c = next;
                    }
                    continue;
                }
                cur.append((char) c);
            } else if (c == '"') {
                quoted = true;
                fieldStarted = true;
            } else if (c == ',') {
                row.add(cur.toString());
                cur.setLength(0);
                fieldStarted = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r') {
                    int next = in.read();
                    endRecord(out, row, cur, fieldStarted);
                    row = new ArrayList<>();
                    fieldStarted = false;
                    c = next;
                    if (next == '\n') c = in.read();
                    continue;
                }
                endRecord(out, row, cur, fieldStarted);
                row = new ArrayList<>();
                fieldStarted = false;
            } else {
                cur.append((char) c);
                fieldStarted = true;
            }
            c = in.read();
        }
        endRecord(out, row, cur, fieldStarted);
        return out;
    }

    private static void endRecord(List<List<String>> out, List<String> row, StringBuilder cur, boolean started) {
        if (!started && row.isEmpty() && cur.length() == 0) return; // blank line
        row.add(cur.toString());
        cur.setLength(0);
        out.add(row);
    }

    static List<Map<String, String>> parse(Reader in) throws IOException {
        List<List<String>> recs = records(in);
        List<Map<String, String>> rows = new ArrayList<>();
        if (recs.isEmpty()) return rows;
        List<String> header = recs.get(0).stream().map(String::trim).toList();
        for (int i = 1; i < recs.size(); i++) {
            List<String> rec = recs.get(i);
            Map<String, String> m = new HashMap<>();
            for (int j = 0; j < header.size(); j++) m.put(header.get(j), j < rec.size() ? rec.get(j) : "");
            rows.add(m);
        }
        return rows;
    }

    /** One CSV field, quoted when it holds a comma, a quote or a line break. */
    public static String field(String v) {
        if (v == null) return "";
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
