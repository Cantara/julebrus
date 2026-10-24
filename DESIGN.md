# Cantaras digitale juleølsmaking — design

Status: draft, decided points marked **[decided]**. Season 2026 runs 1 Nov – 23 Dec.

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
| Max solo tasting days per person | 5 | Pushes tasting into groups, which is the point |

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
5. After two or more beers, each member answers duel questions: "A or B?". Duels
   feed a Bradley–Terry ranking that ignores how generous or stingy each rater is.
6. Optional prediction step: guess the group average before the reveal. Accuracy
   feeds the Sommelier badge.

## Statistics

- **Beers:** weighted Bayesian average (prior = weighted global mean, prior weight
  = 5 votes), people count and effective votes, controversy (weighted standard
  deviation), duel ranking (sidebar), style and brewery breakdowns.
- **People:** tastings, generosity (mean offset vs. crowd), consistency (variance vs.
  own mean), prediction accuracy, solo days used of 5.
- **Activity:** heatmap 1 Nov – 23 Dec, co-tasting graph, time-of-day histogram.
- **Final result:** 23 Dec, "Cantaras juleøl 2026", with the duel ranking beside it.

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
- Beer catalogue: seeded from the Vinmonopolet product API filtered to juleøl,
  plus manual entries for homebrew and imports (phase 2; manual entry in phase 1).

### Data model

```
users            id, email, display_name, created_at
beers            id, name, brewery, style, abv, vinmonopolet_id, image_url
sessions         id, host_id, join_code, created_at, closed_at
session_members  session_id, user_id, joined_at
session_beers    session_id, beer_id, added_at
tastings         id, user_id, beer_id, session_id?, score 1..6, prediction?, notes,
                 appearance, aroma, taste, finish, tasted_at, tasted_day (Oslo date)
                 unique (user_id, beer_id)
tasting_tags     tasting_id, tag
duels            id, user_id, session_id, winner_beer_id, loser_beer_id, created_at
rater_weights    id, season_year, email, weight 0..1, reason, flagged, flag_note
historic_seasons year, title, scale_min, scale_max, weighting_description, source_url
historic_ratings id, season_year, beer_name, brewery, beer_id?, rater_name,
                 rater_email?, score (original scale), weight (rater's that year), notes
```

### API (phase 1)

```
GET  /api/me                      current user, today's count, solo days used
GET  /api/beers?q=                catalogue, with rating counts
POST /api/beers                   manual add
POST /api/sessions                open a session (returns id, joinCode)
POST /api/sessions/join           { code }
GET  /api/sessions/{id}           members, beers, reveal state per beer
POST /api/sessions/{id}/beers     { beerId }
POST /api/sessions/{id}/close
POST /api/tastings                { beerId, sessionId?, score, prediction?, notes }
POST /api/duels                   { sessionId, winnerBeerId, loserBeerId }
GET  /api/stats/beers             Bayesian ranking
GET  /api/stats/duels             Bradley–Terry ranking (sidebar)
GET  /api/badges/me
GET  /api/tastings/mine           this season, with notes and tags
GET  /api/history/seasons         earlier seasons and how they were weighted
GET  /api/history/me              my scores from earlier years, original and on 1–6
GET  /api/history/beers?name=     what earlier years said about a beer
GET  /api/admin/raters            rigging signals, admins only
POST /api/admin/raters/flag       { email, seasonYear?, flagged, note }
POST /api/admin/raters/override   { email, seasonYear?, weight, reason }
```

Rule violations return HTTP 422 with a machine-readable `code`:
`OUTSIDE_SEASON`, `DAILY_LIMIT`, `SOLO_DAYS_EXHAUSTED`, `ALREADY_RATED`.

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

## Plan

Today is 24 Oct. Phase 1 must be live by 1 Nov.

1. **Phase 1 (by 1 Nov):** login, catalogue, sessions with QR, rating with rule
   enforcement, blind reveal, plain results list.
2. **Phase 2 (November):** duels and sidebar ranking, stats pages, heatmap, badges.
   Badges are computed retroactively, so nothing is lost by shipping them late.
3. **Phase 3 (December):** predictions, co-tasting graph, final reveal page for 23 Dec.

## Open questions

- Should half points (4.5) be allowed? Current answer: no, stay with the dice.
- Allowed email domains: cantara.no only, or also partner domains?
- Vinmonopolet API key? Not needed for the 2026 list (official list download + the admin CSV import) and not for images
  (user photos). Only worth it if barcode scanning needs the product's GTIN.
- Does Vinmonopolet's product data carry the barcode (GTIN)? Decides whether barcode scanning can match beers.
- Emails for the historic raters in `history/raters.csv`, so people see their own
  history. Only the names are mapped so far.
