#!/usr/bin/env python3
"""Write a review sheet of beer names that look misspelled, for a human to decide on.

Usage: python3 -I tools/suggest_beer_name_fixes.py [--data DIR] [--app-history DIR] [--out FILE]

Reads the names AS WRITTEN (data/votes.csv, data/results.csv and the app's history/<year>.csv files) and the
correct names from data/vinmonopolet/ (listings.csv and products.csv), and writes data/review/beer-name-candidates.csv.
Nothing is applied: a person fills the `decision` column (accept / keep / another spelling), and the accepted
rows are then copied into backend/src/main/resources/history/beer_names.csv (year, as written, correct name).

Columns: year, as_written, vote_count, suggested_name, source, similarity, note, decision (empty).
Sources:
  vinmonopolet-year        the name matches a Vinmonopolet listing of the same year
  vinmonopolet-other-year  the name matches a listing or product name of another year
  same-name-other-year     the name matches how the beer is written in the votes of another year
  variant-of               the same beer written differently in different years (e.g. "Hansa" and "Hansa Juleøl")
  no-suggestion            rare name that is in no Vinmonopolet list and nowhere else: look at it by hand
Similarity 1.0 means identical after normalisation (case, accents, æøå, hyphens, spacing, "juleøl"/"jule-øl",
a trailing year); below that it is difflib's ratio on the normalised names. Standard library only.
"""
import argparse
import csv
import difflib
import os
import re
import sys
import unicodedata
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIELDS = ["year", "as_written", "vote_count", "suggested_name", "source", "similarity", "note", "decision"]
MIN_SIMILARITY = 0.80      # below this a fuzzy match is not shown
RARE = 2                   # a name with at most this many votes and no match anywhere is "suspicious"
FOLD = str.maketrans({"æ": "ae", "ø": "o", "å": "a", "ö": "o", "ä": "a", "ó": "o", "é": "e", "è": "e", "ü": "u"})
GENERIC = {"juleol", "ol"}  # words that do not tell beers apart


def norm(name):
    """Comparison key: lower case, accents and æøå folded, punctuation and hyphens as spaces, "jule øl" as one word,
    a trailing year dropped, spacing collapsed."""
    s = unicodedata.normalize("NFC", name).lower().translate(FOLD)
    s = "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))
    s = re.sub(r"[^a-z0-9]+", " ", s).strip()
    s = re.sub(r"\bjule ol\b", "juleol", s)
    s = re.sub(r"\s+(19|20)\d\d$", "", s)
    return s


def core(key):
    """The key without the words that do not tell beers apart ("Hansa" for "Hansa Juleøl")."""
    return " ".join(t for t in key.split() if t not in GENERIC)


def read_csv(path):
    with open(path, encoding="utf-8", newline="") as fh:
        return [r for r in csv.DictReader(fh) if r and not (list(r.values())[0] or "").startswith("#")]


def load_written(data_dir, app_dir):
    """(year, name) -> (votes, where it was found). Names are kept exactly as written, only stripped at the ends."""
    counts = {}
    for r in read_csv(os.path.join(data_dir, "votes.csv")):
        k = (int(r["year"]), r["beer"].strip())
        counts[k] = counts.get(k, 0) + 1
    for r in read_csv(os.path.join(data_dir, "results.csv")):
        k = (int(r["year"]), r["beer"].strip())
        if k not in counts:
            counts[k] = int(r["n_votes"]) if (r.get("n_votes") or "").strip().isdigit() else 0
    if app_dir and os.path.isdir(app_dir):
        app = Counter()
        for fn in sorted(os.listdir(app_dir)):
            if re.fullmatch(r"\d{4}\.csv", fn):
                with open(os.path.join(app_dir, fn), encoding="utf-8", newline="") as fh:
                    lines = [ln for ln in fh if not ln.startswith("#")]
                for r in csv.DictReader(lines):
                    app[(int(fn[:4]), r["beer_name"].strip())] += 1
        for k, n in app.items():
            counts.setdefault(k, n)
    return counts


