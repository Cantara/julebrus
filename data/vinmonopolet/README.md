# Vinmonopolet juleøl lists

One CSV per year: `<year>.csv` with `year, article_no, name, selection, category, country, organic, abv_percent, volume_l, price_nok`.
Product facts only (Vinmonopolet's own article number, name, range, fermentation type, country, ABV, volume, list price that year).
The lists' own text (ordering instructions, contact details) is not copied.

| Year | Products | Source |
| --- | --- | --- |
| 2013 | 83 | Vinmonopolet's official list "Juleøl 2013, 30.10.13" (xlsx), attached to the Cantara wiki page of the 2013 tasting (`Juleøl 2013.xlsx`, Cantara/wiki `docs/puben/`). Sheet "Ark1" (the full list; sheet 2 is a subset). |

Rebuild: `python3 tools/extract_vinmonopolet_xlsx.py 2013 <path>/36897787-Juleøl-2013.xlsx Ark1`.

Status: terms of use for Vinmonopolet's data have not been read yet; only the facts above are stored, with the year and source. Not stored: product photos
(copyright; the app's `beers.image_url` is meant to hold a link, not a copy). Other years: not found yet.
