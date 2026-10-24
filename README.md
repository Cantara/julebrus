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
that may flag raters and override weights). The Google OAuth redirect URI is
`https://<host>/login/oauth2/code/google`.

## Historic seasons

Earlier years are imported from CSV at startup. Put the wiki export in
`backend/src/main/resources/history/`, format described in the README there.

## Tests

```bash
cd backend && mvn test
```
