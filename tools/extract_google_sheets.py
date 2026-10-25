"""Votes and results from the raw Google Sheets exports in source/google-sheets/<year>.csv.

Each year's sheet has its own layout (the sheets were built by hand). Functions return
(votes, results, participation) rows in the shapes written by build_data.py. Only values
the sheet states are taken; nothing is recomputed here.
"""
import csv
import os
import re


def read(path):
    with open(path, encoding="utf-8", newline="") as fh:
        return list(csv.reader(fh))


def num(cell):
    """A number from a cell ('4', '3,5', '3.5'), or None for blanks and error cells (#DIV/0!, #N/A, ...)."""
    cell = cell.strip().replace(",", ".")
    return float(cell) if re.fullmatch(r"-?\d+(\.\d+)?", cell) else None


def fmt(value):
    return "" if value is None else f"{value:g}"


def cell(row, j):
    return row[j] if j < len(row) else ""


def voters_down(rows, year, first_beer_col=3):
    """2011-2013: voters as rows, beers as columns, stated summary rows above the voters."""
    header = next(i for i, r in enumerate(rows) if cell(r, 2).strip() == "Sort")
    beers = [(j, c.strip()) for j, c in enumerate(rows[header]) if j >= first_beer_col and c.strip()]
    summary = {}
    first_voter = header + 1
    for i in range(header + 1, len(rows)):
        label = cell(rows[i], 0).strip()
        if label in ("Vektet snitt", "#Stemmer", "Gjennomsnitt"):
            summary[label] = rows[i]
            first_voter = i + 1
    votes, results = [], []
    for r in rows[first_voter:]:
        voter = re.sub(r"\s+", " ", cell(r, 0)).strip()
        if not voter:
            continue
        weight = fmt(num(cell(r, 1)))
        for j, beer in beers:
            score = num(cell(r, j))
            if score is not None:
                votes.append((year, voter, weight, beer, fmt(score)))
    for j, beer in beers:
        get = lambda key: fmt(num(cell(summary.get(key, []), j)))
        results.append((year, beer, get("#Stemmer"), get("Gjennomsnitt"), get("Vektet snitt"), "", ""))
    return votes, results, []


def voters_across(rows, year, name_row, weight_row, first_voter_col, mean_col, weight_sum_col=None):
    """2014, 2017-2019: beers as rows, voters as columns, the stated mean per beer in mean_col.

    2014: the beer list ends at the first row without a beer name. 2017-2019: a row counts when it has a beer
    name and a stated vote weight; votes typed on rows without one (the 2017 sheet keeps a catalogue of untasted
    beers below the scored block) are not read.
    """
    voters = []
    for j in range(first_voter_col, len(rows[name_row])):
        name = rows[name_row][j].strip()
        if not name:
            break
        voters.append((j, name, fmt(num(cell(rows[weight_row], j)))))
    votes, results = [], []
    for r in rows[weight_row + 1:]:
        beer = cell(r, 0).strip()
        if weight_sum_col is None:
            if not beer:
                break  # 2014: the beer list ends at the first blank row
        elif not beer or num(cell(r, weight_sum_col)) is None:
            continue  # 2017-2019: blank rows sit inside the scored block; a row without a stated vote weight is not part of it
        for j, name, weight in voters:
            score = num(cell(r, j))
            if score is not None:
                votes.append((year, name, weight, beer, fmt(score)))
        results.append((year, beer, "", "", fmt(num(cell(r, mean_col))), "",
                        fmt(num(cell(r, weight_sum_col))) if weight_sum_col is not None else ""))
    return votes, results, []


