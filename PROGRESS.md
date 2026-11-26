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
