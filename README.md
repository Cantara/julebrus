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
(comma separated; empty allows any Google account) and `JULEBRUS_ADMINS` (emails
that may flag raters, override weights and approve history claims). The Google OAuth redirect URI is
`https://<host>/login/oauth2/code/google`.

Also optional: `JULEBRUS_CONTACT_EMAIL` (named in the consent text as where to ask for
deletion; blank means "contact the organisers"), `JULEBRUS_SPONSOR_NAME` (default
`eXOReaction`; blank hides the "Sponsored by" line) and `JULEBRUS_SPONSOR_URL`
(default `https://exoreaction.com`).

`JULEBRUS_MCP_TOKEN` switches on the MCP server (see below); leave it empty to keep
`/mcp` off.

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

### Deleting a person's data by hand (procedure to review before use)

Deletion requests go to `JULEBRUS_CONTACT_EMAIL` and are done by an organiser. It was tested against a
throwaway PostgreSQL 16 with seeded data, but never run on a real database. Run each recipe with
`psql -v ON_ERROR_STOP=1 -f recipe.sql`: it stops at the first error and the open transaction is rolled
back when the session ends. A second run of a recipe is harmless (the person or rows are already gone). Review it
against the current schema, take a backup, run it in one transaction and check the row counts
before `commit`.

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
delete from session_members where user_id = :'uid';

-- 4. Sessions they hosted (sessions.host_id is NOT NULL). A session nobody else belongs to can go.
--    One with other members must be handed to a member first (update sessions set host_id = ...),
--    or the delete of users fails on the foreign key. Check first:
--      select s.id, count(m.user_id) from sessions s left join session_members m on m.session_id = s.id
--      where s.host_id = :'uid' group by s.id;
create temp table erase_sessions on commit drop as
  select s.id from sessions s where s.host_id = :'uid'
   and not exists (select 1 from session_members m where m.session_id = s.id);
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

The screen is "Treff / Matches" at the bottom of the profile page for admins (`frontend/src/components/AdminMatches.vue`). **The database is the
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