def sheet_2015(rows):
    """2015: header row 'Voter, Level, Antall, <beers>', voters below, 'Vektet snitt' row after them."""
    header = next(i for i, r in enumerate(rows) if cell(r, 0).strip() == "Voter")
    beers = [(j, c.strip()) for j, c in enumerate(rows[header]) if j >= 3 and c.strip()]
    votes, results = [], []
    stated = []
    for r in rows[header + 1:]:
        label = cell(r, 0).strip()
        if label == "Vektet snitt":
            stated = r
            break
        if not label:
            continue
        weight = fmt(num(cell(r, 1)))
        for j, beer in beers:
            score = num(cell(r, j))
            if score is not None:
                votes.append((2015, label, weight, beer, fmt(score)))
    for j, beer in beers:
        results.append((2015, beer, "", "", fmt(num(cell(stated, j))), "", ""))
    return votes, results, []


def sheet_2016(rows):
    """2016, the stated side: a ranked list of beers with a weighted score, and votes cast per voter (the single votes: matrix_2016)."""
    results, participation = [], []
    for r in rows[1:]:
        if cell(r, 0).strip():
            results.append((2016, cell(r, 0).strip(), "", "", fmt(num(cell(r, 1))), "", ""))
        if cell(r, 4).strip():
            participation.append((2016, cell(r, 4).strip(), fmt(num(cell(r, 5)))))
    return [], results, participation


# The 2016 matrix (source/google-drive/votes-2016.csv) spells beers the way the voters typed them; the stated results
# (source/google-sheets/2016.csv) use cleaned names for these. Each pair below was matched by weighted mean AND vote count.
NAMES_2016 = {
    "Dahls Store Bjorn": "Dahls Store Bjørn Juleøl",
    "Haand Nisse Mor Brygg": "Haandbryggeriet Nissemor",
    "Dahls Jul Øl": "Dahls Juleøl",
    "Roros – mork lager": "Røros Mørk Lager",
    "Aas Vellagret": "Aass Juleøl Vellagret",
    "Sagne sterk jul": "Sagene Sterk Jul",
    "Rignes bokk": "Ringnes Julebokk",
    "Hansa Jul Øl": "Hansa Juleøl",
    "Bådin Tussvatnett": "Bådin Tussvatnet",
    "NisseKjesk": "Qvart Nissekjesk",
    "Qvart Juleskjesk rod": "Qvart Julekjesk rød",
    "Qvart Juleskjesk grønn": "Qvart Julekjesk grønn",
    "nøgne ø rod jul": "Nøgne Ø Rød Jul",
    "St Hallvards Reserve Jul": "St. Hallvards Reserve-Jul",
}


def matrix_2016(rows):
    """2016 single votes: voters as rows, beers as columns (some after the VOTES CAST column), WEIGHTING in column B.

    A blank weight is 1: with that, every beer's weighted mean and every voter's vote count reproduces the stated results
    (one exception, below). "7 fjrll, 7 sorter" is a second 7-Sorter column with one vote (Eirik, 4); it is kept as its
    own item, because merging it would change the stated mean of "7 Sorter".
    """
    beers = [(j, NAMES_2016.get(c.strip(), c.strip())) for j, c in enumerate(rows[0])
             if j >= 2 and c.strip() and c.strip() != "VOTES CAST"]
    votes = []
    for r in rows[1:]:
        voter = re.sub(r"\s+", " ", cell(r, 0)).strip()
        if not voter:
            continue
        weight = fmt(num(cell(r, 1))) or "1"
        for j, beer in beers:
            score = num(cell(r, j))
            if score is not None:
                votes.append((2016, voter, weight, beer, fmt(score)))
    return votes


def extract(sheet_dir):
    path = lambda year: os.path.join(sheet_dir, f"{year}.csv")
    matrix = os.path.join(os.path.dirname(sheet_dir), "google-drive", "votes-2016.csv")
    parts = [voters_down(read(path(y)), y) for y in (2011, 2012, 2013)]
    parts.append(voters_across(read(path(2014)), 2014, 0, 1, 2, 1))
    parts.append(sheet_2015(read(path(2015))))
    parts.append(sheet_2016(read(path(2016))))
    parts.append((matrix_2016(read(matrix)), [], []))
    for y in (2017, 2018, 2019):
        parts.append(voters_across(read(path(y)), y, 0, 1, 4, 2, 1))
    return [sum((p[i] for p in parts), []) for i in range(3)]
