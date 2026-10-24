#!/usr/bin/env python3
"""Extract juleøl test scores from the Cantara wiki pages (docs/puben/*.md in Cantara/wiki).

Used by build_data.py. extract(WIKI_PUBEN_DIR) returns (votes, results) with the values the wiki
pages state; nothing is recomputed (except the 2008 voter weights, which follow from the stated counts, see
sheet_2008). Only years whose page holds a readable table are handled here.
"""
import html
import os
import re
from html.parser import HTMLParser

# Confirmed spelling fixes to beer names as written on the wiki pages: (year, name on the page) -> name in data/.
# The pages live in Cantara/wiki, not in this repository, so a fix to a wiki year's name goes here (the Google
# Sheets years are fixed in source/google-sheets/ directly). Databases imported before a fix also need the
# old -> new row in backend/src/main/resources/history/beer_names.csv.
NAME_FIXES = {
    (2003, "Nøgne ø"): "Nøgne Ø",
}


class TableParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tables, self.stack, self.row, self.cell, self.span = [], [], None, None, 1

    def handle_starttag(self, tag, attrs):
        if tag == "table":
            self.stack.append([])
        elif tag == "tr" and self.stack:
            self.row = []
        elif tag in ("td", "th") and self.row is not None:
            self.cell, self.span = [], int(dict(attrs).get("colspan", "1") or 1)
        elif tag == "br" and self.cell is not None:
            self.cell.append(" ")

    def handle_endtag(self, tag):
        if tag in ("td", "th") and self.cell is not None and self.row is not None:
            self.row.extend([re.sub(r"\s+", " ", "".join(self.cell)).strip()] + [""] * (self.span - 1))
            self.cell = None
        elif tag == "tr" and self.row is not None and self.stack:
            self.stack[-1].append(self.row)
            self.row = None
        elif tag == "table" and self.stack:
            self.tables.append(self.stack.pop())

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)


def tables(path):
    parser = TableParser()
    with open(path, encoding="utf-8", errors="replace") as fh:
        parser.feed(fh.read())
    return parser.tables


def num(cell):
    """A stated number as a float, or None for blanks, '-', '~~', error cells and letters."""
    cell = cell.strip().replace(",", ".")
    return float(cell) if re.fullmatch(r"-?\d+(\.\d+)?", cell) else None


def fmt(value):
    return "" if value is None else f"{value:g}"


def voters_as_rows(grid, label_col, year, votes, results):
    """2003, 2004, 2007: one row per voter, one column per beer, summary rows below."""
    header = next(i for i, row in enumerate(grid) if len(row) > label_col and row[label_col] in ("Navn", "Deltaker"))
    cols = grid[header]
    weight_col = label_col + 1
    beer_cols = []
    for j in range(weight_col + 1, len(cols)):
        if cols[j].startswith("Snitt"):
            break
        if cols[j]:
            beer_cols.append((j, cols[j]))
    summary = {}
    for row in grid[header + 1:]:
        label = row[label_col] if len(row) > label_col else ""
        if not label:
            continue
        if re.match(r"(Vekt, ju|Antall|Uvektet|Vektet|Plassering)", label):
            summary[label.split(",")[0].split(" ")[0] + (" " + label.split(" ")[1] if label.startswith("Antall") else "")] = row
            continue
        weight = num(row[weight_col]) if len(row) > weight_col else None
        for j, beer in beer_cols:
            score = num(row[j]) if j < len(row) else None
            if score is not None:
                votes.append((year, label, fmt(weight), beer, fmt(score)))
    for j, beer in beer_cols:
        def at(key):
            row = summary.get(key)
            return fmt(num(row[j])) if row and j < len(row) and num(row[j]) is not None else ""
        results.append((year, beer, at("Antall stemmer"), at("Uvektet"), at("Vektet"), at("Plassering"), ""))


