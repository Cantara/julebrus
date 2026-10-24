-- Beers added by ordinary people wait for an admin before they count. Existing
-- rows are approved. A pending beer can still be put on the table and rated,
-- so an evening is never blocked; only the rankings leave it out until approved.
alter table beers add column status varchar(16) not null default 'APPROVED'
  check (status in ('PENDING', 'APPROVED', 'REJECTED'));
alter table beers add column created_by uuid references users(id);
alter table beers add column decided_by uuid references users(id);
alter table beers add column decided_at timestamp with time zone;
create index ix_beers_status on beers(status);