def load_vinmonopolet(vin_dir):
    """year -> set of listed names, and the set of all product names (including renames seen)."""
    by_year = defaultdict(set)
    for r in read_csv(os.path.join(vin_dir, "listings.csv")):
        by_year[int(r["year"])].add(r["name"].strip())
    products = set()
    path = os.path.join(vin_dir, "products.csv")
    if os.path.exists(path):
        for r in read_csv(path):
            products.add(r["name"].strip())
            products.update(n.strip() for n in (r.get("names_seen") or "").split("|") if n.strip())
    return by_year, products


def best_match(key, pool):
    """pool: {norm key: [spellings]}. The most similar key (1.0 for an equal one) as (ratio, key), or None."""
    best = None
    for k in pool:
        if k == key:
            return 1.0, k
        # Equal only apart from "juleøl" ("Aass" / "Aass Juleøl") is close, but never as sure as an equal key.
        ratio = max(difflib.SequenceMatcher(None, key, k).ratio(),
                    min(0.97, difflib.SequenceMatcher(None, core(key), core(k)).ratio()) if core(key) and core(k) else 0)
        if ratio >= MIN_SIMILARITY and (best is None or ratio > best[0]):
            best = (ratio, k)
    return best


def pool_of(names):
    pool = defaultdict(list)
    for n in names:
        if n not in pool[norm(n)]:
            pool[norm(n)].append(n)
    return pool


def only_labels_differ(a, b):
    """True when two keys differ only in short tokens ("Fenalår A" / "Fenalår P"): different items, not a typo."""
    return all(len(t) <= 2 for t in set(a.split()) ^ set(b.split())) and a != b


def pick(spellings):
    """The spelling to suggest among those that share a key: the first one in sorted order, for a stable result."""
    return sorted(spellings)[0]


def brewery_words(by_year):
    """First words that start at least three different listed names: read as brewery names."""
    first = defaultdict(set)
    for names in by_year.values():
        for n in names:
            toks = norm(n).split()
            if len(toks) >= 2:
                first[toks[0]].add(norm(n))
    return {w for w, ns in first.items() if len(ns) >= 3}


def variant_groups(written, breweries):
    """Group distinct written keys that are the same beer: equal after dropping 'juleøl', or a brewery-less spelling
    next to a spelling with exactly one brewery word in front. Returns {group id: set of keys}."""
    keys = {norm(n) for (_, n) in written}
    groups = defaultdict(set)
    for k in keys:
        if core(k):
            groups[core(k)].add(k)
    bare = {c: ks for c, ks in groups.items()}
    for k in keys:
        toks = core(k).split()
        if len(toks) >= 2 and toks[0] in breweries:
            rest = " ".join(toks[1:])
            owners = {core(o).split()[0] for o in keys if core(o).endswith(rest) and core(o) != rest
                      and core(o).split()[0] in breweries and " ".join(core(o).split()[1:]) == rest}
            if rest in bare and len(owners) == 1:
                groups[rest] |= {k}
                groups[core(k)] |= bare[rest]
    return {g: ks for g, ks in groups.items() if len(ks) > 1}


