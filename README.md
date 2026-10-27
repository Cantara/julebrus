# julebrus

Cantaras digitale juleølsmaking. Season 1 Nov – 23 Dec, five tastings a day (twelve at a table of eight or more), five
solo days, the rest in groups formed by QR code. Design and rules: [DESIGN.md](DESIGN.md).

## What it does

- **Tables.** A host opens a table and shows a QR code or a six-character code; the others join on their own
  phones. Scores are dice, 1–6, hidden until the last person at the table has rolled, then revealed on every
  phone in the same second (server-sent events), with the notes and tags people wrote. Duels ("which was better?")
  build a Bradley–Terry side ranking; the host can pour a **blind round** where the guests guess which beer is in
  the glass. An open table is one tap away from Home and from "Dine bord".
- **The season.** Max 5 tastings a day (12 at a table of 8 or more, because a party is a different evening), max 5 solo days, only Vinmonopolet-strength juleøl counts, homebrew under
  its own rules. Home is an advent calendar (this week by default) with a streak counter; the results page is a
  weighted Bayesian ranking, filterable per team. Rater weights: 0.5 the first season, 1.0 after a normal one,
  0 when flagged for rigging; veterans from the old spreadsheets are recognised through the admin name mapping.
- **"I kveld".** The day's wall for the whole house: who rolled which beer, the tables going right now, approved labels and
  blind-round results, live, with a 🍻 and four more emojis to give each line. Never a person's dice, and a day average per
  beer only from three rolls. People without active consent are left out.
- **Planned tastings.** Anyone plans a tasting under *Smakinger*, physical with a place or digital with everyone at
  home and the same beers, with an optional cap; the others sign up and see who is coming. On the night the host opens
  the table from the event and every attendee gets the code without a QR. The finale is listed there as the season's
  fixed digital event: 23 December 18:00, everyone on their own phone, the page reveals the result by itself.
- **Teams.** Cantara and Rebel out of the box, more on request. Pick yours after consent, tap a team to see who
  plays for it, each team gets its own winner and "Årets lag" at the finale.
- **Tipp pallen.** Everyone may tip gold, silver and bronze until the last day begins; the league and "Årets
  tipper" come out at the reveal.
- **Every beer has a page.** Tap a beer anywhere (catalogue, results, the wall, a table, the calendar, the finale) for
  its label, this season's rank and dice histogram, its duel record, the tags people gave it, their tasting notes without
  names, your own roll and what earlier years said. Anything from other people's rolls shows only from three rolls, and the
  notes only after you have rolled the beer yourself: taste first, read afterwards. A beer from the archive has a page
  by name (`/historikk/ol/<name>`): its all-time place, every season's votes as the old sheets recorded them, and the
  way to this year's page when the same name is on the shelf.
- **All-time list.** With the old beer names normalised, the history page ranks every beer across all recorded
  seasons, weighted as each year weighted its votes and shrunk towards the all-time mean, with each beer's best year.
- **Ganen din.** After three rolls the profile draws your palate: which tags you score above and below your own
  mean, your taste twin and your taste opposite among the others (from shared beers). The finale lists the
  season's twins and opposites.
- **Badges** as baubles on a tree (ten so far, Blind Taster included), label **photos** with admin approval, a
  bilingual **story page**, the imported **history 1998–2019** with charts and claim-your-name, a public
  **landing page**, optional **push notifications** (the daily nudge, a reminder before a planned tasting and the finale), and an **MCP server** so
  agents can read the aggregates.
- **The finale, 23 December 18:00.** Podium, duel podium, team winners, the tipping league and the season's awards.

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

From the command line the same impersonation is the `X-Dev-User` header (`curl -H 'X-Dev-User: kari@cantara.no'
localhost:8080/api/me`); the event streams take `?devUser=` instead, since EventSource cannot set headers. The dev
profile opens the season window on 1 October and makes `dev@cantara.no` an admin; the public landing page is at
`/velkommen` (the dev profile signs everyone in on `/`).

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
dev admin; the box has it set). Admins see what waits for them (claims to decide, logins without a historic name,
beers and label photos to approve) as a card on Home and a dot on the "Meg" tab. Under *Admin: Folk* they see
every person with this season's weight, why, and their earlier seasons, and can set it by hand with a reason the person sees (the archive has
holes, so a veteran the archive does not show starts at 0.5 until an organiser says otherwise), or hand it back to the
policy. The same list shows who signed in without consenting and who has not answered the team question; the
"Mas" card opens a mail in the organiser's own mail program with those people in BCC and a prepared text. The app sends
no mail itself. The Google OAuth redirect URI is
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

