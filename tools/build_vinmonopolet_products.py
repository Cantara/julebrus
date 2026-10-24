#!/usr/bin/env python3
"""Consolidate the yearly Vinmonopolet lists: data/vinmonopolet/listings.csv and products.csv. Standard library only.

Reads data/vinmonopolet/<year>.csv (2014-2020 carry no article numbers; 2013 and 2022-2025 do).
 - listings.csv: every listing with `product_article_no` and `match`: given (the source states the number), name (an UNAMBIGUOUS exact name match
   to a numbered product, case, accents, punctuation and a trailing year ignored), ambiguous (the name sits under several article numbers: no link),
   none (no numbered product has that name).
 - products.csv: one row per article number. Attributes come from the NEWEST numbered year that states them (brewery and style exist only in 2025,
   ABV in 2013, 2023, 2025); years_given vs years_inferred are kept apart; price_by_year uses both; abv_conflict = 1 when two years state different ABV.
"""
import csv
import glob
import os
import re
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(ROOT, "data", "vinmonopolet")
LIST_OUT = ["year", "article_no", "name", "selection", "price_nok", "abv_percent", "product_article_no", "match"]
PROD_OUT = ["article_no", "name", "brewery", "style", "country", "region", "abv_percent", "abv_conflict", "volume_l", "organic",
            "first_year", "last_year", "years_given", "years_inferred", "price_by_year", "names_seen"]


def key(name):
    n = unicodedata.normalize("NFKD", name.casefold())
    n = "".join(c for c in n if not unicodedata.combining(c)).replace("’", "'")
    n = re.sub(r"\b20\d\d\b", " ", n)
    return re.sub(r"[^a-z0-9]+", " ", n).strip()


def main():
    years = {}
    for path in sorted(glob.glob(os.path.join(DIR, "20??.csv"))):
        with open(path, encoding="utf-8", newline="") as fh:
            rows = list(csv.DictReader(fh))
        years[rows[0]["year"]] = rows
    numbered = {}
    for rows in years.values():
        for r in rows:
            if r["article_no"]:
                numbered.setdefault(key(r["name"]), set()).add(r["article_no"])
    listings, by_art = [], {}
    for year, rows in years.items():
        for r in rows:
            if r["article_no"]:
                art, match = r["article_no"], "given"
            else:
                cands = numbered.get(key(r["name"]), set())
                art, match = (next(iter(cands)), "name") if len(cands) == 1 else ("", "ambiguous" if cands else "none")
            listings.append([year, r["article_no"], r["name"], r["selection"], r["price_nok"], r["abv_percent"], art, match])
            if art:
                by_art.setdefault(art, []).append((int(year), match, r))
    products = []
    for art, items in sorted(by_art.items()):
        items.sort(key=lambda x: x[0])
        given = [x for x in items if x[1] == "given"] or items
        newest = lambda col: next((r[col] for _, m, r in reversed(given) if r[col]), "")
        abvs = sorted({r["abv_percent"] for _, _, r in items if r["abv_percent"]})
        names = []
        for _, _, r in items:
            if r["name"] not in names:
                names.append(r["name"])
        products.append([art, given[-1][2]["name"], newest("brewery"), newest("style"), newest("country"), newest("region"), newest("abv_percent"),
                         "1" if len(abvs) > 1 else "0", newest("volume_l"), newest("organic"), items[0][0], items[-1][0],
                         ";".join(str(y) for y, m, _ in items if m == "given"), ";".join(str(y) for y, m, _ in items if m != "given"),
                         ";".join(f"{y}:{r['price_nok']}" for y, _, r in items if r["price_nok"]), " | ".join(names) if len(names) > 1 else ""])
    for name, head, rows in (("listings.csv", LIST_OUT, listings), ("products.csv", PROD_OUT, products)):
        with open(os.path.join(DIR, name), "w", newline="", encoding="utf-8") as fh:
            w = csv.writer(fh, lineterminator="\n")
            w.writerow(head)
            w.writerows(rows)
    from collections import Counter
    c = Counter((l[0], l[7]) for l in listings)
    print(f"{len(listings)} listings in {len(years)} years, {len(products)} products")
    for y in sorted(years):
        print(f"  {y}: " + ", ".join(f"{m} {c[(y, m)]}" for m in ("given", "name", "ambiguous", "none") if c[(y, m)]))


if __name__ == "__main__":
    main()
