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
- Rule violations are HTTP 422 with a stable `code` (`OUTSIDE_SEASON`, `DAILY_LIMIT`, `SOLO_DAYS_EXHAUSTED`, `ALREADY_RATED`).
- Days are Europe/Oslo. Scores are integers 1–6.
- Schema changes go in a new Flyway migration; Hibernate runs with `ddl-auto: validate`.
- UI copy is Norwegian (bokmål); code and comments are English.
