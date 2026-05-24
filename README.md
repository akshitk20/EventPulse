# EventPulse

Personalized feed aggregator over Hacker News, Reddit, SportsDB, and YouTube.

See `SPEC.md` for product goals and `DATA_MODEL.md` for the schema.

## Run locally

```bash
./run-dev.sh
```

Requires Postgres on `localhost:5432` (use `eventpulse-db` Docker container or
your own) and an `application-dev.yml` with Google OAuth + YouTube credentials
(gitignored — ask a maintainer).

## Run tests

The integration tests need a dedicated `eventpulse_test` database. Create it
once:

```bash
docker exec eventpulse-db psql -U postgres -c "CREATE DATABASE eventpulse_test OWNER postgres;"
```

Then:

```bash
mvn test
```

Flyway recreates the schema each run; the tests roll back their own data via
`@Transactional`. The `test` profile is in `src/test/resources/application-test.yml`.

> Testcontainers would be the cleaner choice and `pom.xml` already has it
> wired, but Docker Desktop's `/info` endpoint is currently misbehaving on
> macOS — revisit once that's resolved.

## Deploy

`application-prod.yml` reads all secrets from environment variables:

| Required | Optional |
| --- | --- |
| `DATABASE_URL` | `PORT` (default 8080) |
| `DATABASE_USERNAME` | `YOUTUBE_API_KEY` |
| `DATABASE_PASSWORD` | |
| `GOOGLE_CLIENT_ID` | |
| `GOOGLE_CLIENT_SECRET` | |

Activate with `SPRING_PROFILES_ACTIVE=prod`.
