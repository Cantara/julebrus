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

## Consent and claims

Everyone must accept the consent notice (`CONSENT_VERSION` in `ConsentService`, texts in
`backend/src/main/resources/consent/`, currently a draft for the organisers to approve)
before any API call except `/api/me`, `/api/consent*` and `/api/config` works; the server
answers 403 `CONSENT_REQUIRED`. Bump the version to ask everyone again. Under *Meg* people
can ask to link the historic people (`rater_key`, the canonical names from `history/raters.csv`) that are them to their login; an admin approves each claim. People raters.csv already ties to an email are not claimable. Candidates: `GET /api/history/claim-candidates`.

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
