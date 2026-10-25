#!/usr/bin/env python3
"""Match every (year, beer) of the tasting results to a Vinmonopolet product -> data/vinmonopolet/vote_matches.csv. Standard library only.

Input: data/results.csv (year, beer), data/vinmonopolet/listings.csv (+ products.csv for the names seen). Output columns:
  year, vote_beer, status, method, score, product_article_no, article_options, product_name, list_year, candidates
status: auto    exact name in that year's Vinmonopolet list (or one unambiguous product with that name in another year)
        likely  one clearly best candidate by token subset or fuzzy match: check before trusting
        review  several plausible candidates or a weak match: a human decides (the candidates are listed)
        not-a-product  not a Vinmonopolet beer (cured ham, homebrew, a host's own): out of scope
        none    no candidate at all (no list for that year and no product with a similar name)
A human's decisions go in data/vinmonopolet/vote_match_overrides.csv (columns year, vote_beer, product_article_no, product_name; fill the
article number OR the product name, or write none in either) and win over this script.
"""
import csv
import difflib
import os
import re
import unicodedata
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(ROOT, "data", "vinmonopolet")
STOP = {"juleol", "jule", "julebrygg", "christmas", "xmas", "ol", "ale", "beer", "bryggeri", "bryggeriet", "brygg", "brewery", "brewing", "co"}


def key(name):
    n = unicodedata.normalize("NFKD", name.casefold())
    n = "".join(c for c in n if not unicodedata.combining(c)).replace("’", "'")
    n = re.sub(r"\b(19|20)\d\d\b", " ", n)
    return re.sub(r"[^a-z0-9]+", " ", n).strip()


def toks(name):
    return [t for t in key(name).split() if t]


def core(name):
    return {t for t in toks(name) if t not in STOP}


def tmatch(a, b):
    """Two tokens match when equal, or alike enough for typos (Vellagert/Vellagret, julobok/julebokk), or a prefix of the other (4+ letters)."""
    if a == b:
        return True
    if len(a) >= 4 and len(b) >= 4:
        return difflib.SequenceMatcher(None, a, b).ratio() >= 0.8 or a.startswith(b) or b.startswith(a)
    if len(a) >= 3 and len(b) >= 3:
        return difflib.SequenceMatcher(None, a, b).ratio() >= 0.85  # Aas / Aass
    return False


def cover(vote_tokens, cand_tokens):
    """(all vote tokens found in the candidate?, number of candidate tokens the vote does not mention)."""
    used = set()
    for v in vote_tokens:
        hit = next((i for i, c in enumerate(cand_tokens) if i not in used and tmatch(v, c)), None)
        if hit is None:
            return False, 0
        used.add(hit)
    return True, len(cand_tokens) - len(used)


def read(path):
    with open(path, encoding="utf-8", newline="") as fh:
        return list(csv.DictReader(fh))


NON_BEER = re.compile(r"fen[aå]l[aå]r|spekeskinke|skinke|hjemmebrygg|vertskapet|vossakj", re.I)


