#!/usr/bin/env python3
"""Turn a Vinmonopolet juleøl list (xlsx, columns Varenr/Artikkel/Utvalg/Varegruppe/Land/Økologisk/Volpst/Volum/Salgspris)
into data/vinmonopolet/<year>.csv. Standard library only.

Usage: extract_vinmonopolet_xlsx.py YEAR LIST.xlsx [SHEET_NAME]   (default sheet: the first one)

Columns written: year, article_no, name, selection, category, country, organic, abv_percent, volume_l, price_nok.
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
OUT = ["year", "article_no", "name", "selection", "category", "country", "organic", "abv_percent", "volume_l", "price_nok"]


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
    head_i = next(i for i, r in enumerate(rows) if r.get("A") == "Varenr")
    cols = {col: HEAD[name] for col, name in rows[head_i].items() if name in HEAD}
    out = []
    for r in rows[head_i + 1:]:
        rec = {cols[c]: v.strip() for c, v in r.items() if c in cols}
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
