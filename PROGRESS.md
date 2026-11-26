# Lexvay — Progress

Rules for this file: one section per milestone. Write the plan **before** building. After building,
paste the real output of `make test` and the real result of every acceptance check. Record what was
deferred. Never claim a test passed without running it.

## Status board

| # | Milestone | Status | Commit |
|---|---|---|---|
| — | Analysis, plan, rules, workflow | Done 2026-11-26; owner decisions applied | — |
| 0 | Scaffold | Done 2026-11-26 | see git log (M0) |
| 1 | Schema, tenancy, auth, audit | Done 2026-11-26 | see git log (M1) |
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

## Milestone 1 — Schema, tenancy, auth, audit

### Plan (written 2026-11-26, before building)

Scope
- Flyway migrations: `V001__schema.sql` (every table in spec section 5 plus `program_jurisdiction`,
  `llm_call`, `report`, `app_user.password_hash`, `determination.assessment_id`; UUID keys,
  `created_at`, `created_by`; CHECK constraints for every enum, for the three finding values, for the
  gap-note rule, for `document.kind` vs `program_id`, for numeric fields by requirement type;
  `document_page.tsv` generated tsvector with GIN index); `V002__row_level_security.sql`
  (`current_tenant()`, ENABLE + FORCE RLS and one policy per table; `job` honours `lexvay.worker`;
  `app_user` SELECT honours `lexvay.auth_lookup` so login can find a user before the tenant is known,
  D-034); `V003__immutability.sql` (`forbid_change()` trigger on immutable tables; column-restricted
  update triggers for `determination.status`, `rule_version` approval columns, `change_event.status`,
  `requirement` after approval, D-035); `V004__jurisdictions.sql` (50 states + DC).
- Tenant plumbing: `TenantContext` (scoped per request/job), `TenantAwareDataSource` that sets
  `lexvay.tenant_id` on connection checkout and resets it on return; `TenantBootstrapService` that
  creates a tenant and its first admin user by choosing the tenant id before inserting (the only code
  path that sets a tenant context not derived from a login or a job).
- Auth: form login against `app_user` (`POST /api/v1/auth/login`, `POST /api/v1/auth/logout`,
  `GET /api/v1/auth/me`), bcrypt, JSON 200/401 responses instead of redirects, SPA-style CSRF cookie,
  `LexvayUser` principal carrying tenant id and role, method security by role.
- Program module (needed to prove isolation through the API): `POST /programs` (CONTRIBUTOR, ADMIN),
  `GET /programs`, `GET /programs/{id}` with target jurisdiction codes (D-007).
- Audit: `AuditService.record(...)` in the same transaction as each mutation; `GET /audit` newest first.
- Problem details (RFC 9457) for 400/401/403/404/409 via `ResponseEntityExceptionHandler`.
- Test fixtures: `SchemaFixtures` that inserts one full chain of rows for a tenant (program → course →
  course version → document → page → rule version → requirement → assessment → evidence span → mapping
  → review event → determination) so immutability and isolation tests have real rows.

Tests to write
- `Rule7_TenantIsolationApiTest`: tenant A creates a program; tenant B lists none, gets 404 by id,
  cannot see A's audit rows.
- `Rule7_TenantIsolationRepositoryTest`: under B's context, A's rows are invisible, an update of A's
  row affects 0 rows, an insert carrying A's tenant id fails the policy check.
- `Rule7_NoTenantFailsClosedTest`: with no tenant set, every tenant table returns zero rows and
  rejects inserts.
- `Rule7_EveryTableHasPolicyTest`: every table except `jurisdiction` and `flyway_schema_history` has
  `tenant_id`, RLS enabled and forced, and at least one policy.
- `Rule8_NoStudentEntityTest`: no table or column name contains `student`.
- `Rule5_ImmutabilityTest`: update and delete rejected on `document`, `document_page`,
  `course_version`, `evidence_span`, `review_event`, `audit_log`, `llm_call`; `determination` accepts
  a status change and rejects any other change; `review_event` with `GAP_CONFIRMED` and empty note is
  rejected by the database.
- `Rule9_AuditCoverageTest`: every `POST`/`PUT`/`DELETE` handler is in the declared audited set;
  `ProgramApiTest` asserts the audit row for `POST /programs`.
- `AuthTest`: login success and failure, `/me`, logout, unauthenticated 401, wrong-role 403 on
  `POST /programs`, CSRF required for state-changing requests.
- `MigrationsApplyCleanlyTest`: Flyway applies 4 migrations on an empty database with no warnings
  about failed validation.

Acceptance evidence to collect
- `make test` output with the new tests; `flyway.info()` listing four applied versions; the
  `pg_policies` count; the API isolation test output.

Decisions expected
- D-034 (login lookup flag), D-035 (column-restricted updates), D-036 (program facts latest-wins).

Known risks
- `FORCE ROW LEVEL SECURITY` also binds the owner; Flyway's data migration (jurisdictions) touches a
  table without RLS, so it is unaffected. Any future seed written as owner must set a tenant context.

### Result (2026-11-26)

