-- A weight an admin set by hand stays; one the policy decided is re-decided whenever
-- the person's earlier seasons come into view (a claim approved, a name linked).
alter table rater_weights add column overridden boolean not null default false;