def build_rows(data_dir, app_dir, vin_dir):
    written = load_written(data_dir, app_dir)
    by_year, products = load_vinmonopolet(vin_dir)
    all_vin = pool_of(set().union(*by_year.values()) | products) if by_year else {}
    year_pool = {y: pool_of(ns) for y, ns in by_year.items()}
    # How each beer is written in the votes, per key, with the number of votes.
    spell = defaultdict(Counter)
    years_of = defaultdict(lambda: defaultdict(set))
    for (y, n), c in written.items():
        spell[norm(n)][n] += c
        years_of[norm(n)][n].add(y)
    rows = []
    listed = set()
    vin_spellings = {n for ns in all_vin.values() for n in ns}
    for (y, name), count in sorted(written.items()):
        key = norm(name)
        in_year = year_pool.get(y, {})
        if not key or name in {n for ns in in_year.values() for n in ns}:
            continue                                       # exactly as Vinmonopolet wrote it that year
        found = None                                       # (ratio, spelling, source, note)
        m = best_match(key, in_year) if in_year else None
        if m and (m[0] == 1.0 or key not in all_vin):
            found = (m[0], pick(in_year[m[1]]), "vinmonopolet-year",
                     "same as the %s listing after normalisation" % y if m[0] == 1.0 else "closest %s listing" % y)
        else:
            cands = []
            m2 = best_match(key, all_vin) if all_vin and name not in vin_spellings else None
            if m2:
                years = sorted(yy for yy, p in year_pool.items() if m2[1] in p)
                cands.append((m2[0], 1, pick(all_vin[m2[1]]), "vinmonopolet-other-year",
                              "listed in %s" % (", ".join(map(str, years)) if years else "the product list")))
            others = {}
            for k, c in spell.items():
                ns = [n for n in c if n != name and any(yy != y for yy in years_of[k][n])]
                if ns:
                    others[k] = ns
            m3 = best_match(key, {k: v for k, v in others.items() if not only_labels_differ(key, k)}) if others else None
            if m3 and not any(yy != y for yy in years_of[key][name]):   # written the same in another year: consistent
                best = max(others[m3[1]], key=lambda n: (spell[m3[1]][n], n))
                cands.append((m3[0], 0, best, "same-name-other-year",
                              "written so in %s (%d votes)" % (", ".join(map(str, sorted(years_of[m3[1]][best]))),
                                                              spell[m3[1]][best])))
            if cands:
                cands.sort(key=lambda t: (-t[0], -t[1]))
                found = (cands[0][0], cands[0][2], cands[0][3], cands[0][4])
        if found:
            ratio, sug, src, note = found
            if sug == name or (ratio < 1.0 and core(norm(sug)) == core(key)):
                continue            # same beer written with or without "juleøl": a variant, not a typo (see below)
            rows.append([y, name, count, sug, src, "%.2f" % ratio, note + (" (no single votes in this year)" if count == 0 else ""), ""])
            listed.add((y, name))
        elif count <= RARE and name not in vin_spellings and not any(
                yy != y for n in spell[key] for yy in years_of[key][n]):
            rows.append([y, name, count, "", "no-suggestion", "", "rare name, in no Vinmonopolet list and in no other year", ""])
    # The same beer written differently across years (also for names that already have a suggestion above).
    for g, keys in sorted(variant_groups(written, brewery_words(by_year)).items()):
        total = Counter()
        for k in keys:
            total.update(spell[k])
        canon = max(total, key=lambda n: (total[n], n))
        for (y, name), count in sorted(written.items()):
            if norm(name) in keys and name != canon and (y, name) not in listed:
                yrs = sorted({yy for yy, nn in [(yy, nn) for (yy, nn) in written if nn == canon]})
                rows.append([y, name, count, canon, "variant-of", "%.2f" % (1.0 if norm(name) == norm(canon) else
                             difflib.SequenceMatcher(None, norm(name), norm(canon)).ratio()),
                             "written \"%s\" in %s" % (canon, ", ".join(map(str, yrs))), ""])
    seen_rows, unique = set(), []
    for r in rows:
        if (r[0], r[1], r[4]) not in seen_rows:
            seen_rows.add((r[0], r[1], r[4]))
            unique.append(r)
    rows = unique
    order = {"vinmonopolet-year": 0, "vinmonopolet-other-year": 1, "same-name-other-year": 2, "variant-of": 3, "no-suggestion": 4}
    rows.sort(key=lambda r: (order[r[4]], -float(r[5] or 0), r[0], r[1]))
    return rows


def confidence(row):
    if row[4] == "no-suggestion":
        return "none"
    s = float(row[5])
    return "exact" if s == 1.0 else "high (>=0.90)" if s >= 0.90 else "medium (0.85-0.90)" if s >= 0.85 else "low (<0.85)"


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--data", default=os.path.join(ROOT, "data"))
    ap.add_argument("--app-history", default=os.path.join(ROOT, "backend", "src", "main", "resources", "history"))
    ap.add_argument("--out", default=os.path.join(ROOT, "data", "review", "beer-name-candidates.csv"))
    a = ap.parse_args(argv)
    rows = build_rows(a.data, a.app_history, os.path.join(a.data, "vinmonopolet"))
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(FIELDS)
        w.writerows(rows)
    with_sug = [r for r in rows if r[3]]
    print("%d rows, %d with a suggestion -> %s" % (len(rows), len(with_sug), a.out))
    for label, n in sorted(Counter(confidence(r) for r in rows).items()):
        print("  %-20s %d" % (label, n))
    for label, n in sorted(Counter(r[4] for r in rows).items()):
        print("  source %-24s %d" % (label, n))


if __name__ == "__main__":
    main()
