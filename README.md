# julebrus

Cantaras digitale juleølsmaking. Season 1 Nov – 23 Dec, five tastings a day, five
solo days, the rest in groups formed by QR code. Design and rules: [DESIGN.md](DESIGN.md).

## Layout

```
backend/   Spring Boot 3.5 · Java 21 · PostgreSQL · Flyway · Google login
frontend/  Vue 3 · TypeScript · Vite · installable PWA
```

## Run locally

```bash
# Backend with in-memory H2 and a fixed dev user, no Google needed
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend dev server on :5173, proxying /api to :8080
cd frontend && npm install && npm run dev
```

To act as a second person in another browser tab while testing a group session:

```js
localStorage.setItem('devUser', 'kari@cantara.no')
```

## Build for production

```bash
cd frontend && npm run build        # writes into backend/src/main/resources/static
cd ../backend && mvn package        # one runnable jar
```

Environment variables: `JULEBRUS_DB_URL`, `JULEBRUS_DB_USER`, `JULEBRUS_DB_PASSWORD`,
`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, optionally `JULEBRUS_ALLOWED_DOMAINS`
(comma separated; empty allows any Google account) and **`JULEBRUS_ADMINS`** (emails
that may flag raters, override weights, approve history claims, beers and photos). **Production must set
`JULEBRUS_ADMINS`**: it has no default, and empty means nobody is an admin (the `dev` profile has its own
dev admin; the box has it set). The Google OAuth redirect URI is
`https://<host>/login/oauth2/code/google`.

Also optional: `JULEBRUS_CONTACT_EMAIL` (named in the consent text as where to ask for
deletion; blank means "contact the organisers"), `JULEBRUS_SPONSOR_NAME` (default
`eXOReaction`; blank hides the "Sponsored by" line) and `JULEBRUS_SPONSOR_URL`
(default `https://exoreaction.com`).

`JULEBRUS_MCP_TOKEN` switches on the MCP server (see below); leave it empty to keep
`/mcp` off. `JULEBRUS_VAPID_PUBLIC_KEY` and `JULEBRUS_VAPID_PRIVATE_KEY` switch on push
notifications (see below); `JULEBRUS_PUSH_SUBJECT` (`mailto:` or `https:`) defaults to the
contact e-mail.

## Consent, privacy page and claims