Built
- Migrations `V001__schema.sql` (23 tables: every spec table plus `program_jurisdiction`, `llm_call`,
  `report`; CHECK constraints for all enums, the three finding values, the gap-note rule, document kind
  vs program, numeric fields by requirement type; generated `tsvector` + GIN index on pages),
  `V002__row_level_security.sql` (`current_tenant()`, ENABLE + FORCE RLS and a policy on all 22 tenant
  tables via one loop, worker flag on `job`, login-lookup flag on `app_user`), `V003__immutability.sql`
  (`forbid_change`, `restrict_update_to_columns`, rule-version approval-only and requirement-locked
  triggers), `V004__jurisdictions.sql` (51 rows).
- `TenantContext`, `TenantAwareDataSource` (sets `lexvay.tenant_id` on checkout, resets on return),
  `DataSourceConfig`; Flyway uses its own owner datasource (`spring.flyway.url/user/password`).
- Auth: form login with JSON responses, bcrypt via delegating encoder, `LexvayUser` principal with
  tenant and role, `TenantContextFilter`, explicit `CookieCsrfTokenRepository` (XSRF-TOKEN /
  X-XSRF-TOKEN) with the documented SPA request handler, problem-detail 401/403 handlers,
  `GET /api/v1/auth/session` (public; sets the CSRF cookie), `GET /api/v1/auth/me`,
  `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`; login and logout are audited.
- `TenantBootstrapService` (creates tenant + admin, adds users), `AppUserRepository.findForLogin`
  under the transaction-local lookup flag (D-034).
- Program module: `POST /api/v1/programs` (CONTRIBUTOR, ADMIN), `GET /api/v1/programs`,
  `GET /api/v1/programs/{id}`; target jurisdictions validated against reference data; audited.
- Audit: `AuditService`, `AuditRepository`, `GET /api/v1/audit?limit&offset` newest first.
- `ApiExceptionHandler`: RFC 9457 problem details for 400/404/409, SQLSTATE `LX001` → 409.
- Test fixtures: `TestTenants` (tenant + admin, contributor, reviewer, signer), `SchemaFixtures` (one
  full row chain per tenant), `TestCsrf` (browser-faithful CSRF, D-038).

Tests written and run (53 backend, 2 frontend)
- `Rule5_ImmutabilityTest` (21): update and delete rejected on 9 tables; determination status-only;
  rule version approve-once; requirements editable before approval, locked after; `GAP_CONFIRMED`
  without note rejected; finding limited to three values; `EVIDENCE_FOUND` without span rejected.
- `Rule7_TenantIsolationApiTest` (3), `Rule7_TenantIsolationRepositoryTest` (3),
  `Rule7_NoTenantFailsClosedTest` (1), `Rule7_EveryTableHasPolicyTest` (2).
- `Rule8_NoStudentEntityTest` (1), `Rule9_AuditCoverageTest` (1).
- `ProgramApiTest` (6): create + audit row with actor and detail, 403 for reviewer and signer, unknown
  jurisdiction → 400 problem detail, validation → 400 problem detail, 404, missing CSRF → 403.
- `AuthTest` (8): login ok + audited, wrong password 401 problem detail, unknown email 401, `/me`,
  unauthenticated 401 problem detail, session → cookie → login flow over real HTTP (403 without the
  header, 200 with it), session reports the signed-in user, logout 204 + audited.
- `MigrationsApplyCleanlyTest` (2), `DatabaseConnectsTest` (3), `HealthEndpointTest` (2).

`make test` output (tail, real)
```
[INFO] Tests run: 53, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  1 passed (1)
MAKE TEST EXIT: 0
```

Acceptance evidence
- Migrations apply cleanly: `MigrationsApplyCleanlyTest` asserts 4 applied, 0 pending, all `SUCCESS`,
  `validateWithResult().validationSuccessful`.
- Tenant A cannot read or write tenant B's rows through the API (`Rule7_TenantIsolationApiTest`) and
  through direct repository calls (`Rule7_TenantIsolationRepositoryTest`: 0 rows visible, update
  affects 0 rows, insert with the other tenant's id raises "new row violates row-level security policy").
- Role checks tested: `ProgramApiTest.reviewerAndSignerMayNotCreatePrograms` (403), method security
  on `POST /programs`.
- Audit rows for every mutating endpoint: `Rule9_AuditCoverageTest` enumerates handler mappings
  (`POST /api/v1/programs` only so far) and `ProgramApiTest.createWritesAuditRow` asserts the row;
  login and logout audited in `AuthTest`.

Found and fixed during the milestone
- PostgreSQL reports a policy violation as SQLSTATE 42501, which Spring maps to
  `BadSqlGrammarException`; the message lives in the root cause (D-037).
- Two `RequestMappingHandlerMapping` beans exist (web + actuator); the guard test qualifies by name.
- `csrf.spa()` could not be verified to apply a custom repository; replaced with explicit
  configuration. `spring-security-test`'s `csrf()` swaps the live CsrfFilter repository for an
  HttpSession one for the rest of the JVM; replaced with `TestCsrf` (D-038). Diagnosed with a throwaway
  debug test over real HTTP (deleted afterwards).

Deferred
- Nothing from the M1 row. `GET /jobs/{id}` arrives with the job runner in M2.
