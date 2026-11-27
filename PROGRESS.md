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
