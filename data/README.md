# Historic juleøl test data

The history of the Cantara juleøl (Christmas beer) tests: who voted what, per beer, per year. The scores come from the Cantara wiki (`#puben` space,
https://wiki.cantara.no, now https://cantara.github.io/wiki), from the Google Sheets the wiki pages link,
and from the Internet Archive.

## Data

- `data/votes.csv`: `year, voter, weight, beer, score`. One row per vote. Scores are the dice scale 1-6.
  `weight` is the voter's weight that year (1 = qualified voter, 0.5 = probie, 0 = failed probie); empty where the sheet gives none.
  For 2008 the weight is derived from the sheet's count of 0.5-weight votes per beer (see SOURCES.md).
- `data/results.csv`: `year, beer, n_votes, mean_unweighted, mean_weighted, rank, weight_sum`. The values the
  source states; nothing is recomputed. An empty cell means the source did not state it. `weight_sum` is the
  stated sum of vote weights (2010 and the 2017-2019 sheets; for 2008 the sum of the two stated counts, full
  votes + 0.5 x half-weight votes).
- `data/participation.csv`: `year, voter, votes_cast`. For 2016, the only year where single votes are not available.
- `../source/google-sheets/<year>.csv`: the raw exports of the Google Sheets (2016 and 2019 via the `gviz` CSV form).

Voters appear under the names used on the wiki pages and in the sheets.
Beer names are as written in each source and are not normalised across years.

## Coverage

| Years | State |
| --- | --- |
| 1998, 2003, 2004, 2007, 2008, 2010 | Full vote matrix, from the wiki pages (2008 and 2010 from the spreadsheet HTML on the page) |
| 2011, 2012, 2013, 2014, 2015, 2017, 2018, 2019 | Full vote matrix, from the linked Google Sheets |
| 2016 | Weighted score per beer and votes cast per voter; single votes not available |
| 1997 | Winner only (Borg), not in the CSV files |
| 1992-1996, 1999-2002, 2005, 2006, 2009, 2020, 2021 | No results found |

See [SOURCES.md](SOURCES.md) for where each year comes from and the known quirks in the sources.

## Rebuild and app import

From the repository root: `python3 -I tools/build_data.py <Cantara/wiki clone>/docs/puben` rewrites the CSV files in `data/` from the wiki pages
and `source/google-sheets/` (standard library only) and prints every place where a stated mean or vote count
differs from the votes listed in the same source.

`python3 tools/to_app_history.py backend/src/main/resources/history` writes the files the tasting app's
`HistoryImporter` loads (`seasons.csv` and one `<year>.csv` per season) from `data/votes.csv`.

`python3 tools/to_landing_stats.py backend/src/main/resources/landing/history-stats.json` writes the aggregates the public
landing page shows (counts and per-beer numbers only, no names). Rerun it whenever `data/` changes; `LandingStatsTest` fails otherwise.

Beer names stay as written in these files. Corrections live in `backend/src/main/resources/history/beer_names.csv`;
`tools/to_landing_stats.py` applies them. `python3 -I tools/suggest_beer_name_fixes.py` writes a review sheet of likely
misspellings to `data/review/beer-name-candidates.csv` (suggestions only, a person decides).
