# EventPulse

> An event-aware personalized entertainment & sports aggregator that adapts to what's happening in the world right now.

---

## 1. The Idea

### One-line pitch
A personalized feed for sports and entertainment that **rebalances itself based on real-world events** — when IPL is live, cricket bubbles to top; when a Marvel movie drops, superhero content surges; on a quiet Tuesday, your baseline interests show.

### The core insight
Most aggregators (Feedly, Google News, Apple News) show you the same kind of stuff every day. They don't understand that **the value of content is event-relative**. A 6-month-old IPL highlight is worthless during the off-season but gold during the final week. EventPulse encodes this insight into the ranking layer.

### Why now
- X/Twitter API became unaffordable in 2023 — there's a real gap in "what people are saying" aggregators.
- Pocket shut down in 2025 — content aggregation is a re-opening space.
- Spring AI matured in 2024–2025 — easy LLM summaries are now table-stakes.
- Java 21 virtual threads make multi-API fan-out trivial.

### Target users
- **Primary:** General sports + entertainment fans who follow multiple things casually (don't have time for 5 separate apps).
- **Secondary:** Diaspora communities (cricket fans abroad, F1 fans in non-F1 countries) underserved by big aggregators.
- **Tertiary:** People who want a single "what's worth my attention right now" tab.

### Goals
- **Project goals:** Real users (target 100+ in first 3 months), strong resume artifact, deployable on a personal repo.
- **Product goals:** A feed that feels alive and timely without being noisy. One-glance value within 5 seconds of opening.

---

## 2. Core Features

### Must-have (v1, 4 weekends)
1. **Onboarding** — User picks broad interests (cricket, F1, NBA, Marvel, K-pop, indie movies, etc.).
2. **Personalized feed** — Mixed cards: video clips, scores, releases, news, social buzz.
3. **Event awareness** — Ranking boosts content tied to live, today, this-week, upcoming events.
4. **Live event strip** — Top of feed shows currently-live games or events relevant to user.
5. **Social pulse panel** — Per-topic "what people are saying" from Reddit + Bluesky + HN.
6. **AI summaries** — Cluster-level summaries ("here's what happened in the IPL final in 3 sentences").
7. **Real-time score updates** — Server-Sent Events push live scores into open feed.
8. **Auth** — Sign in with GitHub or Google.

### Should-have (v2)
- Email/push daily digest ("here's what you missed")
- Save-for-later (Pocket-style read-later for articles)
- Share cards as image/link
- Multi-language support (Hindi/Spanish for diaspora users)
- Topic deep-dive page (everything about one team/show/artist on one page)

### Nice-to-have (v3+)
- Mobile app (React Native or Flutter)
- ML-based personalization (collaborative filtering on engagement)
- Browser extension to save articles/videos to feed
- Public "trending now" page (no login)
- Webhook/API for power users

### Explicitly out of scope
- User-generated content / posting
- Direct messaging / social graph
- Paid streaming / DRM video
- News authoring / journalism

---

## 3. Data Sources / APIs

### Confirmed for v1 (6 sources, 4 categories)

| Slot | Source | Auth | Free tier | Purpose |
|---|---|---|---|---|
| Video | **YouTube Data API v3** | API key | 10k units/day | Highlights, trailers, trending |
| Movies/TV | **TMDB** | API key | 50 req/sec, no daily cap | Releases, metadata, posters |
| Music | **Spotify Web API** | OAuth2 client credentials | Generous rolling limits | New releases, artist data |
| Sports | **TheSportsDB** | Free key (`123` or signup) | Polite use | Multi-sport fixtures + scores |
| News | **Curated RSS feeds** | None | Self-controlled | ESPN, BBC, Variety, IGN, etc. |
| Social | **Reddit JSON + Bluesky AT Proto + HN Algolia** | None / app password / none | 60/min Reddit; generous others | "What people are saying" |

### Planned for v2
- Spotify expanded (charts, artist tours)
- Twitch API (live streams, esports)
- IGDB (video games)
- Ticketmaster Discovery API (concerts/events near user)
- Trakt API (what people are watching)
- Last.fm (music discovery)
- balldontlie.io (NBA deep stats)
- Ergast (F1 deep data)

### Explicitly skipped (and why)
- **Twitter/X API** — paid tier killed it. Replaced by Reddit + Bluesky + HN social pulse.
- **Instagram / Facebook / TikTok** — locked-down APIs, can't pull public content.
- **Google News API** — long deprecated.
- **NewsAPI.org for prod** — free tier prohibits production use; using RSS instead.

### "Twitter replacement" strategy
The social pulse panel unifies three open ecosystems:
- **Reddit** — best for fandoms (r/cricket, r/Marvel, r/F1)
- **Bluesky** — fast-growing, real-time, journalist + tech crowd
- **Hacker News** — tech-adjacent culture, game launches, streaming news

Each topic has aliases mapped to source-specific queries (subreddit names, hashtags, keywords). Results are unified, deduplicated by URL/quote, and ranked by engagement.

---

## 4. Architecture

### High-level diagram

```
                     ┌─────────────────────────────────┐
                     │        Browser (HTMX)           │
                     │  Thymeleaf views + SSE updates  │
                     └────────────┬────────────────────┘
                                  │ HTTPS
                     ┌────────────▼────────────────────┐
                     │   Spring Boot 3.x (Java 21)     │
                     │  ┌──────────────────────────┐   │
                     │  │ Web Layer (Controllers)  │   │
                     │  │  /feed /events /ws/sse   │   │
                     │  └──────────┬───────────────┘   │
                     │             │                   │
                     │  ┌──────────▼───────────────┐   │
                     │  │   Aggregator Service     │   │
                     │  │  (parallel fan-out via   │   │
                     │  │   virtual threads +      │   │
                     │  │   structured concurrency)│   │
                     │  └──┬─────────────────────┬─┘   │
                     │     │                     │     │
                     │  ┌──▼──────┐   ┌──────────▼──┐  │
                     │  │ Source  │   │   Ranking   │  │
                     │  │ Clients │   │  (event-    │  │
                     │  │ x6      │   │   aware)    │  │
                     │  └──┬──────┘   └─────────────┘  │
                     │     │                           │
                     │  ┌──▼──────────────────────┐    │
                     │  │ Schedulers              │    │
                     │  │  - EventRefreshJob      │    │
                     │  │  - LiveScoreJob         │    │
                     │  │  - TrendingRefreshJob   │    │
                     │  └─────────────────────────┘    │
                     └──────┬──────────────┬───────────┘
                            │              │
              ┌─────────────▼──┐   ┌───────▼──────────┐
              │   PostgreSQL   │   │   Redis (cache)  │
              │  users, prefs, │   │   per-source TTL │
              │  events,       │   │   topic results  │
              │  topic_aliases │   │   live scores    │
              └────────────────┘   └──────────────────┘
                            │
                     ┌──────▼──────────────────────────┐
                     │       External APIs             │
                     │  YouTube  TMDB  Spotify         │
                     │  TheSportsDB  Reddit  Bluesky   │
                     │  HN  RSS feeds                  │
                     └─────────────────────────────────┘
```

### Key architectural decisions

1. **Server-rendered (Thymeleaf + HTMX), not SPA.**
   - *Why:* Java-only stack keeps the resume story clean. HTMX + SSE gives live updates without an SPA build pipeline.
   - *Trade-off:* Less polished than React, but ships faster.

2. **Virtual threads + structured concurrency for fan-out.**
   - *Why:* Java 21 feature, directly relevant to interview conversations. Cleaner than `CompletableFuture`.
   - *Trade-off:* Locks in Java 21+ as minimum.

3. **Redis cache as a hard dependency, not optional.**
   - *Why:* Free-tier API quotas (especially YouTube's 10k units/day) require aggressive caching. Without it, the app dies on day 2.
   - *Trade-off:* Adds a deploy dependency, but Upstash free tier handles it.

4. **Per-source TTLs, not blanket caching.**
   - *Why:* Live scores need 30s TTL, movie metadata can sit for 7 days. One TTL doesn't fit all.
   - *Trade-off:* More cache config to manage.

5. **Event-aware ranking is the moat — not source count.**
   - *Why:* Anyone can call 6 APIs. The differentiator is *what we do with the results*.
   - *Trade-off:* Easy to over-engineer ranking. Start with a simple weighted formula.

6. **SSE over WebSockets for live updates.**
   - *Why:* HTMX has first-class SSE support (`hx-sse`). WebSockets are overkill for one-way score streaming.
   - *Trade-off:* No client→server real-time, but we don't need it for v1.

7. **Resilience4j circuit breakers per source.**
   - *Why:* If ESPN goes down, the feed should degrade gracefully, not 500.
   - *Trade-off:* More config, but essential for "real users" goal.

### Ranking formula (v1)

```
score = base_interest_weight
      × event_boost   // live=10, today=5, this_week=2, upcoming=1.5, none=1
      × recency_decay // exp(-hours/24)
      × source_quality // tunable per source
      × personalization_match // 1.0 default, higher if user engaged with similar items
```

Tunable, simple, explainable. ML can come in v3.

---

## 5. Data Model (initial cut)

```sql
-- Identity
users (
  id UUID PRIMARY KEY,
  email TEXT UNIQUE,
  display_name TEXT,
  oauth_provider TEXT,    -- 'github' | 'google'
  oauth_subject TEXT,
  created_at TIMESTAMPTZ,
  last_seen_at TIMESTAMPTZ
);

-- Interests (taxonomy)
interests (
  id UUID PRIMARY KEY,
  slug TEXT UNIQUE,        -- 'cricket', 'ipl', 'csk', 'marvel', 'f1', 'kpop'
  display_name TEXT,
  category TEXT,            -- 'sport' | 'movie' | 'tv' | 'music' | 'gaming'
  parent_id UUID REFERENCES interests(id)  -- 'csk' parent = 'ipl' parent = 'cricket'
);

user_interests (
  user_id UUID REFERENCES users(id),
  interest_id UUID REFERENCES interests(id),
  weight REAL DEFAULT 1.0,
  PRIMARY KEY (user_id, interest_id)
);

-- Events (the moat)
events (
  id UUID PRIMARY KEY,
  title TEXT,
  category TEXT,
  starts_at TIMESTAMPTZ,
  ends_at TIMESTAMPTZ,
  status TEXT,              -- 'upcoming' | 'live' | 'recent'
  related_interests UUID[], -- which interests this event affects
  source TEXT,              -- 'thesportsdb', 'tmdb', 'spotify', 'manual'
  source_id TEXT,
  metadata JSONB,           -- score, teams, venue, etc.
  UNIQUE(source, source_id)
);

-- Topic aliases (for social pulse cross-source queries)
topic_aliases (
  interest_id UUID REFERENCES interests(id),
  source TEXT,              -- 'reddit' | 'bluesky' | 'hn'
  query TEXT,               -- subreddit name OR search string
  PRIMARY KEY (interest_id, source, query)
);

-- Feed cache / engagement
feed_items (
  id UUID PRIMARY KEY,
  source TEXT,              -- 'youtube' | 'tmdb' | 'spotify' | 'thesportsdb' | 'rss' | 'reddit' | 'bluesky' | 'hn'
  source_id TEXT,
  url TEXT,
  title TEXT,
  thumbnail_url TEXT,
  published_at TIMESTAMPTZ,
  related_interests UUID[],
  related_event_id UUID REFERENCES events(id),
  metadata JSONB,
  fetched_at TIMESTAMPTZ,
  UNIQUE(source, source_id)
);

user_engagement (
  user_id UUID REFERENCES users(id),
  feed_item_id UUID REFERENCES feed_items(id),
  action TEXT,              -- 'view' | 'click' | 'save' | 'hide'
  created_at TIMESTAMPTZ,
  PRIMARY KEY (user_id, feed_item_id, action)
);

-- AI summaries
event_summaries (
  event_id UUID REFERENCES events(id),
  summary TEXT,
  generated_at TIMESTAMPTZ,
  model TEXT,
  PRIMARY KEY (event_id)
);
```

---

## 6. Tech Stack

### Backend
| Layer | Choice | Reason |
|---|---|---|
| Language | **Java 21** | Virtual threads, structured concurrency, modern syntax |
| Framework | **Spring Boot 3.x** | Industry standard, resume relevance |
| Build | **Gradle (Kotlin DSL)** | Cleaner than Maven, common in modern projects |
| HTTP client | **Spring WebClient** | Async, parallel fan-out, idiomatic |
| Resilience | **Resilience4j** | Circuit breakers, retries, bulkheads per source |
| ORM | **Spring Data JPA + Hibernate** | Standard, productive |
| Migrations | **Flyway** | Versioned schema evolution |
| Validation | **Jakarta Bean Validation** | Standard |
| AI | **Spring AI** | LLM summaries, abstracted over OpenAI/Anthropic/Gemini |
| RSS parsing | **Rome library** | Mature Java RSS/Atom parser |
| HTML cleaning | **Jsoup** | For RSS fallback content extraction |

### Persistence & infra
| Layer | Choice | Reason |
|---|---|---|
| Database | **PostgreSQL 16** | Free on Render/Supabase, JSONB for metadata |
| Cache | **Redis** (Upstash free tier) | Per-source TTL caching, pub/sub for live scores |
| Object storage | None for v1 | Use external thumbnail URLs directly |

### Frontend
| Layer | Choice | Reason |
|---|---|---|
| Templates | **Thymeleaf** | Server-rendered, Spring-native |
| Interactivity | **HTMX** | Partial updates without SPA |
| Live updates | **HTMX SSE extension** | One-way real-time, simple |
| Styling | **Tailwind CSS** (CDN for v1) | Fast iteration, no build step |
| Icons | **Heroicons** or **Lucide** | Free, clean |

### Auth & security
| Layer | Choice | Reason |
|---|---|---|
| Auth | **Spring Security + OAuth2 Client** | GitHub + Google login |
| Session | **Spring Session + Redis** | Survives multiple instances |
| CSRF | Spring Security default | |
| Rate limiting | **Bucket4j** (Redis-backed) | Per-user, per-IP limits |

### Observability
| Layer | Choice | Reason |
|---|---|---|
| Metrics | **Micrometer + Prometheus** | Standard, exportable |
| Tracing | **Spring Boot Actuator** + OpenTelemetry (v2) | Distributed tracing later |
| Logging | **Logback + JSON encoder** | Structured logs |
| Health checks | **Actuator /health, /info** | Standard |

### Deploy & ops
| Layer | Choice | Reason |
|---|---|---|
| Hosting | **Render** or **Railway** | Free tier, GitHub integration |
| Database hosting | **Supabase** or Render Postgres | Free tier |
| Redis hosting | **Upstash** | Generous free tier |
| CI/CD | **GitHub Actions** | Free for public repos, build + test + deploy |
| Container | **Docker** with Jib (no Dockerfile needed) | Reproducible builds |
| Secrets | Render env vars / GitHub Secrets | |
| Domain | Cheap `.app` or `.io` from Namecheap | $10–20/yr |

### Testing
| Layer | Choice | Reason |
|---|---|---|
| Unit | **JUnit 5 + AssertJ** | Standard |
| Mocking | **Mockito** | Standard |
| Integration | **Testcontainers** (Postgres + Redis) | Real services in tests |
| HTTP mocking | **WireMock** | Mock external APIs |
| E2E | **Playwright (Java)** (v2) | Full UI flow tests |

---

## 7. Roadmap

### Weekend 1 — Foundation + 2 sources
**Goal:** A deployed app that shows YouTube + TMDB content for a hardcoded interest list.
- Spring Boot 3 + Java 21 + Postgres + Flyway scaffolding
- Domain models: User, Interest, FeedItem
- YouTubeClient + TmdbClient with WebClient
- `FeedAggregator` with virtual threads + structured concurrency
- Thymeleaf feed page with mixed cards
- Deploy to Render, hooked to GitHub Actions

### Weekend 2 — Sports + Music + Caching + Auth
**Goal:** 4 sources working, real users can sign up.
- TheSportsDB integration (multi-sport fixtures)
- Spotify OAuth2 client credentials + new releases endpoint
- Redis caching layer (Upstash) with per-source TTLs
- Resilience4j circuit breakers per source
- Spring Security + GitHub OAuth login
- User onboarding flow (pick interests)

### Weekend 3 — Social pulse + RSS + SSE
**Goal:** All 6 sources, live updates working.
- Reddit JSON client + topic aliases table
- Bluesky AT Protocol client (read-only, public search)
- HN Algolia search client
- RSS aggregator with curated source list (Rome)
- Unified `SocialPulseService` with virtual-thread fan-out
- SSE endpoint for live score updates
- HTMX `hx-sse` for live-updating cards

### Weekend 4 — Event awareness + AI + ship
**Goal:** Public launch with users.
- `events` table + scheduled population jobs
- Event-aware ranking algorithm
- Spring AI integration for event summaries
- Polish UI (Tailwind, responsive, dark mode)
- README with screenshots, architecture diagram, demo link
- Public launch on r/SideProject, r/IndieDev, relevant fandom subreddits
- Analytics setup (Plausible or PostHog free tier)

### Post-MVP (v2)
- Daily digest emails
- Save-for-later
- Topic deep-dive pages
- Mobile-responsive polish
- More sources (Twitch, IGDB, Trakt)
- ML-driven personalization
- Multi-language

---

## 8. Cache TTL Strategy

Critical for free-tier survival:

| Data | TTL | Notes |
|---|---|---|
| YouTube search results | 30 min | Quota: search costs 100 units |
| YouTube video details | 24 hr | Static-ish |
| TMDB movie details | 7 days | Mostly static |
| TMDB now playing / upcoming | 6 hr | Daily refresh sufficient |
| Spotify new releases | 6 hr | Friday is the relevant day |
| Spotify artist data | 24 hr | Static-ish |
| TheSportsDB fixtures | 12 hr | Schedules stable |
| TheSportsDB live scores | 30 sec | Hot path during games |
| Reddit hot threads | 5 min | Fast-moving |
| Reddit search | 10 min | |
| Bluesky search | 10 min | |
| HN Algolia search | 30 min | Slower-moving |
| RSS feed parses | 15 min | Most publishers update hourly |
| AI summaries | 24 hr | Per-event, regenerate daily if event ongoing |

---

## 9. Security & Compliance

- **OAuth2 only** — no password storage.
- **HTTPS everywhere** — Render handles certs.
- **CSRF protection** — Spring Security default.
- **Rate limiting** — Bucket4j per user (60 req/min) and per IP (300 req/min).
- **Input validation** — Jakarta Bean Validation on all DTOs.
- **API key storage** — env vars, never committed. `.env.example` documents required vars.
- **Dependabot** — enabled on the repo for security alerts.
- **Privacy** — Store only email + OAuth subject. No tracking beyond engagement events. Privacy policy page in v1.
- **Robots.txt compliance** — for any RSS/scrape fallback paths.
- **API ToS** — verify each source's ToS allows the use case before integration.

---

## 10. Resume Bullet (drafted)

> Built **EventPulse**, an event-aware multi-source aggregator (Spring Boot 3, Java 21, PostgreSQL, Redis, HTMX/SSE) that personalizes a sports + entertainment feed based on real-world events (IPL, FIFA, movie/music releases). Aggregates **6 external APIs** (YouTube, TMDB, Spotify, TheSportsDB, RSS, Reddit/Bluesky/HN social pulse) in parallel using **Java 21 virtual threads with structured concurrency**, with per-source Redis caching to stay within free-tier quotas. Built a **Twitter-API-replacement social pulse panel** unifying Reddit, Bluesky, and Hacker News signals after X's pricing changes. Deployed on Render with GitHub Actions CI/CD. **[N active users, M+ requests/day]**.

---

## 11. Open Questions / Decisions Pending

- [ ] Domain name — need to brainstorm and check availability
- [ ] AI provider — OpenAI vs Anthropic vs Gemini free tier (cost vs quality vs availability)
- [ ] Should v1 require login, or allow anonymous browsing with cookie-based prefs?
- [ ] Mobile-first or desktop-first design?
- [ ] How to seed initial events (manual list vs scrape Wikipedia "events in 2026" pages)?
- [ ] Topic taxonomy — start with how many interests? 50? 200?
- [ ] Logo / branding direction?

---

## 12. Success Metrics

### Product
- 100+ signed-up users in first 3 months
- 30%+ weekly active rate
- <5s feed load time (p95)
- 5+ minutes average session duration

### Technical
- 99%+ uptime
- <1% error rate per source
- <50% API quota utilization on free tiers
- All scheduled jobs completing in <10s

### Resume
- Public GitHub repo with strong README
- Live demo link that recruiters can click
- Architecture diagram + 3+ "interesting decisions" written up
- At least one "war story" worth telling in interviews (e.g., how the cache layer evolved, or how event detection edge-cases were handled)
