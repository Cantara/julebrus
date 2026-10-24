-- Versioned consent. One row per user and consent version; accepting again after a
-- withdrawal reuses the row. A user counts as consented only while withdrawn_at is null.
create table user_consents (
  id               uuid primary key,
  user_id          uuid not null references users(id),
  consent_version  varchar(32) not null,
  accepted_at      timestamp with time zone not null,
  withdrawn_at     timestamp with time zone,
  constraint uq_user_consent unique (user_id, consent_version)
);

-- Append-only audit trail: one row per accept or withdraw, never updated or deleted by the app.
create table consent_events (
  id               uuid primary key,
  user_id          uuid not null references users(id),
  consent_version  varchar(32) not null,
  event            varchar(16) not null check (event in ('ACCEPT', 'WITHDRAW')),
  at               timestamp with time zone not null
);
create index ix_consent_events_user on consent_events(user_id, at);

-- A logged-in person asks to be linked to a historic person, identified by rater_key (the
-- canonical name from raters.csv that joins "Eirik", "Eirik L", ...). Nothing is linked until an admin approves.
--
-- approved_key is rater_key while the claim is APPROVED and NULL otherwise. Its unique
-- constraint means one person (key) can be approved for at most one user. A partial unique
-- index would say the same, but H2 (dev/test) has none; NULLs never collide, so this
-- works on PostgreSQL and H2 alike.
create table historic_rater_claims (
  id             uuid primary key,
  user_id        uuid not null references users(id),
  rater_key      varchar(255) not null,
  status         varchar(16) not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED', 'WITHDRAWN')),
  created_at     timestamp with time zone not null,
  decided_by     uuid references users(id),
  decided_at     timestamp with time zone,
  approved_key   varchar(255),
  version        bigint not null default 0,
  constraint uq_claim_user_key unique (user_id, rater_key),
  constraint uq_claim_approved_key unique (approved_key),
  constraint ck_claim_approved_key check ((status = 'APPROVED') = (approved_key is not null))
);
create index ix_claims_status on historic_rater_claims(status);

-- Append-only history of every claim: who did what, when. The claim row holds the current
-- state; this table keeps what the row overwrites (a reopened claim, a revoked approval).
create table claim_events (
  id             uuid primary key,
  claim_id       uuid not null references historic_rater_claims(id),
  event          varchar(16) not null check (event in ('CREATE', 'REOPEN', 'WITHDRAW', 'APPROVE', 'REJECT', 'REVOKE')),
  actor_user_id  uuid not null references users(id),
  at             timestamp with time zone not null
);
create index ix_claim_events_claim on claim_events(claim_id);
create index ix_claim_events_actor on claim_events(actor_user_id, at);
