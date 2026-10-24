-- Teams: a person picks which teams they play for, once, right after consent. Two are
-- seeded; admins enable more. Membership is many-to-many so a consultant can sit in both.
create table teams (
  id            uuid primary key,
  slug          varchar(40) not null unique,
  name          varchar(100) not null,
  sponsor_name  varchar(100),
  sponsor_url   varchar(1024),
  -- Comma separated e-mail domains that suggest (never decide) this team at signup.
  domains       varchar(255),
  enabled       boolean not null default true,
  created_at    timestamp with time zone not null
);

create table team_members (
  team_id    uuid not null references teams(id),
  user_id    uuid not null references users(id),
  joined_at  timestamp with time zone not null,
  primary key (team_id, user_id)
);
create index ix_team_members_user on team_members(user_id);

-- When the person made their choice, including "later": null means the team page is still due.
alter table users add column teams_chosen_at timestamp with time zone;

insert into teams (id, slug, name, domains, enabled, created_at) values
  ('00000000-0000-4000-8000-000000000001', 'cantara', 'Cantara', 'cantara.no', true, now()),
  ('00000000-0000-4000-8000-000000000002', 'rebel', 'Rebel', null, true, now());
