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

-- A logged-in person asks to be linked to a historic rater name (first names as typed
-- in the old spreadsheets). Nothing is linked until an admin approves.
--
-- approved_name is rater_name while the claim is APPROVED and NULL otherwise. Its unique
-- constraint means one rater name can be approved for at most one user. A partial unique
-- index would say the same, but H2 (dev/test) has none; NULLs never collide, so this
-- works on PostgreSQL and H2 alike.
create table historic_rater_claims (
  id             uuid primary key,
  user_id        uuid not null references users(id),
  rater_name     varchar(255) not null,
  status         varchar(16) not null check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  created_at     timestamp with time zone not null,
  decided_by     uuid references users(id),
  decided_at     timestamp with time zone,
  approved_name  varchar(255),
  constraint uq_claim_user_name unique (user_id, rater_name),
  constraint uq_claim_approved_name unique (approved_name),
  constraint ck_claim_approved_name check ((status = 'APPROVED') = (approved_name is not null))
);
create index ix_claims_status on historic_rater_claims(status);
create index ix_historic_ratings_rater_name on historic_ratings(rater_name);