def main():
    results = read(os.path.join(ROOT, "data", "results.csv"))
    listings = read(os.path.join(DIR, "listings.csv"))
    overrides = {}
    op = os.path.join(DIR, "vote_match_overrides.csv")
    if os.path.exists(op):
        overrides = {(r["year"], r["vote_beer"]): (r["product_article_no"].strip(), r.get("product_name", "").strip()) for r in read(op)}
    # one entry per normalised beer name (display name, set of article numbers seen under it), per year and over all years
    pool_year, pool_all = defaultdict(dict), {}
    for r in listings:
        k = key(r["name"])
        for pool in (pool_year[r["year"]], pool_all):
            ent = pool.setdefault(k, [r["name"], set()])
            if r["product_article_no"]:
                ent[1].add(r["product_article_no"])
    out = []
    for r in results:
        year, beer = r["year"], r["beer"]
        vk = key(beer)
        row = dict(year=year, vote_beer=beer, status="none", method="", score="", product_article_no="", article_options="", product_name="",
                   list_year="", candidates="")
        arts_of = lambda arts: (next(iter(arts)) if len(arts) == 1 else "", ";".join(sorted(arts)) if len(arts) > 1 else "")
        if (year, beer) in overrides:
            art, nm = overrides[(year, beer)]
            if art.lower() == "none" or nm.lower() == "none":
                row.update(status="none", method="override")
            else:
                row.update(status="auto", method="override", score="1.00", product_article_no=art, product_name=nm or next((n for k, (n, a) in pool_all.items() if art in a), ""))
            out.append(row)
            continue
        if NON_BEER.search(beer):
            row.update(status="not-a-product", method="non-beer words")
            out.append(row)
            continue
        ylist = pool_year.get(year)
        if ylist and vk in ylist:
            name, arts = ylist[vk]
            art, opts = arts_of(arts)
            row.update(status="auto", method="exact-year", score="1.00", product_article_no=art, article_options=opts, product_name=name, list_year=year)
            out.append(row)
            continue
        if vk in pool_all:
            name, arts = pool_all[vk]
            art, opts = arts_of(arts)
            row.update(status="auto" if ylist else "likely", method="exact-other-year", score="0.90", product_article_no=art, article_options=opts, product_name=name)
            out.append(row)
            continue
        pool = ylist if ylist else pool_all
        vlist = [t for t in toks(beer) if t not in STOP]
        cands = []
        for k, (n, arts) in pool.items():
            clist = [t for t in toks(n) if t not in STOP]
            ok, extra = cover(vlist, clist) if vlist else (False, 0)
            if ok:
                sc = 0.92 - 0.04 * min(extra, 6)
                if k in (vk + " juleol", vk + " julebrygg"):
                    sc += 0.06  # "Mack" most likely means the brewery's plain "Mack Juleøl"
                cands.append((sc, "subset", n, arts))
            ratio = difflib.SequenceMatcher(None, vk, k).ratio()
            if ratio >= 0.8:
                cands.append((ratio * 0.95, "fuzzy", n, arts))
        best = {}
        for c in cands:
            if c[2] not in best or c[0] > best[c[2]][0]:
                best[c[2]] = c
        ranked = sorted(best.values(), key=lambda x: -x[0])
        row["candidates"] = " | ".join(f"{n} [{len(arts)} article no.] {sc:.2f}" if len(arts) != 1 else f"{n} [{next(iter(arts))}] {sc:.2f}" for sc, _, n, arts in ranked[:3])
        if ranked:
            sc, m, n, arts = ranked[0]
            margin = sc - (ranked[1][0] if len(ranked) > 1 else 0)
            art, opts = arts_of(arts)
            row.update(method=m + ("-year" if ylist else "-allyears"), score=f"{sc:.2f}", product_article_no=art, article_options=opts, product_name=n,
                       list_year=year if ylist else "")
            row["status"] = "likely" if (sc >= 0.8 and (len(ranked) == 1 or margin >= 0.06)) else "review"
        out.append(row)
    head = ["year", "vote_beer", "status", "method", "score", "product_article_no", "article_options", "product_name", "list_year", "candidates"]
    with open(os.path.join(DIR, "vote_matches.csv"), "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=head, lineterminator="\n")
        w.writeheader()
        w.writerows(out)
    from collections import Counter
    c = Counter((r["year"], r["status"]) for r in out)
    print(f"{len(out)} (year, beer) entries")
    for y in sorted({r["year"] for r in out}):
        print(f"  {y}: " + ", ".join(f"{s} {c[(y, s)]}" for s in ("auto", "likely", "review", "none", "not-a-product") if c[(y, s)]) + ("   [no list for this year]" if y not in pool_year else ""))
    print("total:", dict(Counter(r["status"] for r in out)))


if __name__ == "__main__":
    main()
