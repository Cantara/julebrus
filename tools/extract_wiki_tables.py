#!/usr/bin/env python3
"""Extract juleøl test scores from the Cantara wiki pages (docs/puben/*.md in Cantara/wiki).

Used by build_data.py. extract(WIKI_PUBEN_DIR) returns (votes, results) with the values the wiki
pages state; nothing is recomputed. Only years whose page holds a readable table are handled here.
"""
import os
import re
from html.parser import HTMLParser


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


def extract(src):
    votes, results = [], []
    voters_as_columns(tables(os.path.join(src, "Juleøltesten-1998.md"))[0], 1998, votes, results)
    voters_as_rows(tables(os.path.join(src, "Juleøltesten-2003.md"))[0], 0, 2003, votes, results)
    voters_as_rows(tables(os.path.join(src, "Juleøltesten-2004.md"))[0], 0, 2004, votes, results)
    voters_as_rows(tables(os.path.join(src, "Juleøltesten-2007.md"))[0], 1, 2007, votes, results)
    return votes, results
