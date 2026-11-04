#!/usr/bin/env python3
"""Turn a Vinmonopolet juleøl list (xlsx, columns Varenr/Artikkel/Utvalg/Varegruppe/Land/Økologisk/Volpst/Volum/Salgspris)
into data/vinmonopolet/<year>.csv. Standard library only.

Since 2026 Vinmonopolet's own file has the columns Artikkelnr/Artikkelnavn/Introdato/Utvalg/Varegruppe/Opprinnelse/Produsent/Grossist/Distributor/
Alkoholprosent/Volum/Salgspris inkl pant/Emballasjetype; that layout is detected by its "Artikkelnr" header. There `style` is the Varegruppe after the
first comma ("Øl, Mørk lager" -> "Mørk lager"), `category` is the part before it ("Øl", "Mjød", "Alkoholfritt øl"), `region` is the part of Opprinnelse
after the first comma, and the wholesaler, distributor, introduction date and packaging are dropped.

Usage: extract_vinmonopolet_xlsx.py YEAR LIST.xlsx [SHEET_NAME]   (default sheet: the first one)

Columns written (shared with extract_vinmonopolet_blog.py; unknown = empty): year, article_no, name, brewery, style, category,
selection, country, region, organic, abv_percent, volume_l, price_nok. 2013's Varegruppe (fermentation type) goes to `category`.
Only the product facts are kept; the list's own text (ordering instructions, contact details) is dropped.
"""
import csv
import os
import re
import sys
import zipfile
from xml.etree import ElementTree as ET

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
Q = "{%s}" % NS["m"]
HEAD = {"Varenr": "article_no", "Artikkel": "name", "Utvalg": "selection", "Varegruppe": "category", "Land": "country",
        "Økologisk": "organic", "Volpst": "abv_percent", "Volum": "volume_l", "Salgspris": "price_nok"}
HEAD2026 = {"Artikkelnr": "article_no", "Artikkelnavn": "name", "Utvalg": "selection", "Produsent": "brewery", "Alkoholprosent": "abv_percent",
            "Volum": "volume_l", "Salgspris inkl pant": "price_nok"}
OUT = ["year", "article_no", "name", "brewery", "style", "category", "selection", "country", "region", "organic",
       "abv_percent", "volume_l", "price_nok"]


def read_sheet(path, sheet_name=None):
    z = zipfile.ZipFile(path)
    shared = ["".join(t.text or "" for t in si.iter(Q + "t")) for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NS)]
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    sheets = [s.get("name") for s in wb.find("m:sheets", NS)]
    idx = sheets.index(sheet_name) if sheet_name else 0
    root = ET.fromstring(z.read(f"xl/worksheets/sheet{idx + 1}.xml"))
    rows = []
    for r in root.iter(Q + "row"):
        row = {}
        for c in r.findall("m:c", NS):
            v = c.find("m:v", NS)
            if v is None:
                continue
            col = re.match(r"[A-Z]+", c.get("r")).group(0)
            row[col] = shared[int(v.text)] if c.get("t") == "s" else v.text
        rows.append(row)
    return rows


def main(year, path, sheet=None):
    rows = read_sheet(path, sheet)
    head_i = next(i for i, r in enumerate(rows) if r.get("A") in ("Varenr", "Artikkelnr"))
    new_layout = rows[head_i].get("A") == "Artikkelnr"
    cols = {col: (HEAD2026 if new_layout else HEAD)[name] for col, name in rows[head_i].items() if name in (HEAD2026 if new_layout else HEAD)}
    extra = {name: col for col, name in rows[head_i].items() if name in ("Varegruppe", "Opprinnelse")} if new_layout else {}
    out = []
    for r in rows[head_i + 1:]:
        rec = {cols[c]: " ".join(v.split()) for c, v in r.items() if c in cols}
        if new_layout:
            for k in ("abv_percent", "volume_l", "price_nok"):  # Excel stores 79.9 as 79.900000000000006
                if rec.get(k):
                    rec[k] = f"{round(float(rec[k]), 3):g}"
            group, origin = r.get(extra.get("Varegruppe"), ""), r.get(extra.get("Opprinnelse"), "")
            rec["category"], _, rec["style"] = (x.strip() for x in group.partition(","))
            rec["country"], _, rec["region"] = (x.strip() for x in origin.partition(","))
        if not rec.get("article_no") or not rec.get("name"):
            continue
        rec["year"] = year
        out.append([rec.get(k, "") for k in OUT])
    dest = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "data", "vinmonopolet", f"{year}.csv")
    with open(dest, "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh, lineterminator="\n")
        w.writerow(OUT)
        w.writerows(out)
    print(f"{len(out)} products -> {dest}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
