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
  - `live/SessionEvents` — one SSE stream per table (`/api/sessions/{id}/events`, members only). Writes to a session call `events.publish(sessionId, Kind)`, which waits for the commit when inside a transaction; events carry only their kind, the client refetches. Heartbeat comments every 20 s; `X-Accel-Buffering: no` for proxies. Under `dev`, `?devUser=` does what `X-Dev-User` does, because EventSource cannot set headers. Test over a real port (`LiveRevealTest`).
  - `blind/` — blind pour rounds under `/api/sessions/{id}/blind` (`BlindService` holds the rules: host only, 2–6 options from the table, one open round per table, one guess each, host never guesses, auto-reveal on the last guest's guess, session close reveals the rest); `BlindTally` is the pure per-person score used by the Blind Taster badge and the finale award.
  - `team/` — teams (Cantara and Rebel seeded in V7, more via `admin/AdminTeamsController`). `TeamService.choose` sets a person's teams and stamps `users.teams_chosen_at`, so the router sends anyone with `teamsChosen: false` to `/lag` once after consent; an empty choice ("Velg senere") counts. `RankingService.beerRanking(Set<UUID>)` is the team-filtered ranking behind `/api/stats/beers?team=` and the finale's `teamWinners`.
  - `tip/` — the podium tipping game: `TipService` (three distinct beers that `counts()`, one tip per person, nothing after `SeasonProperties.tipDeadline()`, the start of the last day), `TipScoring` (pure: 3 per right spot, 1 per podium beer in the wrong spot), `/api/tips/me`, `PUT`/`DELETE /api/tips`. The league and the "Årets tipper" award are built in `FinaleService` at the reveal.
  - `push/` — Web Push: `WebPushCrypto` (RFC 8291 aes128gcm on the JDK, test vector in `WebPushCryptoTest`), `Vapid` (ES256 JWT), `WebPushClient`, `PushService` (`@Scheduled` every minute; `PushPlanner` is the pure rule for the daily nudge and the finale message), `/api/push/config|me|subscriptions|test`, admin `/api/push/keygen`. Off until `julebrus.push.vapid-*` are set (`PUSH_OFF` 404). The frontend side is `api/push.ts`, `components/PushSettings.vue` and `public/push-sw.js`, which workbox imports into the generated service worker.
  - `duel/BradleyTerry`, `stats/BayesianAverage` — pure ranking math.
  - `badge/BadgeEvaluator` — pure badge rules over a tasting log.
  - `history/` — CSV import of earlier seasons and their weighting.
  - `consent/` — versioned consent (current state in `user_consents`, append-only `consent_events`); `ConsentInterceptor` answers 403 `CONSENT_REQUIRED` on all `/api/**` except `/api/me`, `/api/consent*`, `/api/config`, `/api/privacy` (public texts); `PrivacyPageController` serves `/privacy` as server-rendered HTML. Texts in `resources/consent/`.
  - `history/ClaimService`, `RaterNameSuggester` (pure) — claim-your-history on `rater_key` (create/withdraw run under a row lock on the user; `claim_events` is the history; claims have `@Version`; approving re-checks e-mail linkage and auto-rejects other pending claims; time comes from the injected `Clock`); admin decide/revoke in `admin/AdminClaimsController`.
  - `photo/` — label photos in PostgreSQL (`beer_photos`, V9). `ImageSanitizer` (pure) is the only door for bytes: magic bytes, header size check before decoding, sub-sampled decode, EXIF orientation, flatten, scale to 800 px, re-encode (strips all metadata). `PhotoStore` (JDBC) is the only code that writes the table or reads `data`; the JPA entity `BeerPhoto` does not map `data`. `PhotoService`: quotas (5/day, 30/season, 1 pending per beer, admins exempt from day/season) under `users.lockById`, cover rule, decisions by one conditional update, delete = wipe bytes + `deleted_at` (row stays as quota ledger). Admin queue in `admin/AdminPhotosController`. Limits: `spring.servlet.multipart` 6 MB, 8000 px / 24 MP.
  - `beer/BeerService` — beer creation under a lock on the person: pending cap (10), duplicate name per person, homebrew/grocery-strength rules. `NewBeer` carries `@Size`/`@Pattern`/abv bounds.
  - `web/ConfigController` — public `/api/config` (sponsor line).
  - `config/SecurityConfig` — Google chain (`!dev`) and dev chain with `X-Dev-User` impersonation.
  - `mcp/` — MCP server (Streamable HTTP) at `/mcp`: `McpConfig` mounts the SDK servlet beside the DispatcherServlet (so no login, consent or CSRF there), `McpTokenFilter` is the whole access control (404 when `julebrus.mcp.token` is blank, else bearer token), `McpTools` the read-only tools. Keep tools to aggregates and public history; never emails or per-person season scores. Test over a real port (`McpServerTest`), MockMvc does not reach it.
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
- Rule violations are HTTP 422 with a stable `code` (`OUTSIDE_SEASON`, `DAILY_LIMIT`, `SOLO_DAYS_EXHAUSTED`, `ALREADY_RATED`). Other errors carry their own codes too: `CONSENT_REQUIRED`, `CONSENT_VERSION_MISMATCH`, `RATER_NOT_FOUND`, `CLAIM_EXISTS`, `RATER_TAKEN`, `CLAIM_LIMIT`, `CLAIM_NOT_PENDING`, `CLAIM_NOT_APPROVED`, `CLAIM_DAILY_LIMIT`, `CLAIM_REVOKED`, `CLAIM_NOT_FOUND`, `RATER_LINKED`, `NO_ACTIVE_CONSENT` (admin links a person without active consent), `BEER_REJECTED`, `BEER_DUPLICATE`, `BEER_PENDING_LIMIT`, `PHOTO_FORMAT`, `PHOTO_TOO_LARGE`, `PHOTO_DIMENSIONS`, `PHOTO_UNREADABLE`, `PHOTO_MISSING`, `PHOTO_DAILY_LIMIT`, `PHOTO_SEASON_LIMIT`, `PHOTO_PENDING_EXISTS`, `PHOTO_NOT_PENDING`, `PHOTO_NOT_FOUND`, `PHOTO_BUSY`, `PHOTO_IN_PROGRESS` (429), `CONFLICT` for a lost unique-constraint race, and the blind pour's `BLIND_NOT_HOST` (403), `BLIND_NEEDS_GUESTS`, `BLIND_ROUND_OPEN`, `BLIND_OPTIONS`, `BLIND_ANSWER`, `BLIND_HOST_GUESS`, `BLIND_ALREADY_GUESSED`, `BLIND_NOT_OPTION`, `BLIND_REVEALED`, `BLIND_NO_GUESSES`, `BLIND_NOT_FOUND` (404), the teams' `TEAM_NOT_FOUND` (404 on lookup, 422 on choosing a disabled one) and `TEAM_EXISTS` (409), the tipping game's `TIP_CLOSED`, `TIP_DUPLICATE`, `TIP_BEER`, and push's `PUSH_OFF` (404), `PUSH_SUBSCRIPTION` (422).
- `julebrus.auth.admins` (`JULEBRUS_ADMINS`) has no default: empty means no admins. The consent version is `2026-4` (photos); bump it when the texts change.
- Days are Europe/Oslo. Scores are integers 1–6.
- A beer counts only if it is from Vinmonopolet, i.e. above grocery strength (`Beer.GROCERY_MAX_ABV` = 4.7 %); `Beer.counts()` is what the rankings use. Non-admin additions are PENDING until approved. Homebrew (`Beer.homebrew`) must state an ABV above that, is rated only in a session of `HOMEBREW_MIN_GROUP` (6) members (`HOMEBREW_NEEDS_GROUP`), and ranks only with `HOMEBREW_MIN_RATINGS` (4) scores.
- Schema changes go in a new Flyway migration; Hibernate runs with `ddl-auto: validate`.
- UI copy is Norwegian (bokmål); code and comments are English.

## Historic data
`data/` holds the juleøl test history 1998-2019 (votes, results, sources: see `data/README.md` and `data/SOURCES.md`),
`source/google-sheets/` the raw sheet exports, `tools/` the scripts that rebuild it (`build_data.py`) and write the app's
`history/*.csv` from it (`to_app_history.py`). Standard library Python only.
