# Cantaras digitale juleølsmaking — design

Status: living document, decided points marked **[decided]** or dated. Season 2026 runs 1 Nov – 23 Dec.
Everything below the *Why* section describes what is built unless it says otherwise; the *Status* section at
the end says where the plan stands.

## Why

Cantara has run Christmas beer tastings for years (see wiki.cantara.no). The last
seasons had one recurring problem: the selection grew past what a single evening
can handle. With 40+ bottles on the table everyone gets a splash, palates fade
after the first dozen, and the late scores are noise.

The fix is to spread the tasting over the whole season and keep each sitting
small. Three rules do that:

| Rule | Value | Effect |
|------|-------|--------|
| Season window | 1 Nov – 23 Dec | Follows the Vinmonopolet juleøl release; final reveal 23 Dec |
| Max tastings per person per day | 5 | Real pours, fresh palate |
| ... at a table of 8 or more | 12 | A party is a different evening (decided 25 Oct). The 5 still holds for anything else that day |
| Max solo tasting days per person | 5 | Pushes tasting into groups, which is the point |
| What counts as a juleøl | From Vinmonopolet, above grocery strength (4.7 %) | Butikkøl is refused on add and import, and never ranked |
| Homebrew | Allowed above 4.7 %, rated only at a table of 6+, ranked only with 4+ scores | Nobody rigs their own |

## Decisions

- **Scale [decided]:** 1 to 6, whole numbers ("terningkast"). Same scale as earlier
  years, so results stay comparable.
- **Weights [decided]:** per rater per season, 0.5 / 1.0 / 0, see *Rater weights*.
- **Historic scores [decided]:** earlier seasons are imported with their original
  scale and each rater's weight, shown on the profile and on each beer.
- **Tasting notes [decided]:** optional structured notes (appearance, aroma, taste,
  finish) plus tags, for the professionals. The quick path stays dice and done.
- **Login [decided]:** Google account (OIDC). Optional allow-list of email domains.
- **Duels [decided]:** pairwise "which was better" questions are collected in group
  sessions and shown as a *sidebar* ranking. The official ranking is the 1–6 score.
- **Day:** a calendar day in Europe/Oslo.
- **Solo:** a tasting is solo when its session has exactly one member (or no
  session). A *solo day* is a calendar day on which the person has at least one solo
  tasting. The 5-solo-day cap counts distinct such days.
- **Per-day cap:** counts the person's tastings on that Oslo day across all
  sessions. The sixth rating is refused, whatever session it belongs to.
- **Window:** enforced server side. Nothing can be rated before 1 Nov or after 23 Dec.

## Rater weights

Every person has a weight for the season, decided by how their most recent season
went. Weights are per person, not per rating, and are fixed for the whole season.

| Last season | Weight this season |
|-------------|--------------------|
| Never tasted before | 0.5 |
| Fewer than 7 scores logged | 0.5 |
| 7 or more scores, nothing odd | 1.0 |
| Flagged for rigging (6 to everything from Bergen, 1 to the rest) | 0 |

A weight of 0 is not a ban. The person joins, rates, sees reveals and earns
badges like everyone else. Their scores are simply left out of the averages and
the ranking. After a normal season they are back at 1.0.

Weights are keyed by email so seasons before the app count: the historic import
carries each rater's weight for that year in its `weight` column, and
`history/raters.csv` joins the name variants of one person ("Eirik", "Eirik L")
and attaches their login email.

- Official ranking uses weighted means. `ratings` counts people, `effectiveVotes`
  is the sum of their weights.
- The blind reveal inside a group session shows plain, unweighted scores. That is a
  social moment, not the official result.
