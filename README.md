# ReceiptTrust

Receipt-backed social debt and trust platform (MVP). A Spring Boot REST API where users
upload receipts, assign items to friends, auto-generate debts, record and approve repayments,
and build a trust score from repayment behavior.

## Tech stack

- Java 21, Spring Boot 3.3
- Spring Security + JWT (access + rotating refresh tokens)
- Spring Data JPA / Hibernate, PostgreSQL (H2 in tests)
- Flyway migrations
- Local filesystem storage for receipt/profile images

## Spec

Full requirements, design, and task plan live in
[`docs/`](docs/): `requirements.md`, `design.md`, `tasks.md`.

## Prerequisites

- JDK 21 (e.g. Eclipse Temurin 21). `build.ps1` auto-detects a Temurin JDK 21 under the
  standard Adoptium install locations, or honors `JAVA_HOME` if it already points at a JDK 21.
- Maven (the repo was built with 3.8.6).
- PostgreSQL 16 for running the app (tests use in-memory H2, no DB needed). The easiest way is
  the bundled Docker setup (see below).

## Database with Docker

`docker-compose.yml` runs PostgreSQL 16 on host port **5433** (5432 is left for any native
install):

```powershell
docker compose up -d
```

Credentials:

| Role            | Username       | Password       |
|-----------------|----------------|----------------|
| Admin/superuser | `postgres`     | `postgres`     |
| App user        | `receipttrust` | `receipttrust` |

Database `receipttrust` is created on first boot, and the app user owns the `public` schema so
Flyway can manage it. The default `DB_URL` points at `localhost:5433`.

## Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push and pull request:

- **backend** — `mvn verify` on JDK 21 (unit + integration tests; uploads Surefire reports)
- **frontend** — `npm ci` + `npm run build` (type-check and production build) on Node 20
- **docker-build** — builds the API image, gated on the two jobs above passing

Since Render auto-deploys on push, keep deploys green by ensuring CI passes before merging.

## Deployment

To deploy on Render (managed Postgres + Dockerized API + static frontend), see
[`DEPLOY.md`](DEPLOY.md) and the [`render.yaml`](render.yaml) blueprint.

## Configuration

Defaults are in `src/main/resources/application.yml`. Override via environment variables:

| Variable            | Default                                          |
|---------------------|--------------------------------------------------|
| `DB_URL`            | `jdbc:postgresql://localhost:5432/receipttrust`  |
| `DB_USERNAME`       | `receipttrust`                                   |
| `DB_PASSWORD`       | `receipttrust`                                   |
| `JWT_SECRET`        | dev-only Base64 secret (override in production)  |
| `RECEIPT_DIR`       | `./storage/receipts`                             |
| `SERVER_PORT`       | `8080`                                            |

## Build and test

The `build.ps1` helper pins `JAVA_HOME` to the bundled JDK, then forwards to Maven:

```powershell
./build.ps1 clean verify      # compile + run all tests + package
./build.ps1 test              # tests only
```

Or with your own JDK 21 on `JAVA_HOME`:

```bash
mvn clean verify
```

## Run

1. Create the database:

   ```sql
   CREATE DATABASE receipttrust;
   CREATE USER receipttrust WITH PASSWORD 'receipttrust';
   GRANT ALL PRIVILEGES ON DATABASE receipttrust TO receipttrust;
   ```

2. Start the app (Flyway applies the schema on boot):

   ```powershell
   ./build.ps1 spring-boot:run
   ```

## Frontend

A React + Vite + TypeScript SPA lives in [`frontend/`](frontend/). It covers the full flow:
auth, friends, receipt upload + items + assignment + finalize, dashboard, the
"Why do I owe this?" debt explanation, payments/approval, notifications, and the trust profile.

```powershell
cd frontend
npm install
npm run dev        # dev server on http://localhost:5173 (falls back to 5174 if taken)
```

The dev server proxies `/api` to the backend on `http://localhost:8081` (see
`frontend/vite.config.ts`). If you run the backend on a different port, update the proxy target.

### Running both together (dev)

1. Backend (H2 dev profile, no database setup needed):

   ```powershell
   ./build.ps1 spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--server.port=8081"
   ```

2. Frontend:

   ```powershell
   cd frontend; npm run dev
   ```

Open the frontend URL, register an account, and go. The `dev` profile stores data in a local
H2 file under `./storage/devdb`; the default profile uses PostgreSQL with Flyway migrations.

## API overview

Base path `/api`. All endpoints require a Bearer access token except register/login/refresh.

- Auth: `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`
- Profile: `GET /me`, `GET /users/{username}`, `GET /users/search?query=`, `POST /me/profile-image`
- Friends: `POST /friends/requests`, `GET /friends/requests`,
  `POST /friends/requests/{id}/accept|reject`, `GET /friends`, `DELETE /friends/{userId}`
- Receipts: `POST /receipts` (multipart), `GET /receipts/{id}`, `GET /receipts/{id}/image`,
  item CRUD under `/receipts/{id}/items`
- Assignments: `POST /receipts/{id}/items/{itemId}/assignments`, `POST /receipts/{id}/finalize`
- Debts: `GET /debts/dashboard`, `GET /debts/{id}`, `GET /debts/{id}/explanation`,
  `GET /debts/{id}/history`
- Payments: `POST /debts/{id}/payments`, `GET /debts/{id}/payments`,
  `POST /payments/{id}/approve|reject`
- Notifications: `GET /notifications`, `POST /notifications/{id}/read`

## Trust score

Starts at 500, clamped to [300, 850]. On settlement: +15 full settlement, plus +10 early /
+5 on-time / -10 late by due date; -25 for debts still unpaid 30+ days past due (applied by a
daily sweep). Every change is recorded in `trust_score_events`.
