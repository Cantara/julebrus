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

- 2008 and 2010: the wiki pages hold the votes as run-together text (for example `Eirik104.134454554345`), so blanks
  cannot be told from votes and beers cannot be matched to digits.
- 1997: puben.org/juleoel.html (Internet Archive, 2001-10-24) says only that Borg won ("Vinner ble Borg!!!").
- 1999: puben.org/juleol.html is a frame page; the results table is an image (`alkjul4.gif`) that the Internet Archive
  does not hold.
- 2014 and 2017 also exist as bar-chart images on the wiki; the sheets above are the better source.
- 2021: the wiki page has no results. 1992-1996, 1999-2002, 2005, 2006, 2009 and 2020: no page or file found. The index
  of the old puben.org site in the Internet Archive was searched; apart from the pages above it holds only personal pages and a
  mailing list archive, which were not read.
