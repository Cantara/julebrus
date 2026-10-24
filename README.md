# julebrus

The history of the Cantara juleøl (Christmas beer) tests: who voted what, per beer, per year.
The name is a joke; the data is beer. The scores come from the Cantara wiki (`#puben` space,
https://wiki.cantara.no, now https://cantara.github.io/wiki) and from the Internet Archive.

## Data

- `data/votes.csv`: `year, voter, weight, beer, score`. One row per vote. Scores are the dice scale 1-6.
  `weight` is the voter's weight that year (1 = qualified voter, 0.5 = probie).
- `data/results.csv`: `year, beer, n_votes, mean_unweighted, mean_weighted, rank`. The values the wiki page states;
  nothing is recomputed. An empty cell means the page did not state it.

Voters appear under the names used on the wiki pages.

## Coverage

| Years | State |
| --- | --- |
| 1998, 2003, 2004, 2007 | Full vote matrix in `data/` |
| 2008, 2010 | Page text exists but the table is garbled; not yet extracted |
| 2011-2019, 2021 | Results live in Google Sheets linked from the wiki; not archived |
| 2014, 2017 | Average-per-beer bar chart only (image), not transcribed |
| 1992-1997, 1999-2002, 2005, 2006, 2009 | No page found yet |

See [SOURCES.md](SOURCES.md) for where each year comes from and the known quirks in the source pages.

## Reproduce

`python3 tools/extract_wiki_tables.py <Cantara/wiki clone>/docs/puben data/` rewrites the two CSV files from
the wiki pages (standard library only).