Everyone must accept the consent notice (`CONSENT_VERSION` in `ConsentService`, texts in
`backend/src/main/resources/consent/`, currently a draft for the organisers to approve)
before any API call except the exempt ones works; the server answers 403 `CONSENT_REQUIRED`.
The exempt paths are `/api/me`, `/api/consent`, `/api/consent/**`, `/api/config` and
`/api/privacy` (kept in step in `ConsentWebConfig` and `SecurityConfig`). Bump the version to
ask everyone again. Accepts and withdrawals are kept as an append-only trail in
`consent_events` (erasure on request deletes the person's rows, see below); `user_consents` holds the current state. Withdrawing closes the person's
access only: it deletes nothing (see the deletion procedure below). The admin rater overview and the
season weights cover people with active consent only; before consent only the `users` row exists.

**Privacy policy URL.** The same texts are public at `/privacy`: server-rendered HTML that works
without JavaScript, both languages on one page, `?lang=en` puts English first (the JSON is at
`GET /api/privacy`). No login, no consent. The page itself sends a restrictive
Content-Security-Policy, Referrer-Policy and Permissions-Policy; HSTS and the forwarded-header
handling (`SERVER_FORWARD_HEADERS_STRATEGY`) belong to the proxy and deploy kit (nginx), not to this app. Enter `https://xmas.cantara.no/privacy` as the privacy policy link on
the Google Cloud console OAuth consent screen (APIs & Services, OAuth consent screen, app
information). `JULEBRUS_CONTACT_EMAIL` is shown there and named in the text; the app logs a
WARN at startup when it is blank outside the `dev` profile.

Under *Meg* people can ask to link the historic people (`rater_key`, the canonical names from
`history/raters.csv`) that are them to their login. Candidates: `GET /api/history/claim-candidates`.
People raters.csv already ties to an email are not claimable (an admin cannot approve a claim for a key that has been tied to another login since). An admin approves each claim (approving auto-rejects every other pending claim for that person), can
revoke an approval (`POST /api/admin/history/claims/{id}/revoke`), and sees everyone who asks for
the same person together. A rejected or withdrawn claim can be asked for again; a revoked one cannot (`CLAIM_REVOKED`, only
an admin deleting the claim row resets it). Withdrawing keeps the row (status `WITHDRAWN`), so it
still counts: at most 5 claims wait at once (`CLAIM_LIMIT`) and at most 10 are created or reopened
per 24 hours (`CLAIM_DAILY_LIMIT`). Every step is recorded in `claim_events`; claim rows have an
optimistic lock, so two admins deciding at once get 409 `CONFLICT`.

## Label photos

Optional photo of a beer's label (`BeerPhotoBox` on the beer card and the rating page). Stored in PostgreSQL in
`beer_photos` (migration `V9__beer_photos.sql`, `data` is a `bytea`), never as a file or a public link. Nothing that
lists beers reads the bytes: the JPA entity `BeerPhoto` does not map the `data` column, and only `PhotoStore` (plain
JDBC) writes the row or selects `data`.

| Endpoint | Who | What |
|---|---|---|
| `POST /api/beers/{beerId}/photos` | consented user, CSRF | multipart, part `file`; 201 with the photo (status `PENDING`; an admin's is `APPROVED` at once) |
| `GET /api/beers/{beerId}/photo` | consented user | the cover: the oldest `APPROVED` photo, else the caller's own `PENDING` one; 404 when none |
| `GET /api/beers/photos/{photoId}/image` | uploader, admin, or anyone once `APPROVED` | one photo; 404 for everything else (no difference between "not yours" and "does not exist") |
| `DELETE /api/beers/photos/{photoId}` | uploader (any status) or admin | 204; the bytes are wiped, see below |
| `GET /api/admin/photos?status=PENDING` | admin | id, beer id and name, uploader name and email, time; never bytes. Only people with active consent |
| `POST /api/admin/photos/{id}/decision` | admin | `{"decision":"APPROVE"|"REJECT"}`; 409 `PHOTO_NOT_PENDING` if already decided (one conditional update, so parallel decisions cannot both win) |

`BeerView` gained `hasPhoto` (a cover exists for this caller), `myPhotoId` and `myPhotoStatus` (the caller's own newest
photo only) and `homebrew`. Responses carry `Content-Type: image/jpeg`, `Content-Length`, `Content-Disposition: inline`,
`X-Content-Type-Options: nosniff`, `Cache-Control: private, max-age=60`, `Vary: Cookie` and a `Content-Security-Policy: default-src 'none'; sandbox`.

**What happens to an upload** (`ImageSanitizer`, pure and unit tested): the request is limited to 6 MB by
`spring.servlet.multipart.*` (a bigger one is a 413 `PHOTO_TOO_LARGE`; `server.tomcat.max-swallow-size` is 16 MB so the
client sees the 413 and not a reset). The format is decided by the magic bytes only (JPEG or PNG; the client's content
type and file name are ignored). The header gives the dimensions: more than 8000 px on a side or 24 megapixels is refused
before any pixel is decoded (`PHOTO_DIMENSIONS`, decompression bombs). The picture is then decoded sub-sampled, turned by its EXIF
orientation, flattened onto white, scaled to at most 800 px and written again as a JPEG (quality 0.85, falling to 0.55 if it
would exceed 900 KB). The new file contains none of the original: no EXIF (GPS, device), no ICC profile (wide-gamut colours
are shown as sRGB), no comments, no trailing bytes. At most 3 pictures are decoded at the same time (`PHOTO_BUSY` 503
after 5 s of waiting).

Quotas, per person, enforced under a row lock on the person (like claims) and checked once more before decoding so an
over-limit person costs nothing: 5 uploads per Oslo day, 30 per season year (since 1 January of `julebrus.season.year`) and
one `PENDING` photo per beer. Organisers (admins) are exempt from the day and season limits, since they photograph whole
shelves. Deleting a photo wipes `data` and sets `deleted_at` but keeps the empty row: the quotas count those rows, otherwise
upload-delete-upload would never hit a limit.

Error codes: `PHOTO_FORMAT` (415, also animated PNG), `PHOTO_TOO_LARGE` (413), `PHOTO_DIMENSIONS` (422),
`PHOTO_UNREADABLE` (422, corrupt or truncated), `PHOTO_MISSING` (400), `PHOTO_DAILY_LIMIT` and `PHOTO_SEASON_LIMIT` (422),
`PHOTO_PENDING_EXISTS` (409), `PHOTO_NOT_PENDING` (409), `PHOTO_NOT_FOUND` (404), `PHOTO_BUSY` (503), `PHOTO_IN_PROGRESS` (429, one upload at a time per person), `BEER_REJECTED` (422).
While a person's consent is withdrawn their approved photos are not shown to others (admins still see them, to delete)
and admins cannot decide their pending ones.
A rejected beer takes no photos; homebrew and pending beers do.

Not handled: a browser that does not turn portrait photos by itself still works, because the server applies the EXIF
orientation (JPEG only; PNG has none). Wide-gamut colour profiles are dropped.

### Deleting a person's data by hand (procedure to review before use)

Deletion requests go to `JULEBRUS_CONTACT_EMAIL` and are done by an organiser. Recipe 1 was run
(2026-10-24, schema V1 to V9) on a throwaway PostgreSQL 16 with seeded data, once as an admin (who had decided
beers, photos and claims and uploaded a photo) and once as a normal user (tastings, duels, a hosted session with
a handed-over member, blind-pour rounds and guesses, photos, an approved claim): both ran to the end and the
other people's rows were left alone. It has never been run on a real database, and Recipe 2 was not part of that test.
Run each recipe with `psql -v ON_ERROR_STOP=1 -f recipe.sql`: it stops at the first error and the open transaction is rolled
back when the session ends. A second run of Recipe 1 stops at the first step ("no rows returned": the person is gone)
and changes nothing. Review it against the current schema, take a backup, run it in one transaction and check the row counts
before `commit`. When the schema changes, check that every foreign key to `users` is still handled.

Two things to know first. Everything is deleted, including the person's `consent_events` rows:
that log is append-only for the application, but erasure on request overrides it. And the person's
earlier-season ratings are found through their *approved claims* and their email, so capture the
keys **before** the claims are deleted.

**Recipe 1: erase a person who has logged in.** Replace the email; run with psql.

```sql
\set email 'person@example.com'
begin;
select id as uid from users where lower(email) = lower(:'email') \gset

-- 1. Capture the historic people (rater_key) linked to this person, BEFORE deleting any claim:
--    their approved and revoked claims (a revoked one was linked for a while), and rows already mapped to their email by raters.csv.
create temp table erase_keys on commit drop as
  select rater_key as k from historic_rater_claims where user_id = :'uid' and status in ('APPROVED', 'REVOKED')
  union
  select rater_key from historic_ratings where lower(rater_email) = lower(:'email') and rater_key is not null;
select * from erase_keys;   -- look at it: these names disappear from the history for everyone

-- 2. Their earlier-season ratings (all spellings of each key, and rows carrying their email).
delete from historic_ratings
 where rater_key in (select k from erase_keys) or lower(rater_email) = lower(:'email');

-- 3. Their tastings and duels, in dependency order.
delete from tasting_tags where tasting_id in (select id from tastings where user_id = :'uid');
delete from tastings where user_id = :'uid';
delete from duels where user_id = :'uid';
-- Blind pour: their guesses, and every round they hosted with its options and everybody's guesses in it.
delete from blind_guesses where user_id = :'uid'
                             or round_id in (select id from blind_rounds where host_id = :'uid');
delete from blind_round_options where round_id in (select id from blind_rounds where host_id = :'uid');
delete from blind_rounds where host_id = :'uid';
delete from session_members where user_id = :'uid';

-- 3b. Photos they uploaded (the person owns the copyright, so approved ones go too; the beer just loses that
--     cover. To keep one with the person's written permission, re-assign it instead:
--       update beer_photos set user_id = <an admin's users.id> where id = ...;  before the delete below).
--     Photos they decided as an admin keep the row, only the pointer goes. Beers they added keep existing.
delete from beer_photos where user_id = :'uid';
update beer_photos set decided_by = null where decided_by = :'uid';
update beers set created_by = null where created_by = :'uid';
update beers set decided_by = null where decided_by = :'uid';

-- 4. Sessions they hosted (sessions.host_id is NOT NULL). A session nobody else belongs to can go.
--    One with other members must be handed to a member first (update sessions set host_id = ...),
--    or the delete of users fails on the foreign key. Check first:
--      select s.id, count(m.user_id) from sessions s left join session_members m on m.session_id = s.id
--      where s.host_id = :'uid' group by s.id;
create temp table erase_sessions on commit drop as
  select s.id from sessions s where s.host_id = :'uid'
   and not exists (select 1 from session_members m where m.session_id = s.id);
delete from blind_guesses where round_id in (select id from blind_rounds where session_id in (select id from erase_sessions));
delete from blind_round_options where round_id in (select id from blind_rounds where session_id in (select id from erase_sessions));
delete from blind_rounds where session_id in (select id from erase_sessions);
delete from tasting_tags where tasting_id in (select id from tastings where session_id in (select id from erase_sessions));
delete from tastings where session_id in (select id from erase_sessions);
delete from duels where session_id in (select id from erase_sessions);
delete from session_beers where session_id in (select id from erase_sessions);
delete from sessions where id in (select id from erase_sessions);

-- 5. Claims, and only then the rows that point at them. claim_events names the person as the actor
--    of their own claims and, for an admin, of other people's decisions; both go.
delete from claim_events where claim_id in (select id from historic_rater_claims where user_id = :'uid')
                            or actor_user_id = :'uid';
delete from historic_rater_claims where user_id = :'uid';
update historic_rater_claims set decided_by = null where decided_by = :'uid';

-- 6. Consent state and the consent log, weights (keyed by email, no foreign key), then the user.
delete from consent_events where user_id = :'uid';
delete from user_consents where user_id = :'uid';
delete from rater_weights where lower(email) = lower(:'email');
delete from users where id = :'uid';
-- Check the counts, then: commit;   (or rollback;)
```

Also remove the person from the source files, or a fresh database imports them again (the importer
only skips a season that already has ratings): delete their rows from `history/<year>.csv` and
their lines from `history/raters.csv`.

**Recipe 2: remove a historic name for someone who never logged in.** Find the key first
(`select rater_key, rater_name, count(*) from historic_ratings where rater_name ilike '%name%' group by 1, 2;`).

```sql
\set key 'Some Person'
begin;
-- Pending or rejected claims for the key, and their history.
delete from claim_events where claim_id in (select id from historic_rater_claims where rater_key = :'key');
delete from historic_rater_claims where rater_key = :'key';
-- The ratings under the key, and rows without a key whose source name is the same.
delete from historic_ratings where rater_key = :'key' or (rater_key is null and rater_name = :'key');
-- Check the counts, then: commit;
```

Then edit the files: remove every `history/<year>.csv` row whose `rater_name` is one of that person's
spellings, and every line in `history/raters.csv` whose `canonical_name` is the key (and whose `name`
is one of the spellings). Redeploy; the importer will not bring them back.

## Historic seasons

The juleøl test history 1998–2019 is in the repository and is imported at startup
from `backend/src/main/resources/history/`. The source of truth is `data/votes.csv`
(one row per vote, with each voter's weight that year); `data/README.md` describes
the files and `data/SOURCES.md` where every year comes from. Regenerate the app's
history files with:

```bash
python3 tools/to_app_history.py backend/src/main/resources/history
```

## Push notifications

Two messages, both opt-in per browser from the "Varsler" card on the profile page: a daily
"Dagens luke står uåpnet" nudge at 17:00 Oslo time (`julebrus.push.nudge-time`) to people who
have not tasted that day, and "Stjernen er tent" at the reveal. Web Push with VAPID, encrypted
on the JDK alone (RFC 8291, checked against the RFC's test vector), no third-party library.

To switch it on, log in as an admin and open `/api/push/keygen`; put the two keys in
`JULEBRUS_VAPID_PUBLIC_KEY` and `JULEBRUS_VAPID_PRIVATE_KEY` and restart. Changing the keys
later invalidates every subscription (people switch it on again). The jar needs outbound
HTTPS to the push services (Google, Mozilla, Apple). Browsers that answer 404 or 410, or fail
five times in a row, are forgotten. On iPhone, push works only for the app on the home screen.

## Live reveal

Every phone at a table holds one server-sent event stream (`/api/sessions/{id}/events`). A
reverse proxy in front of the jar must not buffer it: the response carries
`X-Accel-Buffering: no` and `Cache-Control: no-cache`, which nginx honours; otherwise set
`proxy_buffering off` for `/api/sessions/` and give the location a read timeout above the
20-second heartbeat (`proxy_read_timeout 60s` or more). Without the stream the app still
works: the session page falls back to polling every five seconds.

## Teams

Cantara and Rebel exist from the first migration; everyone picks their teams once, right after
consent (or "Velg senere"). Admins add and enable more teams under *Admin: lag* on the profile
page (name, optional sponsor line, optional e-mail domains that pre-select the team at signup).
"Et annet lag?" on the signup page opens a mail to `JULEBRUS_CONTACT_EMAIL`.

## Landing page

A visitor who is not signed in gets a public landing page at `/` (server-rendered, both languages,
`?lang=en` for English); a signed-in person goes straight into the app as before. `/velkommen` always shows
the page (the dev profile signs everyone in, so use that path locally), and so do `/welcome` and `/landing`
(same page, same `?lang=en`); the app sends a logged-out
visitor on `/` there instead of straight to Google. The page has no scripts and loads nothing from other
sites: it sends `default-src 'none'; style-src 'unsafe-inline'; img-src 'self'` and draws its charts as
CSS and inline SVG. It reads nothing from the database. It describes the season's features (teams, live reveal,
blind pour, the podium tip, label photos); the push-notification card appears only when the VAPID keys are set,
with the configured nudge time. Its history numbers come from
`backend/src/main/resources/landing/history-stats.json`, aggregates only (no names, no per-person scores),
written from `data/` by:

```bash
python3 tools/to_landing_stats.py backend/src/main/resources/landing/history-stats.json
```

`LandingStatsTest` recomputes the numbers from `data/` and fails when the file is stale; `LandingPageTest`
checks that no name from `data/votes.csv`, `data/participation.csv` or `history/raters.csv` appears in the page.

## MCP server

The tasting data is also available to AI agents over the Model Context Protocol
(Streamable HTTP) at `/mcp`. The endpoint is off until `JULEBRUS_MCP_TOKEN` is set;
with it set, every request must carry `Authorization: Bearer <token>`, and nothing
else (login, consent, CSRF) applies there. All tools are read-only and return
aggregates or the already public history; no per-person scores from the running
season and no e-mail addresses.

| Tool | Arguments | Answers |
|---|---|---|
| `list_seasons` | | every recorded season with size, winner, weighting and source |
| `season_results` | `year` | that season's ranked result list, weighted as it was that year |
| `beer_history` | `name` | one beer's mean and vote count per season |
| `list_raters` | | everyone who has tasted, with years and vote count |
| `rater_history` | `name` | one rater's mean against the crowd mean per season |
| `current_ranking` | | this season's weighted Bayesian ranking |
| `duel_ranking` | | this season's Bradley-Terry duel ranking |
| `season_status` | | dates, days left, rules and totals so far |
| `finale` | | the revealed result on the last day; before that only the reveal time |

Claude Code, for example, connects with:

```bash
claude mcp add --transport http julebrus https://<host>/mcp --header "Authorization: Bearer <token>"
```

Generate the token with something like `openssl rand -hex 32`. The server is
`McpConfig` and the tools `McpTools`, both in `backend/.../mcp/`; `McpServerTest`
speaks the protocol by hand against a real port.

## Tests

```bash
cd backend && mvn test
```
