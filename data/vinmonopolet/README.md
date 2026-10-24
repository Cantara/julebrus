# Vinmonopolet juleøl lists

One CSV per year: `<year>.csv` with `year, article_no, name, selection, category, country, organic, abv_percent, volume_l, price_nok`.
Product facts only (Vinmonopolet's own article number, name, range, fermentation type, country, ABV, volume, list price that year).
The lists' own text (ordering instructions, contact details) is not copied.

| Year | Products | Source |
| --- | --- | --- |
| 2013 | 83 | Vinmonopolet's official list "Juleøl 2013, 30.10.13" (xlsx), attached to the Cantara wiki page of the 2013 tasting (`Juleøl 2013.xlsx`, Cantara/wiki `docs/puben/`). Sheet "Ark1" (the full list; sheet 2 is a subset). |

Rebuild: `python3 tools/extract_vinmonopolet_xlsx.py 2013 <path>/36897787-Juleøl-2013.xlsx Ark1`.

Status: terms of use for Vinmonopolet's data have not been read yet; only the facts above are stored, with the year and source. Other years: not found yet.

## Images (decision 2026-10-24: link, never copy)
Product photos are not stored in this repository (they belong to Vinmonopolet or the producers). The app links them: `beers.image_url` holds a URL taken from
Vinmonopolet's product API for the CURRENT assortment, i.e. when the 2026 catalogue is imported. Old lists get no image column: an article number from an old list
is not a reliable key to today's image (checked once: the guessed `bilder.vinmonopolet.no/cache/300x300-0/<article>-1.jpg` for 2013's Aass Juleøl redirects to the
placeholder `/bottle.png`; the image host's robots.txt is a plain 404). TODO before the first image is shown: add one line to the consent and privacy text that images
are loaded from Vinmonopolet's server (it receives the visitor's IP address), and bump CONSENT_VERSION. Decided to do that later, with the catalogue import.
