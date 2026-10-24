-- The tipping game: everyone may tip the season's podium, one tip per person, editable
-- until the last day starts. Scored against the official podium at the reveal.
create table tips (
  user_id         uuid primary key references users(id),
  first_beer_id   uuid not null references beers(id),
  second_beer_id  uuid not null references beers(id),
  third_beer_id   uuid not null references beers(id),
  created_at      timestamp with time zone not null,
  updated_at      timestamp with time zone not null
);
