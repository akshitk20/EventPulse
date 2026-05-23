# EventPulse — Data Model

This is the **data layer for v1**. See `SPEC.md` for the full product spec.

## What's here

```
eventpulse/
├── pom.xml                              # Maven, Spring Boot 3.3, Java 21
├── SPEC.md                              # Full product spec
├── DATA_MODEL.md                        # This file
└── src/main/
    ├── java/com/eventpulse/
    │   ├── EventPulseApplication.java
    │   └── domain/
    │       ├── user/        User
    │       ├── interest/    Interest, UserInterest, InterestCategory
    │       ├── event/       Event, EventStatus, EventSummary
    │       ├── feed/        FeedItem
    │       ├── social/      TopicAlias
    │       └── engagement/  UserEngagement, EngagementAction
    └── resources/
        ├── application.yml
        └── db/migration/
            └── V1__init_schema.sql
```

## Key decisions (and why)

| Decision | Why |
|---|---|
| **UUID PKs everywhere** | Lets clients generate IDs, supports merging across environments. Postgres `gen_random_uuid()` is fine for v1 scale. |
| **Self-referencing `interests` tree** | Models `cricket > ipl > csk` with one table. Recursive queries are rare and Postgres handles them. Tagging would lose the hierarchy. |
| **Join tables (not UUID arrays)** | Standard JPA `@ManyToMany`, B-tree indexes work, easier joins. SPEC originally suggested arrays — switched on review. |
| **JSONB metadata** | Source-specific fields (scores, posters, teams) vary too much to model as columns. Mapped as `Map<String,Object>` via hypersistence-utils. |
| **String-coded enums via `AttributeConverter`** | DB stores lowercase (`'live'`, `'view'`) to match CHECK constraints; Java keeps idiomatic `UPPER_CASE`. |
| **`OffsetDateTime` for all timestamps** | Maps cleanly to Postgres `TIMESTAMPTZ`. UTC enforced via `hibernate.jdbc.time_zone=UTC`. |
| **Flyway, not Hibernate `ddl-auto`** | `ddl-auto: validate` only; schema changes go through versioned migrations. |
| **Lombok** | Cuts boilerplate on entities. Already in pom. |
| **Composite-key entities use `@EmbeddedId`** | `UserInterest`, `TopicAlias`, `UserEngagement` — standard JPA pattern, plays nice with Spring Data. |
| **`open-in-view: false`** | Avoids lazy-loading surprises in controllers. |

## Tables

| Table | Purpose |
|---|---|
| `users` | OAuth-only identity (no passwords). |
| `interests` | Taxonomy of things users follow. Self-referencing. |
| `user_interests` | M:N user ↔ interest, with weight. |
| `events` | Real-world events that bias ranking (matches, releases). |
| `event_interests` | M:N event ↔ interest. |
| `topic_aliases` | Per-source search terms for an interest (Reddit subreddit, Bluesky hashtag, HN keyword). |
| `feed_items` | Cached aggregated content from external sources. |
| `feed_item_interests` | M:N feed_item ↔ interest. |
| `user_engagement` | Views/clicks/saves/hides for personalization. |
| `event_summaries` | One AI summary per event (1:1 with events). |

## Running it locally

You don't strictly need Postgres yet — the entities compile without it. To actually run the migration:

```bash
# 1. Start Postgres locally (or use Docker)
docker run --rm -d --name eventpulse-pg \
  -e POSTGRES_DB=eventpulse \
  -e POSTGRES_USER=eventpulse \
  -e POSTGRES_PASSWORD=eventpulse \
  -p 5432:5432 postgres:16

# 2. Build + run (Flyway runs the migration on startup)
./mvnw spring-boot:run
```

`mvnw` wrapper isn't generated yet — run `mvn -N io.takari:maven:wrapper` once if you want it, or just use a system `mvn`.

## What's NOT here yet (next rounds)

- Repositories (`@Repository` Spring Data interfaces)
- Service layer
- Controllers / REST endpoints
- API source clients (YouTube, TMDB, etc.)
- Caching, ranking, schedulers
- Tests beyond a smoke test

These come in their own focused PRs. The data model is the foundation; everything else builds on it.