def voters_as_columns(grid, year, votes, results):
    """1998: one row per beer, one column per voter; '-' means no vote."""
    weights = next(r for r in grid if r and r[0] == "Vekt")
    header = next(r for r in grid if r and r[0] == "Type")
    voter_cols = [(j, header[j]) for j in range(1, len(header)) if header[j] and not header[j].startswith(("Snitt", "Antall"))]
    mean_col = next(j for j, c in enumerate(header) if c.startswith("Snitt"))
    count_col = next(j for j, c in enumerate(header) if c.startswith("Antall"))
    for row in grid[grid.index(header) + 1:]:
        if not row or not row[0]:
            continue
        beer = row[0].strip('"')
        for j, voter in voter_cols:
            score = num(row[j]) if j < len(row) else None
            if score is not None:
                votes.append((year, voter, fmt(num(weights[j])), beer, fmt(score)))
        results.append((year, beer, fmt(num(row[count_col])), "", fmt(num(row[mean_col])), "", ""))


def sheet_grid(path):
    """2008, 2010: a Google spreadsheet saved as HTML, with unclosed cells (`<td>4<td ><td>5`).

    An HTML parser or an HTML-to-text step drops or merges the empty cells, so the raw text is split instead:
    every `<tr` starts a row and every `<td` a cell, empty cells included, so a cell's index is its column.
    Google splits a long sheet into several <table>s of the same width; their rows are joined into one grid.
    Annotations (`<span class='annotation'>`, a comment marker such as "[1](1.md)") are dropped from a cell.
    """
    with open(path, encoding="utf-8", errors="replace") as fh:
        text = fh.read()
    text = re.sub(r"<span class='annotation'>.*?</span>", "", text, flags=re.S)
    grid = []
    for table in re.split(r"<table", text, flags=re.I)[1:]:
        table = re.split(r"</table", table, flags=re.I)[0]
        for tr in re.split(r"<tr", table, flags=re.I)[1:]:
            cells = []
            for td in re.split(r"<td", tr, flags=re.I)[1:]:
                body = td.split(">", 1)[1] if ">" in td else ""
                cells.append(re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]*>", "", body))).strip())
            grid.append(cells)
    return grid


def cell(row, j):
    return row[j] if j < len(row) else ""


def sheet_scores(year, row, beer_cols, voter, notes):
    """(beer, score) for the cells of one voter row that hold a score; any other non-empty cell is noted."""
    out = []
    for j, beer in beer_cols:
        value = cell(row, j)
        score = num(value)
        if score is not None:
            out.append((beer, score))
        elif value:
            notes.append(f"skipped {year} {voter} / {beer}: {value!r} is not a score")
    return out


def sheet_2008(grid, votes, results, voter_checks, notes):
    """2008: header `., Navn, Sum Ølstemmer, Snitt avgitt stemme, <beers>, Sum stemmer`, one row per voter,
    then the rows `Number of votes`, `Number of votes 0.5 weight` and `Total number of votes` per beer.

    The sheet gives no weight per voter, only how many of each beer's votes had weight 0.5. Those counts fix
    each voter's weight: a voter who scored a beer with no 0.5 votes has weight 1, one who scored a beer with no
    full votes has 0.5, and so on until every voter is settled (an error if the counts leave any voter open).
    `Sum Ølstemmer` ("beer votes") and `Snitt avgitt stemme` cover the beer columns only, not the three Fenalår.
    """
    year = 2008
    head = next(i for i, r in enumerate(grid) if cell(r, 1) == "Navn")
    cols = grid[head]
    beer_cols = [(j, cols[j]) for j in range(4, len(cols)) if cols[j] and cols[j] != "Sum stemmer"]
    summary, scored = {}, []
    for row in grid[head + 1:]:
        label = cell(row, 1)
        if label.startswith(("Number of votes", "Total number of votes")):
            summary[label] = row
        elif label and label not in summary and not summary:
            scored.append((label, row, sheet_scores(year, row, beer_cols, label, notes)))
            voter_checks.append((year, label, cell(row, 2), cell(row, 3), [b for _, b in beer_cols if not b.startswith("Fenalår")]))
    full = {b: int(num(cell(summary["Number of votes"], j))) for j, b in beer_cols}
    half = {b: int(num(cell(summary["Number of votes 0.5 weight"], j))) for j, b in beer_cols}
    weight = {}
    voters = [(v, {b for b, _ in s}) for v, _, s in scored if s]
    while len(weight) < len(voters):
        settled = len(weight)
        for _j, beer in beer_cols:
            open_ = [v for v, beers in voters if beer in beers and v not in weight]
            need_half = half[beer] - sum(1 for v, beers in voters if beer in beers and weight.get(v) == 0.5)
            need_full = full[beer] - sum(1 for v, beers in voters if beer in beers and weight.get(v) == 1.0)
            if open_ and (need_half == 0 or need_full == len(open_)):
                weight.update((v, 1.0) for v in open_)
            elif open_ and (need_full == 0 or need_half == len(open_)):
                weight.update((v, 0.5) for v in open_)
        if len(weight) == settled:
            raise ValueError(f"{year}: the 0.5-weight counts do not settle every voter's weight")
    for voter, _row, scores in scored:
        for beer, score in scores:
            votes.append((year, voter, fmt(weight[voter]), beer, fmt(score)))
    total = summary["Total number of votes"]
    for j, beer in beer_cols:
        results.append((year, beer, fmt(num(cell(total, j))), "", "", "", fmt(full[beer] + 0.5 * half[beer])))


