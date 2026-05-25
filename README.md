# EventPulse

A personalized feed aggregator that pulls items from Hacker News, Reddit,
TheSportsDB, and YouTube, classifies them against a fixed interest taxonomy,
and ranks the result for each signed-in user using their engagement history
plus a global trending signal.

> **Status**: pre-launch. Local dev works. Production profile validated in a
> Docker container against a real Postgres. First public deployment is in
> progress (Koyeb + Neon free tier).

See [`SPEC.md`](./SPEC.md) for the product specification and
[`DATA_MODEL.md`](./DATA_MODEL.md) for entity-level schema details.

---

## Table of contents

1. [What it does](#what-it-does)
2. [Tech stack](#tech-stack)
3. [Architecture](#architecture)
4. [Ranking algorithm](#ranking-algorithm)
5. [Schema overview](#schema-overview)
6. [Routes](#routes)
7. [Local development](#local-development)
8. [Running tests](#running-tests)
9. [Configuration profiles](#configuration-profiles)
10. [Environment variables](#environment-variables)
11. [Building the Docker image](#building-the-docker-image)
12. [Production deployment](#production-deployment)
13. [Operations](#operations)
14. [Project layout](#project-layout)
15. [Conventions and gotchas](#conventions-and-gotchas)

---

## What it does

1. **Aggregates** items on a schedule from four free sources:
   - **Hacker News** (`/topstories.json`, every 30 min)
   - **Reddit** (`/r/<sub>/top.json`, every hour)
   - **TheSportsDB** (free tier, every hour)
   - **YouTube Data API v3** (curated channels, every hour)
2. **Classifies** each item by matching its title against a curated set of
   keyword aliases (`feed_topic_aliases`, seeded by `V3`–`V5`, `V7`).
   Items without a matching alias are dropped — we only surface things that
   fit our taxonomy.
3. **Personalizes** the feed per user with three weighted signals plus
   recency. See [Ranking algorithm](#ranking-algorithm).
4. **Searches** with Postgres full-text search (`tsvector` + `ts_rank_cd`
   + GIN index, V6 migration).
5. **Tracks** engagement (view, click, save, hide) which feeds back into
   the ranking next request.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 3.5.6 |
| Web | Spring MVC + Thymeleaf (server-rendered HTML) |
| Auth | Spring Security + OAuth2 Client (Google) |
| Persistence | Spring Data JPA / Hibernate 6.6.29 |
| Database | PostgreSQL 16 |
| Migrations | Flyway 11 (with `flyway-database-postgresql`) |
| Build | Maven 3.9 |
| Test | JUnit Jupiter + AssertJ + Spring Boot Test |
| Container | Multi-stage `Dockerfile` (Maven build → JRE runtime) |

No frontend build pipeline — Thymeleaf templates render directly. There's
no JavaScript bundler, no Node, no npm.

## Architecture

```
                  ┌──────────────────────────┐
                  │  Scheduled aggregators   │
                  │   HN / Reddit / Sports   │
                  │       / YouTube          │
                  └────────────┬─────────────┘
                               │ ingest (transactional)
                               ▼
┌────────────┐         ┌──────────────────────┐
│  Browser   │ HTTPS   │  Spring Boot         │
│ (Thymeleaf │◄───────►│  - controllers       │
│  pages)    │         │  - services          │
└────────────┘         │  - repositories      │
                       │  - SecurityConfig    │
                       └─────────┬────────────┘
                                 │ JDBC
                                 ▼
                       ┌──────────────────────┐
                       │     PostgreSQL 16    │
                       │  - tsvector (FTS)    │
                       │  - GIN/B-tree idx    │
                       │  - 3-axis ranking    │
                       └──────────────────────┘
```

Aggregators run inside the Spring app on a `@Scheduled` cron. They funnel
all writes through `FeedService.upsertBySource(...)` which is idempotent on
the `(source, source_id)` unique constraint, so polling the same item
twice is a no-op.

Ranking is done **in the database**, not in app code, via a single native
query with three `WITH ... AS MATERIALIZED` CTEs that aggregate
`user_engagement` once per request. See `FeedItemRepository.java`.

## Ranking algorithm

The personalized feed mixes three engagement-derived signals plus recency.

| Axis | Window | Scope | Effect |
| --- | --- | --- | --- |
| **Source weight** | 30 days | Per-user, per-source | Sources you save rise; sources you hide sink |
| **Per-interest weight** | 30 days | Per-user, per-interest (averaged across this item's interests) | A music HIDE penalises music-tagged items but not gaming-tagged ones from the same source |
| **Trending** | 24 hours | All users, per-item | Globally hot items surface; gives cold-start users a meaningful signal |

**Action weights**: `save = +2.0`, `click = +0.5`, `hide = -3.0`.

Total ordering:

```
(source_weight + per_interest_avg + trending) DESC,
 published_at DESC NULLS LAST,
 fetched_at DESC
```

For a brand-new user with no history and no globally-trending matches, all
three weights resolve to `0.0` via `coalesce` and ordering collapses to
recency. This case is pinned by `FeedItemRepositoryColdStartTest`.

**Performance**: at 10k feed_items the original CTE-less query took ~13 s
for the unfiltered feed. Adding `MATERIALIZED` to the three weight CTEs
forces single-execution and brings it to ~150 ms.

| Query | Before MATERIALIZED | After |
| --- | --- | --- |
| Q1 unfiltered feed | 13 s | 147 ms |
| Q2 source+interest filter | 954 ms | 12 ms |
| Q3 FTS search | 2.6 s | 42 ms |

`SELECT DISTINCT` interacts badly with `ORDER BY` on subquery expressions
(SQLSTATE 42P10) — the queries use `EXISTS` instead.

## Schema overview

All migrations are in `src/main/resources/db/migration/`:

| Migration | What it does |
| --- | --- |
| `V1__init_schema.sql` | Core tables: users, interests, feed_items, feed_item_interests, user_engagement, user_interests, events, event_summaries, feed_topic_aliases |
| `V2__seed_interests.sql` | Seed taxonomy (5 categories: SPORT, MOVIE, TV, MUSIC, GAMING) |
| `V3__seed_hn_topic_aliases.sql` | HN keyword aliases |
| `V4__seed_sportsdb_topic_aliases.sql` | SportsDB aliases |
| `V5__seed_reddit_topic_aliases.sql` | Reddit subreddit aliases |
| `V6__add_feed_items_fts.sql` | `search_vector tsvector` generated column + GIN index |
| `V7__seed_youtube_topic_aliases.sql` | YouTube channel aliases |
| `V8__add_engagement_feed_item_index.sql` | Index serving the trending CTE |

Foreign-key cascades worth knowing:

- `feed_item_interests.feed_item_id` → `feed_items.id` `ON DELETE CASCADE`
- `user_engagement.feed_item_id` → `feed_items.id` `ON DELETE CASCADE`

The retention job (`FeedItemRetentionJob`) only deletes items that no
user has engaged with, otherwise the cascade would silently wipe the
user's Saved/Hidden views.

## Routes

| Method | Path | Purpose |
| --- | --- | --- |
| `GET`  | `/` | Landing page (logged out) / redirect to `/feed` (logged in) |
| `GET`  | `/login` | Google OAuth login |
| `GET`  | `/feed` | Personalized feed (supports `?q=`, `?source=`, `?interest=`, `?page=`) |
| `GET`  | `/feed/saved` | Items the user has saved |
| `GET`  | `/feed/hidden` | Items the user has hidden |
| `GET`  | `/feed/{id}` | Detail view (loads `relatedEvent` if present) |
| `POST` | `/engagement/{action}/{feedItemId}` | Record click/save/hide/view |
| `POST` | `/engagement/{action}/{feedItemId}/undo` | Undo a save/hide |
| `GET`  | `/interests` | Manage interest subscriptions |
| `POST` | `/interests/{interestId}/subscribe` | Subscribe to an interest |
| `POST` | `/interests/{interestId}/unsubscribe` | Unsubscribe |
| `GET`  | `/ingest/{hn,reddit,sportsdb}` | Manual aggregator trigger (dev only) |

## Local development

### Prerequisites

- Docker Desktop (for the Postgres container)
- Java 21 (`brew install openjdk@21` on macOS)
- Maven 3.9+

### Quick start

```bash
./run-dev.sh                                              # starts/creates eventpulse-db
mvn spring-boot:run -Dspring-boot.run.profiles=dev        # runs the app
open http://localhost:9080
```

### `application-dev.yml`

This file is **gitignored** because it holds Google OAuth credentials and
the YouTube API key. Create it at
`src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/eventpulse
    username: postgres
    password: postgres

  security:
    oauth2:
      client:
        registration:
          google:
            client-id: <your-google-client-id>
            client-secret: <your-google-client-secret>
            scope: openid, profile, email

eventpulse:
  youtube:
    api-key: <your-youtube-api-key>
```

Get a Google OAuth client at
[console.cloud.google.com/apis/credentials](https://console.cloud.google.com/apis/credentials).
Authorized redirect URI: `http://localhost:9080/login/oauth2/code/google`.

Get a YouTube Data API v3 key in the same Google Cloud project (Library →
"YouTube Data API v3" → Enable → Credentials → Create API key). Free quota
is 10,000 units/day, well above what the aggregator uses.

## Running tests

The integration tests need a separate `eventpulse_test` database. Create
it once:

```bash
docker exec eventpulse-db psql -U postgres -c "CREATE DATABASE eventpulse_test OWNER postgres;"
```

Then:

```bash
mvn test
```

What's covered:

| Test | What it pins |
| --- | --- |
| `FeedItemRepositoryColdStartTest` | A new user with no engagement gets a recency-ordered feed (no errors when all three weight axes are 0) |
| `FeedItemRetentionJobTest` | The 90-day purge deletes untouched old items but preserves anything a user has engaged with |

Tests use `@Transactional` rollback for cleanup; Flyway runs against the
test DB on first boot. Configuration is in
`src/test/resources/application-test.yml`.

> **Why not Testcontainers?** The `pom.xml` already pulls in the
> Testcontainers BOM and the Postgres module, but Docker Desktop's `/info`
> endpoint is currently misbehaving on macOS — it returns HTTP 400 with
> an empty body, which Testcontainers can't recover from. Using a
> real-but-local DB until that's fixed.

## Configuration profiles

| Profile | File | Purpose |
| --- | --- | --- |
| `dev` (default) | `application-dev.yml` (gitignored) | Local development against `localhost:5432/eventpulse` |
| `test` | `src/test/resources/application-test.yml` | Integration tests against `localhost:5432/eventpulse_test` |
| `prod` | `src/main/resources/application-prod.yml` | Production — every secret bound to an env var |

Common defaults are in `src/main/resources/application.yml`:
`server.port=9080`, JPA `ddl-auto=validate` (Flyway owns the schema),
UTC time zone, Thymeleaf cache off.

## Environment variables

### Required in production

| Variable | Description |
| --- | --- |
| `DATABASE_URL` | JDBC URL, e.g. `jdbc:postgresql://host:5432/eventpulse?sslmode=require` |
| `DATABASE_USERNAME` | Postgres user |
| `DATABASE_PASSWORD` | Postgres password |
| `GOOGLE_CLIENT_ID` | OAuth 2.0 client ID |
| `GOOGLE_CLIENT_SECRET` | OAuth 2.0 client secret |

### Optional in production

| Variable | Default | Description |
| --- | --- | --- |
| `PORT` | `8080` | HTTP listen port (Koyeb/Render set this automatically) |
| `YOUTUBE_API_KEY` | empty | Disables the YouTube aggregator if unset |
| `SPRING_PROFILES_ACTIVE` | — | Set to `prod` |

### Google OAuth redirect URI

Whatever URL the deployed app lives at, register
`https://<that-url>/login/oauth2/code/google` as an authorized redirect
URI in Google Cloud Console. Without this, login breaks.

## Building the Docker image

The `Dockerfile` is a multi-stage build:

1. **Build stage**: `maven:3.9-eclipse-temurin-21` runs
   `mvn -DskipTests package`. Maven's `~/.m2` is mounted as a build cache
   so subsequent rebuilds skip the dependency download (5+ min → ~10 s).
2. **Runtime stage**: `eclipse-temurin:21-jre` is JRE-only, ~250 MB
   smaller than the build image. Just the fat jar gets copied in.

```bash
docker build -t eventpulse:latest .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL="jdbc:postgresql://host.docker.internal:5432/eventpulse" \
  -e DATABASE_USERNAME=postgres \
  -e DATABASE_PASSWORD=postgres \
  -e GOOGLE_CLIENT_ID=... \
  -e GOOGLE_CLIENT_SECRET=... \
  eventpulse:latest
```

`.dockerignore` keeps `target/`, `.git/`, IDE configs, and
`application-dev.yml` out of the build context — never ship dev secrets
into the image.

## Production deployment

Target stack for v1: **Koyeb (app)** + **Neon (Postgres)**, both on free
tiers. Cold starts apply on both — first request after idle takes
~5–10 seconds.

Deployment steps live in the launch runbook (in chat history); the short
form:

1. Create a Neon project, copy its connection string.
2. Push this repo to GitHub.
3. Create a Koyeb service from the repo (Koyeb auto-detects the
   `Dockerfile`).
4. Set the environment variables listed above.
5. Add the Koyeb URL to Google OAuth's authorized redirect URIs.
6. Deploy. Flyway runs V1–V8 against the empty Neon DB on first boot.

Health check path for Koyeb: `/` (returns 302 unauthenticated, 200
authenticated — both count as up).

## Operations

### Retention

`FeedItemRetentionJob` runs daily at 03:00 server time. It deletes
`feed_items` where `fetched_at < now() - 90 days` AND no row in
`user_engagement` references the item. The `NOT EXISTS` guard is
load-bearing because `user_engagement` cascades on item deletion —
without it, saved/hidden items would silently disappear.

Expected steady-state size at default polling rates: ~20k feed_items,
~30 MB. Well below Neon's 0.5 GB free cap.

### Cold-start observation

Free-tier hosts (Koyeb free, Neon free) suspend after idle. First
request after suspension takes ~5–10 s while the container wakes and
the DB resumes. While the app is suspended, scheduled aggregators
**don't run** — the feed will be stale until someone visits.

### Monitoring

- **Neon dashboard**: storage usage, connection count. Set the email
  alert at 80% storage so you can prune before writes fail.
- **Koyeb logs**: tail in the dashboard, or `koyeb services logs`.
- **Application logs**: Spring Boot default at `INFO`, set
  `LOGGING_LEVEL_COM_EVENTPULSE` if you need DEBUG.

### Rollback

No rollback migrations are written. To revert:

1. Tag every release: `git tag prod-$(date +%Y%m%d)`.
2. To roll back app code only: `git revert` and redeploy.
3. To roll back schema: hand-write the inverse SQL — Flyway forward
   migrations only.

## Project layout

```
src/main/java/com/eventpulse/
├── EventPulseApplication.java     # @SpringBootApplication, @EnableScheduling
├── aggregator/                    # Per-source polling clients
│   ├── hn/
│   ├── reddit/
│   ├── sportsdb/
│   └── youtube/
├── config/                        # Spring configuration beans
├── domain/                        # JPA entities
│   ├── engagement/
│   ├── event/
│   ├── feed/
│   ├── interest/
│   └── user/
├── maintenance/                   # Scheduled cleanup jobs
│   └── FeedItemRetentionJob.java
├── repository/                    # Spring Data JPA repositories
├── security/                      # OAuth2 + UserDetails wiring
├── service/                       # Transactional service layer
└── web/                           # Controllers (server-rendered MVC)

src/main/resources/
├── application.yml                # Common defaults
├── application-prod.yml           # Production (env-var driven)
├── db/migration/                  # Flyway migrations V1–V8
├── static/                        # CSS, no JS bundle
└── templates/                     # Thymeleaf views

src/test/java/com/eventpulse/
├── repository/FeedItemRepositoryColdStartTest.java
└── maintenance/FeedItemRetentionJobTest.java

src/test/resources/
└── application-test.yml           # Test profile

Dockerfile                         # Multi-stage build
.dockerignore                      # Keeps secrets and build artifacts out
run-dev.sh                         # Local dev convenience script
```

## Conventions and gotchas

- **`application-dev.yml` is gitignored.** Never commit OAuth secrets or
  YouTube keys. Treat any key shared in chat as compromised — rotate it.
- **`SELECT DISTINCT` + `ORDER BY` on a subquery in Postgres** raises
  SQLSTATE 42P10. Use `EXISTS` instead. (See
  `feedback_postgres_distinct_orderby` in dev memory.)
- **Plain CTEs are inlined** by Postgres 12+. The ranking queries
  explicitly use `WITH ... AS MATERIALIZED` to force single-execution —
  removing the keyword brings the unfiltered feed back to 13 s at 10k
  rows.
- **`:source = ''` empty-string sentinel** in the filter queries: a
  `null` String binds as `bytea` on Postgres and breaks comparisons.
  Pass `""` rather than `null` from Java.
- **Native queries don't auto-fetch lazy associations** the way
  `LEFT JOIN FETCH` does in JPQL. The feed list view doesn't render
  `relatedEvent` fields, so this is intentional.
- **Aggregator schedules drift on free-tier hosts** that suspend on
  idle. Don't rely on exact polling intervals in production.

---

For implementation details on the ranking query, see the Javadoc on
`FeedItemRepository.findPersonalizedFeed`. For schema specifics, see
[`DATA_MODEL.md`](./DATA_MODEL.md). For the product spec, see
[`SPEC.md`](./SPEC.md).
