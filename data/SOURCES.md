# Sources

## Wiki pages

github.com/Cantara/wiki, `docs/puben/`, commit 38520738 (2026-10-19): an export of the Confluence space `#puben`
of wiki.cantara.no (the site's robots.txt allows reading).

| Year | Page | Notes |
| --- | --- | --- |
| 1998 | `Juleøltesten-1998.md` | Beers as rows, voters as columns; `-` = no vote. The original page was puben.org/juleol98.html via the Internet Archive. |
| 2003 | `Juleøltesten-2003.md` | Voters as rows, beers as columns. |
| 2004 | `Juleøltesten-2004.md` | Same layout. Cells holding `b` are not votes and are skipped. |
| 2007 | `Juleøltesten-2007.md` | Same layout. The row "Morten m.fl.." is a group entry (weight 2.5) holding averages, kept as stated. |
| 2008 | `Juleøltesten-2008.md` | A Google Sheet saved as HTML (see below). Voters as rows, beers as columns. |
| 2010 | `Juleøltesten-2010.md` | A Google Sheet saved as HTML (see below). Voters as rows, beers as columns, a weight per voter. Rules (weights 1, 1/2, 0): `Juleølsmaking-2010.md`. |

### 2008 and 2010: spreadsheet HTML

These two pages hold a Google spreadsheet's HTML export, with unclosed cells (`<td class='s6'>4<td ><td class='s6'>5`).
Read as text, the empty cells vanish and the votes run together (`Eirik104.134454554345`), which is why an earlier
version of this file called them not recoverable. They are recoverable: `tools/extract_wiki_tables.py` (`sheet_grid`)
splits the raw file text, not a rendered version, on `<tr` for rows and on `<td` for cells, keeping empty cells, so a
cell's position is its column and the header row names the beer. Google splits a long sheet into several `<table>`s
of the same width; their rows are read as one grid. Comment markers (`<span class='annotation'>`, shown as
`[1](1.md)`) are dropped. Cells that are not numbers are skipped and printed by `build_data.py`.

- 2008: header `Navn, Sum Ølstemmer, Snitt avgitt stemme, <beers>, Sum stemmer`, 18 voter rows (Janken and an
  empty row scored nothing), then the rows `Number of votes`, `Number of votes 0.5 weight` and `Total number of votes`
  per beer. The three Fenalår columns (cured lamb, as in 2003 and 2007) are kept like the beers; the sheet's
  `Sum Ølstemmer` and `Snitt avgitt stemme` per voter cover the beers only. Columns with no name (three, all with 0
  votes) are skipped. The sheet states no weight per voter, only how many of each beer's votes had weight 0.5; those
  counts settle every voter's weight with exactly one answer (Erik D, Stig L, Mads and Agnes 0.5, everyone else 1), and
  that is the weight in `votes.csv`. In `results.csv`, `n_votes` is `Total number of votes` and `weight_sum` is
  `Number of votes` + 0.5 x `Number of votes 0.5 weight`.
- 2010: a row `Vekting, Antall` above the header of beer names, then `Vektet snitt`, `Stemmevolum` (sum of weights)
  and `Gjennomsnitt` (unweighted mean) per beer, then the voters: name, weight (`Vekting`: 1, 0.5 or 0), `Antall`,
  one cell per beer. Three voters (Kjetil J. D., Karl Inge, Stig Lau) are in the sheet's second table; the sheet's
  statistics include them. The third and fourth tables are empty apart from the comment text. Skipped cells: "snasent!"
  (Kjetil, Hein) in the column "Vertskapet" (the hosts), which therefore has no votes and no result row, and "b"
  (Eirik, Ægir julebrygg). The comment "[1]" on the mean of Berentsen Julefnugg is dropped. Cynthia scored nothing.
  `results.csv` holds `Gjennomsnitt` as `mean_unweighted`, `Vektet snitt` as `mean_weighted` and `Stemmevolum` as
  `weight_sum`; the sheet states no vote count per beer.

## Google Sheets

The Confluence pages for 2011-2019 link a Google Sheet each; these were exported as CSV on 2026-10-24 into
`../source/google-sheets/` (one request per sheet; the sheets' robots.txt allows `/spreadsheet*`).

| Year | Layout | Notes |
| --- | --- | --- |
| 2011, 2012, 2013 | Voters as rows, beers as columns | |
| 2014 | Beers as rows, voters as columns | The stated mean per beer matches the votes. One vote on the last row ("Rignes Julebokk", no stated mean) is kept. |
| 2015 | Header `Voter, Level, Antall, <beers>` | Weights use decimal commas in the sheet. |
| 2016 | Ranked list only | Weighted score per beer, and votes cast per voter. No single votes in the sheet. |
| 2017, 2018, 2019 | Beers as rows, voters as columns | A row counts when it has a beer name and a stated vote weight. |

## Checked against the sources

Every stated mean and vote count is compared with the votes listed in the same source (`../tools/build_data.py` prints
the differences). They agree except:

- 1998, Schous: the page says 3 votes; the row holds 6 and the stated mean 4.33 matches the 6.
- 2007, Hansa and Mack: the stated weighted means (3.68, 3.67) differ from the listed votes (3.49, 3.00).
- 2008: every stated number matches the votes: per voter `Sum Ølstemmer` and `Snitt avgitt stemme` (beers only), per
  beer `Number of votes`, `Number of votes 0.5 weight` and `Total number of votes`.
- 2010: per voter `Antall` matches for every voter (it counts the Fenalår too and not the "b"), and per beer
  `Gjennomsnitt` matches for every beer. `Vektet snitt` and `Stemmevolum` do not follow from the vote rows for 23 of the
  24 columns; they match only for Dahls. For 18 columns the stated volume is 1 more than the listed weights (for
  example Aas Juleøl 8 against 7, Berentsen Julefnugg 5.5 against 4.5), and the stated weighted sum is then about 2
  more, as if one extra vote of 2 with weight 1 were counted; for Hansa Julebrygg, Borg, Hansa juleøl, CB and Mack
  Juleøl the volume matches but the weighted mean does not (CB stated 2.87, from votes 3.13). The cause is not
  known (the sheet's formulas are not in the export). The stated values and the votes are both kept as they are.
- 2013: the sheet's own statistics do not match its vote rows for 25 of the 31 beers that have votes (stated counts are mostly lower than
  the listed votes; for example the means for Aass Premium, Ringnes Juleøl and Nøgne Ø God Jul differ). The sheet has
  three voters added at the bottom without a weight ("tobiast", "Hein", "Hvem?"), but leaving them out does not
  reconcile it. Both the stated results and the listed votes are kept as they are. One voter (Stig) gave
  fractional scores (2.5, 3.1, 4.6, 5.5); kept as written.
- 2018: the sheet lists "CB Juleøl" twice as separate rows with a stated vote weight of 1 each; in `votes.csv` the votes
  of the two rows sit under the same beer name.

Votes typed outside the scored block are not in the data: in the 2017 sheet two votes on a row without a beer name and
four on rows in the catalogue of untasted beers below the block (all four on beers that are also listed in the block,
three of them under a slightly different name); in the 2018 sheet three votes on rows without a stated vote weight ("Sagene Juleøl", "Færder").

## Not in the data

- 1997: puben.org/juleoel.html (Internet Archive, 2001-10-24) says only that Borg won ("Vinner ble Borg!!!").
- 1999: puben.org/juleol.html is a frame page; the results table is an image (`alkjul4.gif`) that the Internet Archive
  does not hold.
- 2014 and 2017 also exist as bar-chart images on the wiki; the sheets above are the better source.
- 2021: the wiki page has no results. 1992-1996, 1999-2002, 2005, 2006, 2009 and 2020: no page or file found. The index
  of the old puben.org site in the Internet Archive was searched; apart from the pages above it holds only personal pages and a
  mailing list archive, which were not read.
