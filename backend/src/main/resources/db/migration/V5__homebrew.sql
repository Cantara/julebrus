-- Homebrew is welcome when it is as strong as the Vinmonopolet beers, but it is
-- only rated in group sessions of more than five people and only enters the
-- ranking once it has at least four scores, so nobody can rig their own.
alter table beers add column homebrew boolean not null default false;
