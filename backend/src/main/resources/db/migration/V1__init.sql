create table users (
  id            uuid primary key,
  email         varchar(255) not null unique,
  display_name  varchar(255) not null,
  created_at    timestamp with time zone not null
);

create table beers (
  id               uuid primary key,
  name             varchar(255) not null,
  brewery          varchar(255),
  style            varchar(100),
  abv              numeric(4,1),
  vinmonopolet_id  varchar(32) unique,
  image_url        varchar(1024),
  created_at       timestamp with time zone not null
);

create table sessions (
  id          uuid primary key,
  host_id     uuid not null references users(id),
  join_code   varchar(8) not null unique,
  created_at  timestamp with time zone not null,
  closed_at   timestamp with time zone
);

create table session_members (
  session_id  uuid not null references sessions(id),
  user_id     uuid not null references users(id),
  joined_at   timestamp with time zone not null,
  primary key (session_id, user_id)
);

create table session_beers (
  session_id  uuid not null references sessions(id),
  beer_id     uuid not null references beers(id),
  added_at    timestamp with time zone not null,
  primary key (session_id, beer_id)
);

create table tastings (
  id          uuid primary key,
  user_id     uuid not null references users(id),
  beer_id     uuid not null references beers(id),
  session_id  uuid references sessions(id),
  score       integer not null check (score between 1 and 6),
  prediction  numeric(3,2) check (prediction between 1 and 6),
  notes       varchar(2000),
  tasted_at   timestamp with time zone not null,
  tasted_day  date not null,
  constraint uq_tasting_user_beer unique (user_id, beer_id)
);
create index ix_tastings_user_day on tastings(user_id, tasted_day);
create index ix_tastings_beer on tastings(beer_id);

create table duels (
  id              uuid primary key,
  user_id         uuid not null references users(id),
  session_id      uuid not null references sessions(id),
  winner_beer_id  uuid not null references beers(id),
  loser_beer_id   uuid not null references beers(id),
  created_at      timestamp with time zone not null,
  constraint ck_duel_distinct check (winner_beer_id <> loser_beer_id)
);
create index ix_duels_session on duels(session_id);

-- Structured tasting notes for the professionals. All optional.
alter table tastings add column appearance varchar(1000);
alter table tastings add column aroma varchar(1000);
alter table tastings add column taste varchar(1000);
alter table tastings add column finish varchar(1000);

create table tasting_tags (
  tasting_id  uuid not null references tastings(id),
  tag         varchar(40) not null,
  primary key (tasting_id, tag)
);

-- Earlier seasons from wiki.cantara.no, imported from CSV. Scores are kept on
-- their original scale together with the weight they had, and normalised to 1–6
-- for display next to this season's results.
create table historic_seasons (
  season_year           integer primary key,
  title                 varchar(255) not null,
  scale_min             numeric(4,2) not null,
  scale_max             numeric(4,2) not null,
  weighting_description varchar(2000),
  source_url            varchar(1024)
);

create table historic_ratings (
  id              uuid primary key,
  season_year     integer not null references historic_seasons(season_year),
  beer_name       varchar(255) not null,
  brewery         varchar(255),
  beer_id         uuid references beers(id),
  rater_name      varchar(255) not null,
  rater_email     varchar(255),
  score           numeric(5,2) not null,
  weight          numeric(5,2) not null default 1,
  notes           varchar(2000)
);
create index ix_historic_ratings_season on historic_ratings(season_year);
create index ix_historic_ratings_rater on historic_ratings(rater_email);
create index ix_historic_ratings_beer_name on historic_ratings(beer_name);

-- Rater weights. Trust carried from one season to the next, keyed by email so
-- seasons before the app count too. 0.5 rookie or < 7 scores last season,
-- 1.0 after a normal season, 0 after a season flagged for rigging. A rater
-- with weight 0 still rates and sees everything; the averages just ignore them.
create table rater_weights (
  id           uuid primary key,
  season_year  integer not null,
  email        varchar(255) not null,
  weight       numeric(3,2) not null check (weight between 0 and 1),
  reason       varchar(255) not null,
  flagged      boolean not null default false,
  flag_note    varchar(1000),
  constraint uq_rater_weight unique (season_year, email)
);
