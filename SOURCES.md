# Sources

Wiki source: github.com/Cantara/wiki, `docs/puben/`, commit 38520738 (2026-10-19). The pages are an export of the
Confluence space `#puben` of wiki.cantara.no (robots.txt of the current site allows reading).

| Year | Page | Notes |
| --- | --- | --- |
| 1998 | `Juleøltesten-1998.md` | Beers as rows, voters as columns; `-` = no vote. Original page was puben.org/juleol98.html via the Internet Archive. |
| 2003 | `Juleøltesten-2003.md` | Voters as rows, beers as columns. |
| 2004 | `Juleøltesten-2004.md` | Same layout. Cells holding `b` are not votes and are skipped. |
| 2007 | `Juleøltesten-2007.md` | Same layout. The row "Morten m.fl.." is a group entry (weight 2.5) holding averages, kept as stated. |

## Checked against the pages

Every extracted vote count equals the page's own "number of votes" per beer, and every stated mean equals the mean
recomputed from the votes, except:

- 1998, Schous: the page says 3 votes; the row holds 6 votes and the stated mean 4.33 matches the 6. The votes are kept.
- 2007, Hansa and Mack: the stated weighted mean (3.68 and 3.67) differs from the mean of the listed votes
  (3.49 and 3.00). The stated values are kept in `results.csv`.
- 2007: the beers "Fenalår 1", "Fenalår 2" and "Ringnes" have no stated rank; "Mack Juleøl" has no stated weighted mean (`#REF!`).

## Not yet in the data

- 2008 and 2010: the wiki pages hold the votes as run-together text (for example `Eirik104.134454554345`), so blanks cannot
  be told from votes. Needs the original spreadsheet or an archived copy of the page.
- 2011-2019: the Confluence pages link Google Sheets (2011, 2012, 2013, 2014, 2015, 2016, 2017, 2018, 2019). The archived
  copies of the pages do not contain the scores.
- 2014 and 2017: an image of average scores per beer (bar chart), too coarse to transcribe as scores.
