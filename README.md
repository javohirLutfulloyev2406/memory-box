# Memory Box

Save links, read them later. Memory Box is a small "save a URL now, read it later"
service: save a link via the HTTP API or a Telegram bot, it fetches a title/description
in the background, and you can list, search, and manage what you've saved — scoped to
you, never another user.

## Stack

- Java 21, Spring Boot 4.1.2 (Web, Data JPA, Security, Validation, Actuator)
- PostgreSQL, schema managed by Flyway
- A Postgres-backed background job queue for metadata fetching (no message broker)
- Telegram Bot API integration (optional, disabled by default)
- springdoc-openapi (Swagger UI)

## Prerequisites

- JDK 21
- Docker (for `docker compose up`, and for the Testcontainers-based tests)

## Running locally

### Option A: Docker Compose (app + Postgres)

```
cp .env.example .env   # edit values as needed; never commit the real .env
docker compose up --build
```

The app listens on `http://localhost:8080`. Postgres is published on `localhost:5433`
by default (see `POSTGRES_PORT` in `.env.example` — not 5432, so it won't collide with
a Postgres instance you might already have running locally).

### Option B: Postgres in Docker, app on the host

```
docker compose up -d db
./mvnw spring-boot:run --spring.profiles.active=local
```

The `local` Spring profile (`application-local.properties`) turns on the dev-auth
bypass described below and enables SQL/debug logging — it is never active unless you
pass it explicitly.

Either way, Flyway runs the migrations in `src/main/resources/db/migration` on startup;
there is nothing to run by hand.

## Authentication

There is no login flow yet. Two ways to authenticate as a user:

1. **Telegram** (the real identity path): message the bot a URL; Memory Box creates a
   user on first contact and saves the link under that Telegram identity. See "Telegram
   bot setup" below.
2. **Dev-auth bypass** (local development only): set `DEV_AUTH_ENABLED=true` (or run
   with `--spring.profiles.active=local`) and every HTTP request to `/api/v1/**` is
   authenticated as a single fixed dev user, with no credentials required. This is
   **never** safe to enable anywhere reachable by more than one person — it performs no
   credential check at all. It must stay `false` (the default) everywhere else.

With dev-auth off and no Telegram-authenticated session, every `/api/v1/**` request
gets a `401` with a JSON error body.

## API

All endpoints below are under `/api/v1` and require authentication (see above).

| Method | Path                | Description                                      |
|--------|---------------------|---------------------------------------------------|
| POST   | `/items`            | Save a URL. Returns `201` if new, `200` if the same URL was already saved (idempotent). |
| GET    | `/items`             | List your saved items, paginated (`?page=&size=`), newest first by default. |
| GET    | `/items/{id}`        | Get one saved item you own (`404` otherwise).     |
| PATCH  | `/items/{id}`        | Update `title` and/or `userNote` (only fields provided are changed). |
| DELETE | `/items/{id}`        | Delete a saved item you own.                      |
| GET    | `/search?q=...`      | Full-text search (Postgres `tsvector`) over your own items' title/description/content. |

Example, with the dev-auth bypass enabled:

```
curl -X POST http://localhost:8080/api/v1/items \
  -H "Content-Type: application/json" \
  -d '{"url": "https://example.com/some-article", "userNote": "read later"}'
```

Interactive docs: `http://localhost:8080/swagger-ui.html` (raw spec at `/v3/api-docs`).

## Telegram bot setup

Disabled by default — the app starts fine with no Telegram configuration at all. To
enable it:

1. Create a bot with [@BotFather](https://t.me/BotFather) and get its token.
2. Set `TELEGRAM_BOT_ENABLED=true`, `TELEGRAM_BOT_TOKEN=<your token>`, and a
   `TELEGRAM_WEBHOOK_SECRET` of your choosing (any random string).
3. Register the webhook so Telegram knows where to send updates, including the secret
   token so the webhook can verify requests actually came from Telegram:

   ```
   curl "https://api.telegram.org/bot<TOKEN>/setWebhook" \
     -d "url=https://<your-public-host>/api/telegram/webhook" \
     -d "secret_token=<TELEGRAM_WEBHOOK_SECRET>"
   ```

4. Message the bot a link. It saves it and replies with a short confirmation.

The webhook endpoint (`POST /api/telegram/webhook`) rejects any request whose
`X-Telegram-Bot-Api-Secret-Token` header doesn't exactly match your configured secret —
and never accepts a request just because no secret happens to be configured.

## Configuration reference

See `.env.example` for the full list of environment variables (database connection,
dev-auth, Telegram, metadata fetching, background worker) with their defaults and
descriptions. Copy it to `.env` and fill in real values for local use; `.env` is
git-ignored.

## Tests

```
./mvnw test
```

Most tests are plain unit tests (Mockito, no external dependencies) and run anywhere.
Two test classes additionally spin up a real Postgres via
[Testcontainers](https://testcontainers.com/) and therefore **require Docker**:
`MemoryBoxApplicationTests` (full context load) and `SavedItemIntegrationTest`
(Postgres-specific behavior: the generated full-text-search column, the per-user unique
URL constraint). If Docker isn't available, every other test class still runs normally.

## Design notes

- **Ownership**: every saved-item query, update, and delete is scoped by the
  authenticated user's ID at the database query level, not just checked in application
  code.
- **Duplicate URLs**: saving the same URL twice (by the same user) returns the existing
  item instead of creating a second one — including under concurrent requests racing to
  save a brand-new URL at the same instant.
- **SSRF protection**: before fetching a saved link's metadata, its hostname is resolved
  and every resolved address is checked against loopback/private/link-local/multicast
  ranges (covering the cloud-metadata endpoint at `169.254.169.254`); redirects are
  re-validated on every hop rather than followed blindly.
- **Background processing**: metadata fetching runs through a Postgres-backed job queue
  (`SELECT ... FOR UPDATE SKIP LOCKED`) polled on a schedule, with retry/backoff and a
  max-attempt cutoff — no external broker required. Disable it with
  `PROCESSING_WORKER_ENABLED=false`.
- **No AI dependency**: search today is Postgres full-text search only. There's a
  placeholder interface for a future embedding-based search, but it isn't wired to
  anything and the app never requires an AI API key to start.