- Everyone can see their own weight and the reason on their profile.
- Flagging is a human decision. `/api/admin/raters` lists every rater with two
  signals: share of scores that are 1 or 6, and the largest brewery bias (mean for
  one brewery minus the person's own mean, breweries with 3+ scores). Admins
  (`JULEBRUS_ADMINS`) flag a season or override a weight from there.

## Group sessions and matching

1. A host opens a session. The app shows a QR code (join URL with a short-lived
   token) and a 6-character code for dim kitchens and dead cameras.
2. Others scan or type the code and become members.
3. The host adds beers to the session. Every member rates each beer on their own
   device, within their own per-day cap.
4. **Locked blind reveal:** scores in a session are hidden until every member has
   rated that beer. Then they are revealed together. No anchoring on the loudest voice.
   Together means together: every write at the table (a score, a duel, a join, a beer, a
   blind guess) is pushed to the members over server-sent events (`/api/sessions/{id}/events`),
   and every phone refetches at once, so the paper tears off on all of them in the same
   second. A slow poll and a refetch when the tab comes back cover a stream that died.
5. After two or more beers, each member answers duel questions: "A or B?". Duels
   feed a Bradley–Terry ranking that ignores how generous or stingy each rater is.
6. Optional prediction step: guess the group average before the reveal. Accuracy
   feeds the Sommelier badge.
7. **Blind pour:** the host pours one of the beers on the table without saying which,
   picks 2–6 candidates and marks the answer (only the host sees that). The other
   members guess once each; the host never guesses. Who has guessed is visible, the
   picks and the answer only once the last guest has guessed, the host reveals, or the
   table closes. One open round per table. Three correct guesses in a season give the
   Blind Taster badge; most correct guesses (min. 3 guesses, ties by hit rate) is a
   finale award.

## Teams (decided 24 Oct)

A person plays for one or more teams. Right after consent, once, the app asks "Hvilket lag spiller du
for?": Cantara and Rebel are seeded, "Et annet lag?" opens a mail to the contact address (other teams
are enabled by an organiser against a contribution to the tasting; no billing in the app), and "Velg
senere" answers the question without a team. The e-mail domain suggests a team (cantara.no → Cantara)
but never decides. The choice is changeable on the profile; admins create, edit and enable teams there.

What a team changes: team pills next to names around the table; a team filter on the ranking (the same
Bayesian ranking over that team's raters only, with the team's own mean as prior); in the finale each
team's own number one and the award "Årets lag" (most tastings per member, so a small team can win);
an MCP tool `team_ranking`; a roster page per team (`/lag/<slug>`: names and rolls, most active first).
Membership is visible to other tasters like the display name; the consent text says so since 2026-5.

## Tipping the podium (decided 24 Oct)

Everyone may tip the season's podium (gold, silver, bronze among the beers that count), one tip
per person, changeable or withdrawable until the last day begins (midnight before the reveal,
Oslo time: `SeasonProperties.tipDeadline()`). Scoring at the reveal against the official podium:
3 points for a beer in its right spot, 1 for a beer on the podium in the wrong spot, 9 is perfect
(`TipScoring`, pure). Before the reveal the finale only says how many have tipped; after it the
whole league is shown, best first, earlier tip ahead on a tie, and "Årets tipper" joins the
awards (at least one point).

## Push notifications (decided 24 Oct)

Opt-in per browser. A daily nudge at 17:00 Oslo time to anyone who has not opened that day's
door, once per day per browser, during the season only; and one "Stjernen er tent" at the reveal
to browsers subscribed before it (`PushPlanner`, pure). Web Push with VAPID keys from the
environment; the message encryption is RFC 8291 on the JDK, verified against the RFC's own test
vector. Subscriptions hold only what the browser hands over (endpoint, its public key, its auth
secret) and are deleted on unsubscribe, on 404/410 from the push service, or after five failures.

## "I kveld", the day's wall (decided 25 Oct)

Everyone sees one wall for the whole house, one Oslo day at a time: who rolled which beer (at which
table, when there was one), which tables opened and whether they are still going, which labels were
approved, how each blind round went, newest first, live over the same SSE machinery as the tables
(`/api/feed/events`). A person's dice never appear on it: scores belong to the table until the reveal
and to the ranking after it. The wall gives the day's average per beer only from three rolls, so no
single roll can be read off it. Interaction is a fixed set of emojis (🍻 first, then 🎅 🔥 😂 👏),
one of each per person per line, tap again to take it back: community without free text, so nothing
to moderate. People without active consent are left out, like everywhere else. The consent text
names the wall, the teams line it owed since October, and bumps to 2026-5.

## Statistics

Built:

