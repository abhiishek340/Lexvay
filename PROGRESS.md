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
| 2 | Documents | Done 2026-11-26 | see git log (M2) |
| 3 | Rules and requirements | Done 2026-11-26 | see git log (M3) |
| 4 | Pipeline | Done 2026-11-26 | see git log (M4) |
| 5 | Review and signing | Done 2026-11-26 | see git log (M5) |
| 6 | Frontend | Done 2026-11-26 | PR #6 |
| 7 | Change tracking and report | Done 2026-11-26 | PR #7 |
| 8 | OIDC and hardening | In progress as WS-SEC (docs/ROADMAP.md) | — |
| 9 | Evaluation and infrastructure | In progress as WS-EVAL (docs/ROADMAP.md) | — |

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

## Milestone 2 — Documents

### Plan (written 2026-11-26, before building)

Scope
- `FileStore` interface (`put`, `get`, `exists`) with `LocalFileStore` (root from
  `LEXVAY_FILESTORE_PATH`, keys `tenant/document`, path traversal rejected) and `S3FileStore` (AWS SDK v2,
  bucket from `LEXVAY_S3_BUCKET`); selected by `LEXVAY_FILESTORE=local|s3`.
- Job infrastructure (`com.lexvay.job`): `JobRepository` (enqueue, claim with `FOR UPDATE SKIP LOCKED`
  under the transaction-local worker flag, complete, fail-or-requeue), `JobRunner` (polls on a fixed
  delay in production, `runPending()` called directly by tests, three attempts, handler runs under the
  job's tenant in its own transaction), `JobHandler` interface, `GET /api/v1/jobs/{id}`.
- Courses (D-039, needed so a syllabus can belong to a course version): `POST /api/v1/programs/{id}/courses`
  (code, title, required) creates the course and its first version for the program's catalog year;
  `GET /api/v1/programs/{id}/courses`. Course versions with `supersedes_id` arrive in M7.
- Upload: `POST /api/v1/programs/{id}/documents` multipart (`file`, `kind`, optional `courseVersionId`)
  for CONTRIBUTOR and ADMIN. Checks: non-empty, size limit (25 MB), kind not `RULE_TEXT`, file type by
  extension and magic bytes (PDF `%PDF-`, DOCX zip, TXT otherwise with `.txt`), course version belongs to
  the program. SHA-256 computed; duplicate per tenant and program → `409` naming the existing document.
  Stores the file, inserts `document`, enqueues `INGEST_DOCUMENT`, returns `202 {documentId, jobId}`.
- Ingest handler: PDFBox per page (CRLF normalized), POI for DOCX (one page, D-017), UTF-8 for TXT (one
  page). `document_page.char_start/char_end` are the page's offsets in the concatenated document text
  (pages joined by a newline); evidence offsets are relative to the page text (D-041). Inserts are
  `ON CONFLICT DO NOTHING` so a retry is idempotent. All pages blank → job fails "Document has no
  extractable text"; parser exception → "File could not be parsed". Audited as `DOCUMENT_INGESTED` by the
  system with the job id.
- `GET /api/v1/documents/{id}`: metadata, page summaries (number, offsets, length) and the latest
  ingest job status and error (D-040). Page text is not returned here.
- Multipart limits configured in `application.yml`; type limit tested now, size limit in M8.

Tests to write
- `TextExtractorTest` (unit): two-page PDF text and offsets, DOCX single page, TXT, CRLF normalization,
  blank PDF → empty, garbage bytes → parse error.
- `LocalFileStoreTest`: round trip, missing key, traversal key rejected.
- `S3FileStoreTest`: `S3Client` mocked with Mockito; verifies bucket, key and body (no AWS call).
- `JobRunnerTest`: claim marks RUNNING and increments attempts; a failing handler requeues twice then
  `FAILED` with the error; handler sees the job's tenant; a job from tenant A is invisible to tenant B via
  `GET /jobs/{id}`; unknown type fails cleanly.
- `DocumentUploadTest`: PDF, DOCX, TXT round trips (202, job runs, pages with expected text and offsets,
  sha256, file present in the store, GET shows pages and `DONE`); duplicate → 409 with existing id; empty
  file → 400; corrupt PDF → job FAILED "could not be parsed" visible on GET; image-only PDF → FAILED "no
  extractable text"; unsupported type → 400; `RULE_TEXT` kind → 400; reviewer → 403; other tenant's
  program → 404; course version from another program → 400; audit rows for upload and ingest.
- `CourseApiTest`: create, duplicate code → 409, list, 403 for reviewer, audit row.
- `Rule9_AuditCoverageTest` set extended with the two new endpoints.

Acceptance evidence to collect
- `make test` output; the upload test assertions for PDF, DOCX and TXT including hash and offsets;
  duplicate 409; empty and corrupt file results.

Decisions expected
- D-039 (course endpoints), D-040 (document GET shape and ingest status via job), D-041 (offset
  conventions), D-042 (S3 test double), AWS SDK pin added to D-002.

### Result (2026-11-26)

Built
- `com.lexvay.job`: `JobRepository` (enqueue, claim with `FOR UPDATE SKIP LOCKED` under the
  transaction-local worker flag, done/failed/requeue, latest-by-payload), `JobRunner` (handler runs in
  its own transaction under the job's tenant; three attempts; `PermanentJobFailure` fails at once;
  final failures audited as `JOB_FAILED`), `JobPoller` (fixed delay, off in tests), `JobHandler`,
  `GET /api/v1/jobs/{id}`.
- `com.lexvay.document`: `FileStore` with `LocalFileStore` (atomic writes, key validation) and
  `S3FileStore` (AWS SDK v2), `FileStoreConfig`; `FileType` detection by name and magic bytes;
  `TextExtractor` (PDFBox per page, POI DOCX, UTF-8 TXT; CRLF normalized; blank → empty error);
  `DocumentRepository` (idempotent page inserts), `DocumentService.upload/get`,
  `IngestDocumentHandler`, `DocumentController` (`POST /api/v1/programs/{id}/documents` → 202,
  `GET /api/v1/documents/{id}`), `DuplicateDocumentException` → 409 with `existingDocumentId`.
- Courses (D-039): `CourseRepository`, `CourseService`, `CourseController`
  (`POST/GET /api/v1/programs/{id}/courses`); creating a course creates its first version.
- Multipart limit 25 MB (`LEXVAY_UPLOAD_MAX_MB`), file store and job settings in `application.yml`.

Tests written and run (85 backend, 2 frontend)
- `TextExtractorTest` (7), `LocalFileStoreTest` (3), `S3FileStoreTest` (4, Mockito), `JobRunnerTest` (5),
  `CourseApiTest` (4), `DocumentUploadTest` (9: PDF with offsets, hash, file in store and audit rows for
  upload by user and ingest by system; DOCX and TXT as one page; duplicate → 409 with existing id; empty →
  400; corrupt PDF → job FAILED "could not be parsed"; blank PDF → FAILED "no extractable text";
  unsupported type and RULE_TEXT → 400; foreign course version → 400; reviewer 403, other tenant 404).
- `Rule9_AuditCoverageTest` set extended with the two new endpoints.

`make test` output (tail, real)
```
[INFO] Tests run: 85, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  1 passed (1)
MAKE TEST EXIT: 0
```

Acceptance evidence
- Upload of PDF, DOCX and TXT stores the file (`files.exists(tenant/document)`), the hash
  (`sha256` equals the test's own digest) and per-page text with offsets (page 2 `charStart` = page 1
  `charEnd` + 1; page texts contain the written lines): `DocumentUploadTest.pdfUploadIsIngestedWithOffsetsAndAudited`,
  `docxAndTxtAreIngestedAsOnePage`.
- Duplicate upload detected by hash: `duplicateUploadIsDetectedByHash` (409, problem detail carries the
  existing document id, even with a different filename).
- Empty file: `emptyFileIsRejected` (400 "File is empty"). Corrupt file:
  `corruptPdfFailsIngestWithClearError` (job FAILED, error "File could not be parsed as a PDF", no pages)
  and `corruptDocxIsRejectedAsUnparsable`.

Found and fixed during the milestone
- `ResponseEntityExceptionHandler` already maps `MaxUploadSizeExceededException` (413); a duplicate
  handler made the context fail to start. Removed.
- Java text blocks strip trailing spaces, so a `RETURNING ` followed by a concatenated column list became
  `RETURNINGid`; fixed with an explicit separator.
- The job queue is shared by all tests in the JVM; job tests now drain the queue first and assert on
  their own job's state rather than on processed counts.

Deferred
- Nothing from the M2 row. Upload size limit is configured; its test is scheduled for M8 as planned.

## Milestone 3 — Rules and requirements

### Plan (written 2026-11-26, before building)

Scope
- `com.lexvay.llm`: `LlmClient` interface (`complete(LlmRequest) → LlmResponse`; request carries
  purpose, a script key, system and user prompts, the JSON schema for the output and max tokens;
  response carries raw text, model, token counts and duration). `FakeLlmClient` (scripted by key, counts
  calls, fails loudly when no script exists; the only client tests use). `AnthropicLlmClient` (Anthropic
  Java SDK 3.14.0, model from `LEXVAY_LLM_MODEL`, effort from `LEXVAY_LLM_EFFORT`, structured output via
  `output_config.format` with the same schema, written from the SDK documentation). `LlmConfig` selects
  by `LEXVAY_LLM_PROVIDER`. `LlmCallRepository` stores every call in `llm_call` (D-014).
  `JsonSchemaValidator` validates model output against the bundled schema.
- Prompt and schema resources: `llm/extract-v1.system.txt`, `llm/requirement-extraction.schema.json`.
  Prompt version `extract-v1` is stored with every call.
- `com.lexvay.rule`: `POST /api/v1/rule-versions` (CONTRIBUTOR, ADMIN; jurisdiction code, license type,
  label, source URL, effective date, optional `supersedesId`, rule text) stores the text as a `RULE_TEXT`
  document with its page synchronously (D-044), enqueues `EXTRACT_REQUIREMENTS`, returns 202
  `{ruleVersionId, jobId}`. `GET /rule-versions`, `GET /rule-versions/{id}` (with extraction job
  status), `GET /rule-versions/{id}/requirements`, `PUT /rule-versions/{id}/requirements` (D-024;
  REVIEWER, SIGNER, ADMIN; unapproved only; audited with before and after),
  `POST /rule-versions/{id}/requirements/approve` (REVIEWER, SIGNER, ADMIN; requires at least one
  requirement; once).
- `ExtractRequirementsHandler`: calls the model, validates the JSON (schema plus unique refs and
  numeric-field consistency), on failure retries once with the validation errors appended, then fails
  the job permanently. Stores one `llm_call` row per attempt. Inserts requirements unapproved. Audited.
- Approval gate for M4: `RuleVersionRepository.findApprovedVersion(jurisdictionId, licenseType, date)`
  returns only approved versions (D-010); tested here, used by the pipeline in M4.
- `supersedesId` is stored and validated (same jurisdiction and license type); the change event and
  reopening it triggers are M7 scope and are listed there.

Tests to write
- `JsonSchemaValidatorTest` (unit): valid output passes; invalid JSON, unknown type, missing numeric
  fields for a numeric type, extra property, empty list each produce a readable error.
- `FakeLlmClientTest` (unit): scripted sequence per key, counting, missing script throws.
- `AnthropicLlmClientTest` (unit, no network): request built from `LlmRequest` carries the configured
  model, the schema as output format and the prompts; response mapping reads text and usage.
- `RuleVersionApiTest`: create → 202 and rule-text document with one page; extraction via Fake →
  requirements listed with types and numeric fields; invalid then valid → requirements present and two
  `llm_call` rows; invalid twice → job FAILED, no requirements, GET shows FAILED; approve → approved
  by/at and audit; approve twice → 409; approve with no requirements → 409; PUT before approval → 200
  with audit before/after; PUT after approval → 409 (`Rule5_ApprovedRequirementsRejectEditsTest`);
  PUT with duplicate refs or inconsistent numeric fields → 400; CONTRIBUTOR approve → 403; other tenant
  → 404; unknown jurisdiction → 400; `findApprovedVersion` empty before approval, present after.
- `Rule9_AuditCoverageTest` set extended with three endpoints.

Acceptance evidence to collect
- `make test` output; the retry test's two `llm_call` rows; the approval gate assertions.

Decisions expected
- D-044 (rule text entry as text, stored as a document synchronously), D-045 (schema validator
  choice given Jackson 3), D-046 (prompt and schema as classpath resources, versioned by name).

### Result (2026-11-26)

Built
- `com.lexvay.llm`: `LlmClient`, `LlmRequest`, `LlmResponse`, `LlmPurpose`; `FakeLlmClient` (scripted by
  key, call counting, loud failure when unscripted); `AnthropicLlmClient` (SDK 3.14.0, model and effort
  from `LEXVAY_LLM_MODEL`/`LEXVAY_LLM_EFFORT`, schema as structured-output format, refusal → exception,
  usage captured); `LlmConfig`; `JsonSchemaValidator` (networknt 3.0.9, errors prefixed with path);
  `LlmCallRecorder` (writes `llm_call` in its own transaction so failed jobs keep their raw output);
  resources `llm/extract-v1.system.txt`, `llm/requirement-extraction.schema.json`.
- `V005__llm_call_rule_version.sql`.
- `com.lexvay.rule`: `RuleVersion`, `Requirement`, `RequirementDraft`, `RequirementType`, `NumericField`,
  `RequirementListParser` (schema + semantic checks), `RuleVersionRepository` (incl.
  `findApprovedVersion` for D-010), `RuleVersionService`, `RuleVersionController`
  (`POST /rule-versions` → 202 with job, `GET /rule-versions`, `GET /rule-versions/{id}`,
  `GET/PUT /rule-versions/{id}/requirements`, `POST /rule-versions/{id}/requirements/approve`),
  `ExtractRequirementsHandler` (one retry with the validation errors, then permanent failure).

Tests written and run (109 backend, 2 frontend)
- `JsonSchemaValidatorTest` (5), `FakeLlmClientTest` (3), `AnthropicLlmClientTest` (2, request
  construction only, mocked client, no network), `RequirementListParserTest` (6), `RuleVersionApiTest`
  (7), `Rule5_ApprovedRequirementsRejectEditsTest` (1); `Rule9_AuditCoverageTest` set extended;
  `MigrationsApplyCleanlyTest` now expects 5 migrations.

`make test` output (tail, real)
```
[INFO] Tests run: 109, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  1 passed (1)
MAKE TEST EXIT: 0
```

Acceptance evidence
- Rule version entry: `createStoresRuleTextAndExtractsRequirements` (202, RULE_TEXT document with one
  page containing the rule text, extraction job QUEUED then DONE, 4 requirements with types and numeric
  fields, prompt contains the rule text and jurisdiction, schema passed to the model, one `llm_call`,
  audit `RULE_VERSION_CREATED` by the user and `REQUIREMENTS_EXTRACTED` by the system with the job id).
- Schema validation with one retry: `invalidOutputIsRetriedOnceWithTheErrors` (second prompt contains
  "previous output was rejected" and the field name; two `llm_call` rows; DONE with 4 requirements).
  `invalidOutputTwiceFailsTheJobAndKeepsBothCalls` (FAILED with "invalid output twice", zero
  requirements, two `llm_call` rows survive the rollback, approve → 409).
- Approval gate: `approvalIsAuditedAndHappensOnce` (`findApprovedVersion` empty before approval and for
  a date before `effective_from`, present after; second approval 409; audit by the reviewer).
  Unapproved requirements cannot be assessed: the pipeline in M4 obtains rule versions only through
  `findApprovedVersion`, which this test proves excludes unapproved versions.
- D-024: `reviewerCanReplaceRequirementsBeforeApproval` (200, audit with 4 before / 2 after, invalid
  body 400 naming the field); `Rule5_ApprovedRequirementsRejectEditsTest` (PUT 409 after approval, direct
  UPDATE and DELETE rejected by trigger, count unchanged).

Found and fixed during the milestone
- networknt 3.x renamed its API (`SchemaRegistry`/`Schema`/`Error`); written from the jar, not memory.
- The Anthropic SDK carries free-form JSON as `JsonValue` built on Jackson 2; the schema is converted
  from the Jackson 3 tree through a `Map`.
- Validator messages lacked the JSON location; now prefixed with the instance path.

Deferred
- Change events for a superseding rule version (M7). The `supersedesId` link is stored and validated now.

## Milestone 4 — Pipeline

### Plan (written 2026-11-26, before building)

Scope
- `QuoteVerifier` (document package): whitespace-normalized exact substring match with an index map back
  to the original page offsets (plan section 4.6). Used by evidence matching and by program facts.
- Program facts (D-006, D-036): `POST /programs/{id}/facts` (CONTRIBUTOR, ADMIN; key, value, documentId,
  pageNumber, quote → verified quote becomes the fact's evidence span), `GET /programs/{id}/facts` with
  the current (newest) fact per key flagged. Audited.
- `com.lexvay.assessment`: `Finding`, `MappingMethod`, `Assessment`, `AssessmentRepository`,
  `MappingRepository` (upsert keyed on assessment + requirement), `EvidenceSpanRepository`,
  `AssessmentService` (start: every target jurisdiction must have an approved rule version for the
  program's license type as of today, else 409 naming the jurisdictions; views), `AssessmentController`
  (`POST /programs/{id}/assessments` → 202, `GET /assessments/{id}` with per-jurisdiction counts,
  `GET /assessments/{id}/mappings?jurisdiction=`), `RunAssessmentHandler` (per-requirement
  transactions so a crash keeps finished work; assessment status RUNNING → FINISHED or FAILED).
- `assessment.rules.RulesEngine`: pure code, no model dependency; D-011 semantics with a calculation
  string; `Rule3_ArchitectureTest` scans the package's sources for any `com.lexvay.llm` import.
- `assessment.match`: `PassageSearch` interface with `PostgresPassageSearch` (OR of the requirement's
  words as a tsquery over the program's non-rule pages, top 8 by `ts_rank`), `EvidenceMatcher` (prompt
  `match-v1`, schema `llm/evidence-match.schema.json`; output rejected on schema failure, forbidden
  finding, unknown document or page → `EVIDENCE_MISSING` "Model output rejected"; quote verified by
  `QuoteVerifier` else `EVIDENCE_MISSING` "No verifiable passage found"; non-required course →
  `AMBIGUOUS`; quote shorter than `LEXVAY_MIN_QUOTE_CHARS` (40) → `AMBIGUOUS`; model id, prompt version,
  inputs and raw output stored per proposal).
- JaCoCo `check` at 85 % line coverage for `com.lexvay.assessment` packages, bound to `verify`.

Tests to write
- `Rule2_QuoteVerifierTest` (unit): exact, double spaces, newline wrap, leading/trailing space,
  fabricated, empty, first of several occurrences, offsets map to original text.
- `RulesEngineTest` (unit): all five operators true and false, missing fact, calculation string,
  `60` vs `60.0`.
- `Rule3_ArchitectureTest`, `Rule3_NoModelCallForNumericTest` (numeric-only rule set: fake call count
  for MATCH is zero, mappings are RULES_ENGINE with the calculation).
- `Rule1_FindingValuesTest` (scripted `CONFIRMED_GAP` output → rejected → `EVIDENCE_MISSING`, raw
  output kept in `llm_call`), `Rule2_FabricatedQuoteDiscardedTest`, `Rule4_ElectiveCourseForcesAmbiguousTest`,
  `Rule4_ThinQuoteForcesAmbiguousTest`.
- `AssessmentApiTest`: two jurisdictions; run to FINISHED; summary counts; mappings carry requirement,
  finding, method, rationale and evidence (filename, course, page, offsets, quote); re-running the
  handler does not duplicate mappings; 409 when a jurisdiction lacks an approved version; 404 for another
  tenant; no candidate passages → `EVIDENCE_MISSING`; model names a document outside the shortlist →
  rejected.
- `ProgramFactApiTest`: verified quote → 201 with span; fabricated quote → 400; newest fact per key is
  current; audit; roles.
- `Rule9_AuditCoverageTest` set extended with two endpoints.

Acceptance evidence to collect
- `make test` output; the four product-rule test classes; the JaCoCo check line for the assessment
  packages.

Decisions expected
- D-050 (assessment start requires an approved version for every target jurisdiction), D-051 (passage
  search query construction), D-052 (per-requirement transactions in the run), D-053 (mapping list
  response shape).

### Result (2026-11-26)

Built
- `QuoteVerifier` (whitespace-normalized exact match with original offsets), program facts
  (`POST/GET /programs/{id}/facts`, verified quote → evidence span, latest fact per key is current).
- `com.lexvay.assessment`: `Finding`, `MappingMethod`, `AssessmentStatus`, `Assessment`,
  `AssessmentRepository`, `MappingRepository` (insert-if-absent, joined read model),
  `EvidenceSpanRepository`, `AssessmentService` (start gate D-050, per-jurisdiction counts, mapping list
  D-053), `AssessmentController` (`POST /programs/{id}/assessments` → 202, `GET /assessments/{id}`,
  `GET /assessments/{id}/mappings?jurisdiction=`), `RunAssessmentHandler` (per-requirement transactions
  D-052, RUNNING → FINISHED/FAILED, audited).
- `assessment.rules.RulesEngine` (pure code, D-011), `assessment.match`: `PassageSearch` +
  `PostgresPassageSearch` (D-051), `EvidenceMatcher` (prompt `match-v1`, schema
  `evidence-match.schema.json`, rejection D-054, quote verification, elective and thin-quote downgrades),
  `MatchProposal`, `Passage`.
- JaCoCo `check` ≥ 85 % line coverage for the three assessment packages, bound to `verify`.

Tests written and run (143 backend, 2 frontend)
- `Rule2_QuoteVerifierTest` (6), `RulesEngineTest` (13), `Rule3_ArchitectureTest` (1),
  `Rule3_NoModelCallForNumericTest` (1), `Rule1_FindingValuesTest` (1), `Rule2_FabricatedQuoteDiscardedTest`
  (1), `Rule4_ElectiveCourseForcesAmbiguousTest` (1), `Rule4_ThinQuoteForcesAmbiguousTest` (1),
  `AssessmentApiTest` (6), `ProgramFactApiTest` (3); `Rule9_AuditCoverageTest` set extended.

`make test` output (tail, real)
```
[INFO] Tests run: 143, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
 Test Files  1 passed (1)
MAKE TEST EXIT: 0
```
JaCoCo line coverage: `com.lexvay.assessment` 91.1 % (175/192), `assessment.match` 91.7 % (100/109),
`assessment.rules` 95.2 % (20/21).

Acceptance evidence
- Rules engine and evidence matching run as jobs: `AssessmentApiTest.fullRunProducesMappingsWithEvidence`
  (202 + job, FINISHED, UT counts 3/2/1, `TOTAL_CREDITS 60 >= 60 → true` with the degree-plan quote,
  ethics match with document, course, page, offsets that reproduce the quote from the page text,
  `INTERNSHIP_HOURS 600 >= 700 → false`, telehealth `EVIDENCE_MISSING` without evidence, missing
  practicum fact rationale, exactly one model call per non-numeric requirement, prompt carries the
  passage ids, audit `ASSESSMENT_STARTED` by the user and `ASSESSMENT_FINISHED` by the system).
- Rule 1: `Rule1_FindingValuesTest` (scripted `CONFIRMED_GAP` → rejected → `EVIDENCE_MISSING`, raw
  output kept in `llm_call`, every stored finding is one of the three values).
- Rule 2: `Rule2_FabricatedQuoteDiscardedTest` (fabricated quote → "No verifiable passage found", no
  evidence span created; `AMBIGUOUS` without a quote → rejected), `Rule2_QuoteVerifierTest`.
- Rule 3: `Rule3_NoModelCallForNumericTest` (zero MATCH calls, all three mappings `RULES_ENGINE` with
  calculation strings), `Rule3_ArchitectureTest` (rules package has no model import), `RulesEngineTest`.
- Rule 4: `Rule4_ElectiveCourseForcesAmbiguousTest` (COUN 650 elective → `AMBIGUOUS` with reason),
  `Rule4_ThinQuoteForcesAmbiguousTest` (18-character quote → `AMBIGUOUS`).
- Also: idempotent re-run (`rerunningTheJobDoesNotDuplicateMappings`), start gate 409 naming TX only,
  no candidates → no model call, foreign document rejected, tenant scoping 404s.

Found and fixed during the milestone
- One test's second requirement shared no vocabulary with the syllabus, so the passage search correctly
  returned nothing; the test now exercises the intended path.

Deferred
- Review decision on mappings (M5), stale handling and re-run (M7), eval thresholds (M9).

## Milestone 5 — Review and signing

### Plan (written 2026-11-26, before building)

Scope
- `com.lexvay.review`: `ReviewKind`, `ReviewEvent`, `ReviewRepository` (append only; `created_at` from
  `clock_timestamp()` so order within a transaction is exact), `ReviewService`, `ReviewController`
  (`POST /mappings/{id}/reviews` for REVIEWER, SIGNER, ADMIN; kinds ACCEPTED, EVIDENCE_REQUESTED,
  GAP_CONFIRMED; `REOPENED` is system-only → 400; `GAP_CONFIRMED` needs a non-blank note → 400 and the
  database CHECK; stale mapping → 409 (D-055); mapping under a SIGNED determination → 409 (D-057)).
  Current decision (D-003) exposed on every mapping as `decision` and counted per jurisdiction on the
  assessment view (`decided`).
- `com.lexvay.determination`: `Outcome` (D-004), `Determination`, `DeterminationRepository`,
  `VersionsHash` (SHA-256 over the rule version id and the sorted course version ids, D-056),
  `DeterminationService` (SIGNER only; jurisdiction must be a program target; latest FINISHED assessment;
  every mapping of that jurisdiction non-stale with a current decision else 409 naming the count; no
  existing SIGNED determination for the pair else 409; snapshot of every course's newest version),
  `DeterminationController` (`POST /programs/{id}/determinations` → 201, `GET /programs/{id}/determinations`).
- JaCoCo gate widened to `com.lexvay.review` and `com.lexvay.determination`.

Tests to write
- `ReviewApiTest`: accept, request evidence, confirm gap with note; gap without note → 400 (API) and
  rejected by the database; user-sent REOPENED → 400; latest event wins; a REOPENED event removes the
  decision; stale mapping → 409; contributor → 403; other tenant → 404; audit row.
- `DeterminationApiTest` with `Rule1_OnlySignerSignsTest` (reviewer, admin, contributor → 403), 409 when
  undecided with the count in the detail, 409 without a finished assessment, 400 for a jurisdiction the
  program does not target, success fields (outcome, signer, SIGNED, rule version, assessment, course
  versions, hash), second signing → 409, review after signing → 409, list newest first, other tenant 404.
- `Rule6_VersionsHashTest` (unit: deterministic, order-independent, changes with any id) and
  `Rule6_DeterminationLockTest` (stored hash equals recomputation; outcome change rejected by the
  database; status change is the only permitted update).
- `Rule9_AuditCoverageTest` set extended with two endpoints.

Acceptance evidence to collect
- `make test` output; JaCoCo lines for review and determination; the 409 and lock assertions.

Decisions expected
- D-055 (no review on stale mappings), D-056 (hash recipe), D-057 (no review after signing),
  D-058 (one SIGNED determination per program and jurisdiction at a time).

### Result (2026-11-26)

Built
- `com.lexvay.review`: `ReviewKind`, `ReviewRequest`, `ReviewRepository` (append-only insert with
  `clock_timestamp()`), `ReviewService` (REOPENED system-only → 400; gap without note → 400; stale → 409
  D-055; signed → 409 D-057), `ReviewController` (`POST /mappings/{id}/reviews`, REVIEWER/SIGNER/ADMIN).
- Mapping read model carries `decision` (latest event unless REOPENED, D-003) with user name; assessment
  view carries `decided` per jurisdiction.
- `com.lexvay.determination`: `Outcome`, `DeterminationStatus`, `Determination`, `VersionsHash` (D-056),
  `SignRequest`, `DeterminationRepository` (course-version snapshot), `DeterminationService` (409 gates,
  D-058), `DeterminationController` (`POST /programs/{id}/determinations` SIGNER only → 201,
  `GET /programs/{id}/determinations`).
- JaCoCo gate now covers review and determination packages.

Tests written and run (153 backend, 2 frontend)
- `ReviewApiTest` (4), `DeterminationApiTest` (3), `Rule1_OnlySignerSignsTest` (1), `Rule6_VersionsHashTest`
  (1), `Rule6_DeterminationLockTest` (1); `Rule9_AuditCoverageTest` set extended.

`make test` output (tail, real)
```
[INFO] Tests run: 153, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
 Test Files  1 passed (1)
MAKE TEST EXIT: 0
```
JaCoCo line coverage: `assessment` 93.6 %, `assessment.match` 91.7 %, `assessment.rules` 95.2 %,
`review` 97.4 %, `determination` 96.4 %.

Acceptance evidence
- Review events and current decision logic: `ReviewApiTest.decisionsAreRecordedAndTheLatestWins`
  (ACCEPTED then GAP_CONFIRMED by a different user → latest wins; `decided` count 1 of 3; two audit rows),
  `reopenedIsSystemOnlyAndRemovesTheDecision` (user REOPENED → 400; a system REOPENED event clears the
  decision; a later ACCEPTED restores one).
- Gap note rule: `confirmingAGapRequiresANoteAtTheApiAndInTheDatabase` (400 for blank and missing; the
  CHECK constraint rejects a direct insert).
- Signing with 409 when incomplete: `DeterminationApiTest.signingIsRefusedWith409UntilEveryMappingHasADecision`
  ("3 of 3 requirements" then "1 of 3 requirements"; UT undecided → 409; TX not targeted → 400).
- Locked determinations and versions hash: `Rule6_DeterminationLockTest` (stored rule version and course
  versions reproduce the hash; outcome and hash updates and deletes rejected; status change allowed),
  `Rule6_VersionsHashTest`, `DeterminationApiTest.signerSignsWhenEveryMappingIsDecided` (hash equals
  recomputation; second signing 409; review after signing 409; list; other tenant 404).
- Only SIGNER signs: `Rule1_OnlySignerSignsTest` (reviewer, admin, contributor → 403).

Found and fixed during the milestone
- `listOfRows()` returns `java.sql.Timestamp`; the determination mapper now uses a typed `RowMapper`.
- Two unused repository methods and an unused record were removed (they were the only uncovered lines).

Deferred
- Reopening on change events and stale handling (M7); the Review screen (M6).

## Milestone 6 — Frontend, seed data, end-to-end test

### Plan (written 2026-11-26, before building)

Scope, in build order
1. Seed content (`seed/`): `manifest.yml` (tenant Northfield University, users Dana Whitfield SIGNER /
   Dr. Priya Raman REVIEWER / Jordan Ellis CONTRIBUTOR, program, 11 courses incl. COUN 660 without a
   syllabus, 14 documents, 3 facts, 5 "Demo rule set" rule versions with 42 requirements, Colorado v2 at
   700 hours, COUN 625 syllabus v2; second tenant Westbrook College with one program), document sources
   `seed/docs/*.md` rendered to text PDFs in `seed/pdf/` by a PDFBox generator (`make seed-pdfs`,
   PDFs committed), rule texts `seed/rules/*.md`, `seed/fake-llm.yml` (scripted extraction per label and
   match per requirement ref; documents named by filename and resolved to ids at load time).
2. Seed loader (`com.lexvay.seed`, Spring profile `seed`, `make seed`): runs through the real services
   with a programmatic security context for the seeded users, refuses to run twice for the same tenant
   name, runs ingest, extraction, approval and the assessment with `FakeLlmClient` scripted from the YAML.
   Target mix: 10 rules-engine, about 24 EVIDENCE_FOUND, about 5 AMBIGUOUS, 3 EVIDENCE_MISSING
   (state law UT and TX, telehealth CA).
3. Backend read endpoints the screens need: `GET /jurisdictions` (codes and names),
   `GET /change-events` (the `change` module's read side; events are created in M7).
4. Angular app: login page; left navigation with five screens; **Program overview** (header, four
   counts, jurisdiction table with stacked findings bar + text legend); **Review** (jurisdiction tabs,
   requirement list with finding and review status, detail with rule text, quote with citation, how it was
   checked, note, three actions, auto-advance, "Sign determination" enabled only when all decided and the
   user is a signer, dialog with the three outcomes); **Changes** (list of change events; re-run action
   wired in M7); **Evidence report** (per jurisdiction, "Draft. Not a determination" until signed;
   PDF download wired in M7); **Audit trail** (newest first). Permanent fictional-data banner, light and
   dark themes via tokens, keyboard operable, visible focus, state by icon shape and text, responsive to
   400 px, no "compliant" or "meets requirements" anywhere.
5. Tests: Vitest unit tests (decision view model, finding display, overview counts); Playwright
   end-to-end (`frontend/e2e`): log in as Dana Whitfield, open California, review every requirement
   (confirm the telehealth gap with a note), sign, open Audit trail and see the entries; `@axe-core/playwright`
   on every screen with no serious or critical issues; backend `ForbiddenWordsTest` (scans frontend
   sources and backend strings) and `SeedIsLabeledFictionalTest` (rule labels and PDF page 1).
6. `make seed`, `make e2e`; `make test` runs backend, frontend unit and end-to-end (starting the stack
   with the seed); CI updated accordingly.

Acceptance evidence to collect
- `make seed` output; `make test` output including the Playwright and axe results; screenshots are not
  required by the spec, so the recorded Playwright trace is the artefact.

Decisions expected
- D-060 (seed as a Spring profile), D-061 (document placeholders in fake scripts), D-062 (screens
  compute overview counts client-side), D-063 (change-events read endpoint in M6, writes in M7).

### Result (2026-11-26)

Built
- Seed: `seed/manifest.yml`, 14 document sources in `seed/docs` rendered to `seed/pdf` by
  `SeedPdfGenerator` (`make seed-pdfs`), 6 rule texts in `seed/rules`, `seed/fake-llm.yml` (6 extraction
  scripts, 31 match scripts), `SeedLoader` + `SeedRunner` (profile `seed`, `make seed`). Northfield
  University loads with 13 documents, 42 requirements and a finished assessment; Westbrook College with one
  program. Colorado revision 2 and the COUN 625 2027-28 syllabus are in the seed for M7.
- Backend: `GET /jurisdictions`, `GET /change-events` (read side), session view with `tenantName` and
  `demo` (D-064).
- Angular app: `ApiService`, `SessionService`, `ThemeService`, `authGuard`, `Shell` (banner, top bar,
  theme select, left navigation), `Login`, `Home`, `Overview`, `Review` (+ `SignDialog` on CDK Dialog),
  `Changes`, `Report`, `Audit`; labels module with glyph-and-text findings and plain outcome wording;
  tokens for light and dark themes; responsive table layout at 480 px.
- End-to-end: `playwright.config.ts`, `e2e/review-and-sign.spec.ts` (two tests, axe on every screen),
  `scripts/e2e.sh` isolated stack (5433/8081/4300), `make e2e`; `make test` = backend + frontend unit +
  e2e; CI installs the Chromium browser.

Tests written and run (160 backend, 8 frontend unit, 2 end-to-end)
- Backend: `SeedQuotesExistTest` (3), `SeedIsLabeledFictionalTest` (2), `SeedLoaderTest` (1),
  `ForbiddenWordsTest` (1). Frontend: `overview.spec.ts` (4), `labels.spec.ts` (3), `app.spec.ts` (1).
  End-to-end: review-and-sign flow; keyboard tabs and login accessibility.

`make test` output (tail, real)
```
[INFO] Tests run: 160, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
[2m Test Files [22m [1m[32m3 passed[39m[22m[90m (3)[39m
  2 passed (6.3s)
MAKE TEST EXIT: 0
```

Acceptance evidence
- All five screens work against the real API with seed data: exercised by the end-to-end test
  (overview counts and banner, review tabs and detail, changes list, evidence report draft/signed stamp,
  audit table) and by hand with `make dev` + `make seed` (login as Dana Whitfield through the proxy
  returned her profile; health 200; page served on :4200).
- End-to-end test uploads nothing, logs in as the signer, opens California, reviews every requirement
  (accepts found ones, requests evidence for the elective-based ambiguous one, confirms the telehealth gap
  with a note), signs "Determined not to meet" in the three-outcome dialog, and sees "Determination
  signed", "Review decision recorded" and `outcome: DOES_NOT_MEET` in the audit trail. Playwright: 2 passed.
- Accessibility: `@axe-core/playwright` with WCAG 2.0 A/AA and 2.1 AA tags on Login, Program overview,
  Review, Changes, Evidence report and Audit trail; zero serious or critical violations.
- Fictional banner: asserted on the overview after sign-in. Forbidden words: `ForbiddenWordsTest`
  across frontend sources, backend sources, prompts and seed; the end-to-end test checks rendered text.

Found and fixed during the milestone
- The seed profile had disabled the web runtime, which removed the `HttpSecurity` bean; it now runs on a
  random port instead.
- `container_name` in docker-compose collided with the isolated e2e project; removed.
- The login POST had no CSRF cookie yet; the session is loaded first so the cookie exists.
- Test selectors: "Review" also matched "Open review" links; the message locator also matched the
  "Current decision" paragraph; jurisdictions are alphabetical, not manifest order.

Deferred
- Change events and re-run action (M7); PDF download on the Evidence report (M7); upload size test (M8).

## Milestone 7 — Change tracking and report

### Plan (written 2026-11-26, before building)

Scope
- Rule version change: `POST /rule-versions` with `supersedesId` creates a `RULE_VERSION` change event
  (OPEN), sets every SIGNED determination on the old version to REOPENED (audited), and marks the old
  version's mappings stale. `POST /change-events/{id}/rerun` (REVIEWER, SIGNER, ADMIN) requires the new
  version to be approved (else 409); for every program assessed against the old version it adds mappings
  for the new requirements into that program's latest finished assessment: requirements with the same
  `ref`, text, type and numeric value are carried over (finding, evidence, rationale copied; the current
  decision replicated as a system review event noting the carry-over); changed or new ones are evaluated
  fresh (rules engine or matcher) and start undecided; old mappings of changed refs get a REOPENED event
  (D-015). The event becomes HANDLED.
- Current mappings: lists, counts and signing consider only mappings whose rule version has no approved
  successor with mappings in the same assessment, so a jurisdiction shows the new list once re-run and the
  old list until then (D-066).
- Course version change: `POST /courses/{id}/versions` (CONTRIBUTOR, ADMIN; catalog year, required,
  optional `supersedesId` defaulting to the newest version) creates a `COURSE_VERSION` change event, marks
  mappings citing the old version stale, and reopens determinations locked to it (D-029). Re-run
  re-verifies each stale mapping's quote against the new version's documents: found → new evidence span
  cited, `stale = false`; not found → `EVIDENCE_MISSING`, REOPENED event (D-028). 409 until the new version
  has an ingested document.
- `GET /change-events` enriched: kind, labels or course codes, old and new ids, status, and the
  determinations it reopened. Frontend Changes screen gains the re-run action and the affected list.
- Report: `POST /determinations/{id}/report` → 202 `{reportId, jobId}`; `GENERATE_REPORT` job renders a
  PDF with PDFBox (program, jurisdiction, outcome, signer, date, rule version, versions hash, course
  versions, then every requirement with finding, decision and quoted evidence with citation) into the file
  store; `GET /reports/{id}` streams the PDF when READY, otherwise returns the status as JSON. Evidence
  report screen gets "Generate PDF" and a download link after signing.
- Seed applies the manifest's `changes`: Colorado revision 2 (approved, 700 hours) and the COUN 625
  2027-28 syllabus, leaving two OPEN change events for the demo.
- JaCoCo gate widened to `com.lexvay.change`.

Tests to write
- `RuleVersionChangeTest`: event created, old mappings stale, signed determination REOPENED and audited;
  rerun before approval → 409; rerun: unchanged refs keep finding, evidence and decision via a system
  event, CO-5 changed → fresh `EVIDENCE_MISSING` without decision, old CO-5 mapping REOPENED; list shows
  the new version only; signing requires deciding the changed one; event HANDLED; second rerun → 409.
- `CourseVersionChangeTest`: event, stale marking, determination REOPENED; rerun before upload → 409;
  rerun with the quote present → new document cited, not stale, decision kept; quote absent →
  `EVIDENCE_MISSING`, REOPENED, decision gone.
- `ReportTest`: 202 + job; PDF READY, downloadable with `application/pdf`, text parsed back contains the
  outcome label, signer, requirement refs and a quote; other tenant 404; unsigned determination rules.
- `SeedLoaderTest` extended for the two change events; `Rule9_AuditCoverageTest` set extended with three
  endpoints; end-to-end: Changes screen shows the seeded events and re-runs the course change.

Decisions expected
- D-066 (current-mappings rule), D-067 (PDF renderer and layout), D-068 (course-version default
  supersedes), D-069 (report readiness response).

### Result (2026-11-26)

Built
- `ChangeService` (events, reopen, stale marking, re-run preconditions), `RerunChangeHandler` (rule
  version: carry over unchanged requirements with their decisions, re-evaluate changed ones; course
  version: re-verify quotes against the new documents), `ChangeEventView`, `POST /change-events/{id}/rerun`.
- `MappingProducer` shared by the assessment run and the re-run; current-mappings rule (D-066) in
  `MappingRepository` and the assessment counts.
- `POST /courses/{id}/versions` (D-068). Report module: `ReportService`, `GenerateReportHandler`,
  `PdfTextWriter`, `POST /determinations/{id}/report`, `GET /reports/{id}` (D-067, D-069).
- Seed applies Colorado revision 2 and the COUN 625 2027-28 syllabus, leaving two OPEN change events.
- Frontend: Changes screen with affected determinations and re-run action; Evidence report "Generate PDF"
  and download link. End-to-end stack is slot-based (`LEXVAY_E2E_SLOT`).

Tests written and run (163 backend, 8 frontend unit, 3 end-to-end)
- `RuleVersionChangeTest`, `CourseVersionChangeTest`, `ReportTest`; `SeedLoaderTest` and
  `Rule9_AuditCoverageTest` extended; end-to-end test for the Changes screen re-run.

`make test` output (tail, real)
```
[INFO] Tests run: 163, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
 Test Files  3 passed (3)
  3 passed (8.7s)
MAKE TEST EXIT: 0
```
JaCoCo line coverage: assessment 94.2 %, assessment.match 91.7 %, assessment.rules 95.2 %, review 100 %,
determination 96.4 %, change 97.9 %.

Acceptance evidence
- New rule version: change event OPEN, signed determination REOPENED and audited, old mappings stale and
  unreviewable (409), re-run refused until approval (409); after re-run the unchanged requirements keep
  finding, evidence and decision ("Carried over from ..."), the changed one is `TOTAL_CREDITS 60 >= 66 →
  false` without a decision, the event is HANDLED, signing needs the new decision, and a new determination
  can be signed against the new rule version (`RuleVersionChangeTest`).
- New course version: change event, stale mappings, determination REOPENED; re-run refused until the new
  syllabus is uploaded; quote still present → re-cited to the new document with the decision kept; quote
  gone → `EVIDENCE_MISSING`, back to review (`CourseVersionChangeTest`).
- PDF report generated as a job, downloadable as `application/pdf`; parsed text contains the outcome,
  signer, requirement refs, decisions, quote and versions hash (`ReportTest`).

Deferred
- Milestones 8 and 9 continue as work items WS-SEC and WS-EVAL in `docs/ROADMAP.md` (CTO mode, D-071).

## WS-EVAL — Evaluation harness and Terraform (PR #11)

Branch `eval-and-infra`, slot 3, run 2026-11-26 on macOS arm64 (Colima), after merging `origin/main`.
Decisions: D-100 to D-109 in `DECISIONS.md`.

### `LEXVAY_E2E_SLOT=3 make eval` (scripted model), exit 0

```
Lexvay evaluation (fictional seed data)
  model   fake-llm (scripted; token counts are estimates)
  tenant  Northfield University (evaluation 6718a7ca)
  golden  42 requirements

Metric                                        Result      Count   Limit        Status
False EVIDENCE_FOUND rate                      0.0 %     0 / 33   <= 0.0 %     ok
Recall on golden EVIDENCE_FOUND              100.0 %    33 / 33   >= 100.0 %   ok
Quote verification                           100.0 %    31 / 31   = 100.0 %    ok
Numeric accuracy (rules engine)              100.0 %    11 / 11   >= 100.0 %   ok
Requirement extraction agreement             100.0 %    42 / 42   >= 100.0 %   ok
Evidence agreement (document, quote)         100.0 %    39 / 39   >= 100.0 %   ok

Model calls                                36
Input tokens                               50,781
Output tokens                              4,672
Cost per assessment (USD)                  n/a (LEXVAY_LLM_PRICE_*_PER_MTOK not set)
Wall time per assessment                   0.4 s

RESULT: pass
```

### A wrong golden entry fails the run

In a copy of `eval/` (the committed file was not touched) UT-2 was changed from EVIDENCE_FOUND to
EVIDENCE_MISSING and the real process was run with `LEXVAY_EVAL_DIR` pointing at the copy. The jar exited 1
(make reports `Error 1`):

```
False EVIDENCE_FOUND rate                      3.0 %     1 / 33   <= 0.0 %     FAIL
Disagreements with the golden set
  UT/UT-2    false EVIDENCE_FOUND   expected EVIDENCE_MISSING, got EVIDENCE_FOUND
RESULT: FAIL
  - false EVIDENCE_FOUND rate 3.0 % exceeds 0.0 %
make: *** [eval] Error 1
```

The same is proved in `EvalServiceTest` (`aWrongGoldenEntryFailsTheRunSoTheProcessWouldExitNonZero`, and a recall
case) and, for every metric, in `EvalScorerTest`.

`make eval-live` without `ANTHROPIC_API_KEY`: "eval-live: ANTHROPIC_API_KEY is not set, so no real model can be
called. Nothing was run." exit 0. It was not run against the real model.

### `make infra-validate`, exit 0

Terraform v1.17.2 on darwin_arm64, `hashicorp/aws` 6.74.0, `hashicorp/random` 3.9.1.

```
terraform -chdir=infra fmt -check -recursive        (no output, exit 0)
terraform -chdir=infra init -backend=false -input=false   Terraform has been successfully initialized!
terraform -chdir=infra validate                     Success! The configuration is valid.
terraform -chdir=infra test
  run "defaults_are_private_encrypted_and_versioned"... pass
  run "one_nat_gateway_per_zone_when_asked"... pass
  run "rejects_unknown_environment"... pass
  run "rejects_the_latest_image_tag"... pass
  run "rejects_a_certificate_that_is_not_acm"... pass
  run "rejects_a_database_that_is_not_postgresql_18"... pass
  run "rejects_a_storage_limit_below_the_initial_size"... pass
  run "rejects_a_vpc_too_small_to_split"... pass
Success! 8 passed, 0 failed.
```

`terraform test` uses mocked providers (no credentials, nothing created). `plan` and `apply` were never run.

### `LEXVAY_E2E_SLOT=3 make test`, exit 0

```
[INFO] Tests run: 237, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
 Test Files  3 passed (3)
      Tests  8 passed (8)
  3 passed (13.4s)
```

New tests: `EvalScorerTest` (9), `GoldenFileTest` (3), `EvalServiceTest` (3), `EvalModelTest` (1),
`FakeLlmClientTest.replaceScriptDropsEarlierResponsesForTheKey`. `SeedLoaderTest` and `RuleVersionDiffSeedTest` now pass
`ScriptedFakeModel`.

### Not done or not verified

- No run against the real model: no API key. The live thresholds for recall, extraction and evidence agreement are
  first guesses (D-104).
- Terraform was never planned or applied. RDS parameter group family `postgres18` and the CloudFront/ALB wiring are
  validated for schema only. Creating the `lexvay_owner` and `lexvay_app` roles on the new database is a manual step
  (D-108).
- The Playwright run logs a `TypeError: Cannot read properties of null (reading 'id')` from
  `frontend/src/app/screens/review/review.ts:82` during the review test; the test passes. Not touched (frontend).

## WS-SEC — OIDC and hardening (PR #12; spec milestone 8)

Branch `sec-oidc-hardening`, merged with `origin/main` (including PRs 8 to 11) on 2026-11-26. Decisions D-090 to D-099
are in `DECISIONS.md` (D-090 to D-099).

### What was built

| Spec item | Built | Tests |
|---|---|---|
| 1 OIDC | Profile `oidc`: `OidcLogin`, `LexvayOidcUserService`; role and tenant from `app_user` by verified email; 403 "No Lexvay account for this identity"; `LOGIN_SUCCEEDED` with `method: oidc`; 401 (no redirect) for API calls without a session | `OidcLoginTest` (17, full code flow against a loopback provider), `AuthTest` |
| 2 Rate limiting | `RateLimitFilter`: login 10/min per address, API 600/min and uploads 30/min per user, 429 with `Retry-After`, Caffeine cache of 10 000 keys with 10 minute idle expiry, `lexvay.ratelimit.*` | `RateLimitFilterTest` (11, fake clock), `RateLimitApiTest` (4), `ForwardedHeadersTest` (2) |
| 3 Headers and cookie | `SecurityHeadersFilter` (exact CSP, nosniff, no-referrer, Permissions-Policy, HSTS on secure requests); session cookie HttpOnly, SameSite=Lax, Secure by `LEXVAY_COOKIE_SECURE` | `SecurityHeadersTest` (21), `SessionSecurityTest`, `SecureCookieTest` |
| 4 Upload limits | 413 problem detail over the limit; DOCX zip bomb guard (50 MiB or ratio 100); PDF page cap 500; file name checks | `UploadSizeLimitTest`, `ContentLimitsTest` (10), `DocumentUploadTest` (13) |
| 5 Validation table | 111 requests covering every endpoint, plus a guard that fails when an endpoint has no row | `InputValidationTableTest` |
| 6 Session fixation | `changeSessionId` explicit; id change tested over a real connection for form login and OIDC | `SessionSecurityTest`, `OidcLoginTest` |

`Rule9_AuditCoverageTest` needed no change: no mutating endpoint was added (the OIDC callback and login are
security filters). The merged-in endpoints from main (evidence responses, evidence requests, rule version diff,
search) have rows in the validation table.

### Bugs the validation table found (all fixed, none whitelisted)

1. A NUL character in any stored string (program name, rule text, review note, requirement title) gave 409 or 500:
   `ApiExceptionHandler` maps SQLSTATE class 22 to 400.
2. `numeric_value: 1e400` in the requirements PUT threw `JsonNodeException` (500): parser requires a finite number.
3. Requirement `ref`, `title` and `text` had no size limit (300 000 characters accepted): 50, 200, 20 000.
4. The `jurisdiction` filter of `GET /assessments/{id}/mappings` took any string: `@Pattern`.
5. File names of 5 000 characters or with control characters were accepted (a NUL name reached the database after
   the file was stored): 400.
6. A NUL in the login username or OIDC email logged a database stack trace: `findForLogin` returns empty.

### Real output

`LEXVAY_E2E_SLOT=2 make test` from the repository root (backend `./mvnw verify`, frontend unit tests, end to end):

```
[INFO] Tests run: 445, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
 Test Files  3 passed (3)
      Tests  8 passed (8)
e2e: starting backend on :8083
  ✓  1 e2e/review-and-sign.spec.ts:13:1 › Dana Whitfield reviews every California requirement, signs, and sees the audit entries (4.3s)
  ✓  2 e2e/review-and-sign.spec.ts:86:1 › the review tabs are keyboard operable and the login page is accessible (993ms)
  ✓  3 e2e/review-and-sign.spec.ts:103:1 › the Changes screen lists the seeded changes and re-runs matching for the new course version (2.2s)
  3 passed (11.3s)
```

New or extended test classes (from the same run):

```
Tests run: 11 auth.RateLimitFilterTest      Tests run: 4  auth.RateLimitApiTest
Tests run: 21 auth.SecurityHeadersTest      Tests run: 3  auth.SessionSecurityTest
Tests run: 1  auth.SecureCookieTest         Tests run: 2  auth.ForwardedHeadersTest
Tests run: 17 auth.OidcLoginTest            Tests run: 111 common.InputValidationTableTest
Tests run: 10 document.ContentLimitsTest    Tests run: 13 document.DocumentUploadTest
Tests run: 2  document.UploadSizeLimitTest  Tests run: 10 auth.AuthTest
```

Mutation check: setting `sessionFixation` to `none()` makes `SessionSecurityTest.loginChangesTheSessionId` fail.

### Notes for the orchestrator

- `InputValidationTableTest.everyEndpointHasARow` fails for any branch that adds an endpoint without a table row;
  branches merged after this one must add rows.
- Production behind a load balancer needs `LEXVAY_FORWARD_HEADERS=native` (D-093), otherwise the login budget is one
  shared bucket.
- `scripts/e2e.sh` sets the login budget to 1000 (the browser tests sign in repeatedly from one address).

## WS-UI — Design system and screen rebuild (PR #15)

PrimeNG 22.1.5 with `LexvayPreset` (tokens once as light-dark pairs), Phosphor icons, IBM Plex self-hosted; shell,
sign-in and the five screens rebuilt; three-pane review with J/K/A/R/G/? shortcuts; axe in both themes on every
screen, dialogs and the 400 px view. `LEXVAY_E2E_SLOT=1 make test` after the WS-SEC merge: 449 backend tests,
27 frontend unit tests, 6 end-to-end tests passed (one screenshot test skipped unless `LEXVAY_SCREENSHOTS=1`).
Initial bundle 672.8 kB raw, 161.9 kB estimated transfer. Backend addition: `GET /api/v1/users`. Open owner
action: the PrimeUI license key (D-080). Screenshots in `docs/screens/`.

## WS-PERF — Read-path latency and read APIs (PR #14)

One connection per read request (tenant bound on checkout only, `Rule7_PooledConnectionRebindsTest`), read
services in one read-only transaction, N+1 removed from determinations, change events and the rule version list
(13 → 1 connections), new read endpoints `GET /programs/{id}/overview`, `GET /programs/{id}/assessments`,
`GET /documents/{id}/pages/{n}` (immutable private cache, ETag, 304) and `GET /mappings/{id}/reviews`, audit
filters, gzip over 1 kB, nine planner-verified indexes in V008 (`IndexUsageTest`), `make perf` with
`EndpointLatencyTest` (every p95 under 40 ms on the seeded and the 62-tenant database; tables and method in
`docs/PERF.md`). Final run: 487 backend tests, 6 end-to-end tests passed. Decisions D-110 to D-119.

## F11 — OCR for scanned documents (PR #13)

`OcrEngine` seam with a Tesseract CLI engine (`LEXVAY_OCR=tesseract`; `FakeOcrEngine` in tests), V009 adds
`document_page.text_source` and `ocr_confidence`; a PDF page's text layer is never discarded, OCR replaces it only
when the layer is under 20 characters and OCR yields at least 20; evidence cited from an OCR page under 80 %
confidence is downgraded to AMBIGUOUS through the ordered `DowngradeRule` list (`Rule4_LowOcrConfidenceForcesAmbiguousTest`).
Real-binary test against Tesseract 5.5.5 (31 words, mean confidence 96.29). Final run after merging main: 531
backend tests, 6 end-to-end tests passed. Decisions D-160 to D-169.

## F6 — Production packaging and observability (PR #16)

Three-stage `Dockerfile` (Angular bundle, layered jar, JRE runtime as a non-root user with a read-only root
filesystem), `make image` and `make smoke` (`scripts/smoke.sh`, 31 checks: health, login, upload, assessment,
metrics, graceful stop), `/livez` and `/readyz` on the main port and actuator on a management port with its own
security chain, request ids, ECS structured logs in profile `prod`, Prometheus metrics (`lexvay_job_*`,
`lexvay_llm_*` with `purpose`, `lexvay_mapping_finding_total`, `lexvay_quote_verification_total`; `MetricsTest`
proves no id, email or quote text in a scrape), `GET /assessments/{id}/usage` with cost estimate, V010 index on
`llm_call.assessment_id`, compose `app` profile, `docs/OPERATIONS.md`. Final run on the merged tree: 589 backend
tests, 6 end-to-end tests, smoke passed; image 216 MiB, cold start to healthy 6 s. Decisions D-140 to D-149.

## F12 — Rule source monitoring (PR #19)

Branch `feat-rule-sources`, migration `V011__rule_source.sql`, decisions in `DECISIONS.md (D-180 to D-189)` (D-180 to D-189).
Backend only. Tested after merging `origin/main` (WS-PERF, F11 and F6 included, V010 on main) on 2026-11-26.

### What exists

- Schema `rule_source`, `rule_source_check` (RLS, append-only checks, source updates limited to `active`, `last_checked_at`,
  `last_content_hash`) and two worker-flag policies for the scheduler's cross-tenant statement.
- `HttpFetcher` seam, `JdkHttpFetcher` (3 redirects, every hop through the `SsrfGuard`, 20 MB cap, 10 s connect, 30 s
  whole response), `FakeHttpFetcher` and `FakeHostResolver` in tests.
- `FileType.HTML` (fetched content only) with jsoup 1.24.1, page splitting at 4 000 characters.
- Job `CHECK_RULE_SOURCE`: UNCHANGED, CHANGED, DRAFT_EXISTS, FETCH_FAILED; a changed page becomes an unapproved draft that
  supersedes the current approved version, through `RuleVersionService`, so extraction and the change event follow.
- `RuleSourceScheduler` (off unless `LEXVAY_RULE_SOURCES_ENABLED=true`), API under `/api/v1/rule-sources`, inert seed source.

### Test evidence

`make backend-test` (`./mvnw verify`, JaCoCo checks included):

```
[INFO] Tests run: 719, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

`LEXVAY_E2E_SLOT=8 make e2e`:

```
  ✓  4 e2e/review-and-sign.spec.ts:160:1 › the theme choice persists across a reload (1.4s)
  ✓  5 e2e/review-and-sign.spec.ts:175:1 › at 400 px the review screen fits without horizontal page scroll (1.8s)
  ✓  6 e2e/review-and-sign.spec.ts:191:1 › the Changes screen lists the seeded changes and re-runs matching for the new course version (3.1s)
  -  7 e2e/screens.spec.ts:23:1 › screenshots of every screen in light and dark
  1 skipped
  6 passed (22.1s)
```

(`screens.spec.ts` is skipped by its own design; it takes screenshots on request.)

`make perf` (read-path budget, p95 under 40 ms): the two new read endpoints, seed only and with 30 renamed copies of both
tenants: `GET /rule-sources` p95 1.43 ms and 0.93 ms, `GET /rule-sources/{id}/checks` p95 1.07 ms and 0.97 ms, one
connection each (`ReadPathConnectionsTest` covers both). `BUILD SUCCESS` for `EndpointLatencyTest` and `IndexUsageTest`.

### New test classes

`SsrfGuardTest`, `JdkHttpFetcherTest`, `HtmlTextTest`, `FileTypeTest`, `CheckRuleSourceTest`, `RuleSourceApiTest`,
`RuleSourceEnqueueTest`, `RuleSourceSchedulerLoopTest`, `Rule7_RuleSourceIsolationTest`, `Rule5_RuleSourceImmutabilityTest`;
rows in `InputValidationTableTest` (all five endpoints) and `Rule9_AuditCoverageTest` (three mutating endpoints); the seed
assertion in `SeedLoaderTest`.

### Open points

See D-189: pagination wording of the spec, scheduler package, retry delay after a failed check, DNS rebinding, no screen yet.

## F7 — Retrieval quality (PR #17)

Branch `retrieval-quality`, slot 3, run 2026-11-26 on macOS arm64 (Colima), after merging `origin/main` (PR #12). Decisions: `DECISIONS.md (F7)`
(D-170 to D-179). Full numbers per query: `docs/RETRIEVAL.md`, rewritten by `make eval`.

### What was measured

Corpus: 27 fictional documents (the 14 seed documents and 13 added syllabi of four pages each, every one ending in the same policy page).
35 queries: 29 count toward the limits and 6 are lexical gaps, reported apart. Both rankers ran over the same queries through the production
`PassageSearch`, excerpts included.

| Metric (29 counted queries) | ts_rank baseline | BM25 | Limit |
|---|---|---|---|
| Recall@8 | 96.6 % (28 / 29) | 96.6 % (28 / 29) | >= 95 %, not below baseline |
| Recall@3 | 86.2 % (25 / 29) | 89.7 % (26 / 29) | not below baseline |
| Mean reciprocal rank | 0.796 | 0.766 | none |
| Excerpt coverage | 100 % (28 / 28) | 100 % (28 / 28) | 100 % |

| Lexical gaps (6 queries, never thresholded) | ts_rank baseline | BM25 |
|---|---|---|
| Recall@8 | 33.3 % (2 / 6) | 33.3 % (2 / 6) |
| Recall@3 | 16.7 % (1 / 6) | 16.7 % (1 / 6) |

Reading these numbers honestly: BM25 improves recall@3 by one query in 29 and loses 0.03 of mean reciprocal rank, so on this corpus it is not
clearly better than the baseline, only not worse where the limits look. The corpus is small (about 100 pages) and a shortlist of eight covers a
large part of it, so recall@8 cannot separate the rankers; the limit that matters in practice is that the shortlist holds the page, and both do.
Neither ranker reaches a lexical gap (4 of 6 are not in the top eight), which is the case for a synonym-aware retriever later. The one counted
query BM25 misses (R13, "a course in which counseling students learn to read and apply research") is also one the baseline misses.

The existing pipeline metrics on the seed are unchanged: 0.0 % false EVIDENCE_FOUND (0 / 33), recall 33 / 33, quote verification 31 / 31,
numeric accuracy 11 / 11, extraction agreement 42 / 42, evidence agreement 39 / 39.

Latency (`PassageSearchLatencyTest`, synthetic 500-page program, one BM25 statement with headlines, 200 requests after a warm-up): p50 9 ms and
p95 15 ms alone; on this shared machine (load average about 6) 12 to 17 ms p50 and 15 to 22 ms p95. With the tables not analyzed the planner chose
nested loops and p95 was 37 ms; autovacuum analyzes real tables, so the test runs `ANALYZE` after loading (D-173).

### Tests added

`PassageRankingTest` (BM25 scores asserted to 1e-6 against hand computations on three tiny corpora, title boost, idf, ties, empty query, quotes
in a requirement, baseline ranker, tenant isolation), `PassageExcerpterTest` (short page, long page, header budget, fragment not found, blank
headline, merging, whitespace, out-of-order fragments), `ExcerptMatchingTest` (the prompt carries the excerpt and not the page; a quote from an
excerpt verifies; a quote spanning `[...]` is discarded), `RetrievalScorerTest`, `RetrievalFileTest` (queries tied to the committed PDFs,
corpus labelled fictional, shared policy page, PDFs not stale), `RetrievalServiceTest` (limits met; a broken expectation fails them),
`PassageSearchLatencyTest` (tag `perf`), and `GoldenFileTest` now checks the retrieval limits.

### Test tails

See the pull request description for the output of `make backend-test`, `make eval` and `LEXVAY_E2E_SLOT=3 make e2e`.

## F13 — Work queue API (PR #23)

Branch `feat-work-queue`, backend only. Decisions D-190 to D-199 in `DECISIONS.md (F13)`.

### Delivered

- `GET /api/v1/work-queue` and `GET /api/v1/work-queue/count`, package `com.lexvay.workqueue`: one SQL statement per
  endpoint (CTEs plus one `UNION ALL` branch per kind, role bound as `:role`), one read-only transaction.
- `MappingRepository.LATEST_EVENT`, `NEEDS_DECISION`, `UNDECIDED` shared by `countUndecided` and the queue;
  `ChangeEventRepository.AFFECTS_PROGRAM` shared by the program overview's open changes and the queue.
- No migration: `EXPLAIN (ANALYZE, BUFFERS)` as the application role shows no sequential scan on `mapping`,
  `review_event` or `job` (`IndexUsageTest` asserts it for every role).
- Read-only endpoints: nothing added to `Rule9_AuditCoverageTest`.

### Tests (all with the real stack, no network)

- `WorkQueueApiTest` (8): kinds visible per role and their order (a contributor never sees READY_TO_SIGN); ids and
  counts per kind; counts move with accept, request evidence, confirm gap, answer, change event open, re-run and
  signature; READY_TO_SIGN parity with `POST /determinations` over every program and jurisdiction pair (201 exactly
  for the listed ones, 409 for the rest, items gone after signing); another tenant sees nothing; oldest first and the
  20-id cap; stale evidence requests counted as blocked; approval and extraction failure; 401.
- `InputValidationTableTest`: 200 and 401 rows for both endpoints (its harness gained an anonymous actor).
- `ReadPathConnectionsTest`: both endpoints use exactly one connection. `ForbiddenWordsTest` green.
- `EndpointLatencyTest` and `IndexUsageTest` (perf tag): see `docs/PERF.md`, "Work queue (F13)".

### Runs after merging origin/main (F11 and F6 included)

- `make backend-test` (re-run after the final merge of main): `Tests run: 731, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- `make perf` (twice, both `BUILD SUCCESS`): work queue and count p95 3.05 and 2.73 ms (seed), 5.80 and 15.36 ms (62 tenants); second run 14.15 and 4.22 ms, 3.74 and 3.77 ms.
- `LEXVAY_E2E_SLOT=9 make e2e`: 6 passed, 1 skipped (the screenshot spec), exit 0.

## F3 — Rule workspace screens (PR #22)

Branch `feat-rule-workspace`, end-to-end slot 10 (PostgreSQL 5443, backend 8091, web 4310). Decisions: `DECISIONS.md (F3)`
(D-210 to D-219).

### What was built
- **Route and rail**: `programs/:programId/rules` and `.../rules/:ruleVersionId` (one route, lazy chunk `rules`); "Rules" after
  Changes with `phosphorScales` and the badge "n rule versions waiting for approval" from `ruleVersionStatus`.
- **Version list**: grouped PrimeNG Listbox (program jurisdictions first, then others), status Tag with icon and word for
  Extracting (animated spinner), Extraction failed (error as helper text), Needs approval, Approved, Superseded; one
  `GET /rule-versions`, polled every 5 s only while a version is extracting and the tab is visible.
- **New rule version** dialog (contributors and administrators): jurisdiction, license type (program default), label,
  source URL, effective date (DatePicker, ISO), supersedes (approved versions of the same jurisdiction and license type,
  with the helper text), rule text with a character counter; inline field problems, server details mapped to fields;
  202 closes the dialog, toasts "Extraction started" and opens the new version.
- **Version detail**: label, jurisdiction, license type, effective date, source link, status, approval line; tabs
  Requirements (table; in-place edit grid for reviewers, signers and administrators before approval; save, discard,
  add, move, delete; server row errors under their row; approval dialog with focus return), Rule text (WS-PERF page
  endpoint, page navigation), Compare (default against the superseded version, Select for any other approved version,
  summary line with icons, status Tags and "ref changed", word diffs in `ins`/`del`, numeric before and after, rule
  text diff in an Accordion; the first-version sentence).
- **No backend change.** The rule text uses `GET /documents/{id}/pages/{n}`, which reached `main` (WS-PERF) during the work.

### Tests
- Vitest: `core/rules.spec.ts` (every status, precedence, badge count, polling predicate, grouping and order, comparable
  versions), `screens/rules/requirement-list-editor.spec.ts` (add, move, delete, dirty, restore, PUT body, problem
  detail to row mapping), `screens/rules/rule-version-form.spec.ts` (required fields, limits, problem detail to field),
  `core/labels.spec.ts` (new labels free of the forbidden words, distinct registered icons).
- Playwright `e2e/rules.spec.ts` (axe in light and dark on every state, at 1280 and 400 px; no request leaves localhost):
  the seeded Colorado revision read-only with exactly one Changed row (CO-5, numeric and text, `ins`/`del`), keyboard
  selection in the list, a contributor's new version through Extracting to Extraction failed, a reviewer's hand-entered
  list with a server row error, save, title edit, reload, approval through the dialog and the audit entries.
- Deviation from spec point 7 (D-219): the seed approves the Colorado revision and the e2e backend's FakeLlmClient has
  no extraction scripts, so the reviewer flow runs on a version entered in the test and extraction ends in
  Extraction failed rather than Needs approval.

`make frontend-test`:
```
 Test Files  8 passed (8)
      Tests  54 passed (54)
```

`LEXVAY_E2E_SLOT=10 make test` (backend, frontend unit, end-to-end):
```
[INFO] Tests run: 589, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  8 passed (8)
      Tests  54 passed (54)
e2e: starting backend on :8091
  ✓   7 e2e/rules.spec.ts:26:1 › a reviewer reads the seeded Colorado revision: its list, its rule text and what it changed (4.0s)
  ✓   8 e2e/rules.spec.ts:92:1 › a contributor enters a new rule version and watches its extraction (10.3s)
  ✓   9 e2e/rules.spec.ts:143:1 › a reviewer enters the list by hand, corrects it, saves, reloads and approves it (9.3s)
  ✓  10 e2e/rules.spec.ts:224:1 › at 400 px the rule workspace stacks without horizontal page scroll (3.1s)
  -  11 e2e/screens.spec.ts:9:1 › screenshots of every screen in light and dark
  1 skipped
  10 passed (52.2s)
```

### Screenshots (`docs/screens/`, light and dark, 1440 x 900)
`rules-list`, `rules-edit` (edit grid with a server row error corrected), `rules-approve` (approval dialog),
`rules-compare` (the CO-5 change), `rules-new-version` (entry dialog). Taken with
`LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=10 make e2e`, through the `shoot` helper now shared in `e2e/support.ts`.

### Follow-ups
- Orchestrator decision for the spec's original e2e scenario (D-219): leave the seeded revision unapproved, or script
  FakeLlmClient at runtime from `seed/fake-llm.yml` for the e2e stack.
- When F1's `DocumentViewer` merges, the Rule text tab should use it (read-only) instead of its own frame.
- Choosing another version while the edit grid has unsaved changes discards them without asking.
- The existing `docs/screens` images of other screens predate the Rules rail entry; regenerate them with the next
  screenshot run.

## F1 — Evidence viewer with in-context highlight (PR #21)

Branch `feat-evidence-viewer`, merged with `origin/main` (including F11 #13 and F6 #16) on 2026-11-26. Decisions D-220
to D-229 are in `DECISIONS.md (F1)`.

### What was built

| Spec point | Built | Tests |
|---|---|---|
| 1 Backend | `GET /api/v1/documents/{id}/file`: stored bytes, media type of the stored file type, `inline` with RFC 5987 file name, `private, max-age=31536000, immutable`, ETag = document sha256, 304 before the store is read, 404 for another tenant, any role, one statement | `DocumentFileApiTest` (8), 3 rows in `InputValidationTableTest`, `ReadPathConnectionsTest` (one connection); `Rule9_AuditCoverageTest` unchanged (GET) |
| 2 `DocumentViewer` | `shared/document-viewer/`: serif page text, one `mark` from text nodes, scrolled to centre, focus on request (`focusHighlight`), page `n of N` as a polite status, previous and next (disabled at the ends, focus handed over), Open original (`rel="noopener"`), mismatch message; "Read by OCR, NN % confidence" for OCR pages (F11) | `page-text.spec.ts` (`splitForHighlight`, `ocrNote`), `viewer.spec.ts` |
| 3 Page cache | `core/document-page-cache.ts`: LRU of 40, shared in-flight observable, failed requests forgotten | `document-page-cache.spec.ts` (eviction order, in-flight sharing, retry) |
| 4 Selection | Mouse selection with "Use this passage" toolbar, Escape clears; keyboard Dialog with start and end InputNumber and live quote | `page-text.spec.ts` (`rangeOffsets`: one text node, across the mark, element boundaries, collapsed, outside), `viewer.spec.ts` on the development-only harness route |
| 5 Review | Header kept as a list, "Quote only" disclosure, viewer with the quote marked; under 1024 px behind "Show on page" | `viewer.spec.ts`, `review-and-sign.spec.ts` |
| 6 Overview | `ProgramContext` reads `GET /programs/{id}/overview`; `latestAssessmentId` deleted | `review-and-sign.spec.ts` (every screen loads through it) |
| 7 Tests | as above; axe in both themes at 1280 and 400 px (collapsed and open viewer, range dialog) | |

Backend addition beyond point 1: `PageView` carries `textSource` and `ocrConfidence` (D-228), asserted in
`DocumentOcrIngestTest`.

Screenshots: `docs/screens/review-viewer-{light,dark}.png` (1440 px) and `review-viewer-400px-{light,dark}.png`
(after "Show on page"); the other screens were retaken in the same run.

### Real output

`LEXVAY_E2E_SLOT=1 LEXVAY_SCREENSHOTS=1 make test` from the repository root (backend `./mvnw verify`, frontend unit
tests, end to end), after merging `origin/main`:

```
[INFO] Tests run: 600, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
 Test Files  7 passed (7)
      Tests  43 passed (43)
  ✓   1 e2e/review-and-sign.spec.ts:3:1 › Dana Whitfield reviews every California requirement, signs, and sees the audit entries (8.3s)
  ✓   2 e2e/review-and-sign.spec.ts:95:1 › the review tabs are keyboard operable and the sign-in page is accessible (2.2s)
  ✓   3 e2e/review-and-sign.spec.ts:118:1 › keyboard shortcuts move through the list and record a decision (2.8s)
  ✓   4 e2e/review-and-sign.spec.ts:160:1 › the theme choice persists across a reload (1.5s)
  ✓   5 e2e/review-and-sign.spec.ts:175:1 › at 400 px the review screen fits without horizontal page scroll (1.9s)
  ✓   6 e2e/review-and-sign.spec.ts:191:1 › the Changes screen lists the seeded changes and re-runs matching for the new course version (2.9s)
  ✓   7 e2e/screens.spec.ts:23:1 › screenshots of every screen in light and dark (17.7s)
  ✓   8 e2e/viewer.spec.ts:3:1 › the review screen shows the cited page with the quote marked, pages through it, and opens the original (2.2s)
  ✓   9 e2e/viewer.spec.ts:62:1 › under 1024 px the viewer waits behind "Show on page" and takes focus when opened (2.6s)
  ✓  10 e2e/viewer.spec.ts:85:1 › a passage can be chosen by mouse selection and by character range from the keyboard (2.4s)
  10 passed (48.2s)
```

Production build: initial bundle 677.5 kB raw, 163.4 kB estimated transfer; the harness route is absent from it.

### Not done or open

- The seed has no OCR documents, so the OCR note is covered by a unit test of `ocrNote` and the backend test, not
  by an end-to-end test.
- Whether Chrome's PDF viewer renders a PDF served with this CSP (`object-src 'none'`) was not checked in a headed
  browser; the end-to-end test checks the response (200, `application/pdf`, `inline`).

## FIX-1 — Requirement order column (PR #18)

Branch `fix-requirement-ordinal`, migration `V012__requirement_ordinal.sql`, decisions D-230 to D-238 in
`DECISIONS.md (FIX-1)`.

### Done
- `requirement.ordinal` (backfilled, `NOT NULL`, `UNIQUE (rule_version_id, ordinal)`); migration lifts and restores
  `FORCE ROW LEVEL SECURITY` and the `requirement_locked` trigger around the backfill.
- Extraction and the replace endpoint store list position from 1 (`RuleVersionRepository.insertRequirements`).
- `Requirement.ordinal`; requirement and mapping listings order by `ordinal`; the diff follows it.
- Tests: `RequirementOrderApiTest` (twelve requirements through requirements, mappings and diff; replace keeps
  submitted order), `RequirementOrdinalBackfillTest` (the migration's own UPDATE on a temporary table).

### Evidence
`make backend-test` (2026-11-26, Docker via Colima):

    Tests run: 492, Failures: 0, Errors: 0, Skipped: 0
    BUILD SUCCESS

`LEXVAY_E2E_SLOT=5 make e2e`:

    1 skipped
    6 passed (23.2s)

(the skipped test is `screens.spec.ts`, the screenshot spec.)

## OPS-1 — Image build and smoke in CI (PR #20)

Branch `ops-ci-image`, PR #20, 2026-11-27. Decisions: `DECISIONS.md (OPS-1)` (D-240 to D-244).

### What changed

- `.github/workflows/ci.yml`: job `image` (`needs: test`, 30 minute timeout, same triggers and concurrency group).
  Buildx build with the GitHub Actions cache, tag `lexvay:ci-<sha>`, `load: true`, `push: false`, empty license;
  image size to `$GITHUB_STEP_SUMMARY`; then `scripts/smoke.sh lexvay:ci-<sha>` on slot 0.
- `scripts/smoke.sh [image-tag]`: tests an existing image, or builds `lexvay:local` when given none. Slot, compose
  project, cleanup and temporary directory come from `scripts/lib/stack.sh`. `docker-compose.yml` reads the image
  name from `LEXVAY_IMAGE`.
- `scripts/lib/stack.sh`: was not on `main`; copied from F7's branch (PR #17) with only the header comment changed.
- `docs/OPERATIONS.md`: section "Continuous integration" names the job and the tag convention.

### Evidence

Local, Colima, slot 11 (`LEXVAY_E2E_SLOT=11`): `make smoke` (builds `lexvay:local`) and
`scripts/smoke.sh lexvay:ci-local` (existing image) both ended `smoke: PASSED`, 31 checks each, image 216 MiB.

CI, run 45946473005 on PR #20: `test` pass (3m58s), `infra` pass (21s), `image` pass (3m41s). In the `image` job log:
`smoke: testing the existing image lexvay:ci-<sha>`, `smoke: image size 218 MiB`, 31 `smoke: ok` lines,
`Started LexvayApplication in 4.869 seconds`, `smoke: ok: shutdown completed`, `smoke: PASSED`. The job summary
line reads `Image lexvay:ci-<sha>: 218 MiB` (the same `docker image inspect` value the smoke script prints).

### Not verified

`actionlint` is not installed on this machine, so the workflow was validated only by its own run.

## F3b — Rule workspace review fixes (PR #24)

Branch `feat-rule-workspace-fixes`, end-to-end slot 10. Decisions: `DECISIONS.md (F3b)`.

### The five review changes and the bundle
1. **Edit grid**: two-line form rows in the same PrimeNG Table body. Line one: ref, title, type, numeric check, and
   the move and delete actions. Line two: the text, with the row's server error under it. Laid out by container
   width. No horizontal scroll at 1024, 1280 and 1440 px (checked in e2e); fields stack under 640 px.
2. **Extraction failure**: "Requirement extraction did not produce a list. A reviewer can enter the list by hand with
   Edit list." The job error is in a collapsed native `details`, "Technical detail", in mono. The list row shows
   only the *Extraction failed* Tag.
3. **Unsaved-changes guard**: a ConfirmDialog, "Discard unsaved changes to this list?", with "Discard changes" and
   "Keep editing" (Keep editing is focused). A CanDeactivate guard covers other versions, other screens and
   signing out; `beforeunload` covers a reload or closing the tab. `leaveNeedsConfirmation` has a unit test. In e2e,
   the reviewer edits a title, chooses another version, keeps editing, and the edit is still there.
4. **Rule text** uses F1's `DocumentViewer` (read-only, no highlight); `rule-text.ts` is deleted.
5. **Screenshots**: every screen regenerated with `LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=10 make e2e` (14 passed),
   including `rules-edit-{light,dark}` with the new layout.
- **Bundle**: the initial bundle is 698.9 kB raw (was 704.6 kB). The Accordion, ConfirmDialog, DatePicker and
  Listbox token sets now load with the Rules chunk (F3b-2).

### Tests
`make frontend-test`:
```
 Test Files  11 passed (11)
      Tests  73 passed (73)
```
`LEXVAY_E2E_SLOT=10 make test`:
```
[INFO] Tests run: 788, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  11 passed (11)
      Tests  73 passed (73)
e2e: starting backend on :8091
  ✓   7 e2e/rules.spec.ts:40:1 › a reviewer reads the seeded Colorado revision: its list, its rule text and what it changed (4.5s)
  ✓   8 e2e/rules.spec.ts:106:1 › a contributor enters a new rule version and watches its extraction (9.4s)
  ✓   9 e2e/rules.spec.ts:163:1 › a reviewer enters the list by hand, corrects it, saves, reloads and approves it (10.0s)
  ✓  10 e2e/rules.spec.ts:258:1 › at 400 px the rule workspace stacks without horizontal page scroll (3.3s)
  -  11 e2e/screens.spec.ts:9:1 › screenshots of every screen in light and dark
  1 skipped
  13 passed (1.0m)
```

### Noticed while testing (not changed)
- `scripts/e2e.sh` sets `LEXVAY_MANAGEMENT_PORT=8091+slot`, and the backend port is `8081+slot`. Slot 10's backend
  (8091) therefore takes the same port as slot 0's management port, and slots 0 to 9 and 10 to 19 overlap in the
  same way. Two such stacks cannot run at once.

## TEST-1 — Scripted fake model in the end-to-end backend (PR #25)

Branch `test-scripted-e2e-model`, end-to-end slot 11. Decisions: `docs/decisions/TEST-1.md` (D-280 to D-284).

### Done
- `lexvay.llm.fake-scripts` (`LEXVAY_LLM_FAKE_SCRIPTS`, comma-separated, empty by default): `FakeLlmStartupScripts`
  scripts the fake client from each file in order through `ScriptedFakeModel` (now takes a list of files; the seed's
  overload delegates). Ignored with one warning for the `anthropic` provider.
- `scripts/e2e.sh` sets it to `seed/fake-llm.yml,frontend/e2e/fixtures/fake-llm.e2e.yml`. The fixture holds the Nevada
  extraction (four fictional requirements, one numeric).
- `frontend/e2e/rules.spec.ts`: the contributor's Nevada version reaches *Needs approval* with four requirements
  listed; a separate test covers *Extracting* then *Extraction failed* for an Oregon version with no script; the
  reviewer test enters the Oregon list by hand and approves it (badge counts adjusted: Nevada stays waiting). Axe checks
  kept, one added for the Nevada state.
- `FakeLlmStartupScriptsTest`: two files in order, repeated load, no files, missing file, unknown provider fails
  startup, non-fake client never called with one warning (anthropic provider through `ApplicationContextRunner`),
  `ScriptedFakeModel.load` refuses a non-fake client.

### Test evidence
`make backend-test`:
```
[INFO] Tests run: 796, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
`LEXVAY_E2E_SLOT=11 make test`:
```
[INFO] Tests run: 796, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  11 passed (11)
      Tests  73 passed (73)
  ✓   8 e2e/rules.spec.ts:143:1 › a contributor enters a new rule version and its extraction proposes a list for approval (9.4s)
  ✓   9 e2e/rules.spec.ts:181:1 › a version whose extraction fails says so in words and leaves the list to a reviewer (8.1s)
  ✓  10 e2e/rules.spec.ts:208:1 › a reviewer enters the list by hand, corrects it, saves, reloads and approves it (11.0s)
  14 passed (1.3m)
```

### Noticed (not changed)
- The roadmap bullet says "profile `e2e`"; no such profile exists, the e2e stack is configured by environment
  variables in `scripts/e2e.sh`, so the property is set there (D-284).
- Match scripts are not loaded at startup (they need document ids); only extraction is (D-280).

## F8 — Determination register (PR #26)

Branch `feat-determination-register`, end-to-end slot 2. Decisions D-270 to D-279 in `docs/decisions/F8.md`.

### Delivered
1. `GET /api/v1/determinations/register?programId=&jurisdiction=&outcome=&current=` — one statement, read-only
   transaction, in `EndpointLatencyTest`, `ReadPathConnectionsTest` and `InputValidationTableTest`.
2. `GET /api/v1/programs/{id}/determination-lists` — one statement; the three lists partition the program's targets
   (`RegisterApiTest.theThreeListsPartitionTheTargetsExactly`, `...signingAgainAfterTheReopening...`,
   `RegisterSeedTest`).
3. `GET /api/v1/determinations/register.csv` — `common/CsvWriter` (RFC 4180, CRLF, every field quoted, formula prefix,
   UTF-8; `CsvWriterTest`), audited as `REGISTER_EXPORTED` with the filters and the row count, listed in
   `Rule9_AuditCoverageTest.AUDITED_READ_ENDPOINTS`.
4. Screen `/register` with the tenant-wide rail entry, Register and By program tabs, print stylesheet.
5. Tests: backend as above; Vitest `core/register.spec.ts`; Playwright `e2e/register.spec.ts` (runs after the suite,
   D-278) with axe in both themes at 1280 and 400 px; screenshots in `docs/screens/register-*`.

### Evidence (after merging `origin/main` at 05fd4ce; runs on 2026-11-26)

`make backend-test`:
```
[INFO] Tests run: 813, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```
The first run after the merge failed once in `RuleSourceSchedulerLoopTest` (F12, untouched here: `expected 0L but was
1L` for the inactive source); run alone it failed 1 of 3 times, and the full rerun above is green. It is a timing race
in that test, reported to the orchestrator.

`make frontend-test`:
```
 Test Files  13 passed (13)
      Tests  80 passed (80)
```

`LEXVAY_E2E_SLOT=2 LEXVAY_SCREENSHOTS=1 make test` (backend and frontend as above, then Playwright):
```
  ✓  14 [suite] › e2e/viewer.spec.ts:85:1 › a passage can be chosen by mouse selection and by character range from the keyboard (3.3s)
  ✓  15 [register] › e2e/register.spec.ts:14:1 › the register lists the California signature, filters, exports CSV, groups the program and prints (3.3s)
  ✓  16 [register] › e2e/register.spec.ts:109:1 › the register is accessible in both themes at 400 px and fits without horizontal scroll (2.8s)
  16 passed (1.8m)
```

`make perf` (run after the first merge, at 8134fff; the second merge changed only scripts and frontend):
`EndpointLatencyTest` passes (every read endpoint under the 40 ms p95 budget; the highest p95 of the 62 rows
is 6.32 ms). The new endpoints, all one connection per call:

| Endpoint | 2 tenants p95 ms | 62 tenants p95 ms |
|---|---:|---:|
| GET /determinations/register | 2.32 | 1.61 |
| GET /determinations/register?programId=&current=false | 1.67 | 1.68 |
| GET /programs/{id}/determination-lists | 2.13 | 2.38 |

The second step, `IndexUsageTest`, fails with `[indexes of V008 the planner did not choose: drop them] Expecting empty
but was: ["requirement_rule_version_id_ref_key"]`. It fails the same way on unmodified `origin/main` (checked from a
clean export): FIX-1's `requirement_ordinal_key (rule_version_id, ordinal)` is now chosen for the candidate query, which
still orders by `created_at, ref`. Not part of this item; reported.

Production build: initial bundle 703.1 kB raw (warning budget 700 kB; `origin/main` at 05fd4ce alone 698.9 kB), see D-277.

Screenshots: `docs/screens/register-{light,dark}.png`, `register-by-program-{light,dark}.png`,
`register-print-{light,dark}.png` (print media; both black on white). The other screenshots were retaken because the
rail now starts with the register entry.

### Notes for review
- `register.spec.ts` reads the California signature recorded by `review-and-sign.spec.ts` rather than signing it
  itself (D-278).
- The lists entry carries `reopenedAt` in addition to the spec's fields, needed for "Reopened on <date> after a
  change" (D-272).

## FIX-2 — Job entity id column and index (PR #27)

Branch `fix-job-entity-index`, migration `V013__job_entity_id.sql`, decisions D-200 to D-209 in
`DECISIONS.md (FIX-2)`, measurements in `docs/PERF.md` ("Job lookups (FIX-2)").

### Done
- `job.entity_id uuid NOT NULL`, backfilled from the single value of the payload object, `job_entity_idx
  (type, entity_id, created_at DESC)`; `FORCE ROW LEVEL SECURITY` lifted for the backfill only, in the
  migration's transaction.
- `JobRepository.enqueue(type, entityId, payload)`, `latest(type, entityId)`, `latestByEntity(type, ids)`;
  `Job.entityId`. Six call sites pass the id they already put in the payload.
- Every `payload->>` comparison in main code replaced by `entity_id`: job lookups, the work queue's failed extraction
  test, the rule source scheduler's dedupe. Policies of V002 and V011 unchanged.
- Tests: `JobEntityIdBackfillTest` (the migration's own UPDATE on a temporary table), `JobEntityLookupTest`,
  `IndexUsageTest` (`job_entity_idx` for the three lookups; the dedupe and scheduler statements; fails on any
  `Seq Scan on job`).
- Also: `IndexUsageTest` named `requirement_rule_version_id_ref_key`, which V012 replaced, so `make perf` was red on
  `main`; the candidate now orders by `ordinal` and expects `requirement_ordinal_key`.
  `RuleSourceSchedulerLoopTest` registers and deactivates its inactive source in one transaction (the running
  scheduler could queue it in between) and counts jobs by `entity_id`.

### Evidence (2026-11-26, Docker via Colima, after merging `origin/main`)
`make backend-test`:

    Tests run: 792, Failures: 0, Errors: 0, Skipped: 0
    BUILD SUCCESS

`make perf` (EndpointLatencyTest, then IndexUsageTest):

    Tests run: 1, Failures: 0, Errors: 0, Skipped: 0   (EndpointLatencyTest)
    BUILD SUCCESS
    Tests run: 1, Failures: 0, Errors: 0, Skipped: 0   (IndexUsageTest)
    BUILD SUCCESS

`LEXVAY_E2E_SLOT=5 make e2e`:

    1 skipped
    13 passed (1.2m)

`RuleSourceSchedulerLoopTest` run ten times in a row: 10 of 10 green.

Before and after for `GET /documents/{id}` and `GET /rule-versions`: `docs/PERF.md`. At the seeded volume the rows
do not move beyond noise; the plans change from sequential scans of `job` to index scans.

### Index key (orchestrator decision, D-202)
The roadmap's key led with `tenant_id`. Measured on the perf database, one lookup touched 636 shared buffers with it
(1304 for the old sequential scan) because the policies' OR branch makes `tenant_id` a filter and the index is
skip-scanned over every tenant. V013 now creates `(type, entity_id, created_at DESC)`: 24 buffers for the ingest status
and the extraction status, 28 for the rule version list. `IndexUsageTest` records these and fails above 100.
The scheduler's dedupe is served by `job_claim_idx` (queued jobs are few), never a sequential scan of `job` (D-206).

## F4 — Evidence request screens (PR #28)

Branch `feat-evidence-requests`, end-to-end slot 4. Decisions D-260 to D-269 in `docs/decisions/F4.md`.

### What was built
- **Route and rail**: `programs/:programId/requests` (lazy chunk `requests`), "Evidence requests" after Review with
  `phosphorTray` and the open count badge ("n open evidence requests"), every role.
- **Inbox**: Open / Answered / All SelectButton with counts; PrimeNG Table with jurisdiction and ref in mono, title,
  the note in two lines (full note in the row detail, opened with a toggle button), who asked and when, the status Tag
  with icon (Open, "Answered, awaiting review", Blocked with the re-run helper text and no action), the response count
  and the latest response (who, when, file, page). Empty state sentence as specified.
- **Answer drawer** (deferred chunk `evidence-drawer`): the requirement with its rule text in serif, the request note
  and who asked; a grouped document Select (program documents first, then courses; current catalog years by default,
  "Include earlier catalog years" ToggleSwitch, documents without text disabled with "No text available yet");
  `DocumentViewer` with `selectable`; "Selected passage" in serif with page and characters in mono; optional comment
  (4000 characters); "Submit evidence". 400 under the passage with the server's detail, 409 in a Message plus inbox
  reload, success toast "Evidence sent to review" and the row moves to Answered. The drawer is a labelled modal
  dialog, traps focus, starts on its heading and returns focus to the row action.
- **Review**: the Evidence pane shows "Evidence provided by <name>, <date>" (with `phosphorUser`) under the system's
  evidence, each with the quote marked in `DocumentViewer` and the comment; "evidence provided" marker
  (`phosphorChatCircleText`) in the requirement list; note placeholder "Note the evidence you relied on" while a
  person has answered and nobody has decided.
- **Backend**: `stale` on `EvidenceRequestView`; `responseCount` on the mapping read; `GET /programs/{id}/documents`
  (orchestrator decisions D-263, D-264, D-266).
- No new npm or Maven dependency.

### Document picker source
`GET /api/v1/programs/{id}/documents`, added in this item: `main` had no endpoint that lists a program's documents
(the courses endpoint carries none, and every document read takes a document id).

### Bundle (production build, raw)
| | Initial total | Note |
|---|---:|---|
| `main` when F4 started | 703.67 kB | |
| F4 before merging F3b and F8 | 710.30 kB | Drawer and ToggleSwitch tokens were in the eager preset |
| `main` after F3b and F8 | 703.12 kB | |
| F4 merged with that `main` | 707.30 kB | tokens in the drawer's chunk; +4.2 kB icons and badge code |
Lazy chunks: `requests` (inbox), `evidence-drawer` 32.6 kB.

### Latency (`make perf`, p95 ms; seed only / seed plus 30 renamed copies of both tenants)
| Endpoint | Before | After |
|---|---:|---:|
| `GET /assessments/{id}/mappings?jurisdiction=CA` | 4.92 / 3.57 | 2.71 / 5.68 |
| `GET /assessments/{id}/mappings` (all) | 9.38 / 9.27 | 5.77 / 11.14 |
| `GET /programs/{id}/documents` | n/a | 5.47 / 5.40 |
| `GET /programs/{id}/evidence-requests?status=ALL` | 4.12 / 11.88 | 5.66 / 2.51 |
Every read endpoint stays under 40 ms; `ReadPathConnectionsTest` shows one connection per read endpoint, including the
new one. `IndexUsageTest` (the second half of `make perf`) fails on `main` and on this branch alike:
`requirement_rule_version_id_ref_key` is no longer chosen since FIX-1's `requirement_ordinal_key`; not touched here.

### Screenshots (`docs/screens/`, light and dark)
`evidence-requests-*.png` (inbox), `evidence-request-drawer-*.png` (drawer with a selected passage),
`review-provided-evidence-*.png` (Review with provided evidence).

### Test evidence
`LEXVAY_E2E_SLOT=4 make test` (backend, frontend unit and end-to-end in one run), final run after merging
`origin/main` at 7eba7a7 (FIX-2):
```
[INFO] Tests run: 831, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  15 passed (15)
      Tests  95 passed (95)
  ✓   1 [suite] › e2e/evidence-requests.spec.ts:15:1 › a reviewer asks for evidence, a contributor answers with a verified passage, and the reviewer accepts it (8.8s)
  ✓   2 [suite] › e2e/evidence-requests.spec.ts:169:1 › at 400 px: the inbox, the drawer, a blocked request and the provided evidence (6.3s)
  ...
  ✓  19 [register] › e2e/register.spec.ts:109:1 › the register is accessible in both themes at 400 px and fits without horizontal scroll (2.9s)
  18 passed (1.7m)
```
The run before (at 91f6602):
```
[INFO] Tests run: 827, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  15 passed (15)
      Tests  95 passed (95)
  ✓   1 [suite] › e2e/evidence-requests.spec.ts:15:1 › a reviewer asks for evidence, a contributor answers with a verified passage, and the reviewer accepts it (10.5s)
  ✓   2 [suite] › e2e/evidence-requests.spec.ts:169:1 › at 400 px: the inbox, the drawer, a blocked request and the provided evidence (6.0s)
  ...
  ✓  18 [register] › e2e/register.spec.ts:14:1 › the register lists the California signature, filters, exports CSV, groups the program and prints (3.6s)
  ✓  19 [register] › e2e/register.spec.ts:109:1 › the register is accessible in both themes at 400 px and fits without horizontal scroll (2.4s)
  18 passed (1.4m)
```
Screenshots, `LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=4 make e2e` (after the drawer heading fix): `19 passed (2.3m)`. Every
existing screenshot was regenerated because the rail gained "Evidence requests".

Before the final runs the evidence-requests spec ran 13 times against a freshly seeded slot-4 stack; it failed 1 in 3
runs with PrimeNG's Drawer mask left on the page, which led to D-265's own backdrop; the last 4 consecutive runs and
the full suite passed.

### Known gaps and follow-ups
- An open request on a requirement whose jurisdiction is signed is listed as Open and answering it returns 409 (shown
  in the drawer). Showing it as blocked like a stale one is the F13 follow-up already in the roadmap; the request row
  would need the signed flag.
- The development-only document viewer harness (F1) is no longer the only user of `selectable`; `viewer.spec.ts`
  still uses it, so it stays.
- Table stacking under 720 px is copied per screen (Changes, Audit, Overview, Report and now Evidence requests); a
  shared rule belongs to the UI2 restyle.

## PERF-FE-1 — Icons and theme tokens per chunk (PR #29)

Branch `perf-frontend-chunks`, built on `origin/main` at 91f6602 (F8 merged, design v2 docs). Structural only: no token
value, font or visual changed. Decisions D-295 to D-299 in `docs/decisions/PERF-FE-1.md`.

### Status

Built and tested. The budget step is not done: the initial bundle is 695.0 kB raw, not under 640 kB, so
`angular.json` keeps 700 kB / 800 kB. The breakdown and the next cuts are in `docs/PERF.md`, "Frontend bundle".

### After merging F4 (PR #28)

F4's evidence request screen and drawer follow the pattern (`requests.routes.ts`, `REQUESTS_ICONS`, `requests-theme.ts`;
`phosphorTray`, the rail entry, joins `SHELL_ICONS`; Review's new `ChatCircleText` and `User` join `REVIEW_ICONS`).
Initial bundle on the merged tree: 698.75 kB raw / 168.84 kB transfer (main 691.30 kB), `requests-routes` chunk
12.59 kB / 4.27 kB. The budget stays at 700 / 800 kB; the figures below are from before the merge.

### What changed

- `SHELL_ICONS` (17 icons) is the only registry in `app.config.ts`; `LEXVAY_ICONS` is gone.
- Routed screens with their own icons (Overview, Review, Changes, Rules, Report, Register) have a
  `<screen>.routes.ts` that provides `<SCREEN>_ICONS`; `app.routes.ts` loads them with `loadChildren`.
  `DocumentViewer`, `FindingLabel` and `DecisionState` provide theirs with `viewProviders`.
- `registerComponentTokens(sets)` (`theme/component-tokens.ts`) replaces `registerRulesTheme` and
  `registerRegisterTheme`; `<screen>-theme.ts` files export `<SCREEN>_TOKENS`. Dialog, InputNumber, ProgressBar,
  ProgressSpinner, RadioButton, Splitter and Textarea left the shared preset.
- Icon label tables moved next to their one screen; `confirmLeave` moved to `core/confirm-leave.ts`.
- Tests: `theme/icons.spec.ts` (every name shown is registered by its chunk, and nothing extra),
  `theme/component-tokens.spec.ts`, and an `iconGuard` fixture that fails any end-to-end test on a "No icon named"
  warning. `@types/node` 26.6.11 added as a dev dependency for the spec.
- `docs/DESIGN.md` "Bundle" says how a new screen follows the pattern.

### Bundle (production build, raw / estimated transfer)

| | before (91f6602) | after |
|---|---:|---:|
| Initial total | 703.12 kB / 169.43 kB | 695.03 kB / 168.22 kB |
| main.js | 695.67 kB / 167.84 kB | 687.58 kB / 166.62 kB |

Lazy chunks per screen are in `docs/PERF.md`. Why the icon cut does not shrink the initial chunk: the package's
`regular` entry is one module, so the bundler keeps every icon any chunk uses in the chunk the shell shares
(52 icons, 26.9 kB, in `main`). A scratch build in which the shell holds its own copy of its 17 icons measures
676.5 kB.

### Test evidence

```
make test (LEXVAY_E2E_SLOT=6)
  backend:   Tests run: 821, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS
  frontend:  Test Files 15 passed (15) / Tests 92 passed (92)
  e2e:       1 skipped (the screenshot test) / 16 passed (1.2m)
LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=6 make e2e
  17 passed (1.7m)
```

Axe (zero serious or critical, light and dark) runs inside those end-to-end tests and passed.

### Screenshots

The committed `docs/screens` are unchanged (no visual change to record). To check, every screenshot was regenerated
with this branch and again with `91f6602` on the same stack and slot, and the two sets were compared pixel by pixel:
25 of the 38 are identical. The others differ only where a time or a generated record id is printed (audit trail times and ids, the register's
"Generated" line, the evidence report's signature time: 111 to 9455 px in those boxes) or by 2 to 34 pixels of
capture noise (a dialog's scroll position in rules-new-version-light, one-pixel edges in review-400px-light and
rules-approve-light). No icon, colour or layout moved.

## F7c — Search terms proposed at extraction (PR #30)

Branch `search-terms`, slot 3. Backend and evaluation, plus a frontend pass-through so the grid keeps the terms; the screen cell is UI2-4. Decisions D-250 to D-259 in `docs/decisions/F7c.md`.

### What was built
- **Migration `V014__requirement_search_terms.sql`**: `requirement.search_terms text[] NOT NULL DEFAULT '{}'` and `search_terms_valid(text[])`
  (at most 12 terms, each 1 to 40 characters after trimming) in a CHECK. No existing row is updated, so the lock trigger and `FORCE ROW LEVEL SECURITY`
  are left alone (D-250).
- **Extraction**: `extract-v2.system.txt` (v1 stays on disk, the job records `extract-v2`), `search_terms` required in the extraction schema
  (`maxItems` 8, `maxLength` 40). `SearchTerms.normalise` is the one normaliser (trim, lower-case, de-duplicate, drop empty terms, terms with a digit or a
  state name, and terms whose words are all in the title or text). The replace endpoint takes `search_terms` optionally; more than 12 is a 400 naming the row.
- **`RequirementComparison.Aspect.SEARCH_TERMS`** (does not affect evaluation): a terms-only edit carries decisions over on re-run, and the diff reports it.
- **Retrieval**: the one BM25 statement gets the terms' lexemes (boost 1.0) next to the title's (2.0) and the text's (1.0); `Passage.matchedVia`
  (`TITLE`, `TEXT`, `TERMS`) and `matchedTerms`; `EvidenceMatcher` adds "Found through the search terms: ..." when the cited page was found only through terms.
  Quotes are verified against the stored page exactly as before.
- **Seed and fixtures**: hand-written fictional terms for all 55 scripted seed requirements and the 4 Nevada fixture ones; the Colorado revision keeps them identical.
- **Evaluation**: `searchTerms` for all 35 queries, a third column "BM25 + terms", `minGapRecallAtDeep: 0.66` (4 of 6) in `eval/thresholds.yml`.

### Retrieval (`make eval`, `docs/RETRIEVAL.md`)
Fictional corpus of 27 documents; 29 counted queries and 6 lexical gaps.

| Counted queries | ts_rank (baseline) | BM25 | BM25 + terms |
|---|---|---|---|
| Recall@8 | 96.6 % (28 / 29) | 96.6 % (28 / 29) | 96.6 % (28 / 29) |
| Recall@3 | 86.2 % (25 / 29) | 89.7 % (26 / 29) | 93.1 % (27 / 29) |
| Mean reciprocal rank | 0.796 | 0.766 | 0.839 |
| Excerpt coverage | 100.0 % (28 / 28) | 100.0 % (28 / 28) | 100.0 % (28 / 28) |

| Lexical gaps | ts_rank (baseline) | BM25 | BM25 + terms |
|---|---|---|---|
| Recall@8 | 33.3 % (2 / 6) | 33.3 % (2 / 6) | 100.0 % (6 / 6) |
| Recall@3 | 16.7 % (1 / 6) | 16.7 % (1 / 6) | 100.0 % (6 / 6) |
| Mean reciprocal rank | 0.111 | 0.076 | 0.917 |

The limit (lexical-gap recall@8 at least 4 of 6) is met with margin on the first run; no term was changed after any run. The only edits to the term lists
after writing them removed terms the production normaliser would have dropped (all their words are in the requirement), so no result moved. The result is one
author's one pass over six queries, written with the expected passages' `contains` strings in the same file (D-258); it shows that the mechanism works when the
vocabulary is right, not how often a model's proposals will be. R13 is still outside the top eight in all three columns.
The pipeline on the seed is unchanged: 0 false EVIDENCE_FOUND (0 / 33), recall 33 / 33, quote verification 31 / 31, numeric accuracy 11 / 11,
extraction agreement 42 / 42, evidence agreement 39 / 39, 36 model calls.

### Review fixes (after PR review)
- **No data loss on save**: the grid passes `search_terms` through (`RequirementView.searchTerms`, `RequirementDraft.search_terms`, `RequirementListEditor` keeps each row's terms).
  `make frontend-test`: 110 passed (17 files), including the new editor tests.
- **Eval profile** sets `management.server.port: -1`; `make eval` ran with a development backend holding :8090 and passed.
- `LEXVAY_E2E_SLOT=3 make e2e`: 18 passed, 1 skipped. Re-run after merging `origin/main` again: `make backend-test` 882 tests, 0 failures, BUILD SUCCESS.

### Tails

`LEXVAY_E2E_SLOT=3 make eval` (the eval profile now has `management.server.port: -1`, so it runs beside a development backend on :8090; D-259), exit 0:
```
Metric                                        Result      Count   Limit        Status
False EVIDENCE_FOUND rate                      0.0 %     0 / 33   <= 0.0 %     ok
Recall on golden EVIDENCE_FOUND              100.0 %    33 / 33   >= 100.0 %   ok
Quote verification                           100.0 %    31 / 31   = 100.0 %    ok
Numeric accuracy (rules engine)              100.0 %    11 / 11   >= 100.0 %   ok
Requirement extraction agreement             100.0 %    42 / 42   >= 100.0 %   ok
Evidence agreement (document, quote)         100.0 %    39 / 39   >= 100.0 %   ok
RESULT: pass

Metric                         ts_rank (baseline)                 BM25         BM25 + terms   Limit                          Status
Recall@8                          96.6 %  28 / 29      96.6 %  28 / 29      96.6 %  28 / 29   >= 95.0 %, not below baseline  ok
Recall@3                          86.2 %  25 / 29      89.7 %  26 / 29      93.1 %  27 / 29   not below baseline             ok
Mean reciprocal rank                        0.796                0.766                0.839   -                              -
Excerpt coverage                 100.0 %  28 / 28     100.0 %  28 / 28     100.0 %  28 / 28   = 100.0 %                      ok

Lexical gaps: recall@8 2 / 6 with ts_rank, 2 / 6 with BM25, 6 / 6 with BM25 + terms (limit >= 66.0 %, ok); recall@3 1 / 6, 1 / 6 and 6 / 6
RETRIEVAL: pass
```

`make backend-test` after merging `origin/main` (F4 included), exit 0:
```
[INFO] Tests run: 882, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```
`ForbiddenWordsTest` is in that run.

BM25 statement with six terms per requirement (`PassageSearchLatencyTest`, 500 pages, budget p95 30 ms), run apart because `make perf` does not include it:
```
BM25 shortlist over 500 pages, 200 requests: p50=12 ms, p95=17 ms, max=21 ms
```
F7 measured p50 9 ms and p95 15 ms without terms (D-173).

`make perf`: `EndpointLatencyTest` passed (BUILD SUCCESS; every read endpoint p95 under 17 ms on the seed, under 9 ms with 62 tenants; one connection per
call). `IndexUsageTest`, the second half of the target, fails:
```
[indexes of V008 and V013 the planner did not choose: drop them]
Expecting empty but was: ["job_claim_idx"]
```
This item adds no job query or index. F4's progress note records the same test failing on `main` for another index (`requirement_rule_version_id_ref_key`), so the test
is failing before this branch; not touched here.

`LEXVAY_E2E_SLOT=3 make e2e`, exit 0:
```
  18 passed (1.5m)
  1 skipped
```
(The skipped test is the screenshot spec, which only runs on request.)

### Tests added or changed
`SearchTermsTest`, `RequirementListParserTest` (dedupe, words already in the text, state and digit, 12 and 13, 40 and 41, shapes, 8 against 12),
`JsonSchemaValidatorTest` (nine terms, long term, missing member), `RequirementComparisonTest`, `RequirementSearchTermsConstraintTest` (the CHECK),
`Rule5_ApprovedRequirementsRejectEditsTest` (terms are locked), `PassageSearchTermsTest` (BM25 by hand with a term only on a heading line, labels, stemming,
matched terms, quotes in terms, ts_rank), `SearchTermsMatchingTest` (the rationale sentence, rule 2), `RuleVersionApiTest` (the scripted terms are stored; replace stores,
normalises, rejects 13 naming the row and a 41-character term, locks after approval), `RuleVersionChangeTest` (carry-over and diff), `SeedSearchTermsTest`,
`RetrievalFileTest` and `RetrievalScorerTest` (limits), `RetrievalServiceTest` (the lexical-gap limit with and without terms). Fixtures that build extraction output by hand now
supply `search_terms`.

## FIX-3 — Work queue and evidence request follow-ups (PR #31)

- ASSESSMENTS_FAILED ignores failures older than the latest finished assessment (D-360).
- Evidence requests carry `signed`; the work queue counts signed and stale requests as blocked (D-361).
- `findingCounts` shares the decided predicate (D-362).
- `CURRENT` measured (D-363) and rewritten as a once-per-statement set (D-364): mappings of a 500-row assessment 15 to
  30 ms down to about 3 ms, work queue 280 ms down to 2 to 5 ms on the same tenant.
- `IndexUsageTest` no longer pins one index for the manual dedupe (D-365).

## F5a — User administration and audit export API (PR #32)

User administration and audit export API, backend only (V015, D-370 to D-379 in `docs/decisions/F5a.md`).

### Built

- `V015__user_administration.sql`: `app_user.active` and `security_stamp`; `user_setup_token` with the tenant policy, an
  `auth_lookup` SELECT policy, an open-codes index, and immutability except `used_at`.
- `POST /users`, `POST /users/{id}/setup-code`, `PUT /users/{id}/role`, `PUT /users/{id}/active` (ADMIN);
  `GET /users` with `active`, `createdAt`, `passwordSet` for administrators; `POST /auth/setup` (anonymous, login budget).
- Session revocation: stamp in `LexvayUser`, `UserStampCheck` (15 s per-instance cache) in `TenantContextFilter`.
- `GET /audit.csv` (ADMIN), streamed through `CsvWriter`, audited as `AUDIT_EXPORTED`.

### Where each point of the roadmap is tested

| Point | Test |
|---|---|
| 1 schema, RLS, immutability | `Rule7_UserSetupTokenIsolationTest`, `Rule5_UserSetupTokenImmutabilityTest`, `MigrationsApplyCleanlyTest`, `Rule7_EveryTableHasPolicyTest` |
| 2 create, codes, email rules | `UserAdminApiTest` (create, duplicate across tenants, lower case, new code closes old ones, audit without the code), `SetupCodesTest` |
| 3 set password | `PasswordSetupApiTest` (once, same message for unknown, used and expired, rules, concurrent use, CSRF), `RateLimitApiTest.guessingSetupCodesDrawsOnTheLoginBudgetOfTheAddress` |
| 4 role and access | `UserAdminApiTest` (self, last administrator behind the cache, audit, no-op, login of inactive), `LastAdministratorConcurrencyTest` (two threads, 30 rounds) |
| 5 revocation | `SessionRevocationTest` (real HTTP), `UserStampCheckTest` (fake clock: 14 s not seen, 16 s seen), `ReadPathConnectionsTest` (warm-up, one checkout) |
| 6 reads | `UserAdminApiTest.administratorsSeeTheStateOfEveryUser...`, `UserApiTest` |
| 7 export | `AuditExportApiTest`, `AuditExportMemoryTest`, `CsvWriterTest` |
| 8 guards | `InputValidationTableTest` (224 rows, every new endpoint), `Rule9_AuditCoverageTest`, `ForbiddenWordsTest` |

Checked by hand: removing the administrator lock from `lockActiveAdministrators` makes `LastAdministratorConcurrencyTest`
fail (deadlock between the target locks). `AuditExportMemoryTest` on this machine: 20 001 records, 63 MB, live heap 54 MB
before and 57 MB at the highest sample while serving.

### Results

`make backend-test` (after merging origin/main with F7c and FIX-3): `Tests run: 1003, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.

`make perf`: `EndpointLatencyTest` and `IndexUsageTest` both `BUILD SUCCESS`; every read endpoint stays at one connection per
call and the slowest p95 is 14.48 ms (program screen, three requests); budget 40 ms.

`LEXVAY_E2E_SLOT=5 make e2e`: `1 skipped`, `18 passed (1.9m)`, exit 0.

### Deviations from the roadmap (see D-373, D-377)

- Password limit is 72 bytes, not 128 characters: bcrypt refuses more.
- The `auth_lookup` policy on `user_setup_token` is SELECT only; nothing needs UPDATE.
- `AUDIT_EXPORTED` also records `complete`.

### Deferred

The screens (F5b). Raising the password limit needs an encoder decision.

### FIX-4 — Rule source backoff, live search terms, PDF page cap at upload

Branch `fix-backoff-terms-pages`, migration `V016__rule_source_schedule.sql` (V015 is reserved for F5a and was not on `main` when the PR was opened),
decisions D-380 to D-389 in `docs/decisions/FIX-4.md`. Backend and evaluation only; nothing under `frontend/src`.

#### What changed
- **Rule source schedule (D-380 to D-382).** `rule_source.next_check_at` and `consecutive_failures`; the scheduler's due predicate is
  `active AND next_check_at <= now()` with the partial index `rule_source_due_idx`. `RuleSourceRepository.markChecked(id, hash, outcome)` sets both in
  one statement: a check that read the page waits the interval and clears the failures, a failed fetch waits `least(interval, 15 minutes x 2^(failures - 1))`.
  `GET /rule-sources` shows `nextCheckAt` and `consecutiveFailures`. The update restriction (V003 function) is recreated with five allowed columns.
- **Live evaluation with search terms (D-383, D-384).** `eval/golden.yml` has `searchTerms` for all 42 requirements; the replace path of `make eval-live`
  carries them; the live report prints the mean Jaccard overlap between the model's terms and the golden terms and one line per requirement
  (`TermOverlap`). `make eval` is unchanged except that the golden file validates the new field.
- **PDF page cap at upload (D-385).** `ContentLimits.requirePdfPages(byte[])` (PDFBox, temp-file stream cache) before anything is stored; ingest keeps its check.
- **`IndexUsageTest` (D-386).** Covers the due query. Before the merge of FIX-3 the manual-check candidate named `job_claim_idx` while the planner chooses
  `job_entity_idx`, which failed the first `make perf` run on this branch; FIX-3 had already fixed it (`Candidate.eitherOf`), so the merge took its version.

#### Verification
All four run on the tree after merging `origin/main` (FIX-3 included).

`make backend-test` (exit 0):
```
[INFO] Tests run: 904, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
`make perf` (exit 0, run after merging `origin/main`):
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 95.49 s -- in com.lexvay.perf.EndpointLatencyTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 91.42 s -- in com.lexvay.perf.IndexUsageTest
[INFO] BUILD SUCCESS
| `rule_source_due_idx` | which active rule sources are due (scheduler, worker flag, all tenants; V016) | yes | Index Scan using rule_source_due_idx on rule_source rs; Index Scan using job_claim_idx on job j | yes | |
```
`GET /rule-sources` p95 1.41 ms (first table; 2.96 ms with more tenants), `GET /rule-sources/{id}/checks` p95 3.51 ms (2.80 ms).

`LEXVAY_E2E_SLOT=7 make eval` (exit 0):
```
Metric                                        Result      Count   Limit        Status
False EVIDENCE_FOUND rate                      0.0 %     0 / 33   <= 0.0 %     ok
Recall on golden EVIDENCE_FOUND              100.0 %    33 / 33   >= 100.0 %   ok
Quote verification                           100.0 %    31 / 31   = 100.0 %    ok
Numeric accuracy (rules engine)              100.0 %    11 / 11   >= 100.0 %   ok
Requirement extraction agreement             100.0 %    42 / 42   >= 100.0 %   ok
Evidence agreement (document, quote)         100.0 %    39 / 39   >= 100.0 %   ok
RESULT: pass
RETRIEVAL: pass
```
`LEXVAY_E2E_SLOT=7 make e2e` (exit 0):
```
  1 skipped
  18 passed (1.6m)
```

#### Not run
`make eval-live` (no `ANTHROPIC_API_KEY` here). The live report's text and the overlap arithmetic are covered by `TermOverlapTest`, and `EvalModelTest`
proves the model's terms are remembered and the golden terms are stored through the replace path.

#### Tests added or changed
`RuleSourceScheduleTest`, `RuleSourceScheduleBackfillTest`, `RuleSourceEnqueueTest`, `Rule5_RuleSourceImmutabilityTest`, `RuleSourceApiTest`, `IndexUsageTest`,
`ContentLimitsTest`, `DocumentUploadTest` (501 pages now a 400 at upload), `GoldenFileTest`, `TermOverlapTest`, `EvalModelTest`, `EvalServiceTest`, `EvalScorerTest`.

#### For the orchestrator
The roadmap says `search_terms` for the golden file; the key is `searchTerms` like the file's other keys (D-383). If F5a (V015) merges after this item, a database
that already applied V016 fails Flyway validation on V015 (D-389).

## UI2-0 — Design v2 foundation, shell and sign-in (PR #34)

Branch `ui2-foundation`, decisions D-301 to D-319 in `docs/decisions/UI2-0.md`. F4 (evidence requests) and PERF-FE-1
merged into the branch while it was in flight; PERF-FE-1's per-chunk pattern is followed and its cuts 1 and 2 are taken.

### Spec points
1. **Fonts**: Inter and Source Serif 4 variable (wght axis, latin and latin-ext), IBM Plex Mono kept; Plex Sans and Serif
   removed; measured metric-matched fallbacks (D-301, D-302). CLS on first paint: 0.00 (sign-in and overview).
2. **Preset v2**: the DESIGN.md token table in `theme/tokens.ts` with derived tokens, `light-dark()` values published as
   `--p-lx-*` (renamed in one pass), elevation, radius, type scale; Lexvay overrides for Button, Badge, Popover (shared)
   and DataTable, Dialog, Message, Tag, Tabs, Skeleton, ProgressBar, ToggleSwitch, SelectButton, ToggleButton, Toast,
   Menu, CommandMenu, Splitter, RadioButton (per chunk); Select, InputText, Password, Textarea, Listbox, Drawer take the
   semantic form-field, list and overlay tokens (D-303 to D-305). Contrast test: `theme/tokens.spec.ts`, every pair in
   both themes.
3. **Motion**: tokens in `styles.css`, `MotionPreferences`, `lxEnter`, `lxCountUp`, route view transitions that skip
   same-screen navigation and reduced motion (D-307).
4. **Shared components** in `ui/`: StateMap (three sizes, roving keyboard, popover card, wave), ProgressRing, Stat,
   InfoTip, StatusIcon, Seal, Mark, CommandPalette; a development-only harness at `dev/ui` (D-308 to D-311, D-315).
5. **Shell v2**: top bar, rail with gliding pill, collapse and bottom tab bar, avatar menu with the theme, Demo data pill,
   work queue (F13) button and popover; the keyboard shortcuts dialog lists the palette (D-312, D-313).
6. **Sign in v2**: story map and headline, form with inline errors; the story stops on typing and is still under reduced
   motion; the stage sizes the map from its width. Demo account chips come from the build (`LEXVAY_DEMO_ACCOUNTS`), because `/session` cannot report `demo: true` before sign-in (D-314).
7. Every screen uses the new tokens; layouts unchanged; axe clean in the suite.
8. Tests: listed below.

### Unit tests (`make frontend-test`)
New: `theme/tokens.spec.ts` (contrast), `ui/status.spec.ts`, `ui/state-map/state-map-model.spec.ts` (layout
completeness, names, keyboard order), `ui/command-palette/fuzzy.spec.ts`, `ui/command-palette/commands.spec.ts`,
`core/work-queue.spec.ts`, `screens/login/sign-in-story.spec.ts`; `theme/icons.spec.ts` gained units for every deferred
shell piece and shared component and the check that the shell's icon copy equals the package's.

### End-to-end
`e2e/shell.spec.ts` (story, reduced motion, palette by shortcut to a state and to a requirement, work queue popover,
avatar theme across a reload, sign out, rail collapse, axe in both themes at 1440 and 400 px on sign-in and overview,
plus palette, work queue and avatar menu); `review-and-sign.spec.ts` checks the Demo data pill; `screens.spec.ts`
captures the v2 screenshots.

### Bundle
Initial 606.77 kB raw, 155.87 kB estimated transfer (PERF-FE-1 on main: 695.03 kB, 168.22 kB). Budget 680 kB warning,
760 kB error. Details in `docs/PERF.md`, "Frontend bundle".

### Performance (production build on loopback, Chrome, 1440 × 900)
- Sign-in: LCP 224 ms (1,547 ms before the headline stopped fading in), CLS 0.00. Frames over the first six seconds
  (bootstrap, first paint, the map's wave entrance and one full turn of the story): 361 frames, median 16.7 ms, worst
  18.7 ms, 0 dropped. After the design review (51 px tiles on the larger stage, chips): LCP 180 ms, CLS 0, 360 frames,
  1 dropped at 39 ms during bootstrap (before first paint), none during the wave or the story.
- Overview reload with the shell: LCP 356 to 375 ms, CLS 0.00 (0.19 before the shell waited for the first navigation to
  resolve). Frames over three seconds: 179, 1 dropped at 79 ms during script evaluation, before first paint.
- Method: `performance_start_trace` (Chrome DevTools) for LCP and CLS; a `requestAnimationFrame` recorder installed
  before the page's scripts for frame intervals (a frame counts as dropped for each whole median interval it overran).

### Screenshots (`docs/screens/`, light and dark)
`ui2-sign-in-{1440,400}`, `ui2-overview-{1440,400}`, `ui2-command-palette-{1440,400}`, `ui2-work-queue-{1440,400}`,
`ui2-avatar-menu-{1440,400}`, `ui2-components-{1440,400}`, and every existing screen regenerated.

### Test tails (2026-11-27, final)
`LEXVAY_E2E_SLOT=1 make test`:
```
[INFO] Tests run: 886, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  25 passed (25)
      Tests  262 passed (262)
  26 passed (2.3m)
```
`LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=1 make e2e` (the suite plus both screenshot tests): `28 passed (3.5m)`.

### F10a — Coverage matrix API

Branch `feat-coverage-api`, no migration. Decisions D-390 to D-397 in `docs/decisions/F10a.md`. Backend only; nothing under `frontend/src`.

#### What changed
- **`GET /api/v1/programs/{id}/coverage?jurisdiction=XX`** (`com.lexvay.coverage`: `CoverageController`, `CoverageService`, `CoverageRepository`, `CoverageMatrix`).
  One read-only transaction, one statement (`CoverageRepository.MATRIX`), one row whose `courses` and `requirements` are `jsonb` read into the view records (D-390).
  Cells per (course version, source) with `SYSTEM` or `PERSON` and a distinct-quote count; program-level documents; `risk` computed in SQL (D-391, D-392).
  Numbers checked by the rules engine have no cells and risk `NONE` (D-393). 400 for an unknown, malformed or non-target jurisdiction, 404 for an unknown program or another tenant (D-390).
- **Shared predicates (D-396).** `AssessmentRepository.LATEST_FINISHED` and `CourseRepository.SUPERSEDED`, each used by its previous statement and by the matrix; `MappingRepository.current(...)` and `LATEST_EVENT` are reused.
- **Outdated evidence (D-394, review change).** `risk = OUTDATED_EVIDENCE` and `outdatedCells` for evidence on superseded course versions; no `stale` field.

#### Verification
`make backend-test` and `make perf` were rerun after the D-394 change and after merging `origin/main` again (F5a and FIX-4 included). `make e2e` ran before that change (18 passed, 1 skipped, slot 8); the change is backend only and the matrix has no screen yet.

`make backend-test` (exit 0):
```
[INFO] Tests run: 1044, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
`make perf` (exit 0):
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 109.8 s -- in com.lexvay.perf.EndpointLatencyTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 121.2 s -- in com.lexvay.perf.IndexUsageTest
| GET /programs/{id}/coverage?jurisdiction=CA | 3.15 | 4.91 | 5.88 | 1.0 | 4921 | 1666 |   (seed only: p50, p95, p99, connections, body, wire)
| GET /programs/{id}/coverage?jurisdiction=CA | 2.64 | 4.15 | 5.28 | 1.0 | 4921 | 1666 |   (62 tenants)
```
`LEXVAY_E2E_SLOT=8 make e2e` (exit 0):
```
  1 skipped
  18 passed (1.7m)
```

#### Tests added or changed
`CoverageApiTest` (11: order, cells, risk, decisions, person cell beside the system cell, program-level response, elective-only, required plus elective, rule revision before and after the re-run, superseded course version, no assessment, 400s, 404 and 401),
`CoverageSeedTest` (4: California order and the COUN 605 cell of CA-2, the elective-only CA-6, numeric rows without cells, accreditation as program-level evidence, UT-7, AZ-4 and TX-8 as `OUTDATED_EVIDENCE` with one outdated COUN 625 cell, Colorado outdated before the re-run),
`EndpointLatencyTest`, `ReadPathConnectionsTest`, `IndexUsageTest` (EXPLAIN of the statement), `InputValidationTableTest` (eight rows). `ForbiddenWordsTest` is green in the full run.

#### Open points for the orchestrator
- The work queue's `latest` CTE still has its own copy of the latest-finished ordering (D-396).

## SEC-2 — Argon2id passwords and audit list plans (PR #36)

Argon2id passwords and the audit list index, backend only (no migration; D-400 to D-407 in `docs/decisions/SEC-2.md`).

### Built

- `PasswordEncoders.application()`: `DelegatingPasswordEncoder`, default `argon2` (Argon2id, 32 MiB, 4 passes, one lane, about
  73 ms per hash here), `bcrypt` verified with or without its prefix, nothing else verifies. `bcprov-jdk18on` 1.87 pinned.
- `LexvayUserDetailsService` is also the `UserDetailsPasswordService`: a successful sign-in with a hash that is not the current
  kind replaces it (one guarded UPDATE, `PASSWORD_REHASHED`, no stamp change).
- Password rule 12 to 128 characters (code points); the 72-byte rule of D-373 is gone.
- `docs/OPERATIONS.md` "Users and sessions": hash and length, and the ADMIN setup-code power with the advice to use OIDC for SIGNER
  and ADMIN accounts.
- Audit plans: `IndexUsageTest` with about 80 000 audit rows. The index the roadmap names exists since V001
  (`audit_log_newest_idx`), so no V017.

### Where each point of the roadmap is tested

| Point | Test |
|---|---|
| 1 encoder, bcrypt still verifies, new hashes `{argon2}` | `PasswordEncodersTest`, `SeedLoaderTest` (hash prefix and sign-in with the demo password), `PasswordSetupApiTest` |
| 2 upgrade on login | `PasswordRehashTest` (prefixed and unprefixed bcrypt, once, audit without secrets, stamp unchanged, wrong password, concurrent change) |
| 3 length | `PasswordSetupApiTest` (128 accepted, 129 refused in ASCII, 2-byte and 4-byte characters, 128 multi-byte characters sign in, last character counts), `PasswordEncodersTest` (73, 128, 2 000 characters) |
| 4 audit index | `IndexUsageTest` (list and export plans, `audit_log_newest_idx` candidate), plans in `docs/PERF.md` and D-403 |
| 5 docs | `docs/OPERATIONS.md` |

Checked by hand: a throwaway PostgreSQL 18.7 with the perf data shape and an added `(tenant_id, created_at DESC, id DESC)` index
chose the same export plan as without it.

### Results

`make backend-test` (after merging origin/main with FIX-4): `Tests run: 1047, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS` (with the hash gate of D-407; 1035 before it).

`make perf`: `EndpointLatencyTest` and `IndexUsageTest` both `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`;
the slowest p95 is 6.63 ms (program overview), budget 40 ms.

`LEXVAY_E2E_SLOT=5 make e2e`: `1 skipped`, `18 passed (1.3m)`.

### Deviations and follow-ups (see D-400, D-403)

- No `V017`: the index named in the roadmap already exists (V001). The export's sort is the planner's choice for reading a quarter
  of a tenant's rows, not a missing index.
- Parameters are explicit (32 MiB, 4 passes) rather than Spring's defaults, which measured 14 to 17 ms, below the 50 ms floor.
- Review change (D-407): every hash goes through a fair gate of `lexvay.security.password-hash-concurrency` permits (default
  `max(2, processors)`); a caller that waits 2 s gets 503 "Sign-in is busy. Try again in a moment." with `Retry-After: 2` and nothing is
  hashed. Tests: `PasswordHashGateTest`, `PasswordHashBusyApiTest`.

## UI2-3 — Changes, audit trail and evidence report in v2 (PR #38)

Branch `ui2-changes-audit-report`, end-to-end slot 7. Decisions D-340 to D-349 in `docs/decisions/UI2-3.md`.

### Plan
1. Audit trail: one pure sentence function per audited action (complete against the backend's sources), worded
   details, day grouping, server-side filters as chips, "Download CSV" for administrators with the same filters.
2. Changes: a timeline of cards built by one pure card model (title, version pills, status, one action, what it
   affects), re-run progress from the job status, then a toast.
3. Evidence report: a document sheet with the Seal or a Draft stamp, requirements as a list with StatusIcon and quotes,
   a floating "Download PDF" pill.
4. Explanations into InfoTips; icons and token sets per chunk; initial bundle under the 680 kB warning.
5. Tests: Vitest for sentences, details, grouping, filters and the change card; end-to-end for the re-run from a card,
   the filter chips, the administrator's CSV (header row), the report download, axe in both themes at 1440 and 400 px.

### What was built
- `screens/audit/audit-sentence.ts`: `AUDIT_SENTENCES` (37 actions, one function each) and `ACTION_LABEL`, moved
  out of `core/labels.ts`; `audit-view.ts`: rows (actor, initials, sentence), `detailRows`, `groupByDay`,
  `auditParams` / `auditCsvUrl`, the filter options. The screen pages through `GET /audit` 100 entries at a time.
- `screens/changes/change-card.ts`: `versionPills`, `affects`, `changeCard`, `changeCards`; the screen renders them on
  PrimeNG Timeline with list semantics.
- `screens/report/`: the sheet, the stamp moment, the pill; `core/report-answer.ts` reads the report poll as JSON status
  or the PDF itself. This fixed a v1 defect: the poll parsed the ready PDF as JSON, so "Generate PDF" always ended in
  "The PDF could not be generated." once the PDF was ready.
- `theme/timeline-tokens.ts` (shared by Changes and Audit), Avatar tokens in `audit-theme.ts`; `audit.routes.ts`
  provides `AUDIT_ICONS`.

### Results (2026-11-27, after merging `origin/main` at 5f58ab9 with SEC-2 and F10a)

`make frontend-test`:
```
 Test Files  28 passed (28)
      Tests  281 passed (281)
```

`LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=7 make test` (backend, frontend unit, end-to-end; exit 0):
```
[INFO] Tests run: 1070, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  07:59 min
 Test Files  28 passed (28)
      Tests  281 passed (281)
  ✓  15 [suite] › e2e/screens.spec.ts:153:1 › screenshots of changes, the audit trail and the evidence report at 1440 and 400 px (17.2s)
  ✓  30 [records] › e2e/records.spec.ts:20:1 › the audit filter chips ask the server, details open in place, and the administrator downloads the same entries as CSV (4.7s)
  ✓  31 [records] › e2e/records.spec.ts:59:1 › an empty filter result says so with one action that clears it (2.8s)
  ✓  32 [records] › e2e/records.spec.ts:69:1 › the signed report carries the seal and downloads its PDF; a draft carries the Draft stamp (3.4s)
  ✓  33 [records] › e2e/records.spec.ts:92:3 › changes, the audit trail and both report states pass axe at 1440 px without horizontal scroll (7.2s)
  ✓  34 [records] › e2e/records.spec.ts:92:3 › changes, the audit trail and both report states pass axe at 400 px without horizontal scroll (7.9s)
  34 passed (5.1m)
```
The re-run from a timeline card is `review-and-sign.spec.ts:181` (passed, 5.1 s). Every end-to-end test also fails on
a request to a host other than localhost and on an unregistered icon; none did.

Production build (`npx ng build`), initial bundle under the 680 kB warning:
```
main-5S4N4VJ4.js    | main            | 598.52 kB |               153.92 kB
styles-VLABZARL.css | styles          |   9.22 kB |                 2.39 kB
                    | Initial total   | 607.74 kB |               156.31 kB
chunk-g_02x9GH.js   | audit-routes    |  34.00 kB |                  9.94 kB
```
UI2-0 measured 606.77 kB (D-317); the +1 kB is the Blob reading of the report poll in the eager `ApiService` and
`isAdmin`, less the action labels that left the shell.

Screenshots (`docs/screens/`, light and dark each): `ui2-3-changes-1440`, `ui2-3-changes-400`, `ui2-3-audit-1440`,
`ui2-3-audit-400`, `ui2-3-report-draft-1440`, `ui2-3-report-draft-400`, `ui2-3-report-signed-1440`,
`ui2-3-report-signed-400`; the v1 names `changes`, `evidence-report`, `evidence-report-ca` and `audit-trail` were
retaken with the new screens. Screenshots of other items' screens that the run retook were left as they were.

### Follow-ups outside this item
- Backend: `LOGOUT` is recorded after the session is cleared, so its entry has no user (rule 9, "who"). The screen
  attributes own-account actions to the account they are recorded on (D-344); the backend should record the actor.
- A new audited action fails `audit-sentence.spec.ts` until it has a sentence and a label (D-341): intended, but it
  couples backend items to one frontend file.

## FIX-5 — Logout audit actor and work queue predicate (PR #40)

- Logout: `SecurityConfig` records `LOGOUT` with `recordAs(user.id(), ...)` (D-410). `LogoutAuditTest` (real HTTP, session
  cookie, CSRF header) asserts actor, `entity_type = app_user`, `entity_id` for two users and that an anonymous logout records
  nothing. Run against the unfixed handler, the two tests that expect an actor failed.
- Work queue: `latest` CTE uses `AssessmentRepository.LATEST_FINISHED` (D-411); `WorkQueueSeedParityTest` (D-412).
- Before/after on the seed (one-off scratch test, not kept): the SIGNER, REVIEWER, CONTRIBUTOR and ADMIN views (kind, program,
  jurisdiction, count, id list lengths; 18 lines) are identical with the old and the new statement.
- Frontend: UI2-3 had merged by the time of the PR; `audit-view.ts` keeps its fallback for `LOGOUT` only, with a spec for recorded and old entries (D-413). `make frontend-test`: 28 files, 282 tests passed.
- Test order: `runPending()` before `fake.reset()` in 11 test classes (D-415).
- Not done: `MappingRepository.latestAssessmentsUsing` still has its own copy of the ordering (D-411).

## UI2-1 — Program overview and determination register in v2 (PR #39)

Branch `ui2-overview-register`, decisions D-320 to D-329 in `docs/decisions/UI2-1.md`. Built on UI2-0's shared
components; `origin/main` (F10a) merged before the pull request.

### Spec points
1. **Overview title**: the program's name in Display type, license and catalog year as two pills (PrimeNG Tag).
2. **Hero panel**: StateMap `hero` with its one legend (the statuses on the map and "Not a target"), three Stats beside
   it: To review (count-up, accent ring of reviewed of all), Evidence found (percentage, segmented findings ring),
   Signed ("1 of 5", seal ring). One InfoTip ("About these figures") holds the only explanation: findings are proposals,
   reviewers decide, only a signer signs. Without a finished assessment the hero says so in one sentence with "Run
   assessment".
3. **Needs you**: at most three work-queue items of this program, soonest-to-finish first, each a compact row with one
   verb-first action; "Run again" for a failed assessment runs it in place (D-322, D-324).
4. **State cards**: one per target, name and code, segmented findings ring, status chip (icon and words), one
   stage-dependent action: Review, Sign (signers only, else Review), View report; none before an assessment. Cards enter
   staggered and the whole card is the action's link and lifts on hover (D-321).
5. **Shared element**: the tile or card whose action runs takes `view-transition-name: state-<code>` (upper-case code)
   for that navigation only, so the name is unique; Review's state header takes the same name in UI2-2 (D-325).
6. **Register**: StateMap `compact` of the signed states coloured by current outcome (common outcome or "Different
   outcomes" across programs) with its card opening the evidence report; the filters and the count (a Stat) in a panel
   beside it; the table on its own surface with 52 px rows and hover tint; an empty table says one sentence with "Clear
   filters" (D-327).
7. **By program**: three lists as cards with counts, each signed row with its Seal, unsigned rows with a dashed
   placeholder and the note (D-329).
8. **Explanations**: register subtitle and the lists' paragraph moved into InfoTips; the lists' paragraph still prints
   (print-only copy), the rest of the print view unchanged in content (D-328).
9. **Motion**: map wave, count-ups, ring sweeps, staggered card and row entrances, hover lift; all off under reduced
   motion through the UI2-0 tokens and `MotionPreferences`.

### Shared changes (called out in the pull request)
- `ui/state-map`: `legend` input, `MIXED` tile status, no progress ring or "not assessed" for `total` 0, the
  `departing` shared-element name (D-325, D-326). `ui/progress-ring`: `seal` tone.
- `core/assessment-runner.ts` from the command palette (D-324); `ui/initials.ts` out of `ui/seal.ts` (bundle, D-329).
- `styles.css`: link buttons keep the button's colours and no underline (D-329).
- Removed: `ui/findings-bar.ts` and `barSegments` (only the v1 overview used them).
- `e2e/evidence-requests.spec.ts` (F4): the blocked-request step now waits for Review to open before going back to the
  inbox; without it the second click could land while the first navigation was still loading and the router ignored
  it (failed in two of three runs on this branch).

### Unit tests (`make frontend-test`)
New `screens/overview/overview-view.spec.ts` (card stage and action per stage and role, reopened wording, seal tone,
findings ring and label, map facts, hero numbers, "Needs you" scoping, priority, limit, run-again and change rows);
`screens/register/register-view.spec.ts` gains the register map (single program, common outcome, mixed, reopened);
`ui/state-map/state-map-model.spec.ts` gains the legend and progress-free names; `core/overview.spec.ts` follows the
stage and `reopened` flag.

### End-to-end
New `e2e/overview.spec.ts`: map keyboard (one tab stop, arrows, Home, End, Escape, Enter runs the action), a tile card's
action by pointer, axe with a card open, every card has one stage action and its ring is named, the Texas card's
Review lands on Review for Texas (URL and heading), hero numbers match the API, "Needs you" has at most three rows with
one action each, a reviewer is never offered Sign, reduced motion shows final numbers, axe in both themes at 1440 and
400 px without horizontal scroll. `e2e/register.spec.ts`: the map (one signed tile, its card's report link, axe with the
card open, empty map under a filter that matches nothing), the InfoTips, "Clear filters", the lists as list items with
the seal's sentence, the print-only note. `review-and-sign`, `shell` and `screens` specs follow the new overview.

### Screenshots
`docs/screens/ui2-1-*`: overview, overview with a map card, overview cards, register, register lists, each at 1440 and
400 px in light and dark. `program-overview-*`, `register-*`, `register-by-program-*`, `register-print-*` and
`ui2-overview-*` were retaken because their screens changed.

### Bundle
Initial 605.46 kB raw, 155.80 kB estimated transfer after merging UI2-3 (606.36 kB against 606.77 kB on `origin/main` before it). Budget 680 kB warning.

## UI2-2 — Review and signing in design v2 (PR #37)

Branch `ui2-review`, end-to-end slot 1. Decisions D-330 to D-339 in `docs/decisions/UI2-2.md`.

### What changed
- **Header**: the states as pills with full names and 20 px rings (decided of total; the numbers are each tab's
  accessible description), the active pill named `state-<code>` for the overview's shared-element morph, a 40 px ring
  with "6 of 8", "Sign <state>" once every requirement is decided (one pulse, then still), the Seal and outcome once
  signed, a Reopened chip, the keyboard button. Under 640 px the signing action has its own row.
- **List rail** (288 px, `RequirementList`): a segmented icon filter whose segment names and tooltips carry the counts
  ("7 to review"), rows of ref, title and one StatusIcon, the "evidence provided" marker, the selected row kept in view
  inside the list only.
- **Requirement hero** (`RequirementCard`): ref chip, title at Title size, rule text in the Document type in a quiet
  inset with its version and source, the finding as a large chip with the check method in one line and an InfoTip,
  the model's rationale clamped, the current decision with who, when and its note, the stale warning.
- **DecisionBar** (`ui/decision-bar/`): Accept (A), Request (R), Gap (G) on the raised, blurred bar floating at the foot
  of the centre pane; Gap, G or the note button open the note in place and the third action becomes Confirm gap;
  Escape closes an empty note. The note opens by itself where a person answered an evidence request.
- **Decision motion**: the row's ring draws around its new filled icon, the row settles on the spring, the next
  requirement slides in from the side it comes from, the rings advance. Nothing waits for motion; reduced motion
  removes it.
- **Signing** (`SignDialog`): "Sign California", "8 of 8 decided" with an InfoTip, three outcome cards, a faint Seal
  preview hidden from assistive technology; after the server signs, the recorded Seal stamps down with one ink ring,
  then Done (focused). `Seal` has a `stamp` input.
- **DocumentViewer**: `softWrap(text)` shows sentence-wrapping line breaks as spaces, one character for one, so the
  mark, selections and stored offsets are unchanged; the header is an icon pager with the same accessible names.
- **Evidence pane** (`EvidencePane`): the viewer on the right, a compact meta line, "Quote only", F4's provided
  passages, "Show on page" under 1024 px.
- Every keyboard shortcut (J, K, A, R, G, ?) and the polite live region (`data-testid="message"`) are kept; the live
  region is visually hidden (the toast and the motion show the result).

### Tests
- Vitest: `softWrap` (joins, kept breaks, non-ASCII lower case, equal length with only joined newlines differing, a DOM
  selection over the shown text with the mark mapping back to the stored text exactly); `review-workspace.spec.ts`
  (filter labels, filter icons equal to the StatusIcon's, travel direction by J, K, click and after a decision, header
  and pill progress, `checkMethod`); `icons.spec.ts` (a unit for the DecisionBar).
- End to end: the California review under reduced motion with no running animation after each decision, the gap note,
  a request with a note, signing through the outcome cards with the preview hidden and the stamped seal named, keys
  during the card's entrance, G and Escape in the note, the bar in view at 400 px, the pill descriptions, axe in both
  themes on Review at 1440 and 400 px, the gap note, the signing dialog at 1440 and 400 px with and without an outcome,
  the signed seal. The viewer suite compares the page with `softWrap` of the stored page and selects across a joined
  break. `evidence-requests.spec.ts` waits for Review before returning to the inbox (a click during the route's view
  transition superseded the first navigation, seen once in a full run).

### Bundle
Initial 609.25 kB raw, 156.79 kB estimated transfer (budget 680 kB warning). Review's chunk is 67.47 kB raw.

### Screenshots (`docs/screens/`, light and dark)
`ui2-2-review-{1440,400}`, `ui2-2-decided-{1440,400}`, `ui2-2-gap-note-{1440,400}`, `ui2-2-ready-to-sign-{1440,400}`,
`ui2-2-signing-{1440,400}`, `ui2-2-signed-1440`; the existing `review-*` and `evidence-request-drawer` captures
regenerated.

### Test tails (2026-11-27, after merging origin/main)
`LEXVAY_E2E_SLOT=1 make test` (backend and frontend unit green; its end-to-end stage failed once in
`evidence-requests.spec.ts`, the navigation race fixed above), then `LEXVAY_E2E_SLOT=1 make e2e`:
```
[INFO] Tests run: 1044, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  25 passed (25)
      Tests  277 passed (277)
  26 passed (3.5m)
```
`LEXVAY_SCREENSHOTS=1`, slot 1 (the suite plus the three screenshot tests): `29 passed (4.2m)`.

### Review round 1 (2026-11-27)
- The DecisionBar sits in flow 24 px under the requirement and sticks to the pane's foot (16 px) only when the
  requirement is longer than the pane; checked on TX-2 (short) and a lengthened rule text at 1440 and 400 px in both
  themes. `ui2-2-*` screenshots regenerated.
- CI on the PR head failed in `shell.spec.ts` (UI2-0's avatar menu at 400 px): the test clicked the avatar right after
  `setViewportSize`, and PrimeNG's Popover closes itself on a window resize (after a reload the menu is also a deferred
  chunk), so the menu was not open. The test now waits two animation frames for the resize to pass and opens the menu
  until it is there. Not the Maven download problem.
- After merging `origin/main`: `make frontend-test` `Test Files 25 passed (25)`, `Tests 277 passed (277)`;
  `LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=1 make e2e` (the whole suite plus the three screenshot tests) `29 passed (3.8m)`.

### Review round 2 (2026-11-27)
- Merged UI2-3 (PR #38); `screens.spec.ts` keeps both screenshot tests, `review-and-sign.spec.ts` merged cleanly.
- CI run 46026619701 failed `review-and-sign.spec.ts:3` with a strict-mode violation: the faint Seal preview in the
  signing dialog carried the signed-seal sentence ("Signed by Dana Whitfield on ...: determined not to meet") as its
  `aria-label`, and `getByLabel` matches `aria-label` text, so the chosen outcome's label was not unique. Whether the
  preview had rendered when the locator resolved decided the result, hence green locally and red on the slower runner.
  `review-and-sign.spec.ts:150` failed only because California was then never signed. Fix in the product: `Seal` has a
  `preview` input (no role, no name, `aria-hidden`), used by the dialog; the test asserts the radio is the only element
  with that label and that no image claims a signature before signing.
- A local run showed a second race: switching states quickly could pull Review back to the previous state when that
  state's `?jurisdiction=` landed after the person had moved on (the param effect read it as an external link). Review
  now ignores the parameters it is writing itself (`ownNavigations`).
- `make frontend-test`: `Test Files 28 passed (28)`, `Tests 296 passed (296)`. `LEXVAY_E2E_SLOT=1 make e2e` twice in a
  row: `31 passed (3.4m)`, `31 passed (3.7m)`.

### Merge of UI2-1, FIX-5 and the shell fix (2026-11-27)
Main's opening overview checks in `review-and-sign.spec.ts` kept with the `stateTab` helper and the Review steps;
`screens.spec.ts` keeps the UI2-1, UI2-2 and UI2-3 screenshot tests; `evidence-requests.spec.ts` takes main's wait
(equivalent to this branch's); `initials` imported from `ui/initials.ts`. `make frontend-test`: `Test Files 29 passed
(29)`, `Tests 312 passed (312)`. `LEXVAY_E2E_SLOT=1 make e2e` twice in a row: `37 passed (4.0m)`, `37 passed (3.7m)`.
Initial bundle 605.33 kB raw, 155.71 kB transfer.

## F10b — Coverage matrix screen (PR #41)

Branch `ui2-coverage`, end-to-end slot 7. Decisions D-416 to D-425 in `docs/decisions/F10b.md`.

### What changed
- **Route and rail**: `programs/:programId/coverage`, a lazy chunk with its own icons and token sets; "Coverage" in the
  rail after Review (`phosphorGridFour`, added to the generated `SHELL_ICONS`) and so in the command palette. The state
  is the `jurisdiction` query parameter; the selected state is requested first, then each other target once for the
  pill rings.
- **`StatePills`** (`ui/state-pills.ts`): Review's pills from UI2-2, extracted as the tab list of the screen's own
  PrimeNG Tabs, with the 20 px ring as each tab's description and the `state-<code>` shared element; here the ring is
  the share of requirements without a flag ("2 of 8 with no flag").
- **Matrix**: a real table; requirements as row headers (ref in mono, title), current courses as columns (code in mono
  with the title in a tooltip), "Required" and "Elective" marked once per run under the codes, "Program" last. Filled
  dot for the system's quote, ring for a person's, dot in a ring for both, dashed ring with a clock for evidence on a
  superseded course version (under the column of its current version). Faint points in empty cells; rules-engine rows
  read "Checked by the rules engine".
- **Card**: hover or focus opens a PrimeNG Popover with the code, title, required or elective, each source with its
  quote count (or the program documents), and "Open in Review" (`review?jurisdiction=XX&ref=REF`). Enter moves the
  focus into it, Escape closes it and gives the focus back.
- **Flags**: a sticky right column with icon, colour and words; flag chips above the matrix with counts (a PrimeNG
  SelectButton drawn as pills) dim the other rows, which keep their place.
- **Keyboard**: one tab stop; arrows between the cells that hold evidence, Home and End, Ctrl+Home and Ctrl+End; the
  row and column of the current cell light up.
- **Motion**: column by column, 20 ms apart (at most twelve), marks scale in from .6 on the spring; off under reduced
  motion.
- **Narrow windows**: the matrix scrolls inside its frame with sticky requirement and flag columns; a focused cell is
  scrolled clear of them; under 640 px the chips scroll in one row, the requirement column is 164 px with titles on two
  lines at most (ellipsis, a tooltip only for a cut title), every row 60 px, and the flag column shows its icon.
- **Shell** (one line, D-423): bottom tab bar items 40 px so nine screens fit 400 px.

### Tests
- Vitest `coverage-view.spec.ts` (16 tests): column order and groups, cell lookup by course version, outdated cells under
  `supersededBy`, current plus outdated, program column, rules-engine rows, flag counts and matching, pill share,
  keyboard movement; `icons.spec.ts` has a unit for Coverage; `commands.spec.ts` lists the new screen.
- End to end `coverage.spec.ts` (5 tests): CA-2's COUN 605 dot in its column, CA-6 "Rests on an elective", CA-7 "No
  evidence cited", the rules-engine rows, Utah's UT-7 "Outdated evidence" with the dashed cell in the COUN 625 column,
  UT-6 in the program column, the state across a reload, keyboard movement with one tab stop, Enter and Escape on the
  card, "Open in Review" landing on CA-2 in Review, the flag filter dimming rows (and the live region), axe in both themes
  at 1440 and 400 px (plain, with the card open, with a filter on), no sideways page scroll, a focused cell clear of the
  sticky columns.

### Bundle
Initial 605.94 kB raw, 155.78 kB estimated transfer (warning 680 kB). Coverage chunk 35.48 kB raw, 9.27 kB transfer;
Review chunk 66.01 kB raw.

### Review and `StatePills`
UI2-2 merged first, so this branch moved Review onto the shared pills (D-417): Review's header renders
`<lx-state-pills>` inside its `p-tabs`, its own pill markup and styles are gone, and `stateProgress` returns the shared
`PillProgress`. Review's behaviour and end-to-end tests are unchanged; a Review capture on the slot-7 stack shows the
same header as `ui2-2-review-1440`, so the `ui2-2-*` screenshots were not retaken (they differ only by the rail's new
Coverage entry).

### Test tails (2026-11-27, `LEXVAY_E2E_SLOT=7 make test`, after merging origin/main with UI2-2 and the review fixes)
```
[INFO] Tests run: 1074, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  30 passed (30)
      Tests  329 passed (329)
  5 skipped
  42 passed (5.2m)
```
The five skipped tests are the screenshot tests, which run only with `LEXVAY_SCREENSHOTS=1`. The `f10b-*` screenshots
were taken from `coverage.spec.ts` with `LEXVAY_SCREENSHOTS=1` on a freshly seeded slot-7 stack.

### Screenshots (`docs/screens/`, light and dark)
`f10b-california-{1440,400}`, `f10b-utah-{1440,400}`, `f10b-card-{1440,400}` (the outdated UT-7 cell's card open),
`f10b-filter-{1440,400}` (a flag highlighted).

## FIX-6 — OCR checkpoints per page (PR #42)

Branch `fix-ocr-checkpoints`, slot 5, migration V017. Decisions D-436 to D-441 are in `docs/decisions/FIX-6.md`.

## Done
- V017 `document_ocr_page`: tenant policy, forced RLS, `forbid_change()` trigger, primary key as the only index.
- `OcrCheckpoints` (with `NONE`), `DocumentOcrCheckpoints` (`REQUIRES_NEW` per page, one query to load), two
  `DocumentRepository` statements, `OcrPage.NOTHING`.
- `TextExtractor.extract(FileType, byte[], OcrCheckpoints)`; the two-argument form delegates with `NONE`.
- `IngestDocumentHandler` passes the document's checkpoints and audits `ocrPagesReused`.
- Tests: checkpoint cases in `TextExtractorOcrTest`, `DocumentOcrCheckpointIngestTest`,
  `Rule5_OcrCheckpointImmutabilityTest`, `Rule7_OcrCheckpointIsolationTest`.
- `docs/OPERATIONS.md`: one sentence.

## Test runs
`LEXVAY_E2E_SLOT=5 make test`, exit 0: backend `Tests run: 1091, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`),
frontend `Test Files 29 passed (29)`, `Tests 312 passed (312)`, end-to-end `37 passed (4.0m)`. The first backend run had one
failure in the new ingest test: `JobRunner.runOne` claims the oldest queued job of any tenant, and jobs that other test
classes had left queued were claimed first. The test now drains the queue in `setUp`.

## FIX-7 — One "latest finished assessment" ordering (PR #45)

Branch `fix-latest-finished`, decisions D-450 to D-452 in `docs/decisions/FIX-7.md`. Backend only, no migration.

- `MappingRepository.latestAssessmentsUsing` now runs `LATEST_ASSESSMENTS_USING`: `program` joined laterally to
  `AssessmentRepository.LATEST_FINISHED`, filtered by an `EXISTS` over the program's latest assessment's mappings of the
  rule version (D-450). `grep -rn "finished_at DESC" backend/src/main/java` shows only `LATEST_FINISHED`.
- `LatestAssessmentsUsingParityTest`: old statement as a literal against the new method, equal as sets for every rule version
  of the seeded tenant, with three extra programs (newest finished lacks the version; newest assessment FAILED; tied finish
  time) and explicit expectations (D-451).
- `IndexUsageTest` explains the statement on the perf seed (100 tenant copies, application role): `program_tenant_idx`,
  then per program `assessment_program_status_finished_idx`, then `mapping_assessment_jurisdiction_idx` (500 rows of
  the large assessment) and `requirement_pkey`. No sequential scan on `mapping` or `assessment`.
  Execution 1.05 ms, 1 551 shared buffers, planning 0.6 ms (`target/perf/latest-assessments-using-plan.txt`) (D-452).
- `make perf`: `EndpointLatencyTest` and `IndexUsageTest` green (slot 6).
- ROADMAP: the FIX-5 and F10a follow-up lines about this ordering are removed.

## F5b — Administration screens (PR #44)

Branch `ui2-admin`, decisions D-426 to D-435 in `docs/decisions/F5b.md`. Frontend only; uses F5a's endpoints and
SEC-2's busy gate. `origin/main` (UI2-2) merged before the pull request.

### Spec points
1. **People** (`/people`, D-426): tenant-wide rail entry under Determination register (`phosphorUsers`), administrators
   only; `adminGuard` sends anyone else to the overview. Table of avatar initials, name (with "You" on one's own row),
   email, role chip, status chip (Active, Setup pending, Deactivated; icon and word), created date; search by name or
   email; role and status filter chips; the count as "1 of 6 people". Row menu: Change role, Deactivate or Reactivate,
   New setup code; one's own row offers New setup code only. Rows stack into blocks under 720 px.
2. **Add person** (D-427): Dialog with name, email and role (Select, Contributor by default, "About roles" InfoTip with
   one line per role). On success the dialog becomes the one-time link view: the whole `<origin>/setup#code=…` in mono,
   "Copy link", "Valid for 72 hours, until 30 Nov 15:04", "Shown once. Copy it now and hand it over yourself.", Done.
   "New setup code" shows the same view ("Earlier links stop working."). The code is held only by the dialog and dropped
   when it closes; no storage, no logs.
3. **Change role, deactivate, reactivate** (D-432): ConfirmDialog with the person, the new role Select, and the
   consequence in one sentence ("Their current session ends." / "They can sign in again."); the server's 409 sentences
   appear inside the dialog, which stays open.
4. **Set password** (`/setup#code=…`, D-428, D-429, D-435): the sign-in frame without the story (`ui/auth-frame.ts`, shared with
   sign-in), the code read and removed from the address bar with `history.replaceState` in the constructor, two fields
   with show and hide, the live checklist (12 to 128 characters, not an email address, both fields match) with icons, a
   strength meter from length and character variety, Submit to `POST /auth/setup`; the invalid-code view for the
   server's invalid-code 400; success goes to sign-in with the toast "Password set. Sign in."
5. **Session and busy handling** (`core/access.ts`, `core/busy.ts`, D-430, D-431): the 401 "Your access changed. Sign in
   again." signs out locally, clears the program, routes to sign-in and shows the sentence; a 503 with `Retry-After` on
   sign-in or setup shows "Sign-in is busy. Try again in a moment." and disables the button with "Retry in 2 s".
6. **Tests** (D-434): see below.

### Deviation
"Not the email" is checked as "not an email address": the setup page knows only the code (D-428). The client rule is
stricter than the server's and implies it; the server keeps checking its own.

### Shared changes (called out in the pull request)
- `ui/auth-frame.ts` from the sign-in page (sign-in looks as before); `@keyframes lx-rise` to `styles.css`.
- `.lx-chip` (the audit trail's pill Selects) from `audit.css` to `styles.css`; the Avatar token set from `audit-theme.ts`
  to `theme/avatar-tokens.ts`. Values unchanged.
- `navLinks` takes the role (People for administrators, also in the command palette); `beforeSignIn` replaces the
  shell's `/login` test; `phosphorUsers` in `SHELL_ICONS`.
- `SessionService`: `notice`, `end`, and a 401 from the session call read as signed out (D-374); `ApiService` and
  `models.ts` gain the user administration calls and types.

### Unit tests (`make frontend-test`)
New: `screens/setup/password-rules.spec.ts`, `screens/people/people-view.spec.ts`, `core/access.spec.ts`,
`core/busy.spec.ts`, `core/navigation.spec.ts`; `commands.spec.ts` (People for administrators) and `icons.spec.ts`
(units for People and setup) extended.

### End-to-end (`e2e/people.spec.ts`)
Add a contributor, copy the link (clipboard read back, code in no storage), sign out, set the password (the code gone
from the address bar), toast, sign in as the new person (no People), the used link refused; a second context signs in,
the administrator changes the role and the person's next request lands on sign-in with the access-changed sentence,
they sign in again as Reviewer; deactivate (their session ends, sign-in refused), reactivate (sign-in works), new setup
code (a different link); one's own row offers only New setup code and a 409 stays in the role dialog; search and filter
chips; a reviewer has no People and `/people` sends them to the overview; a busy sign-in counts down; axe in both themes
at 1440 and 400 px without horizontal scroll on People, Add person, the link view, Change role and the setup page with
rules partly met.

### Screenshots (`docs/screens/`, light and dark)
`f5b-people-{1440,400}`, `f5b-add-person-{1440,400}`, `f5b-setup-link-{1440,400}`, `f5b-change-role-{1440,400}`,
`f5b-setup-{1440,400}` (rules partly met), taken by `e2e/people.spec.ts` with `LEXVAY_SCREENSHOTS=1`.

### Bundle
Production build: initial 608.71 kB raw, 156.51 kB estimated transfer (main 598.63 kB, styles 10.08 kB), against
605.33 kB / 155.69 kB on `origin/main` at the merge: +3.38 kB raw (the interceptor, the session notice, the People rail
icon and the chip styles now global). Lazy: `people-routes` 31.89 kB raw (8.61 kB transfer), `setup-routes` 21.50 kB
(6.11 kB), plus the PrimeNG chunks they share with other screens. Budget 680 kB warning.

### Follow-ups outside this item
- An anonymous lookup of a setup code's email would let the page check "not your email" exactly (a backend change).

### Test tails (2026-11-27, after merging `origin/main` at 2bc911c)
`make frontend-test` and `LEXVAY_E2E_SLOT=2 make test`:
```
[INFO] Tests run: 1074, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  34 passed (34)
      Tests  351 passed (351)
  45 passed (4.8m)
```
An earlier `LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=2 make test` run (49 passed, 1 failed) found that `records.spec.ts`'s empty
audit filter ("Access withdrawn" alone) now matches People's deactivation; the test now also filters by Dana, who never
withdraws access. Only the `f5b-*` screenshots from that run are committed.

### Review round 1 (2026-11-27, after merging `origin/main` at da71fbc with F10b and FIX-6)
- **Setup code in the fragment** (D-435): the link is `<origin>/setup#code=…`; the page reads `location.hash` and clears it
  with `history.replaceState`; `?code=` is not read (nothing was ever issued). `people.spec.ts` records every request URL
  while the link is opened and the password set, and requires none to carry `code=` or the code. The backend does not
  build or document the link (it returns the code alone), so no backend or `docs/OPERATIONS.md` change.
- **Register table** (UI2-1's file): `class="lx-table"` instead of the `styleClass` input `p-table` lacks, so the narrow
  rules apply; `ui2-1-register-400-*` retaken.
- **Bottom tab bar** (D-433): with Coverage and People an administrator had ten tabs and the bar scrolled at 400 px. A
  `priority` on every `NavLink`, `tabSlots` and `tabBarOverflow` (pure, unit-tested) put the least used into a final
  "More" tab (PrimeNG Menu in the deferred `shell/more-menu.ts`); at 400 px an administrator's People and Audit trail
  move there, every other role still fits. New `people.spec.ts` test: the bar does not scroll, More opens by keyboard,
  lists People and Audit trail, passes axe, ArrowDown and Enter open People, and the tab then shows it is current.
  `e2e/support.ts` gains `goTo` (link, or More), used by `records.spec.ts` and `screens.spec.ts` for the administrator at
  400 px. Screenshot `f5b-more-menu-400`; the other `f5b-*` retaken.
- Bundle: initial 611.74 kB raw, 157.27 kB transfer (main 601.67 kB, styles 10.08 kB) against 605.94 / 155.81 kB on
  `origin/main` da71fbc (+5.80 kB: the round-1 More tab and People's rail icons on top of the first round). Lazy:
  `people-routes` 32.06 kB, `setup-routes` 21.51 kB, `more-menu` 2.51 kB.

`LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=2 make test`:
```
[INFO] Tests run: 1091, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  35 passed (35)
      Tests  374 passed (374)
  56 passed (7.4m)
```
Only the `f5b-*` and `ui2-1-register-400-*` captures of that run are committed.

## INFRA-2 — Remote state, WAF, flow logs and private-range egress (PR #46)

Branch `infra-hardening`, decisions D-463 to D-470 in `docs/decisions/INFRA-2.md`. Terraform only: nothing was planned
against an account or applied.

### Spec points
1. **Remote state** (D-463): `backend "s3" {}` in `versions.tf`, `infra/backend.hcl.example`, `backend.hcl` ignored,
   `init -backend=false` kept; bootstrap and `init -backend-config=backend.hcl` in `docs/OPERATIONS.md`.
2. **WAF** (D-464 to D-466): alias `aws.us_east_1`, `infra/waf.tf` with the four rules in order and the
   `SizeRestrictions_BODY` count override, `web_acl_id` on the distribution, logging to
   `aws-waf-logs-<name>` with its own resource policy, `authorization` and `cookie` redacted, `waf_login_rate_limit`.
3. **Flow logs** (D-467): `infra/flow_logs.tf`, delivery role, key policy statement for CloudWatch Logs limited by
   encryption context.
4. **Egress** (D-468): `infra/nacl.tf`, the specified outbound and inbound rules, link-local caveat in a comment and in
   `docs/OPERATIONS.md`.
5. **Tests** (D-470): eight new runs in `infra/tests/stack.tftest.hcl`.
6. **Docs**: "Deploy on AWS (Terraform)" section in `docs/OPERATIONS.md`; the F12 deployment note and the WS-EVAL
   Terraform sentence removed from the roadmap's follow-ups.

### Checks
- Provider arguments were checked against the schema of the installed `hashicorp/aws` 6.74.0
  (`terraform providers schema -json`) and its registry documentation for that version.
- `make infra-validate`: format check, `init -backend=false`, validate, `terraform test` (16 runs).
- `make backend-test`: 1091 tests, 0 failures (run before merging origin/main; this item changes no backend file).

### Deviations from the spec
None. Added beyond it, because the resources need them: a CloudWatch Logs resource policy for the WAF log group
(D-466), text transformations on the rate rule's path match (D-465) and the account-root statement kept in the key
policy (D-467).

## UI2-4 — Rules, evidence requests and program setup (F2) in v2 (PR #43)

Branch `ui2-rules-requests-setup`, e2e slot 4. Decisions in `docs/decisions/UI2-4.md` (D-285 to D-294, D-350 to D-359).

## Built
- **Rules v2**: version list and selected version as one hero panel; status as icon and word in the list and a Tag in
  the header; requirement count as a Stat; InfoTips for every explanatory paragraph; extraction states as empty states;
  the edit grid's title takes the free width (F3b follow-up); the **Search terms** cell as editable chips (at most 12,
  40 characters each, normalised like the server; the notice says how many the server did not keep); a Search terms
  column in the read-only table; Compare shows `SEARCH_TERMS` as its own aspect with removed and added terms marked;
  the approve dialog with the count and the seal icon.
- **Evidence requests v2**: the inbox as a hero panel with the segmented filter and counts, rows with the state tile,
  the asker's initials, status Tags with icons; requests with `signed: true` (FIX-3) are blocked like stale ones, with the
  reason in the row and the sentence behind an InfoTip; the rail badge no longer counts signed requests; the answer
  drawer restyled on a shared sheet with numbered parts and the passage in Document type.
- **Program setup (F2)**: `/programs/new` and `/programs/:id/setup` (one route), "Program setup" in the rail, "New
  program" on the home screen and in the program switcher; a restyled Stepper with per-step status; Details and States
  (editable license Select, MultiSelect, StateMap `mini`); Courses (add course, new catalog year); Documents (FileUpload
  in custom mode, per-file kind and course version, sequential upload, Uploading, Reading, Ready (n pages), Failed,
  Already uploaded with a link, reading polled every 2 s while visible, read-only viewer sheet); Program facts (rows from
  the assessed rule versions' numeric fields, Record sheet with the document picker and a selectable viewer, mismatch
  warning, history); Readiness (checklist, Run assessment, progress ring, toast, Open Review).
- Shared: `shared/sheet` (the F4 drawer pattern as one component), `shared/document-picker` (F4's picker), Lexvay token
  sets for Drawer, Stepper, FileUpload, MultiSelect (with Checkbox and Chip) and Chip.

## Not built, and why
- No backend change: every endpoint F2 needs exists (spec point 7). The assessment view has no "processed" field, but
  its per-state totals already count the mappings produced so far, so the progress ring needs none.
- The Add course dialog makes one request, not two (D-288, disagreement with the spec).
- The StateMap `mini` is a picture of the chosen states beside the MultiSelect, not a second control (D-287).

## Verification
`export LEXVAY_E2E_SLOT=4; make test` after the review round and merging `origin/main` (F10b, FIX-6, F5b, FIX-7, design v3
docs), real tails:

```
[INFO] Tests run: 1092, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
 Test Files  40 passed (40)
      Tests  414 passed (414)
  6 skipped
  55 passed (6.7m)
```
The six skipped are the screenshot tests; the `LEXVAY_SCREENSHOTS=1` run on the same slot passed all 61 and retook the
`ui2-4-*` screenshots. No backend file changed.

- Bundle: initial 615.51 kB raw, 157.92 kB transfer (under the 680 kB warning); `program-setup-routes` 236.8 kB raw.
- Review round: Rules opens on the version that needs a person; setup confirmations are toasts plus a hidden live
  region; request cards say "No answers" / "N answers"; Program setup has tab-bar priority 65 in F5b's overflow; the
  program setup screen moved to `screens/program-setup/` because F5b's password screen took `screens/setup/`.
- Screenshots: `docs/screens/ui2-4-*` in light and dark at 1440 and 400 px.

## F14 — Check a report file (PR #47)

Branch `feat-report-check`, slot 3, migration V018. Decisions D-453 to D-462 are in `docs/decisions/F14.md`.

## Done
- V018: `report.content_sha256` and `report_content_sha_idx`; `GenerateReportHandler` sets the hash in the update that
  marks the report ready (`ReportRepository.markReady` gained the parameter).
- `POST /api/v1/reports/check`: `ReportCheckService` (stream hash with `PdfFingerprint`, lookup and audit in one
  transaction), `ReportCheck` with `Match`, `ReportRepository.CHECK`. `REPORT_CHECKED` audited with `{match, sha256}`.
- `DeterminationRepository.reopenedAt` shared by the register and the check.
- Tests: `ReportCheckTest` (match with every field, one byte changed, another tenant, reopened, not a PDF, audit rows),
  `PdfFingerprintTest`, the 413 case in `UploadSizeLimitTest`, rows in `Rule9_AuditCoverageTest` and
  `InputValidationTableTest`, the candidate and plan budget in `IndexUsageTest` with report volume in `PerfSeed`.
- Frontend: "Check a file" in the Evidence report title row, the deferred `ReportCheckDialog` (FileUpload drop zone,
  "Checking…" while the server hashes, three result views in design v3, D-461), `report-check.ts` with its spec, the audit sentence, `fileupload-tokens.ts`, the
  report screen following its `jurisdiction` query parameter.
- End-to-end: `report-check.spec.ts` (runs after the suite with the records specs), axe on every state in both themes at
  1440 and 400 px, screenshots `f14-*`.

## Design v3
The owner replaced design v2 with v3 "Ledger" while F14 was in progress (b28318a). On the orchestrator's direction the
dialog follows v3 where it costs nothing (D-461): no ring, icons, tints or InfoTip; UI3-2 finishes its styling.

## Test runs
`LEXVAY_E2E_SLOT=3 make test` after merging origin/main (b28318a), exit 0: backend `Tests run: 1103, Failures: 0,
Errors: 0, Skipped: 0` (`BUILD SUCCESS`), frontend `Test Files 36 passed (36)`, `Tests 381 passed (381)`, end-to-end
`60 passed (9.5m)` (run with `LEXVAY_SCREENSHOTS=1`, so the screenshot tests ran too). An earlier run failed
`ForbiddenWordsTest`: the unit spec listed the forbidden words to assert their absence; it now checks only "valid" and
"authentic".

`make perf`, exit 0: `EndpointLatencyTest` and `IndexUsageTest` pass. The check statement uses `report_content_sha_idx`
with both columns as index conditions: 17 shared buffers and 0.09 ms for a match, 3 buffers and 0.05 ms for no match.

Initial bundle: 601 667 bytes on main, 602 031 on this branch (+364 bytes); the dialog's chunk is 53 kB.

## Screenshots (`docs/screens/`, light and dark)
`f14-drop-{1440,400}`, `f14-match-{1440,400}`, `f14-no-match-{1440,400}`, `f14-reopened-{1440,400}`.

## Merge with UI2-4 (69985fc)
UI2-4 added program setup with its own FileUpload token set (`lexvayFileupload`) under the component name the report
check had used for its own. Token sets register once per name, so the dialog now registers that shared set and draws its
frame in its stylesheet (`fileupload-tokens.ts` is main's). The report check spec runs in `records`, which `setup` now
depends on, and opens screens with `goTo` (the More menu at 400 px). `LEXVAY_E2E_SLOT=3 make test`, exit 0: backend
`Tests run: 1103, Failures: 0, Errors: 0, Skipped: 0`, frontend `Test Files 41 passed (41)`, end-to-end
`58 passed (9.0m)`, 7 skipped (the screenshot tests).

## F15 — Evidence package (PR #49)

Branch `feat-evidence-package`, migration `V020__evidence_package.sql`, decisions D-540 to D-549 in
`docs/decisions/F15.md`.

## Built

- V020: `evidence_package` with forced RLS, a trigger that finishes a row once (QUEUED to READY or FAILED, file columns
  only), no deletes, a CHECK that the file columns are set exactly when READY, index `(determination_id, created_at DESC)`.
- `FileStore.put(key, Path)` and `open(key)`: local (temporary file, atomic move) and S3 (single put up to 16 MiB,
  multipart above, abort on failure, `GetObject` stream). `s3:AbortMultipartUpload` added to the task role.
- API: `POST /determinations/{id}/package` (202), `GET /packages/{id}`, `GET /packages/{id}/file`; audited
  `PACKAGE_REQUESTED`, `PACKAGE_READY` (actor null) and `PACKAGE_DOWNLOADED`, in `Rule9_AuditCoverageTest`,
  `InputValidationTableTest` and `audit-sentence.ts` with its spec (plus the `BUILD_PACKAGE` job noun).
- Job `BUILD_PACKAGE`: `PackageSnapshotLoader` reads the records, `PackageBuilder` writes the deterministic ZIP through
  `PackageZip` into a temporary file, the handler stores it with one `put` and marks it READY; `JobHandler.onFailed`
  marks it FAILED. Size limit `LEXVAY_PACKAGE_MAX_BYTES` enforced while writing.
- Shared with the F5a export through `AuditCsv`; `DocumentService.sha256Digest()` next to `sha256`; a `Clock` bean.
- Docs: `docs/OPERATIONS.md` "Evidence packages" and the new variable, `docs/RULES.md` section, `.env.example`.

## Tests

`PackageApiTest` (seeded-scenario package, unzip and manifest, report bytes, page hashes against `document_page`,
refusals, other tenant 404, determinism), `PackageSeedTest` (the Northfield seed with real PDFs),
`PackageSizeLimitTest` (FAILED with a message, nothing stored, no temporary file), `PackageEntriesTest`,
`Rule5_EvidencePackageImmutabilityTest`, local and S3 streaming tests, the `onFailed` hook in `JobRunnerTest`.

## Not done here

The button on the Evidence report and in the register is UI3-2 or later. `seq` and `row_hash` are not in `audit.csv`
because SEC-3 has not merged (D-545).

## MODE-1 — Data mode and spend gate (PR #51)

Branch `mode-data-spend`, migration V022, slot 8. Decisions D-560 to D-565 are in `docs/decisions/MODE-1.md`.

## Plan (written before building)
1. V022: `tenant.data_mode`, an audit trigger for a change of mode, a spend-gate SELECT policy and index on `llm_call`.
2. Tenant code: `DataMode`, `Tenant`/`TenantRepository`, `TenantBootstrapService.createTenant(mode)`, session `demo` from the mode,
   `lexvay.demo-tenant-names` removed.
3. LIVE rules: gateway refuses the scripted model, the seed refuses LIVE tenants, a failing-closed approval hook.
4. Spend gate: `LlmClient.metered()` and `scripted()`, `LlmRates`, `TokenPrice`, `SpendGate`, `LlmUsageRepository.thisMonthExcluding`,
   `LlmRefusalAudit`, permanent job failure.
5. DEMO marker in the PDF report, register CSV and audit CSV.
6. Tests, OPERATIONS, `.env.example`, compose.

## Done
All of the plan. Changes outside the spec's list of files, each small:
- `JobRunner`: a refused model call fails the job at once.
- `RuleVersionService`: one constructor parameter and one call to `RealDataApprovalGuard`.
- `ReportService`, `RegisterService`/`RegisterCsv`, `AuditExportService`: the DEMO marker.
- `audit-sentence.ts` and its spec (the audit actions `LLM_CALL_REFUSED` and `TENANT_DATA_MODE_CHANGED`; the spec now also reads
  migrations for audit actions).
- `docker-compose.yml`: passes the three new variables with refusing defaults.
- e2e `register.spec.ts` and `records.spec.ts`: the CSVs of the seeded (DEMO) tenant start with the notice line.

## Deferred and notes
- Requirement approval in a LIVE tenant is refused until RG-1 records a dates basis (D-561); RG-1 replaces `RealDataApprovalGuard.datesBasis`.
- `infra/ecs.tf` now passes `LEXVAY_LLM_PAID_APPROVED=false`, a budget of 0 and empty rates, from variables with those defaults, so a
  deployment is visibly fail-closed. Terraform is not installed on this machine, so `make infra-validate` was not run; CI's infra job validates it.
- `LEXVAY_LLM_PRICE_*_PER_MTOK` and the Terraform variables behind them are removed; `LEXVAY_LLM_RATES` is the one price table (D-562).

## After review (merge with F15, one price table, Terraform)
- Merged origin/main (F14, F15 with V020 and the shared `AuditCsv`, the setup URL fix). The two conflicts (audit export service and
  its test) keep both sides: the demo notice row, then `AuditCsv.header`.
- Evidence package: `README.txt` of a DEMO tenant starts with "Fictional demo data."; `manifest.json` has `"dataMode"` (`DEMO` or
  `LIVE`); a LIVE README is unchanged; still deterministic (`PackageApiTest`).
- `LlmRates`/`TokenPrice` are the only price table and cost formula; `AssessmentUsageService` and `EvalService` use them and the
  `LEXVAY_LLM_PRICE_*` settings and Terraform variables are removed (D-562).
- `infra/ecs.tf` and `variables.tf`: `LEXVAY_LLM_PAID_APPROVED=false`, `LEXVAY_LLM_MONTHLY_BUDGET_USD=0`, empty `LEXVAY_LLM_RATES`.
  Terraform is not installed here, so `make infra-validate` was not run; CI's infra job validates it.

## Test runs after the merge
- `LEXVAY_E2E_SLOT=8 make test`: backend 1182 tests, 0 failures; frontend 425 passed; end-to-end 44 passed, 2 failed
  (`evidence-requests.spec.ts:14` and `:170`, an Enter key on a focused toggle not expanding the row), 13 did not run.
- `make e2e` alone: 58 passed, 7 skipped, exit 0.

## Test runs before the merge
All with `LEXVAY_E2E_SLOT=8` on 2026-11-27, tree equal to origin/main (7b04d82 plus the docs commit 4eb75fa) plus this branch.

- `make test`, first run: backend 1151 tests, 0 failures; frontend 42 files, 425 tests passed; end-to-end 55 passed, 7 skipped, 1 failed
  (`report-check.spec.ts:58`, a focus assertion on a dialog heading; the screen is untouched by this item).
- `make test`, second run: backend 1151 tests, 0 failures; frontend 425 passed; end-to-end 3 failed of a different set
  (`people.spec.ts:230`, `rules.spec.ts:167` and `rules.spec.ts:232`, which depends on the one before it), all timing of
  screen state (a prefilled field, a keyboard step) on a machine running several slots at once.
- `make e2e` alone, third run: 58 passed, 7 skipped (the screenshot tests), exit 0, the same count as main.

The end-to-end failures differed between runs and passed in the third, and none of them involves data mode, exports or the
model gate; I treated them as the load-dependent flakiness the intake mentions and did not change those specs.

## OPS-2 — Cap concurrent local test stacks (PR #54)

Branch `ops-stack-cap`, end-to-end slot 5. Decisions D-641 to D-643 in `docs/decisions/OPS-2.md`. Tooling only: no
application code, no migration.

## Done
- `scripts/lib/stack.sh`: `stack_init` acquires a directory token (`LEXVAY_STACK_CONCURRENCY`, default 2; 0 turns it off),
  waiting up to `LEXVAY_STACK_WAIT_MINUTES` (default 60) with a status line every 30 s; `stack_cleanup` releases it, and
  INT and TERM now run the cleanup. `scripts/e2e.sh`, `scripts/eval.sh` and `scripts/smoke.sh` are unchanged.
- `scripts/lib/stack-lock-test.sh` and `make test-scripts` (about 7 s, no Docker): the third holder waits and then takes a
  released token, a dead owner's token and a token without a pid are reclaimed, a TERM-killed holder releases, a wait
  that times out exits 1 naming the slots, concurrency 0 creates no token, bad limits are refused.
- CI runs `make test-scripts` before `make test`.
- Docs: CLAUDE.md "Local environment", README.

## Results
See the pull request for the command output.

## UI3-0 — Design v3 foundation, shell, sign-in and overview (PR #52)

Branch `ui3-foundation`, decisions D-480 to D-498 in `docs/decisions/UI3-0.md`. Frontend only. `origin/main` with UI2-4
(Rules, Evidence requests, Program setup) merged into the branch, and those screens moved to the v3 parts as well.

### Spec points
1. **Tokens and preset v3** (D-480, D-482): the DESIGN.md colour table, radius, elevation, spacing and type scale; every v2
   token without a v3 equivalent removed; one derived shade (`inkHover`) and the overlay elevation. PrimeNG restyled
   through the semantic tokens and per-component sets for button, inputs, select, multiselect, dialog, drawer, popover,
   menu, tabs, datatable, toggleswitch, selectbutton, tooltip, toast, message, skeleton, chip, checkbox, fileupload,
   metergroup and stepper; the per-chunk `registerComponentTokens` pattern unchanged. `tokens.spec.ts` proves AA over the
   table, including `line-control` (#8C8F95 / #6E7178), the 3:1 border of text inputs, select, multiselect, autocomplete
   and search (WCAG 1.4.11); secondary buttons and rules keep `line-strong`.
2. **Source scan** (D-481): `source-scan.spec.ts` fails on colour literals outside `theme/`, the retired names, unpublished
   `--p-lx-*` tokens and undeclared `--lx-*` variables.
3. **Shared components** (D-483 to D-486): `status-mark.ts`, `meter.ts`, `figure.ts`, `label.ts`, `help.ts`, `state-tabs.ts`
   (replacing `state-pills.ts`); `seal.ts` redrawn as line art; `mark.ts` monochrome. Deleted after moving every caller:
   `state-map/`, `progress-ring.ts`, `stat.ts`, `count-up.ts`, `status-icon.ts`, `info-tip.ts`, `finding.ts`, `enter.ts`,
   `state-pills.ts`, the sign-in story. DecisionBar: a footer, no blur, no key chips.
4. **Shell** (D-487): top bar, rail in two groups with the left rule, counts as tabular meta text, "Demo data" as meta
   text, the queue dot, the bottom tab bar and More menu; programmatic focus on the title draws no ring.
5. **Sign in** (D-488): one centred 360 px column; the map story and headline panel gone.
6. **Program overview** (D-489): title row, Figure row, States table, Needs you; spacing measured by an end-to-end test.
7. **Motion** (D-490): v3 tokens; staggered entrances, ring sweeps, the seal stamp and the sign-in story deleted; route
   change a 180 ms fade with a 4 px rise. FIX-8's `data-transition="none"` had not merged when this branch was pushed.
8. **Tests and evidence** (D-493): unit tests for StatusMark, Meter, the source scan, the overview view model and the
   review Meter; end-to-end selectors updated for the removed components; axe in both themes on every screen the suite
   visits; `ui3-0-*` captures and `ui3-0-before-*` starting points in `docs/screens`.

### Bundle
Initial (main and styles): 611.74 kB raw / 157.27 kB transfer at the branch point; 581.62 kB / 151.38 kB at the head
(after merging UI2-4, F14 and F15).

### Test results
`LEXVAY_E2E_SLOT=1 make test` on the head (origin/main with F14 and F15 merged), exit 0:
- backend: `Tests run: 1133, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`
- frontend unit: `Test Files 42 passed (42)`, `Tests 374 passed (374)`
- end-to-end: `56 passed`, `7 skipped` (the screenshot tests, which run only with `LEXVAY_SCREENSHOTS=1`)

Screenshots: `LEXVAY_SCREENSHOTS=1` run of the review-and-sign and UI3-0 screenshot tests on a fresh slot-1 stack, `3 passed`.
Under heavy machine load, two earlier runs failed `people.spec.ts` (the More menu step, D-497) and once
`coverage.spec.ts` (a Review request that did not return within 15 s on a loaded machine); the first is fixed, and the
second passed in every later run.

## FIX-8 — No console errors (PR #48)

Branch `fix-console-errors`, decisions D-471 to D-474 in `docs/decisions/FIX-8.md`. Frontend only.

### Spec points
1. **View-transition errors** (D-471): the roadmap's `ignoreSkippedTransition` handlers cannot silence errors that
   Angular's router logs itself in development, so transitions are no longer skipped (orchestrator decision). A
   same-screen change sets `data-transition="none"` and `styles.css` turns the animations off; reduced motion stays in CSS.
2. **`consoleGuard`** (D-473): automatic fixture in `e2e/support.ts` with `allowConsoleErrors(page, pattern)`.
3. **One navigation per user action** (D-472): `landingGuard` for sign-in and `/`; `openingVersionGuard` for Rules; Review and the report no longer write
   the default state into the URL. Unit tests: `core/auth.guard.spec.ts`, `core/navigation.spec.ts`.
4. **Whole end-to-end suite** run with the guard: the only application console errors were the view-transition
   `AbortError`s; eight tests declare an expected 4xx or 5xx (D-474).

## SEC-3 — Tamper-evident audit trail (PR #53)

Branch `sec-audit-seal`, slot 6, migration V023 (written as V019, renumbered before merge because V020 and V022 reached main first).
Decisions D-530 to D-537 are in `docs/decisions/SEC-3.md`.

## Done
- V023: `audit_seal` (unique `(tenant_id, seq)`, `forbid_change()` trigger, forced RLS), `audit_unsealed` (the queue;
  forced RLS plus the worker-flag read policy `worker_queue_select`; index `(tenant_id, created_at, audit_id)`),
  backfill of every existing audit entry into the queue.
- Write path: `AuditRepository.insert` is one `WITH ... INSERT` statement that also queues the entry;
  `Rule9_SingleAuditWritePathTest` greps `src/main` for any other `INSERT INTO audit_log`.
- `AuditCanonical` (canonical form, hash through `DocumentService.sha256`), `AuditChainCheck` (pure walk rules),
  `AuditSealRepository` (all sealer and verification SQL), `AuditSealer` (batches of 500, per-tenant advisory lock),
  `AuditSealPoller` (the timer, `lexvay.audit.sealing-enabled`, `LEXVAY_AUDIT_SEAL_INTERVAL_MS`, stops with the app).
- `GET /api/v1/audit/verify` (ADMIN), `AuditVerificationService`, `AuditVerification`; audited as `AUDIT_VERIFIED`
  (row in `Rule9_AuditCoverageTest`, `InputValidationTableTest`, and the sentence and label in `audit-sentence.ts`
  with its spec).
- Export: `Seq`, `Row hash`, `Entry ID`, `Actor ID` columns (empty seq and hash while pending); end-to-end header
  assertion updated.
- `docs/OPERATIONS.md`: "Audit trail integrity" (what is proved and what is not, canonical form, a Python check of an
  export, the SQL check against a restored database) and the new setting. `docs/PERF.md`: the seal section.
- `make perf` runs `AuditSealPerfTest`.

## Checks
Final tree: SEC-3 merged with origin/main after OPS-2 (migrations V018, V020, V022, then this item's V023; F15's shared
`AuditCsv` carries the seal columns, so the evidence package's `audit.csv` has them too, checked in `PackageApiTest`).
`LEXVAY_E2E_SLOT=6 make test` and `make perf`, one run each, both exit 0:
- Backend 1231 tests, 0 failures. Frontend unit 425 passed. End-to-end 56 passed (the stage waited for a free test
  stack under the OPS-2 cap, as expected).
- `make perf`: `EndpointLatencyTest`, `IndexUsageTest` and `AuditSealPerfTest` pass. Verifying 20 005 sealed entries took
  71 to 89 ms (median 76 ms, budget 2 s); sealing them took 0.5 s. The queue read uses `audit_unsealed_tenant_idx` with
  entries fetched by `audit_log_pkey`; the dequeue finds queue rows by `audit_unsealed_pkey`.
- Earlier runs on this branch, made while other builds loaded the machine (load average 14 to 23) before OPS-2, lost
  single timing-sensitive end-to-end tests and endpoint p95 values; none touched the audit trail and all passed on the
  quiet reruns above.
- The Python recipe in the operations guide was run against a real export: intact, and "broken at entry 6" after one
  word of one entry was changed.

## Deviations from the spec
- Four export columns instead of two (D-535), and `AUDIT_VERIFIED` also records `firstBrokenSeq` when broken (D-536).
- `AuditSealer` is not itself scheduled; `AuditSealPoller` is (D-533), so tests can switch the timer off.
- The migration was V019 while it waited and is V023 now: V020 (F15) and V022 (MODE-1) merged first, and numbers follow merge
  order.

## OPS-2b — Stack lock race on Ubuntu 26.04 runners (PR #60)

Branch `ops-stack-lock-fix`. Decision D-647 in `docs/decisions/OPS-2b.md`. Tooling only: no application code, no migration.

## Cause
The CI runner image is Ubuntu 26.04, whose `mkdir` is uutils coreutils 0.10.0. When two processes `mkdir` the same new
path together, the loser's `mkdirat` fails with `EEXIST`, uutils looks at the path again, sees a directory and exits 0.
The `mkdir`-based mutex of OPS-2 then admitted two holders, which shared one token, so the third holder found a free slot
at once. Reproduced in `ubuntu:26.04` (1 failure in 6 runs of the unchanged test); `ubuntu:24.04` and macOS never fail.

## Done
- `scripts/lib/stack.sh`: the mutex is the file `<lock dir>/.mutex`, created by the shell's `O_EXCL` open (`set -C`),
  removed with `rm -f`; the stale-mutex rule is unchanged. Header comment updated.
- `scripts/lib/stack-lock-test.sh`: a new first section runs the mutex against a `mkdir` that reports success for an
  existing directory and checks that a second contender stays blocked; it fails on the old code every time and passes
  now. `no_mutex` checks for a file instead of a directory.

## Results
See the pull request for the command output.
