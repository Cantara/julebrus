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
  status         varchar(16) not null check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  created_at     timestamp with time zone not null,
  decided_by     uuid references users(id),
  decided_at     timestamp with time zone,
  approved_key   varchar(255),
  constraint uq_claim_user_key unique (user_id, rater_key),
  constraint uq_claim_approved_key unique (approved_key),
  constraint ck_claim_approved_key check ((status = 'APPROVED') = (approved_key is not null))
);
create index ix_claims_status on historic_rater_claims(status);
