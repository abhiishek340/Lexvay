# Lexvay — Progress

Rules for this file: one section per milestone. Write the plan **before** building. After building,
paste the real output of `make test` and the real result of every acceptance check. Record what was
deferred. Never claim a test passed without running it.

## Status board

| # | Milestone | Status | Commit |
|---|---|---|---|
| — | Analysis, plan, rules, workflow | Done 2026-11-26; owner decisions applied | — |
| 0 | Scaffold | Done 2026-11-26 | see git log (M0) |
| 1 | Schema, tenancy, auth, audit | Not started | — |
| 2 | Documents | Not started | — |
| 3 | Rules and requirements | Not started | — |
| 4 | Pipeline | Not started | — |
| 5 | Review and signing | Not started | — |
| 6 | Frontend | Not started | — |
| 7 | Change tracking and report | Not started | — |
| 8 | OIDC and hardening | Not started | — |
| 9 | Evaluation and infrastructure | Not started | — |

## Prerequisites on this machine (checked 2026-11-26)

| Tool | Found | Needed | Action |
|---|---|---|---|
| Java | OpenJDK 21.0.8 | 21 | none |
| Maven | missing | any (wrapper) | Maven wrapper is committed in M0; `brew install maven` only to generate it once |
| Docker | missing → Colima 0.x + Docker 29.5.2 + Compose 5.8.0 (Homebrew) | Compose v2 | Done 2026-11-26. `colima start` must be running for `make test`/`make dev`; `brew uninstall colima docker docker-compose` reverts |
| Node | 22.18.0 → 26.13.1 (nvm, default alias) | ^22.22.3, ^24.15.0 or ^26.x | Done 2026-11-26; `frontend/.nvmrc` says 26 |
| psql client | 17.11 | optional | none |
| Terraform | missing | 1.17.2 | Install before M9 |
| git | 2.54.0 | any | `git init` done in planning step |

## Milestone 0 — Scaffold

### Plan (written 2026-11-26, before building)

Scope
- `backend/`: Spring Boot 4.1.3 (`spring-boot-starter-parent`), Java 21, Maven wrapper; starters: web,
  validation, security, jdbc, actuator, flyway + `flyway-database-postgresql`, postgresql driver;
  test: `spring-boot-starter-test`, `testcontainers` postgresql + junit-jupiter; JaCoCo plugin configured
  (gate rules added in M4). `GET /actuator/health` exposed without login. Application properties read
  `LEXVAY_*` env vars. Profiles: `local` (default), `test`.
- `frontend/`: `ng new` Angular 22.3.0, standalone, strict, routing, SCSS off (plain CSS with custom
  properties), Vitest unit tests, `@angular/cdk` 22.3.0, Playwright + axe added (first spec in M6).
  Dev proxy to `http://localhost:8080/api`.
- `docker-compose.yml`: `postgres:18.7`, volume, `initdb/01-roles.sql` creating `lexvay_owner`,
  `lexvay_app` (NOBYPASSRLS) and database `lexvay`; healthcheck.
- `Makefile`: `dev`, `test`, `backend-test`, `frontend-test`, `db-up`, `db-down`. No placeholder
  targets: `seed` arrives in M6 and `eval` in M9. Also `.env.example`, `.gitignore`, README run section.
- `.github/workflows/ci.yml`: checkout, Java 21, Node 26, `make test` (Docker available on
  `ubuntu-latest` for Testcontainers).

Tests to write
- `HealthEndpointTest`: `GET /actuator/health` → 200, body `status: UP`, no login required.
- `DatabaseConnectsTest`: Testcontainers `postgres:18.7` starts, Flyway baseline migration `V0__init.sql`
  (empty schema marker) applies, `SELECT 1` works as `lexvay_app`.
- Frontend: default `app.spec.ts` renders the shell title "Lexvay".