Deletion requests go to `JULEBRUS_CONTACT_EMAIL` and are done by an organiser. Recipe 1 was last run on
2026-10-25 against the full schema (V1 to V13) on a throwaway PostgreSQL 16, on a person seeded with everything that
references `users`: solo and table tastings with notes and tags, duels, a hosted table and a table joined, blind rounds
hosted and guessed, two teams, a podium tip, an approved label photo, an approved history claim, a push subscription,
cheers given and received. It ran to `commit`, the two other seeded people kept every row of theirs, the claimed
historic name disappeared from the history, and a second run stopped at the first step. An earlier run (2026-10-24,
V1 to V9) covered an admin who had decided beers, photos and claims. It has never been run on a real database, and
Recipe 2 was not part of either test.
Run each recipe with `psql -v ON_ERROR_STOP=1 -f recipe.sql`: it stops at the first error and the open transaction is rolled
back when the session ends. A second run of Recipe 1 stops at the first step ("no rows returned": the person is gone)
and changes nothing. Review it against the current schema, take a backup, run it in one transaction and check the row counts
before `commit`. When the schema changes, check that every foreign key to `users` is still handled:
`select conrelid::regclass, conname from pg_constraint where confrelid = 'users'::regclass;` lists them.

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

-- 3. Their tastings and duels, in dependency order (the cheers others gave their rolls go with the rolls).
delete from feed_reactions where target_id in (select id from tastings where user_id = :'uid');
delete from tasting_tags where tasting_id in (select id from tastings where user_id = :'uid');
delete from tastings where user_id = :'uid';
delete from duels where user_id = :'uid';
-- Blind pour: their guesses, and every round they hosted with its options and everybody's guesses in it.
delete from blind_guesses where user_id = :'uid'
                             or round_id in (select id from blind_rounds where host_id = :'uid');
delete from blind_round_options where round_id in (select id from blind_rounds where host_id = :'uid');
delete from feed_reactions where target_id in (select id from blind_rounds where host_id = :'uid');
delete from blind_rounds where host_id = :'uid';
delete from session_members where user_id = :'uid';

