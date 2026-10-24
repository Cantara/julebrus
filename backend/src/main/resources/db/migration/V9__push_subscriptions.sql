-- Web Push: one row per browser that said yes to notifications. The keys are the
-- browser's; the server's VAPID pair lives in the environment, never here.
create table push_subscriptions (
  id              uuid primary key,
  user_id         uuid not null references users(id),
  endpoint        varchar(2048) not null unique,
  p256dh          varchar(255) not null,
  auth            varchar(64) not null,
  user_agent      varchar(255),
  created_at      timestamp with time zone not null,
  last_nudge_day  date,
  finale_sent_at  timestamp with time zone,
  failures        integer not null default 0
);
create index ix_push_subscriptions_user on push_subscriptions(user_id);
