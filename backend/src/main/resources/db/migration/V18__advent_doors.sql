-- The advent calendar game: one row per door a person opened, with their answer to the day's question.
-- late: opened after its day; key_used: a nisse-key earned at a table paid for it.
create table advent_doors (
  id         uuid primary key,
  user_id    uuid not null references users(id),
  door_day   date not null,
  opened_at  timestamp with time zone not null,
  answer     integer,
  correct    boolean,
  late       boolean not null default false,
  key_used   boolean not null default false,
  constraint uq_advent_doors_user_day unique (user_id, door_day)
);
create index ix_advent_doors_day on advent_doors(door_day);
