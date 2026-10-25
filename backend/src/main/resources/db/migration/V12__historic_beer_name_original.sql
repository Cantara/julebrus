-- history/beer_names.csv corrects misspelled beer names in place; the name as the source wrote it is kept here
-- (null = never corrected).
alter table historic_ratings add column beer_name_original varchar(255);
