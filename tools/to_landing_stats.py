#!/usr/bin/env python3
"""Write the aggregates the public landing page shows (landing/history-stats.json) from data/.

Usage: to_landing_stats.py OUT_FILE   (the app's backend/src/main/resources/landing/history-stats.json)

Aggregates only: counts per season, the dice distribution, the mean score given per season, the top beer
per season, the beers that come back most often and the size of the Vinmonopolet juleøl lists. No voter
name, no per-person score and nothing derived from one person goes into the file; the landing page reads
nothing else, so a visitor who is not logged in never gets personal data. LandingStatsTest recomputes a few
of the numbers from data/ and checks that no voter name is in the file.

Rules (the page states them):
- A blank weight counts as 1, as in the app's history import.
- The top beer of a season is the highest weighted mean among beers with at least MIN_VOTES votes
  (beer names compared case-insensitively, as written in that year's source). Ties are all listed.
- 2016 has no single votes: beers and the top beer come from the sheet's ranked list (data/results.csv),
  voters and votes from data/participation.csv (counted, never named).
- Beer names are corrected with the app's history/beer_names.csv (year, as written, correct name), the same
  file the app applies to the database, so the page and the app group the same spellings. data/ stays as written.
- Beers that come back: names as written, compared case-insensitively; spellings are not merged.
"""
import csv
import json
import os
import sys
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data")
MIN_VOTES = 3
REGULARS = 8


def read(*path):
    with open(os.path.join(DATA, *path), encoding="utf-8", newline="") as fh:
        return list(csv.DictReader(fh))


def beer_names():
    """(year, name as written) -> corrected name, from the app's history/beer_names.csv."""
    path = os.path.join(ROOT, "backend", "src", "main", "resources", "history", "beer_names.csv")
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8", newline="") as fh:
        lines = [ln for ln in fh if not ln.startswith("#") and ln.strip()]
    return {(r["year"].strip(), r["as_written"].strip()): r["canonical"].strip() for r in csv.DictReader(lines)}


def correct(rows, names):
    """The rows with `beer` replaced by its corrected name where beer_names.csv has one."""
    return [dict(r, beer=names.get((r["year"], r["beer"].strip()), r["beer"])) for r in rows]


def weight(row):
    return float(row["weight"]) if row["weight"].strip() else 1.0


def weighted_mean(rows):
    w = sum(weight(r) for r in rows)
    return None if w == 0 else sum(float(r["score"]) * weight(r) for r in rows) / w


def season_from_votes(year, rows):
    beers = defaultdict(list)
    for r in rows:
        beers[r["beer"].strip().lower()].append(r)
    ranked = []
    for votes in beers.values():
        m = weighted_mean(votes)
        if m is not None and len(votes) >= MIN_VOTES:
            ranked.append((m, len(votes), votes[0]["beer"].strip()))
    best = max(m for m, _, _ in ranked)
    top = sorted(([name, round(m, 2), n] for m, n, name in ranked if abs(m - best) < 1e-9), key=lambda t: t[0].lower())
    mean = weighted_mean(rows)
    return {
        "year": year,
        "source": "votes",
        "beers": len(beers),
        "voters": len({r["voter"] for r in rows}),
        "votes": len(rows),
        "meanScore": None if mean is None else round(mean, 2),
        "top": [{"beer": name, "mean": m, "votes": n} for name, m, n in top],
    }


def season_2016(results, participation):
    rows = [r for r in results if r["year"] == "2016"]
    best = max(float(r["mean_weighted"]) for r in rows)
    top = sorted((r["beer"].strip() for r in rows if float(r["mean_weighted"]) == best), key=str.lower)
    people = [r for r in participation if r["year"] == "2016"]
    return {
        "year": 2016,
        "source": "aggregate",
        "beers": len(rows),
        "voters": len(people),
        "votes": sum(int(r["votes_cast"]) for r in people),
        "meanScore": None,
        "top": [{"beer": name, "mean": round(best, 2), "votes": None} for name in top],
    }


def main(out):
    names = beer_names()
    votes = correct(read("votes.csv"), names)
    results = correct(read("results.csv"), names)
    participation = read("participation.csv")

    by_year = defaultdict(list)
    for r in votes:
        by_year[int(r["year"])].append(r)
    seasons = [season_from_votes(y, rows) for y, rows in sorted(by_year.items())]
    seasons.append(season_2016(results, participation))
    seasons.sort(key=lambda s: s["year"])

    dice = Counter()
    other = 0
    for r in votes:
        s = float(r["score"])
        if s.is_integer() and 1 <= s <= 6:
            dice[int(s)] += 1
        else:
            other += 1

    seen = defaultdict(set)
    count = Counter()
    spellings = defaultdict(Counter)
    for r in votes:
        key = r["beer"].strip().lower()
        seen[key].add(int(r["year"]))
        count[key] += 1
        spellings[key][r["beer"].strip()] += 1
    regulars = sorted(seen, key=lambda k: (-len(seen[k]), -count[k], k))[:REGULARS]

    vinmonopolet = []
    folder = os.path.join(DATA, "vinmonopolet")
    for name in sorted(os.listdir(folder)):
        stem = name[:-4]
        if name.endswith(".csv") and stem.isdigit():
            vinmonopolet.append({"year": int(stem), "listings": len(read("vinmonopolet", name))})

    stats = {
        "_comment": "Generated by tools/to_landing_stats.py from data/. Aggregates only, no names of people. Do not edit by hand.",
        "minVotesForTop": MIN_VOTES,
        "seasons": seasons,
        "totals": {
            "seasonsWithVotes": len(by_year),
            "seasonsRecorded": len(seasons),
            "firstYear": seasons[0]["year"],
            "lastYear": seasons[-1]["year"],
            "votes": len(votes),
            "votesIncludingAggregate": len(votes) + sum(s["votes"] for s in seasons if s["source"] == "aggregate"),
        },
        "dice": {str(k): dice[k] for k in range(1, 7)},
        "nonIntegerScores": other,
        "regulars": [{"beer": spellings[k].most_common(1)[0][0], "seasons": len(seen[k]),
                      "votes": count[k], "years": sorted(seen[k])} for k in regulars],
        "vinmonopolet": vinmonopolet,
    }
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(stats, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    print(f"{len(seasons)} seasons, {len(votes)} votes, {len(vinmonopolet)} Vinmonopolet lists -> {out}")


if __name__ == "__main__":
    main(sys.argv[1])