-- 3b. Photos they uploaded (the person owns the copyright, so approved ones go too; the beer just loses that
--     cover. To keep one with the person's written permission, re-assign it instead:
--       update beer_photos set user_id = <an admin's users.id> where id = ...;  before the delete below).
--     Photos they decided as an admin keep the row, only the pointer goes. Beers they added keep existing.
delete from feed_reactions where target_id in (select id from beer_photos where user_id = :'uid');
delete from beer_photos where user_id = :'uid';
update beer_photos set decided_by = null where decided_by = :'uid';
update beers set created_by = null where created_by = :'uid';
update beers set decided_by = null where decided_by = :'uid';

-- 3c. Teams, the podium tip, push subscriptions and the cheers they gave on "I kveld".
--     (Cheers others gave to this person's rolls go with the rolls: feed_reactions.target_id has no foreign key.)
delete from team_members where user_id = :'uid';
delete from tips where user_id = :'uid';
delete from push_subscriptions where user_id = :'uid';
delete from feed_reactions where user_id = :'uid';

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
delete from feed_reactions where target_id in (select id from erase_sessions)
                              or target_id in (select id from tastings where session_id in (select id from erase_sessions));
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

## Fixing a beer name

Some old beer names are misspelled ("Rignes Julebokk"). The app imports a season only once, so the database in
production is fixed through `backend/src/main/resources/history/beer_names.csv`, which it applies at every startup:

1. Either fix the name in the source too (`source/google-sheets/<year>.csv`, or `NAME_FIXES` in
   `tools/extract_wiki_tables.py` for the wiki years), rerun `tools/build_data.py`, `tools/to_app_history.py` and
   `tools/to_landing_stats.py` (see `data/README.md`), and add `year,old name,new name,legacy` to beer_names.csv;
   or leave the source as written and add `year,as written,correct name,` (the name exactly as in `data/votes.csv`).
   Quote a name that has a comma. Remove a line to undo a fix.
2. Open a pull request. The fix takes effect on the next deploy and restart: the app renames the ratings already
   in its database; nothing is deleted or re-imported.
3. The original spelling is kept (`historic_ratings.beer_name_original`); the landing page numbers use the
   corrected names. A `legacy` row is one whose old name is gone from the corrected files: it renames databases
   imported before the correction and matches nothing in a fresh one.

Candidates: `python3 -I tools/suggest_beer_name_fixes.py` writes a review sheet,
`data/review/beer-name-candidates.csv` (suggestions only; fill in its `decision` column). After a change run
`python3 -I tools/to_landing_stats.py backend/src/main/resources/landing/history-stats.json` and
`cd backend && mvn -B -ntp test -Dtest=BeerNamesTest,LandingStatsTest,SuggestBeerNameFixesTest`.

## Push notifications

Three messages, all opt-in per browser from the "Varsler" card on the profile page: a daily
"Dagens luke står uåpnet" nudge at 17:00 Oslo time (`julebrus.push.nudge-time`) to people who
have not tasted that day, "Smaking om en time" to everyone signed up for a planned tasting, and
"Stjernen er tent" at the reveal. Web Push with VAPID, encrypted
on the JDK alone (RFC 8291, checked against the RFC's test vector), no third-party library.

To switch it on, log in as an admin and open `/api/push/keygen`; put the two keys in
`JULEBRUS_VAPID_PUBLIC_KEY` and `JULEBRUS_VAPID_PRIVATE_KEY` and restart. Changing the keys
later invalidates every subscription (people switch it on again). The jar needs outbound
HTTPS to the push services (Google, Mozilla, Apple). Browsers that answer 404 or 410, or fail
five times in a row, are forgotten. On iPhone, push works only for the app on the home screen.

## Live reveal and the wall

Every phone at a table holds one server-sent event stream (`/api/sessions/{id}/events`), and
every phone on "I kveld" holds the day-wide one (`/api/feed/events`). A reverse proxy in front
of the jar must not buffer them: the responses carry `X-Accel-Buffering: no` and
`Cache-Control: no-cache`, which nginx honours; otherwise set `proxy_buffering off` for
`/api/sessions/` and `/api/feed/` and give the locations a read timeout above the 20-second
heartbeat (`proxy_read_timeout 60s` or more). Without the streams the app still works: both
pages fall back to polling every five seconds. Streams time out after 30 minutes and the
browser reconnects on its own. The events carry only a kind, never data; the client refetches,
so the reveal rules stay on the server.

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

## Vinmonopolet products and the match review

`data/vinmonopolet/` (Vinmonopolet's juleøl lists 2013-2025 and the machine's guess for each of the 401 tasting results) is imported at
startup into three tables, so an administrator can finish the review in the app:

| Table | Holds | Key |
|---|---|---|
| `vmp_products` | one row per article number (`products.csv`) | `article_no` |
| `vmp_listings` | every yearly listing (`listings.csv`), with the article number it is linked to and on what basis (`link_basis`) | `id` (no natural key: a list may repeat a name) |
| `vote_matches` | one row per (`season_year`, `vote_beer`): the `machine_*` guess and `candidates` from `vote_matches.csv`, and the person's decision (`decision` CONFIRMED / CHOSEN / NONE / NOT_A_PRODUCT, `decision_article_no`, `decision_product_name`, `decided_by`, `decided_at`), `version` for optimistic locking | `id`, unique (`season_year`, `vote_beer`) |

The CSVs are not copied into the backend: `backend/pom.xml` maps `../data/vinmonopolet/*.csv` (not `README.md`) into the jar under
`vinmonopolet/`, and `VmpImportService` reads `vinmonopolet/products.csv`, `listings.csv` and `vote_matches.csv` (the per-year files are
in the jar but not read; `listings.csv` has them all). The import runs at startup (`VmpImportRunner`; a failure is logged, not fatal)
and on `POST /api/admin/vinmonopolet/import`. It is additive and idempotent: missing products and listings are inserted, a new
(year, beer) is inserted, and if a newer `vote_matches.csv` changes the machine side of an existing entry only the `machine_*` columns
and `candidates` are updated (which also bumps `version`, so a page loaded before the import gets 409 on its next click). A decision is
never written, cleared or deleted by the import, and rows absent from the files are never deleted. A repeated (year, beer) in
`vote_matches.csv` is used once (the first wins; 2018 "CB Juleøl" is in the results twice, so 400 rows come from 401 lines).

Admin API (admin check as everywhere under `/api/admin`, behind the consent gate, CSRF on the POSTs):

| Endpoint | Does |
|---|---|
| `GET /api/admin/matches?status=&year=&decided=&page=&size=` | a page of entries plus a `summary` (counts per machine status, undecided per status, counts per decision). `status` is `auto, likely, review, none, not-a-product`. Order: undecided first (review, none, likely, auto, not-a-product), then decided; then year and name. `size` default 25, at most 100. |
| `POST /api/admin/matches/{id}/decision` | `{action, version, articleNo?, productName?}`. `CONFIRM` accepts the machine guess (needs one), `CHOOSE` needs an article number and/or a name that exists in `vmp_products` / `vmp_listings` (and, when both are given, belong together), `NONE`, `NOT_A_PRODUCT`, `CLEAR` removes the decision. `version` must be the one the page showed. |
| `POST /api/admin/matches/bulk` | `{action: "CONFIRM", year, status: "likely"\|"auto", dryRun?, expectedCount?}`. Confirms the undecided entries of that year and status that have a guess. `dryRun` defaults to **true** and only counts. With `expectedCount`, an apply fails with 409 if the number is no longer that. Returns `{dryRun, year, status, count}`. |
| `GET /api/admin/matches/overrides.csv` | every decision as `year,vote_beer,product_article_no,product_name`; NONE and NOT_A_PRODUCT both as `none`; RFC 4180 quoting |
| `GET /api/admin/products/search?q=` | type-ahead over products and distinct listing names, 20 at most, case-, accent- and ø/æ/å-insensitive |
| `POST /api/admin/vinmonopolet/import` | re-runs the import and returns the counts |

Error codes: `FORBIDDEN` (403, not an admin), `CONSENT_REQUIRED` (403), `MATCH_NOT_FOUND` (404), `CONFLICT` (409, stale `version` or a changed
count), `INVALID_FILTER` (422, unknown `status`), `NO_MACHINE_GUESS`, `CHOICE_REQUIRED`, `UNKNOWN_PRODUCT`, `CHOICE_MISMATCH`, `BULK_STATUS` (all
422), `VALIDATION` (400, unreadable body, unknown action, wrong parameter type).

The screen is "Treff" under Admin (profile page, then Admin, then the Treff tab) (`frontend/src/components/AdminMatches.vue`). **The database is the
source of truth after the import.** To carry decisions back into the repo, download `overrides.csv` there, save it as
`data/vinmonopolet/vote_match_overrides.csv` and run `python3 tools/match_votes_to_products.py`; the script applies it over its own guesses and
rewrites `vote_matches.csv`, which the next deploy then imports (changing only machine fields of rows, never decisions).
The app's `vote_matches`/`vmp_*` tables hold product data and beer names only; no personal data, so the consent text is not involved.

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
| `alltime_ranking` | `min` (default 5) | every beer across all recorded seasons, Bayesian-adjusted, with its best year |
| `list_raters` | | everyone who has tasted, with years and vote count |
| `rater_history` | `name` | one rater's mean against the crowd mean per season |
| `current_ranking` | | this season's weighted Bayesian ranking |
| `team_ranking` | `team` (slug) | the same ranking over one team's raters |
| `duel_ranking` | | this season's Bradley-Terry duel ranking |
| `season_status` | | dates, days left, rules (daily limits, big table, solo days), teams, tips and totals so far |
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
