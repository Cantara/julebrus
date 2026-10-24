# julebrus

The history of the Cantara juleøl (Christmas beer) tests: who voted what, per beer, per year.
The name is a joke; the data is beer. The scores come from the Cantara wiki (`#puben` space,
https://wiki.cantara.no, now https://cantara.github.io/wiki), from the Google Sheets the wiki pages link,
and from the Internet Archive.

## Data

- `data/votes.csv`: `year, voter, weight, beer, score`. One row per vote. Scores are the dice scale 1-6.
  `weight` is the voter's weight that year (1 = qualified voter, 0.5 = probie); empty where the sheet gives none.
- `data/results.csv`: `year, beer, n_votes, mean_unweighted, mean_weighted, rank, weight_sum`. The values the
  source states; nothing is recomputed. An empty cell means the source did not state it. `weight_sum` is the
  stated sum of vote weights (2017-2019 sheets).
- `data/participation.csv`: `year, voter, votes_cast`. For 2016, the only year where single votes are not available.
- `source/google-sheets/<year>.csv`: the raw exports of the Google Sheets (2016 and 2019 via the `gviz` CSV form).

Voters appear under the names used on the wiki pages and in the sheets.
Beer names are as written in each source and are not normalised across years.

## Coverage

| Years | State |
| --- | --- |
| 1998, 2003, 2004, 2007 | Full vote matrix, from the wiki pages |
| 2011, 2012, 2013, 2014, 2015, 2017, 2018, 2019 | Full vote matrix, from the linked Google Sheets |
| 2016 | Weighted score per beer and votes cast per voter; single votes not available |
| 1997 | Winner only (Borg), not in the CSV files |
| 2008, 2010 | The wiki pages hold the votes as run-together text; not recoverable |
| 1992-1996, 1999-2002, 2005, 2006, 2009, 2020, 2021 | No results found |

See [SOURCES.md](SOURCES.md) for where each year comes from and the known quirks in the sources.

## Rebuild

`python3 tools/build_data.py <Cantara/wiki clone>/docs/puben` rewrites the CSV files in `data/` from the wiki pages
and `source/google-sheets/` (standard library only) and prints every place where a stated mean or vote count
differs from the votes listed in the same source.
