# julebrus

## Purpose
Cantaras digitale juleølsmaking: a season-long (1 Nov – 23 Dec) Christmas beer tasting
app. Max 5 tastings per person per day, max 5 solo tasting days, groups formed by QR
code, scores 1–6, blind reveal within a group, pairwise duels as a sidebar ranking,
badges, and imported historic seasons. See DESIGN.md for rules and decisions.

## Tech Stack
- Backend: Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, PostgreSQL (H2 under the `dev` profile), Google OIDC login
- Frontend: Vue 3, TypeScript, Vite, Pinia, vue-router, vite-plugin-pwa
- Build: Maven (`backend/`), npm (`frontend/`)

## Architecture
- `backend/src/main/java/no/cantara/julebrus/`
  - `tasting/TastingRules` — the season rules as a pure class. Change rules here and in `application.yml`.
  - `session/` — group sessions, join codes, blind reveal is computed in `SessionController.view`.
  - `duel/BradleyTerry`, `stats/BayesianAverage` — pure ranking math.
  - `badge/BadgeEvaluator` — pure badge rules over a tasting log.
  - `history/` — CSV import of earlier seasons and their weighting.
  - `consent/` — versioned consent (current state in `user_consents`, append-only `consent_events`); `ConsentInterceptor` answers 403 `CONSENT_REQUIRED` on all `/api/**` except `/api/me`, `/api/consent*`, `/api/config`, `/api/privacy` (public texts); `PrivacyPageController` serves `/privacy` as server-rendered HTML. Texts in `resources/consent/`.
  - `history/ClaimService`, `RaterNameSuggester` (pure) — claim-your-history on `rater_key` (create/withdraw run under a row lock on the user; `claim_events` is the history; claims have `@Version`; approving re-checks e-mail linkage and auto-rejects other pending claims; time comes from the injected `Clock`); admin decide/revoke in `admin/AdminClaimsController`.
  - `web/ConfigController` — public `/api/config` (sponsor line).
  - `config/SecurityConfig` — Google chain (`!dev`) and dev chain with `X-Dev-User` impersonation.
- `frontend/src/views/` — one view per screen; `api/client.ts` handles CSRF, 401 redirect and dev user.
- Frontend production build lands in `backend/src/main/resources/static` and is served by the jar.

## Development
```bash
cd backend && mvn test
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm install && npm run dev
cd frontend && npm run build
```

## Conventions
- Rule violations are HTTP 422 with a stable `code` (`OUTSIDE_SEASON`, `DAILY_LIMIT`, `SOLO_DAYS_EXHAUSTED`, `ALREADY_RATED`). Other errors carry their own codes too: `CONSENT_REQUIRED`, `CONSENT_VERSION_MISMATCH`, `RATER_NOT_FOUND`, `CLAIM_EXISTS`, `RATER_TAKEN`, `CLAIM_LIMIT`, `CLAIM_NOT_PENDING`, `CLAIM_NOT_APPROVED`, `CLAIM_DAILY_LIMIT`, `CLAIM_REVOKED`, `CLAIM_NOT_FOUND`, `RATER_LINKED`, and `CONFLICT` for a lost unique-constraint race.
- Days are Europe/Oslo. Scores are integers 1–6.
- A beer counts only if it is from Vinmonopolet, i.e. above grocery strength (`Beer.GROCERY_MAX_ABV` = 4.7 %); `Beer.counts()` is what the rankings use. Non-admin additions are PENDING until approved.
- Schema changes go in a new Flyway migration; Hibernate runs with `ddl-auto: validate`.
- UI copy is Norwegian (bokmål); code and comments are English.

## Historic data
`data/` holds the juleøl test history 1998-2019 (votes, results, sources: see `data/README.md` and `data/SOURCES.md`),
`source/google-sheets/` the raw sheet exports, `tools/` the scripts that rebuild it (`build_data.py`) and write the app's
`history/*.csv` from it (`to_app_history.py`). Standard library Python only.
