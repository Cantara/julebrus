#!/usr/bin/env python3
"""Product facts from two Norwegian beer blogs' yearly posts about the Vinmonopolet juleøl launch -> data/vinmonopolet/<year>.csv.

Usage: extract_vinmonopolet_blog.py YEAR SAVED_PAGE.html     (YEAR = 2022, 2023, 2024 or 2025)
The pages are fetched by hand (or by a script that respects robots.txt) and are NOT stored in the repository; only the facts are kept.

Formats (secondary sources, not Vinmonopolet's own files):
  2025 olbloggen.no: one HTML table (Land, Artikkelnr, Produsent, Artikkelnavn, Ølstil, ABV, Volum, Salgspris inkl pant).
  2022, 2024 thegundersen.wordpress.com: a run-together list "- article no - name - price" (2022: some numbers carry a "(BU)" marker,
  kept as `selection`).
  2023 thegundersen.wordpress.com: "- article no - name - ABV - price".
Standard library only. Columns are the same as extract_vinmonopolet_xlsx.py; unknown = empty.
"""
import csv
import html
import os
import re
import sys
from html.parser import HTMLParser

OUT = ["year", "article_no", "name", "brewery", "style", "category", "selection", "country", "region", "organic",
       "abv_percent", "volume_l", "price_nok"]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def num(text):
    text = text.strip().replace(",", ".")
    if not re.fullmatch(r"\d+(\.\d+)?", text):
        raise ValueError(f"not a number: {text!r}")
    return f"{float(text):g}"


ART = re.compile(r"^(?:\(([A-Za-zÆØÅæøå]{1,4})\)\s*)?(\d{5,9})$")
NUM = re.compile(r"^\d{1,4}(,\d{1,2})?$")


def dash_runs(page, tail):
    """Records of a ' - '-separated run, anchored on the article numbers: [tag, article no, name, *tail numbers].

    A record is everything up to the next article number; its last `tail` tokens are numbers (2 = ABV and price, 1 = price), the tokens
    between the number and the tail are the name (rejoined with ' - ' if the name itself held a dash). `tag` = an optional "(BU)"-style
    marker before the number. A record whose tail is not numeric is returned with blank numbers and reported on stderr.
    """
    text = re.sub(r"(?s)<(script|style)[^>]*>.*?</\1>", "", page)
    paras = [re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", p))).strip() for p in re.findall(r"(?s)<p[^>]*>(.*?)</p>", text)]
    rows = []
    for para in paras:
        if para.count(" – ") < 6:
            continue
        toks = [t.strip().lstrip("–").strip() for t in para.split(" – ")]
        merged = []
        for t in toks:  # a lone "(TU)" marker belongs to the article number that follows it
            if merged and re.fullmatch(r"\([A-Za-zÆØÅæøå]{1,4}\)", merged[-1]):
                merged[-1] = merged[-1] + " " + t
            else:
                merged.append(t)
        if merged and re.fullmatch(r"\([A-Za-zÆØÅæøå]{1,4}\)", merged[-1]):
            merged.pop()  # a marker at the end of a paragraph belongs to the next paragraph's first number
        toks = merged
        starts = [i for i, t in enumerate(toks) if ART.match(t)]
        for n, i in enumerate(starts):
            end = starts[n + 1] if n + 1 < len(starts) else len(toks)
            chunk = toks[i:end]
            m = ART.match(chunk[0])
            body = chunk[1:]
            nums = body[-tail:] if len(body) > tail else []
            glued = re.match(r"^(.*\S)\s+(\d{1,4},\d{1,2})$", body[-1]) if body else None
            if len(nums) == tail and all(NUM.match(x) for x in nums):
                name = " – ".join(body[:-tail])
            elif tail == 1 and glued:  # 2022's second half writes "name price" in one token
                nums, name = [glued.group(2)], " – ".join(body[:-1] + [glued.group(1)])
            else:
                nums, name = [""] * tail, " – ".join(body)
                print(f"warning: record {m.group(2)} has no numeric tail: {chunk[:4]}", file=sys.stderr)
            rows.append([m.group(1) or "", m.group(2), name] + nums)
    return rows


class Tables(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tables, self._t, self._row, self._cell = [], None, None, None

    def handle_starttag(self, tag, attrs):
        if tag == "table":
            self._t = []
        elif tag == "tr" and self._t is not None:
            self._row = []
        elif tag in ("td", "th") and self._row is not None:
            self._cell = []

    def handle_endtag(self, tag):
        if tag in ("td", "th") and self._cell is not None:
            self._row.append(re.sub(r"\s+", " ", "".join(self._cell)).strip())
            self._cell = None
        elif tag == "tr" and self._row is not None:
            self._t.append(self._row)
            self._row = None
        elif tag == "table" and self._t is not None:
            self.tables.append(self._t)
            self._t = None

    def handle_data(self, d):
        if self._cell is not None:
            self._cell.append(d)


def rec(year, **kw):
    r = dict.fromkeys(OUT, "")
    r["year"] = year
    r.update(kw)
    return [r[k] for k in OUT]


def parse(year, page):
    if year == "2025":
        p = Tables()
        p.feed(page)
        t = max(p.tables, key=len)
        head = t[0]
        ix = {h: head.index(h) for h in ("Land", "Artikkelnr", "Produsent", "Artikkelnavn", "Ølstil", "ABV", "Volum", "Salgspris inkl pant")}
        out = []
        for r in t[1:]:
            if len(r) < len(head) or not re.fullmatch(r"\d{5,9}", r[ix["Artikkelnr"]]):
                continue
            country, _, region = [x.strip() for x in (r[ix["Land"]] + ",").partition(",")]
            out.append(rec(year, article_no=r[ix["Artikkelnr"]], name=r[ix["Artikkelnavn"]], brewery=r[ix["Produsent"]], style=r[ix["Ølstil"]],
                           country=country, region=region.rstrip(", "), abv_percent=num(r[ix["ABV"]]), volume_l=num(r[ix["Volum"]]),
                           price_nok=num(r[ix["Salgspris inkl pant"]])))
        return out
    if year in ("2022", "2024"):
        return [rec(year, article_no=a, name=n, selection=t, price_nok=num(p) if p else '') for t, a, n, p in dash_runs(page, 1)]
    if year == "2023":
        return [rec(year, article_no=a, name=n, selection=t, abv_percent=num(v) if v else '', price_nok=num(p) if p else '') for t, a, n, v, p in dash_runs(page, 2)]
    raise SystemExit("YEAR must be 2022, 2023, 2024 or 2025")


def main(year, path):
    with open(path, encoding="utf-8", errors="replace") as fh:
        rows = parse(year, fh.read())
    ids = [r[1] for r in rows]
    assert len(ids) == len(set(ids)), "duplicate article numbers"
    dest = os.path.join(ROOT, "data", "vinmonopolet", f"{year}.csv")
    with open(dest, "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(OUT)
        w.writerows(rows)
    print(f"{len(rows)} products -> {dest}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
