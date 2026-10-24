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

## Consent, privacy page and claims

Everyone must accept the consent notice (`CONSENT_VERSION` in `ConsentService`, texts in
`backend/src/main/resources/consent/`, currently a draft for the organisers to approve)
before any API call except the exempt ones works; the server answers 403 `CONSENT_REQUIRED`.
The exempt paths are `/api/me`, `/api/consent`, `/api/consent/**`, `/api/config` and
`/api/privacy` (kept in step in `ConsentWebConfig` and `SecurityConfig`). Bump the version to
ask everyone again. Accepts and withdrawals are kept as an append-only trail in
`consent_events`; `user_consents` holds the current state. Withdrawing closes the person's
access only: it deletes nothing (see the deletion procedure below).

**Privacy policy URL.** The same texts are public at `/privacy` (data from `GET /api/privacy`,
no login, no consent). Enter `https://xmas.cantara.no/privacy` as the privacy policy link on
the Google Cloud console OAuth consent screen (APIs & Services, OAuth consent screen, app
information). `JULEBRUS_CONTACT_EMAIL` is shown there and named in the text; the app logs a
WARN at startup when it is blank outside the `dev` profile.

Under *Meg* people can ask to link the historic people (`rater_key`, the canonical names from
`history/raters.csv`) that are them to their login. Candidates: `GET /api/history/claim-candidates`.
People raters.csv already ties to an email are not claimable. An admin approves each claim, can
revoke an approval (`POST /api/admin/history/claims/{id}/revoke`), and sees everyone who asks for
the same person together. A rejected claim can be asked for again; at most 5 claims wait at once
and at most 10 are created or reopened per 24 hours.

### Deleting a person's data by hand (procedure to review before use)

Deletion requests go to `JULEBRUS_CONTACT_EMAIL` and are done by an organiser. Nothing below has
been run anywhere. Review it against the current schema, take a backup, run it in a transaction
and check the row counts before `commit`. Replace the email; run with psql.

```sql
\set email 'person@example.com'
begin;
select id as uid from users where lower(email) = lower(:'email') \gset

-- Their tastings and duels, in the tables' dependency order.
delete from tasting_tags where tasting_id in (select id from tastings where user_id = :'uid');
delete from tastings where user_id = :'uid';
delete from duels where user_id = :'uid';
delete from session_members where user_id = :'uid';

-- Sessions they hosted: sessions.host_id is NOT NULL. A session that nobody else belongs to can go;
-- one with other members must be handed to a member first (update sessions set host_id = ... ),
-- otherwise the delete of users fails on the foreign key. Check before deleting:
--   select s.id, count(m.user_id) from sessions s left join session_members m on m.session_id = s.id
--   where s.host_id = :'uid' group by s.id;
delete from tasting_tags where tasting_id in (select id from tastings where session_id in
  (select s.id from sessions s where s.host_id = :'uid'
     and not exists (select 1 from session_members m where m.session_id = s.id)));
delete from tastings where session_id in (select s.id from sessions s where s.host_id = :'uid'
  and not exists (select 1 from session_members m where m.session_id = s.id));
delete from duels where session_id in (select s.id from sessions s where s.host_id = :'uid'
  and not exists (select 1 from session_members m where m.session_id = s.id));
delete from session_beers where session_id in (select s.id from sessions s where s.host_id = :'uid'
  and not exists (select 1 from session_members m where m.session_id = s.id));
delete from sessions s where s.host_id = :'uid'
  and not exists (select 1 from session_members m where m.session_id = s.id);

-- Consent and claims. decided_by points at admins, so clear it rather than delete other people's rows.
delete from consent_events where user_id = :'uid';
delete from user_consents where user_id = :'uid';
delete from historic_rater_claims where user_id = :'uid';
update historic_rater_claims set decided_by = null where decided_by = :'uid';

-- Weights are keyed by email, not by a foreign key.
delete from rater_weights where lower(email) = lower(:'email');

-- Their earlier-season ratings: by email, plus the approved key if they had one. Look first:
--   select rater_key, count(*) from historic_ratings where lower(rater_email) = lower(:'email') group by 1;
-- Then delete those rows, and remove the person from history/*.csv and raters.csv in the repo,
-- or a fresh database will import them again (the importer skips seasons that already have ratings).
delete from historic_ratings where lower(rater_email) = lower(:'email');

delete from users where id = :'uid';
-- Check the counts, then: commit;   (or rollback;)
```

## Historic seasons

The juleøl test history 1998–2019 is in the repository and is imported at startup
from `backend/src/main/resources/history/`. The source of truth is `data/votes.csv`
(one row per vote, with each voter's weight that year); `data/README.md` describes
the files and `data/SOURCES.md` where every year comes from. Regenerate the app's
history files with:

```bash
python3 tools/to_app_history.py backend/src/main/resources/history
```

## Tests

```bash
cd backend && mvn test
```