- **Beers** (`/stats`, `/api/stats/beers`): weighted Bayesian average (prior = weighted global mean, prior weight
  5 votes), people count and effective votes, filterable per team (the team's own mean as prior). Beers that do
  not count (grocery strength, pending, homebrew under four scores) are out of the list.
- **Duels** (`/api/stats/duels`): Bradley–Terry over the pairwise answers, shown as a sidebar ranking.
- **Ganen din** (profile, `/api/stats/palate/me`, `PalateMath` pure): tastings and own mean, the lift of each tag
  against the person's own mean (likes and dislikes), and the taste twin and taste opposite among the others
  (cosine-like similarity over shared beers, from `minCommon` beers in common). The finale lists everyone's twins
  and opposites (`/api/stats/palate/pairs`).
- **History** (`/historikk`): per season and per beer, the old results with their weighting, "you against the
  crowd" for linked people, and the admin's rigging signals (`/api/admin/raters`: share of 1s and 6s, largest
  brewery bias).
- **The day** ("I kveld"): rolls, people and tables today, day average per beer from three rolls.
- **Final result:** 23 Dec 18:00, "Cantaras juleøl 2026", with the duel podium, team winners, the tipping league,
  the twins and the season's awards.

Not built, and not missed so far: an activity heatmap over the season, a co-tasting graph, a time-of-day
histogram. The advent calendar on Home covers the first for the person themselves.

## Badges

Computed from the tasting log, so they can be added or re-tuned mid-season.

| Badge | Rule |
|-------|------|
| First Blood | First tasting of the season |
| Full Week | Tasted on 7 consecutive days |
| Explorer | Rated a beer fewer than 3 others had rated at the time |
| Lone Wolf | All 5 solo days used |
| Social Butterfly | 10 distinct co-tasters |
| Contrarian | Largest mean deviation from the crowd (min. 10 ratings) |
| Advent | Rated every day 1 – 23 Dec |
| Sommelier | Best prediction accuracy (min. 10 predictions) |
| Closing Time | A rating on 23 Dec |
| Blind Taster | Named the beer in a blind pour 3 times |

The home screen shows "least tasted beers" so the long tail gets covered.

## Architecture

```
frontend/   Vue 3 + TypeScript PWA (Vite), mobile first, installable
backend/    Spring Boot 3.5, Java 21, PostgreSQL, Flyway, Google OIDC
```

- Backend serves `/api/**` and, in production, the built frontend as static files.
- Rules live in `TastingRules`, a pure class with no Spring or JPA, unit tested.
- Ranking math (`BayesianAverage`, `BradleyTerry`) is pure as well.
- `dev` profile: H2 in memory, security replaced by a fixed dev user, so the app runs
  without Google credentials or a database.
