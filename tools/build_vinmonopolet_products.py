#!/usr/bin/env python3
"""One row per Vinmonopolet article number across all yearly lists -> data/vinmonopolet/products.csv.

Reads data/vinmonopolet/<year>.csv. Attributes come from the NEWEST year that states them (brewery and style only exist in 2025, ABV in
2013, 2023 and 2025), so older listings inherit them; `abv_conflict` is 1 when two years state different ABV for the same number.
Standard library only.
"""
import csv
import glob
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(ROOT, "data", "vinmonopolet")
OUT = ["article_no", "name", "brewery", "style", "country", "region", "abv_percent", "abv_conflict", "volume_l", "organic",
       "first_year", "last_year", "years", "price_by_year", "names_seen"]


def main():
    by_art = {}
    for path in sorted(glob.glob(os.path.join(DIR, "20??.csv"))):
        with open(path, encoding="utf-8", newline="") as fh:
            for r in csv.DictReader(fh):
                by_art.setdefault(r["article_no"], []).append(r)
    rows = []
    for art, rs in sorted(by_art.items()):
        rs.sort(key=lambda r: int(r["year"]))
        newest = lambda col: next((r[col] for r in reversed(rs) if r[col]), "")
        abvs = sorted({r["abv_percent"] for r in rs if r["abv_percent"]})
        names = []
        for r in rs:
            if r["name"] not in names:
                names.append(r["name"])
        rows.append([art, rs[-1]["name"], newest("brewery"), newest("style"), newest("country"), newest("region"), newest("abv_percent"),
                     "1" if len(abvs) > 1 else "0", newest("volume_l"), newest("organic"), rs[0]["year"], rs[-1]["year"],
                     ";".join(r["year"] for r in rs), ";".join(f'{r["year"]}:{r["price_nok"]}' for r in rs if r["price_nok"]),
                     " | ".join(names) if len(names) > 1 else ""])
    dest = os.path.join(DIR, "products.csv")
    with open(dest, "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(OUT)
        w.writerows(rows)
    have = lambda i: sum(1 for r in rows if r[i])
    print(f"{len(rows)} products -> {dest}")
    print(f"with brewery {have(2)}, style {have(3)}, country {have(4)}, abv {have(6)}; abv conflicts {sum(1 for r in rows if r[7] == '1')}; name variants {have(14)}")


if __name__ == "__main__":
    main()
