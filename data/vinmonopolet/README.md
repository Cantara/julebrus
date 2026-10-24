# Vinmonopolet juleøl lists, 2013 and 2022-2025

Product facts only: Vinmonopolet's article number, name, brewery, style, country, ABV, volume and the list price that year. The sources' own text,
tables and pictures are not copied, and the fetched pages are not kept in the repository.

## Files

- `<year>.csv`: the listing of one year. Columns `year, article_no, name, brewery, style, category, selection, country, region, organic, abv_percent, volume_l, price_nok`;
  unknown = empty. Prices are NOK as the source states them (2025: "inkl pant", i.e. including deposit; for the other years the source does not say).
- `products.csv`: one row per article number across all years (`first_year`, `last_year`, `years`, `price_by_year`, `names_seen`). Attributes come from the NEWEST
  year that states them, so older listings inherit brewery and style (only 2025 states them) and ABV (2013, 2023, 2025). `abv_conflict = 1` marks the few numbers where two
  years state different ABV. Article numbers are mostly stable across years (a handful of beers are renamed, and a few names sit under several numbers, e.g. other
  pack sizes or batches: not merged here).

## Sources

| Year | Products | Source |
| --- | --- | --- |
| 2013 | 83 | Vinmonopolet's own list "Juleøl 2013, 30.10.13" (xlsx) attached to the Cantara wiki page of the 2013 tasting (`Juleøl 2013.xlsx` in Cantara/wiki `docs/puben/`), sheet "Ark1". Has `selection` and fermentation type (`category`), no brewery. |
| 2022 | 274 | Blog post https://thegundersen.wordpress.com/2022/10/26/juleol-2022-vinmonopolet/ (article number, name, price). Markers "(BU)"/"(TU)" kept as `selection` (BU/TU, presumably the ordering and the additional range: an inference, not stated). No ABV or brewery. |
| 2023 | 285 | Blog post https://thegundersen.wordpress.com/2023/10/29/juleol-2023-vinmonopolet/ (article number, name, ABV, price). No brewery. |
| 2024 | 262 | Blog post https://thegundersen.wordpress.com/2024/10/31/juleol-2024-vinmonopolet/ (article number, name, price). No ABV or brewery. |
| 2025 | 270 | Blog post https://www.olbloggen.no/vinmonopolet-nyheter/disse-juleolene-kommer-pa-vinmonopolet-5-november-2025/ (a table with country and region, producer, style, ABV, volume, price incl. deposit). |

The 2022-2025 lists are SECONDARY sources (two beer blogs transcribing Vinmonopolet's launch lists), not Vinmonopolet's own files; a typo in a blog is a typo here.
Both blogs' robots.txt allow reading the posts (no AI-bot rules, no crawl delay); one request per post. Vinmonopolet's own terms for its data have not been read; nothing from their
site was fetched for these files (their robots.txt asks automated visits to stay between 00:00 and 05:00 UTC).

## Rebuild

```
python3 tools/extract_vinmonopolet_xlsx.py 2013 <path>/36897787-Juleøl-2013.xlsx Ark1
python3 tools/extract_vinmonopolet_blog.py <2022|2023|2024|2025> <saved page.html>
python3 tools/build_vinmonopolet_products.py
```

## Images (decision 2026-10-24)
Product photos are not stored here and not hotlinked: the app's images come from its users' own label photos (see DESIGN.md). The lists have no image column.
