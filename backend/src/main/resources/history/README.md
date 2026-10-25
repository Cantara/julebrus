# Historic seasons

Drop the exports from wiki.cantara.no here. They are imported at startup, once per
season (a season already in the database is left alone, so editing a name here changes
nothing in production: use beer_names.csv for that).

## seasons.csv

```
year,title,scale_min,scale_max,weighting_description,source_url
2019,Juleølsmaking 2019,1,6,"Smak teller dobbelt",https://wiki.cantara.no/...
```

## <year>.csv  (one file per season, e.g. 2019.csv)

```
beer_name,brewery,rater_name,rater_email,score,weight,notes
Aass Juleøl,Aass,Totto,totto@cantara.no,4.5,1,"Mild, litt tynn"
```

- `score` is on that season's original scale. The app maps it onto 1–6 for display.
- `weight` is the rater's weight that season (0.5 rookie or under 7 scores, 1
  normal, 0 flagged). Defaults to 1 when blank. The same number on every row for
  one person in one year.
- `rater_email` is optional but is what links a row to a logged-in person. Rows
  without it still count in the season's result list.
- Quote fields that contain commas. Lines starting with `#` are ignored.

## raters.csv

```
name,canonical_name,email
Eirik L,Eirik Litangen,eirik@example.com
```

Joins the name variants of one person and links them to a Google login. `name` is
the rater name exactly as it appears in a `<year>.csv`; `canonical_name` is blank
when the name is already the one to show. `email` is what makes the person's
history appear on their own profile. Unlike the season files, this one is applied
on every startup, so a corrected mapping needs only a restart.

## beer_names.csv

```
year,as_written,canonical,note
2019,Ringnes julebokk,Ringnes Julebokk,
2014,Rignes Julebokk,Ringnes Julebokk,legacy
```

Corrects a misspelled beer name. `as_written` is the name as it was when the season was first imported. When `note` is
empty, that is the name still written in `<year>.csv`. When `note` is `legacy`, the source files have since been
corrected, so `<year>.csv` holds `canonical` and the row only renames a database imported before that (production);
on a fresh database it matches nothing. `BeerNamesTest` checks both. Like raters.csv it is
applied on every startup, to ratings already in the database: the stored name changes, the source name is kept
in `historic_ratings.beer_name_original`, nothing is deleted. Remove a line and the original comes back.
Candidates: `python3 -I tools/suggest_beer_name_fixes.py` (writes `data/review/beer-name-candidates.csv`).
