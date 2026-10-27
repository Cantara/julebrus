-- Planned tastings: anyone with consent can host one, physical or digital, and the
-- others join. On the night the host opens a table from it (session_id). The host is
-- always an attendee.
create table events (
  id           uuid primary key,
  host_id      uuid not null references users(id),
  title        varchar(120) not null,
  starts_at    timestamp with time zone not null,
  online       boolean not null default false,
  place        varchar(200),
  note         varchar(1000),
  capacity     integer,
  session_id   uuid references sessions(id),
  created_at   timestamp with time zone not null,
  cancelled_at timestamp with time zone
);
create index ix_events_starts_at on events(starts_at);
create table event_attendees (
  event_id   uuid not null references events(id),
  user_id    uuid not null references users(id),
  joined_at  timestamp with time zone not null,
  primary key (event_id, user_id)
);
