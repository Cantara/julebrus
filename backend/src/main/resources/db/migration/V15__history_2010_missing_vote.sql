-- A vote the wiki's snapshot of the 2010 sheet lacks (Eirik, Aass Juleøl premium, 5), found in the original workbook (data/SOURCES.md).
-- The history importer reads a season ONCE, so a database that already holds 2010 (production, since v1.5.0) gets the vote here.
-- A fresh database has no 2010 rows when this runs, so it does nothing: history/2010.csv, which has the vote, is imported afterwards.
-- (An insert here on a fresh database would make the importer skip the season, because it already has ratings.)
-- Idempotent: a second run, or a database that already has the vote, is left alone. rater_key is set from raters.csv at startup.
insert into historic_ratings (id, season_year, beer_name, rater_name, score, weight)
select cast('ba857f59-73e6-55fe-83d0-fd7611f16daf' as uuid), 2010, 'Aass Juleøl premium', 'Eirik', 5, 1
from (values (1)) as v(x)
where exists (select 1 from historic_ratings where season_year = 2010)
  and not exists (select 1 from historic_ratings
                  where season_year = 2010 and rater_name = 'Eirik' and lower(beer_name) = 'aass juleøl premium');