def sheet_2010(grid, votes, results, voter_checks, notes):
    """2010: a row `Vekting, Antall` above the header row of beer names, then the rows `Vektet snitt`,
    `Stemmevolum` (sum of weights) and `Gjennomsnitt` (unweighted mean), then one row per voter:
    name, weight (Vekting), number of scores (Antall), one cell per beer. The sheet runs on in a second
    table with three more voters. `Antall` counts every column, the Fenalår included."""
    year = 2010
    head = next(i for i, r in enumerate(grid) if cell(r, 2) == "Vekting") + 1
    cols = grid[head]
    beer_cols = [(j, cols[j]) for j in range(4, len(cols)) if cols[j]]
    summary = {}
    for row in grid[head + 1:]:
        label = cell(row, 1)
        if label in ("Vektet snitt", "Stemmevolum", "Gjennomsnitt"):
            summary[label] = row
        elif label:
            weight = num(cell(row, 2))
            if weight is None:
                raise ValueError(f"{year}: no weight for {label}")
            for beer, score in sheet_scores(year, row, beer_cols, label, notes):
                votes.append((year, label, fmt(weight), beer, fmt(score)))
            voter_checks.append((year, label, cell(row, 3), "", [b for _, b in beer_cols]))
    for j, beer in beer_cols:
        stated = [fmt(num(cell(summary[k], j))) for k in ("Gjennomsnitt", "Vektet snitt", "Stemmevolum")]
        if any(stated):
            results.append((year, beer, "", stated[0], stated[1], "", stated[2]))


def extract(src, voter_checks=None, notes=None):
    """Votes and results of the wiki years. voter_checks gets (year, voter, stated count, stated mean, the beers
    the stated numbers cover) for the sheets that state them per voter; notes gets every skipped cell."""
    voter_checks = [] if voter_checks is None else voter_checks
    notes = [] if notes is None else notes
    votes, results = [], []
    voters_as_columns(tables(os.path.join(src, "Juleøltesten-1998.md"))[0], 1998, votes, results)
    voters_as_rows(tables(os.path.join(src, "Juleøltesten-2003.md"))[0], 0, 2003, votes, results)
    voters_as_rows(tables(os.path.join(src, "Juleøltesten-2004.md"))[0], 0, 2004, votes, results)
    voters_as_rows(tables(os.path.join(src, "Juleøltesten-2007.md"))[0], 1, 2007, votes, results)
    sheet_2008(sheet_grid(os.path.join(src, "Juleøltesten-2008.md")), votes, results, voter_checks, notes)
    sheet_2010(sheet_grid(os.path.join(src, "Juleøltesten-2010.md")), votes, results, voter_checks, notes)
    votes = [(y, v, w, NAME_FIXES.get((y, b), b), s) for y, v, w, b, s in votes]
    results = [(r[0], NAME_FIXES.get((r[0], r[1]), r[1])) + tuple(r[2:]) for r in results]
    return votes, results
