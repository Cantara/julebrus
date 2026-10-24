#!/usr/bin/env python3
"""Rebuild data/*.csv from the wiki pages and the raw Google Sheets exports.

Usage: build_data.py WIKI_PUBEN_DIR   (a clone of Cantara/wiki: docs/puben)

Writes data/votes.csv, data/results.csv and data/participation.csv, then compares every stated
mean with the mean recomputed from the votes and prints the differences (nothing is corrected).
"""
import collections
import csv
import os
import sys

# python3 -I leaves the script's own folder off sys.path; the two extractors live next to this file.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.dont_write_bytecode = True
import extract_google_sheets  # noqa: E402
import extract_wiki_tables  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FILES = {
    "votes.csv": ["year", "voter", "weight", "beer", "score"],
    "results.csv": ["year", "beer", "n_votes", "mean_unweighted", "mean_weighted", "rank", "weight_sum"],
    "participation.csv": ["year", "voter", "votes_cast"],
}


def write(name, rows):
    with open(os.path.join(ROOT, "data", name), "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(FILES[name])
        w.writerows(rows)


def compare(votes, results):
    by = collections.defaultdict(list)
    for year, _voter, weight, beer, score in votes:
        by[(int(year), beer)].append((float(score), float(weight) if weight else 1.0))
    for year, beer, n, mean_u, mean_w, _rank, weight_sum in results:
        scores = by.get((int(year), beer), [])
        if not scores:
            continue
        calc_w = sum(a * w for a, w in scores) / sum(w for _, w in scores)
        calc_u = sum(a for a, _ in scores) / len(scores)
        if mean_w and abs(float(mean_w) - calc_w) > 0.02:
            print(f"differs {year} {beer}: stated weighted mean {mean_w}, from votes {calc_w:.2f}")
        if mean_u and abs(float(mean_u) - calc_u) > 0.02:
            print(f"differs {year} {beer}: stated mean {mean_u}, from votes {calc_u:.2f}")
        if n and int(float(n)) != len(scores):
            print(f"differs {year} {beer}: stated {n} votes, listed {len(scores)}")
        if weight_sum and abs(float(weight_sum) - sum(w for _, w in scores)) > 0.01:
            print(f"differs {year} {beer}: stated vote weight {weight_sum}, listed {sum(w for _, w in scores):g}")


def compare_voters(votes, voter_checks):
    """The per-voter numbers a sheet states (2008: beer votes and their mean; 2010: Antall) against the votes."""
    by = collections.defaultdict(list)
    for year, voter, _weight, beer, score in votes:
        by[(int(year), voter)].append((beer, float(score)))
    for year, voter, n, mean, covered in voter_checks:
        scores = [s for b, s in by.get((year, voter), []) if b in covered]
        if n and int(float(n)) != len(scores):
            print(f"differs {year} {voter}: stated {n} votes, listed {len(scores)}")
        stated = extract_wiki_tables.num(mean) if mean else None
        if stated is not None and scores and abs(stated - sum(scores) / len(scores)) > 0.02:
            print(f"differs {year} {voter}: stated mean {mean}, from votes {sum(scores) / len(scores):.2f}")


def main(wiki_dir):
    voter_checks, notes = [], []
    wiki_votes, wiki_results = extract_wiki_tables.extract(wiki_dir, voter_checks, notes)
    for note in notes:
        print(note)
    sheet_votes, sheet_results, participation = extract_google_sheets.extract(os.path.join(ROOT, "source", "google-sheets"))
    votes, results = wiki_votes + sheet_votes, wiki_results + sheet_results
    write("votes.csv", votes)
    write("results.csv", results)
    write("participation.csv", participation)
    print(f"{len(votes)} votes, {len(results)} result rows, {len(participation)} participation rows")
    compare(votes, results)
    compare_voters(votes, voter_checks)


if __name__ == "__main__":
    main(sys.argv[1])