Acceptance evidence to collect
- `docker compose up -d` output and `docker compose ps` showing healthy.
- `make dev` output showing backend on 8080 and frontend on 4200.
- `make test` output, both suites.
- `curl -i localhost:8080/actuator/health` → 200.
- `ci.yml` parsed by `yq` or `python -c yaml.safe_load`.

Decisions expected
- Effective Boot-managed versions of Flyway, JDBC driver, Testcontainers (record in D-002 note).

Known risks
- Docker runtime and Node 26 are being installed as part of M0 (owner said proceed on 2026-11-26).

### Result (2026-11-26)

Built
- `backend/`: Spring Boot 4.1.3, Java 21, Maven Wrapper 3.3.4 → Maven 3.10.2. Starters: webmvc,
  validation, security, jdbc, flyway (+ `flyway-database-postgresql`), actuator; PostgreSQL driver;
  test: starter-test, webmvc-test, security-test, `spring-boot-testcontainers`,
  `testcontainers-postgresql`, `testcontainers-junit-jupiter`; JaCoCo 0.8.16 agent + report. Health
  endpoint public, everything else 401. Datasource connects as `lexvay_app`; Flyway as `lexvay_owner`.
- `frontend/`: Angular 22.3.0 standalone, strict TypeScript 6.0.3, CDK 22.3.0, Vitest 5.1.2, exact
  version pins, `engines.node >= 26`, dev proxy for `/api` and `/actuator`, theme tokens for light and
  dark, visible focus style, shell renders "Lexvay".
- `docker-compose.yml` (postgres:18.7, healthcheck) + `db/initdb/01-roles.sh` creating `lexvay_owner`
  and `lexvay_app` (NOBYPASSRLS) and database `lexvay`; the same script runs inside Testcontainers.
- `Makefile` (`dev`, `test`, `backend-test`, `frontend-test`, `db-up`, `db-down`), `.env.example`,
  `.gitignore`, `.github/workflows/ci.yml`, README run section.

Tests written and run
- `DatabaseConnectsTest` (3): app role is `lexvay_app` with `rolbypassrls = false`, `rolsuper =
  false`; server is PostgreSQL 18.x; Flyway connection is `lexvay_owner` and zero migrations applied.
- `HealthEndpointTest` (2): `/actuator/health` → 200 `UP` without login; other paths → 401.
- Frontend `app.spec.ts` (2): shell creates; `<h1>` contains "Lexvay".

`make test` output (tail, real)
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.520 s -- in com.lexvay.common.DatabaseConnectsTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.168 s -- in com.lexvay.common.HealthEndpointTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  1 passed (1)
      Tests  2 passed (2)
MAKE TEST EXIT: 0
```

Acceptance evidence
- `docker compose up -d --wait` → `lexvay-db  postgres:18.7  Up (healthy)  0.0.0.0:5432->5432/tcp`;
  `pg_roles`: `lexvay_app|f|f`, `lexvay_owner|f|f`; database `lexvay` owned by `lexvay_owner`.
- `make dev`: `curl -i localhost:8080/actuator/health` → `HTTP/1.1 200` body
  `{"groups":["liveness","readiness"],"status":"UP"}`; through the Angular proxy
  `localhost:4200/actuator/health` → `HTTP 200`; `localhost:4200/` → `<title>Lexvay</title>`.
- `make test` → exit 0 (above).
- `.github/workflows/ci.yml` parsed by Ruby `YAML.load_file`; one job `test` running `make test` on
  `ubuntu-latest` with Java 21 and Node 26.

Found and fixed during the milestone
- Testcontainers could not find the Colima socket. The Makefile now exports `DOCKER_HOST` from the
  active Docker context and `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
- One container per test class broke under Spring's context caching (the cached datasource pointed
  at a stopped container). Changed to one container per JVM; D-032 updated.
- `Flyway.getConfiguration().getUser()` is null when Boot wires Flyway with its own datasource; the
  test now checks `current_user` on that datasource instead.

Deferred
- Nothing from the M0 row. The CI workflow has not been executed on GitHub (no remote; pushing is
  out of scope by instruction).
