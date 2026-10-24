# Vinmonopolet juleøl lists, 2013-2025 (2021 missing)

Product facts only: name, article number (where the source has it), price, ABV, brewery, style, range. The sources' own text, tables and pictures are not
copied, and the fetched pages are not kept in the repository.

## Files

- `<year>.csv`: the listing of one year, exactly as the source gives it. Columns `year, article_no, name, brewery, style, category, selection, country, region,
  organic, abv_percent, volume_l, price_nok`; unknown = empty. `article_no` is empty for 2014-2020 (those posts have no article numbers).
- `listings.csv`: all years in one file (2,518 listings) with `product_article_no` and `match`: `given` (the source states the number), `name` (an UNAMBIGUOUS exact
  name match to a numbered product; case, accents, punctuation and a trailing year ignored), `ambiguous` (the name sits under several article numbers, so no link) or
  `none`. Inferred links are guesses from names: treat `match = name` as likely, not certain.
- `products.csv`: one row per article number (662). Attributes come from the NEWEST numbered year that states them (brewery and style exist only in 2025, ABV in 2013,
  2023 and 2025). `years_given` and `years_inferred` are kept apart; `price_by_year` uses both; `abv_conflict = 1` marks numbers where two years state different ABV;
  `names_seen` lists renames. A few beers sit under several article numbers (other pack sizes or batches): not merged.

## Sources

| Year | Listings | Has | Source |
| --- | --- | --- | --- |
| 2013 | 83 | number, range, fermentation type, ABV, volume, price | Vinmonopolet's own list "Juleøl 2013, 30.10.13" (xlsx), attached to the Cantara wiki page of the 2013 tasting (`Juleøl 2013.xlsx`, Cantara/wiki `docs/puben/`), sheet "Ark1" |
| 2014 | 106 | names, range | https://thegundersen.wordpress.com/2014/10/28/juleol-pa-vinmonopolet-2014/ |
| 2015 | 160 | names | https://thegundersen.wordpress.com/2015/10/09/juleol-2015/ |
| 2016 | 176 | names | https://thegundersen.wordpress.com/2016/11/01/juleol-2016/ |
| 2017 | 201 | names | https://thegundersen.wordpress.com/2017/10/26/juleol-2017/ |
| 2018 | 217 | names, price, range | https://thegundersen.wordpress.com/2018/10/25/juleol-2018-vinmonopolet/ (two tables) |
| 2019 | 248 | names, price | https://thegundersen.wordpress.com/2019/10/04/juleol-2019-vinmonopolet/ |
| 2020 | 236 | names, price, range (BU/TU) | https://thegundersen.wordpress.com/2020/10/07/3340/ |
| 2021 | - | - | no usable list: the blog's only 2021 launch post is a mixed "novelties" post (all product types) and a Norwegian-only preview; not included |
| 2022 | 274 | number, name, price, range (BU/TU) | https://thegundersen.wordpress.com/2022/10/26/juleol-2022-vinmonopolet/ |
| 2023 | 285 | number, name, ABV, price | https://thegundersen.wordpress.com/2023/10/29/juleol-2023-vinmonopolet/ |
| 2024 | 262 | number, name, price | https://thegundersen.wordpress.com/2024/10/31/juleol-2024-vinmonopolet/ |
| 2025 | 270 | number, name, brewery, style, country and region, ABV, volume, price incl. deposit | https://www.olbloggen.no/vinmonopolet-nyheter/disse-juleolene-kommer-pa-vinmonopolet-5-november-2025/ (a table) |

2014-2025 are SECONDARY sources (two beer blogs transcribing Vinmonopolet's launch lists), not Vinmonopolet's own files: a typo in a blog is a typo here. "BU/TU" is read as
the ordering range and the additional range (an inference, not stated). Prices are NOK as the source states them (2025 "inkl pant"; for the other years the source does not say).
The blogs' robots.txt allow reading the posts (no AI-bot rules, no crawl delay); the posts were fetched one at a time a few seconds apart (about 20 requests to the Jan Egil blog,
one to Ølbloggen), and the blog's post list came from WordPress's documented public API (one request). Vinmonopolet's own terms for its data have not been read; nothing from their
site was fetched for these files (their robots.txt asks automated visits to stay between 00:00 and 05:00 UTC).

## Rebuild

```
python3 tools/extract_vinmonopolet_xlsx.py 2013 <path>/36897787-Juleøl-2013.xlsx Ark1
python3 tools/extract_vinmonopolet_blog.py <2014..2020|2022..2025> <saved page.html>
python3 tools/build_vinmonopolet_products.py        # listings.csv and products.csv
```

## Images (decision 2026-10-24)
Product photos are not stored here and not hotlinked: the app's images come from its users' own label photos (see DESIGN.md). The lists have no image column.
