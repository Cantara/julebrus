#!/usr/bin/env python3
"""Write the app's history files (history/seasons.csv and history/<year>.csv) from data/votes.csv.

Usage: to_app_history.py OUT_DIR   (the app's backend/src/main/resources/history)

Layout follows HistoryImporter in the app: seasons.csv is `year,title,scale_min,scale_max,weighting_description,source_url`
and each <year>.csv is `beer_name,brewery,rater_name,rater_email,score,weight,notes`. Brewery, e-mail and notes are
empty (the sources do not state them); a blank weight means the source gave none, which the app reads as 1.
Only years with single votes get a season (2016 has none).
"""
import csv
import os
import sys
import urllib.parse

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEIGHTING = "Kvalifiserte stemmegivere teller 1, probies 1/2, failed probies 0 (vekt per stemmegiver som oppgitt i kilden)"
# Where a year's own page states the rules, its season row says what that page says.
WEIGHTING_BY_YEAR = {
    2008: "Stemmer med vekt 1 og 0.5 (regnearket oppgir per øl hvor mange stemmer som hadde vekt 0.5; vekten per stemmegiver er utledet av disse tellingene)",
    2010: "Probies 1/2 vekt, qualified voters 1/1 vekt, failed probies 0/2 vekt (stemmeregler, Juleølsmaking 2010; vekt per stemmegiver som oppgitt i kilden)",
}
CONFLUENCE = {2011: 26083648, 2012: 32178223, 2013: 36897787, 2014: 38964246, 2015: 43649089,
              2017: 45547558, 2018: 46137421, 2019: 47022084}


def title(year):
    return f"Juleølsmaking {year}" if year >= 2010 else f"Juleøltesten {year}"


def source_url(year):
    if year in CONFLUENCE:
        return f"https://wiki.cantara.no/pages/viewpage.action?pageId={CONFLUENCE[year]}"
    return "https://cantara.github.io/wiki/puben/" + urllib.parse.quote(f"Juleøltesten-{year}") + "/"


def main(out):
    by_year = {}
    with open(os.path.join(ROOT, "data", "votes.csv"), encoding="utf-8", newline="") as fh:
        for row in csv.DictReader(fh):
            by_year.setdefault(int(row["year"]), []).append(row)
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(out, "seasons.csv"), "w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(["year", "title", "scale_min", "scale_max", "weighting_description", "source_url"])
        for year in sorted(by_year):
            w.writerow([year, title(year), 1, 6, WEIGHTING_BY_YEAR.get(year, WEIGHTING), source_url(year)])
    for year, rows in sorted(by_year.items()):
        with open(os.path.join(out, f"{year}.csv"), "w", encoding="utf-8", newline="") as fh:
            w = csv.writer(fh, lineterminator="\n")
            w.writerow(["beer_name", "brewery", "rater_name", "rater_email", "score", "weight", "notes"])
            for r in rows:
                w.writerow([r["beer"], "", r["voter"], "", r["score"], r["weight"], ""])
    print(f"{len(by_year)} seasons, {sum(map(len, by_year.values()))} ratings")


if __name__ == "__main__":
    main(sys.argv[1])