- Beer catalogue: manual entries (pending until an admin approves, admins' own approved at once) and the
  admin CSV import of a Vinmonopolet list; no product API, see *Product data* below.
- MCP server at `/mcp` (MCP Java SDK, Streamable HTTP) with read-only tools over the
  history and the running season, guarded by one shared bearer token
  (`JULEBRUS_MCP_TOKEN`; unset means the endpoint answers 404). It sits beside the
  DispatcherServlet, so login, consent and CSRF do not apply; the tools only expose
  what the web app already shows to everyone logged in, and never e-mails or
  per-person scores from the running season.

### Data model (V1–V13)

```
users             id, email, display_name, created_at, teams_chosen_at
beers             id, name, brewery, style, abv, vinmonopolet_id, image_url, created_at,
                  status PENDING|APPROVED|REJECTED, created_by?, decided_by?, decided_at?, homebrew
sessions          id, host_id, join_code, created_at, closed_at?
session_members   session_id, user_id, joined_at
session_beers     session_id, beer_id, added_at
tastings          id, user_id, beer_id, session_id?, score 1..6, prediction?, notes,
                  appearance, aroma, taste, finish, tasted_at, tasted_day (Oslo date)
                  unique (user_id, beer_id)
tasting_tags      tasting_id, tag
duels             id, user_id, session_id, winner_beer_id, loser_beer_id, created_at
blind_rounds      id, session_id, host_id, answer_beer_id, created_at, revealed_at?
blind_round_options  round_id, beer_id, position
blind_guesses     round_id, user_id, beer_id, guessed_at          (one per person per round)
teams             id, slug, name, sponsor_name?, sponsor_url?, domains?, enabled, created_at
team_members      team_id, user_id, joined_at
tips              user_id (pk), first_beer_id, second_beer_id, third_beer_id, created_at, updated_at
beer_photos       id, beer_id, user_id, status, content_type, width, height, size_bytes,
                  data (bytea, read only by PhotoStore), created_at, decided_by?, decided_at?, deleted_at?
push_subscriptions id, user_id, endpoint, p256dh, auth, user_agent?, created_at, last_nudge_day?,
                  finale_sent_at?, failures
feed_reactions    id, target_kind, target_id, user_id, emoji, created_at   unique (target_id, user_id, emoji)
rater_weights     id, season_year, email, weight 0..1, reason, flagged, flag_note?, overridden
user_consents     id, user_id, consent_version, accepted_at, withdrawn_at?   unique (user_id, version)
consent_events    id, user_id, consent_version, event ACCEPT|WITHDRAW, at     (append-only)
historic_rater_claims  id, user_id, rater_key, status, created_at, decided_by?, decided_at?, approved_key?, version
claim_events      id, claim_id, event, actor_user_id, at
historic_seasons  season_year, title, scale_min, scale_max, weighting_description, source_url
historic_ratings  id, season_year, beer_name, beer_name_original?, brewery?, beer_id?, rater_name,
                  rater_key?, rater_email?, score (original scale), weight (rater's that year), notes?
```

Hibernate validates the schema against the entities (`ddl-auto: validate`); every change is a new Flyway
migration. Badges are not stored: `BadgeEvaluator` recomputes them from the tasting log.

### API

Everything under `/api/**` needs a Google login (the dev profile impersonates with `X-Dev-User`) and accepted
consent, except `/api/me`, `/api/consent*`, `/api/config` and `/api/privacy`. State-changing calls need the CSRF
cookie. Admin routes also need the e-mail in `JULEBRUS_ADMINS`.

```
# person
GET  /api/me                         who, today's count and the limit that applies, solo days, weight, teams, open table
GET  /api/consent  POST /accept /withdraw   the texts, accept the current version, withdraw
GET  /api/config                     public: sponsor line
GET  /api/privacy  GET /privacy      public: consent texts as JSON / server-rendered HTML
GET  /api/teams  GET /api/teams/{slug}  PUT /api/me/teams   the enabled teams, one roster, my choice

# beers and tastings
GET  /api/beers?q=  POST /api/beers  GET /api/beers/least-tasted   catalogue, manual add (PENDING unless admin)
POST /api/tastings                   { beerId, sessionId?, score, prediction?, notes?, appearance.., tags }
GET  /api/tastings/mine  GET /api/tastings/tags
POST /api/beers/{id}/photos  GET /api/beers/{id}/photo  GET /api/beers/photos/{id}/image  DELETE /api/beers/photos/{id}

# tables
POST /api/sessions  POST /api/sessions/join { code }  GET /api/sessions/mine
GET  /api/sessions/{id}              members, beers, reveal state, scores and notes once revealed
POST /api/sessions/{id}/beers  POST /api/sessions/{id}/close
GET  /api/sessions/{id}/events       SSE, members only
POST /api/duels  GET /api/duels/pending/{sessionId}
GET  /api/sessions/{id}/blind  POST .../blind  POST .../blind/{roundId}/guess  POST .../blind/{roundId}/reveal

# the day, the game, the season
GET  /api/feed?day=  PUT /api/feed/reactions  GET /api/feed/events      "I kveld"
GET  /api/tips/me  PUT /api/tips  DELETE /api/tips                       podium tip
GET  /api/stats/beers?team=  GET /api/stats/duels  GET /api/stats/palate/me  GET /api/stats/palate/pairs
GET  /api/badges/me
GET  /api/finale                     the result after the reveal, the countdown before
GET  /api/push/config /me  POST|DELETE /api/push/subscriptions  POST /api/push/test

# history
GET  /api/history/seasons /seasons/{year} /beers?name= /raters /overview /me /me/summary
GET  /api/history/claim-candidates  POST /api/history/claims  GET /claims/me  DELETE /claims/{id}

# admin
GET  /api/admin/raters  POST /raters/flag  POST /raters/override
GET  /api/admin/beers  POST /api/admin/beers/{id}/decision  POST /api/admin/beers/import (CSV)
GET  /api/admin/photos?status=  POST /api/admin/photos/{id}/decision
GET  /api/admin/history/claims  POST .../{id}/decision  POST .../{id}/revoke
GET  /api/admin/history/raters /logins  POST /link  POST /unlink
GET  /api/admin/teams  POST /api/admin/teams  POST /api/admin/teams/{id}
GET  /api/push/keygen

# outside the web app's gates
/mcp                                 MCP (Streamable HTTP), bearer token only, read-only tools
/  /velkommen  /welcome  /landing    public landing page (anonymous visitors on /)
```

Rule violations return HTTP 422 with a machine-readable `code` (`OUTSIDE_SEASON`, `DAILY_LIMIT`,
`SOLO_DAYS_EXHAUSTED`, `ALREADY_RATED`); every other refusal carries its own code too, listed in CLAUDE.md.

## Product data, images, barcode and photos (decided 24 Oct)

**Vinmonopolet lists.** The yearly juleøl lists live in `data/vinmonopolet/<year>.csv`: product facts only
(article number, name, range, category, country, ABV, volume, price that year), each year with its source in
`data/vinmonopolet/README.md`. 2013 is in; other years are added as they are found. Their terms of use are read
before the lists are published, and nothing but facts is stored.

**Images: our users' photos, not Vinmonopolet's.** Product photos belong to Vinmonopolet or the producers, so neither the
repository nor the database holds a copy of theirs, and the app does not hotlink them (no visitor IP goes to a third
party). The images come from the people tasting: before tasting, a person may take a photo of the beer's label.
The photographer owns the copyright of their own photo, so the consent text carries a licence line ("you allow the tasting
to show it"); the label artwork in the picture is the producer's, which is normal for a casual label photo in a community app.
Old lists (`data/vinmonopolet/<year>.csv`) therefore get no image column.

**Matching a beer.** Today: search by name. Next (after the season starts): scan the barcode in the browser (the browser's
own detector where it exists, a small library elsewhere), nothing stored; it maps to a product when the product data carries
the code (to be checked). Recognising beers from photos is not planned: it would send users' photos to a third party.

**User photos (v0.5, decided 24 Oct).** Optional, never required to rate; offered before tasting. A plain file input
(`accept="image/*" capture="environment"`). Stored in PostgreSQL (a resized photo is about 60-100 KB; a season gives a few
hundred), not S3: no new infrastructure before the season. Server side: limits on size and pixel dimensions BEFORE decoding,
only JPEG and PNG, re-encoded and resized to about 800 px (this removes GPS and device metadata), a per-person quota.
Served through the app with login, never as public links. An admin approves each photo before others see it (the uploader
sees their own at once; admins' uploads are auto-approved); the first approved photo becomes the beer's image. The consent and
privacy text covers the licence, storage, who sees it and deletion, and the manual deletion procedure in the README includes
the photos. A move to S3 later is possible if the volume asks for it.

**Order.** v0.5 (about 30-31 Oct, one consent bump): the small fixes from the last reviews, the consent-text answers from
Cantara, the photo feature, and the 2026 Vinmonopolet list imported as DATA through the admin CSV import (the owner downloads
the official list by hand on the day it is published; no API key and no release needed for the list itself). If the photo review
finds a blocker that cannot be fixed in time, v0.5 ships without photos and they follow as v0.5.1. Then barcode scanning.

## Plan and status

Written 24 Oct: phase 1 (login, catalogue, tables with QR, rating with the rules, blind reveal, results) by
1 Nov; phase 2 (duels, stats, badges) in November; phase 3 (predictions, finale page) in December.

Status 25 Oct, before the season: all three phases are in, plus what nobody planned in October: the live
reveal, the blind pour, teams, the podium tip, push notifications, label photos, the day's wall, the public
landing page, the MCP server and the imported history with claim-your-name. Left for later, in rough order of
how much they would add: the barcode scanner, the 2026 Vinmonopolet list as data, a dice histogram per beer,
an evening summary to share, dark mode, team colours. The framework majors (Spring Boot 4, Vite 8, Vue Router 5,
Pinia 4, TypeScript 7) wait until after 23 December.

## Open questions

- Should half points (4.5) be allowed? Current answer: no, stay with the dice.
- Allowed email domains: cantara.no only, or also partner domains?
- Vinmonopolet API key? Not needed for the 2026 list (official list download + the admin CSV import) and not for images
  (user photos). Only worth it if barcode scanning needs the product's GTIN.
- Does Vinmonopolet's product data carry the barcode (GTIN)? Decides whether barcode scanning can match beers.
- Emails for the historic raters: `history/raters.csv` maps names only. People link themselves under *Meg*
  (an admin approves), and an admin can link or unlink a historic name to a login under *Admin*. Whether to
  seed the file with known e-mails as well is still open.
