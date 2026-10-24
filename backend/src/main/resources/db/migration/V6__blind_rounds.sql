-- Blind pour: the host secretly pours one of the beers on the table, the others guess
-- which. One open round per session at a time; the answer is hidden until revealed.
create table blind_rounds (
  id              uuid primary key,
  session_id      uuid not null references sessions(id),
  host_id         uuid not null references users(id),
  answer_beer_id  uuid not null references beers(id),
  created_at      timestamp with time zone not null,
  revealed_at     timestamp with time zone
);
create index ix_blind_rounds_session on blind_rounds(session_id);

create table blind_round_options (
  round_id  uuid not null references blind_rounds(id),
  beer_id   uuid not null references beers(id),
  position  integer not null,
  primary key (round_id, beer_id)
);

create table blind_guesses (
  round_id    uuid not null references blind_rounds(id),
  user_id     uuid not null references users(id),
  beer_id     uuid not null references beers(id),
  guessed_at  timestamp with time zone not null,
  primary key (round_id, user_id)
);
