-- A canonical name per historic rater, so "Eirik", "Eirik L" and "Eirik Litangen"
-- are one person. Filled from history/raters.csv at startup; rows without a
-- mapping get their source name.
alter table historic_ratings add column rater_key varchar(255);
create index ix_historic_ratings_rater_key on historic_ratings(rater_key);
