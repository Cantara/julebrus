-- Photos of the beer label, stored in the database (never as public links). Only re-encoded
-- JPEG bytes are kept (metadata stripped, at most 800 px). A photo starts PENDING and is shown
-- to others only once an admin has approved it; the uploader sees their own at once.
-- "Deleting" a photo wipes data and sets deleted_at: the empty row stays as the ledger the
-- upload quotas count (otherwise upload-delete-upload would never hit a limit). The bytes are
-- never selected by anything that does not serve the image.
create table beer_photos (
  id            uuid primary key,
  beer_id       uuid not null references beers(id),
  user_id       uuid not null references users(id),
  status        varchar(16) not null check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  content_type  varchar(32) not null,
  width         integer not null,
  height        integer not null,
  size_bytes    integer not null,
  data          bytea not null,
  created_at    timestamp with time zone not null,
  decided_by    uuid references users(id),
  decided_at    timestamp with time zone,
  deleted_at    timestamp with time zone
);
create index ix_beer_photos_beer on beer_photos(beer_id);
create index ix_beer_photos_beer_status on beer_photos(beer_id, status);
create index ix_beer_photos_user_created on beer_photos(user_id, created_at);
