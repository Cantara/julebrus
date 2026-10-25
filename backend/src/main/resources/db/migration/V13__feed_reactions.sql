-- "I kveld": a cheer or an emoji given to something on the day's wall (a roll, a table,
-- an approved label, a revealed blind round). One of each emoji per person per target.
create table feed_reactions (
  id          uuid primary key,
  target_kind varchar(16) not null,
  target_id   uuid not null,
  user_id     uuid not null references users(id),
  emoji       varchar(16) not null,
  created_at  timestamp with time zone not null,
  unique (target_id, user_id, emoji)
);
create index ix_feed_reactions_target on feed_reactions(target_id);
