# Lexvay — Build plan

Date: 2026-11-26. This plan turns the specification into ten milestones with concrete designs, tests
and acceptance evidence. It is the engineering plan only; the commercial gates in the architecture
design (paid pilots before building) are the owner's decision and are not repeated here.

## 1. Shape of the system

One Spring Boot service, one PostgreSQL database, one file store, one Angular app. Background work is
a thread pool inside the service that polls the `job` table. No broker, no vector store, no second
process.

```
Browser ── Angular 22 (static) ──► Spring Boot 4.1 /api/v1 ──► PostgreSQL 18 (RLS, FTS, jobs)
                                        │                         ▲
                                        ├── worker threads ───────┘  (SELECT ... FOR UPDATE SKIP LOCKED)
                                        ├── FileStore (local dir | S3)
                                        └── LlmClient (Fake | Anthropic)
```

Module packages under `com.lexvay`: `tenant`, `program`, `document`, `rule`, `assessment`, `review`,
`determination`, `change`, `report`, `audit`, `auth`, `llm`, plus `job` and `common`.

Persistence: Spring JDBC (`JdbcClient`) with hand-written SQL and Java records. No ORM (D-020). The
schema has triggers and row-level security, and explicit SQL keeps both visible.

## 2. Repository layout

```
Lexvay/
  backend/            Spring Boot service (Maven wrapper, pom.xml)
  frontend/           Angular app (standalone components, strict TS, CDK only)
  eval/               golden.yml, thresholds.yml, README
  seed/               manifest.yml, docs/*.md (sources), pdf/*.pdf (generated, committed), rules/*.md, fake-llm.yml
  infra/              Terraform (milestone 9, validate only)
  docs/               ANALYSIS.md, PLAN.md, RULES.md, WORKFLOW.md
  .github/workflows/  ci.yml
  docker-compose.yml  Makefile  README.md  CLAUDE.md  DECISIONS.md  PROGRESS.md  .env.example
```

Make targets: `make dev` (backend + frontend), `make test` (backend tests, frontend unit tests,
end-to-end tests), `make seed` (demo tenant), `make eval`, `make eval-live`, `make seed-pdfs`
(regenerate seed PDFs from sources).

## 3. Versions (checked against registries on 2026-11-26)

| Component | Pin | Note |
|---|---|---|
| Java | 21 (local: OpenJDK 21.0.8) | Fixed by spec |
| Spring Boot | 4.1.3 | Owner chose the 4.1.x line; 4.2.0 (2026-11-19) judged too new |
| Spring Security | as managed by Boot 4.1.3 | Effective version recorded in M0 |
| PostgreSQL | 18.7 (`postgres:18.7` image, released 2026-11-12) | Newest major GA on AWS RDS; 19 is RDS preview only |
| Flyway | 13.13.0 | Use Boot-managed version if it differs; record the effective one |
| PostgreSQL JDBC | 42.7.15 | |
| Apache PDFBox | 3.0.8 | Text extraction with page offsets; also used to generate seed PDFs |
| Apache POI (poi-ooxml) | 6.0.0 | DOCX text |
| OpenPDF | 3.0.6 | Report PDF (decide at milestone 7 whether PDFBox alone is enough) |
| Anthropic Java SDK | 3.14.0 | `AnthropicLlmClient` |
| networknt json-schema-validator | 3.0.9 | Validates model output against the JSON schema |
| Bucket4j | 8.10.1 | Rate limiting, milestone 8 |
| Testcontainers (postgresql) | 1.21.4 | Backend integration tests |
| JaCoCo | 0.8.16 | Coverage gate |
| Node | 26.13.1 LTS | Local Node is 22.18.0; Angular 22 needs ^22.22.3, ^24.15.0 or ^26 |
| Angular core, CLI, CDK | 22.3.0 | |
| TypeScript | 6.0.x | Angular 22 allows >=6.0.0 <6.1.0; npm "latest" 7.0.2 is out of range |
| Playwright | 1.65.1 | |
| @axe-core/playwright | 4.14.1 | Accessibility check |
| Terraform | 1.17.2 | Milestone 9, `terraform validate` only |
| Claude model | `LEXVAY_LLM_MODEL`, default `claude-sonnet-5-5`; `LEXVAY_LLM_EFFORT` default `medium` | Lower-cost model by owner decision (D-033); structured outputs via `output_config.format` |

Local prerequisites still missing on this machine: Docker (Desktop, OrbStack or Colima), Node 26
(nvm), Terraform (milestone 9 only). Maven is replaced by the Maven wrapper.

## 4. Cross-cutting designs

### 4.1 Tenancy and row-level security
- Two database roles created by the compose init script: `lexvay_owner` (runs Flyway, owns objects)
  and `lexvay_app` (`NOSUPERUSER NOBYPASSRLS`, granted DML). The service uses `lexvay_app`; Flyway
  uses `lexvay_owner`.
- Every table except `jurisdiction`: `tenant_id uuid NOT NULL`, `ENABLE` and `FORCE ROW LEVEL
  SECURITY`, one policy for all commands: `USING (tenant_id = current_tenant()) WITH CHECK (tenant_id =
  current_tenant())`.
- `current_tenant()` is `NULLIF(current_setting('lexvay.tenant_id', true), '')::uuid`. Absent setting
  → NULL → no row visible, no row writable. Fail closed.
- The service sets the tenant on each pooled connection it hands out (`set_config(..., false)` on
  checkout, `RESET` on return) from the authenticated user's tenant. The worker sets it from the job.
- `job` additionally allows `current_setting('lexvay.worker', true) = 'on'` for `SELECT`/`UPDATE` so
  the poller can claim across tenants; nothing else honours that flag (D-013).

### 4.2 Auth and roles
- Local profile: Spring Security form login; `app_user.password_hash` (bcrypt, nullable for OIDC users,
  D-021); session cookie; CSRF cookie-to-header for Angular.
- Roles `CONTRIBUTOR` (upload, create programs/facts), `REVIEWER` (approve requirements, review
  mappings), `SIGNER` (everything a reviewer can do, plus sign), `ADMIN` (manage users). Method security
  with `@PreAuthorize`. Milestone 8 adds OIDC behind a profile mapping claims to the same roles.

### 4.3 Audit
- `AuditService.record(action, entityType, entityId, detail)` is called inside the transaction of every
  mutation. `audit_log` is append only (trigger). Worker actions use `user_id = NULL` with the job id in
  `detail` (D-018).
- `Rule9_AuditCoverageTest` lists every `POST`/`PUT`/`DELETE` handler via
  `RequestMappingHandlerMapping` and compares with the declared audited set.

### 4.4 Jobs
- `job(id, tenant_id, type, payload jsonb, status, attempts, locked_at, error, created_at, created_by)`.
  Statuses `QUEUED`, `RUNNING`, `DONE`, `FAILED`. Claim:
  `UPDATE job SET status='RUNNING', locked_at=now(), attempts=attempts+1 WHERE id = (SELECT id FROM job
  WHERE status='QUEUED' OR (status='RUNNING' AND locked_at < now() - interval '10 minutes') ORDER BY
  created_at FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *`. Three attempts, then `FAILED` with the error.
- Types: `INGEST_DOCUMENT`, `EXTRACT_REQUIREMENTS`, `RUN_ASSESSMENT`, `RERUN_CHANGE`,
  `GENERATE_REPORT`. Every stage is idempotent: upserts keyed on natural keys
  (`document_id, page_number`; `assessment_id, requirement_id`).
- Tests call `JobRunner.runPending()` synchronously; no sleeping on schedulers.

### 4.5 Immutability
- `forbid_change()` trigger function raising an exception, attached as `BEFORE UPDATE OR DELETE` to
  `document`, `document_page`, `rule_version`, `course_version`, `evidence_span`, `review_event`,
  `audit_log`, `llm_call`; to `requirement` once its rule version is approved; to `determination` for
  any column other than `status`.

### 4.6 Quote verification (`QuoteVerifier`)
1. Normalize both strings: collapse every run of whitespace to one space, trim. Keep an index map from
   normalized positions back to original positions of the page text.
2. Exact substring search of the normalized quote in the normalized page text. No fuzzy matching.
3. On success: map back to original `char_start`/`char_end` within the page, store the span with the
   quote as it appears in the page text. On failure: discard the proposal, finding `EVIDENCE_MISSING`,
   rationale "No verifiable passage found".
4. A quote that spans two pages fails by design and is documented as such.

### 4.7 Model calls (`LlmClient`)
- Interface: `extractRequirements(ruleText) → RequirementExtraction`, `matchEvidence(requirement,
  passages) → EvidenceProposal`. Both return typed records parsed from JSON validated against a schema
  bundled in the jar. Every call is recorded in `llm_call` (model, prompt version, request, raw
  response, token counts, duration) (D-014).
- `FakeLlmClient`: scripted by requirement `ref` (and by rule-version label for extraction) from a
  YAML/JSON script; tests use inline scripts; seed and eval use `seed/fake-llm.yml`. Records call counts
  so tests can assert "never called".
- `AnthropicLlmClient`: Anthropic Java SDK 3.14.0; model from `LEXVAY_LLM_MODEL`; structured outputs
  through `output_config.format` with the same schema; adaptive thinking left at the default; effort from
  `LEXVAY_LLM_EFFORT` (default `medium`); no sampling parameters. Written against the SDK README in the
  Claude docs at milestone 4, not from memory.
- Prompt versions are constants (`extract-v1`, `match-v1`) stored on `assessment` and `llm_call`.
- Any finding outside the three values → output rejected. For extraction: retry once, then the job
  fails. For matching: the proposal is discarded and the mapping is `EVIDENCE_MISSING` with rationale
  "Model output rejected" (D-022).

### 4.8 Rules engine
- Input: requirement (`numeric_field`, `numeric_operator` in `>=`, `<=`, `=`, `>`, `<`,
  `numeric_value`) and the program's facts. Output: mapping with `method = RULES_ENGINE`,
  `evidence_span_id` = the fact's span, rationale = calculation string, e.g.
  `TOTAL_CREDITS 60 >= 60 → true`. Semantics in D-011.

### 4.9 API conventions
- `/api/v1`, JSON, Bean Validation on every request body, RFC 9457 problem details, `202` with
  `{jobId}` for ingest, extraction, assessment, re-run and report generation; `GET /jobs/{id}` to poll.

## 5. Milestones

Each milestone ends with the spec's acceptance checks run for real and pasted into `PROGRESS.md`.

### M0 — Scaffold
- Backend: Spring Boot 4.1.3 project via Maven wrapper; `GET /actuator/health`; Testcontainers smoke test
  that starts `postgres:18.7` and connects. Frontend: Angular 22.3 CLI app, strict, standalone, one
  unit test. `docker-compose.yml` with Postgres 18.7 and an init script creating the two roles and the
  database. `Makefile` targets. `.env.example`. GitHub Actions workflow running `make test`.
- Acceptance: `docker compose up`, `make dev`, `make test` work; health returns 200; CI file present and
  valid YAML.
- Risk: local Docker runtime and Node 26 must be installed first.

### M1 — Schema, tenancy, auth, audit
- Flyway `V1__schema.sql` (all tables of spec section 5 plus `program_jurisdiction`, `llm_call`,
  `report`, `app_user.password_hash`, `determination.assessment_id`), `V2__rls.sql`,
  `V3__immutability.sql`, `V4__jurisdictions.sql` (50 states + DC, reference data).
- Tenant connection customizer, security config, seeded local users for two tenants (test fixtures),
  `AuditService`.
- Tests: `Rule7_*` (API and repository isolation, fail-closed, every table has a policy),
  `Rule8_NoStudentEntityTest`, `Rule5_ImmutabilityTest` (for tables that exist), `Rule9_AuditCoverageTest`,
  role checks per endpoint that exists so far.
- Acceptance: migrations apply cleanly on a fresh database; isolation proved both ways; role checks
  tested; audit rows for every mutating endpoint.

### M2 — Documents
- `FileStore` (local default; S3 implementation using the AWS SDK, tested against a fake `S3Client`
  only for key layout, never against AWS). `POST /programs/{id}/documents` multipart → sha256 → duplicate
  check per tenant and program (`409` with existing id) → store → `INGEST_DOCUMENT` job → PDFBox per page
  with character offsets, POI for DOCX (one page, D-017), TXT one page → `document_page` rows with
  `tsvector`. Empty text → job fails with "Document has no extractable text"; corrupt file → "File could
  not be parsed". Upload size and type limits are configured here and tested in M8.
- Tests: upload of each type, offsets verified against known content, duplicate by hash, empty file,
  corrupt file, scanned-PDF-without-text rejected.

### M3 — Rules and requirements
- `POST /rule-versions` (jurisdiction, license type, label, source url, effective date, supersedes id,
  rule text) → stores a `RULE_TEXT` document (D-009) → `EXTRACT_REQUIREMENTS` job → `LlmClient`
  extraction → schema validation (networknt), one retry, then `FAILED`. `GET
  /rule-versions/{id}/requirements`; `PUT /rule-versions/{id}/requirements` replaces the list of an
  unapproved version (D-024), audited with before and after; `POST .../approve` (REVIEWER or above) sets
  approved by/at and makes requirements immutable. Assessments refuse rule versions without approval.
- Tests: valid extraction; invalid JSON then valid (retry works); invalid twice (job fails); approval
  gate; numeric fields required for numeric types; `ref` unique within a version; edit before approval
  succeeds and is audited; edit after approval → `409` and direct update rejected by trigger.

### M4 — Pipeline
- `POST /programs/{id}/assessments` → `RUN_ASSESSMENT` job. For each target jurisdiction: pick the
  rule version (D-010); for each requirement: rules engine or evidence match. `PassageSearch`
  (PostgreSQL FTS over `document_page` of the program's documents, up to 8 pages by `ts_rank`), model
  proposal, `QuoteVerifier`, non-required course → `AMBIGUOUS`, thin quote → `AMBIGUOUS` (D-023),
  persist mapping and `llm_call`. `GET /assessments/{id}`, `GET /assessments/{id}/mappings?jurisdiction=`.
- Tests: `Rule1_*`, `Rule2_*`, `Rule3_*`, `Rule4_*` including the fabricated-quote script and the
  forbidden-finding script; idempotent re-run; coverage gate switched on.

### M5 — Review and signing
- `POST /mappings/{id}/reviews` (kind, note); `GAP_CONFIRMED` requires a non-empty note (DB CHECK and
  validation); current decision per D-003. `POST /programs/{id}/determinations` (SIGNER): `409` unless
  every non-stale mapping of the jurisdiction in the latest finished assessment has a current decision;
  stores rule version, assessment, course versions, `versions_hash` (SHA-256 over sorted ids). `GET
  /programs/{id}/determinations`.
- Tests: `Rule1_OnlySignerSignsTest`, `Rule5_ImmutabilityTest` (review events, determinations),
  `Rule6_*`, gap note rule, `409` when incomplete, current-decision logic including `REOPENED`.

### M6 — Frontend
- Five screens in a left navigation; jurisdiction tabs on Review; three actions; auto-advance to the
  next unreviewed requirement; "Sign determination" enabled only when complete and the user is a signer;
  signing dialog with the three outcomes (D-004); permanent fictional-data banner for the demo tenant;
  light and dark themes via CSS custom properties; keyboard operable; findings shown with icon shape and
  text. Draft evidence report screen labeled "Draft. Not a determination".
- Tests: Vitest unit tests for the current-decision view model and the finding display; Playwright
  end-to-end against the real API with seed data: log in as Dana Whitfield, open California, review every
  requirement (confirm the telehealth gap with a note), sign, open Audit trail and see the entries;
  `@axe-core/playwright` on every screen with no serious or critical issues; `ForbiddenWordsTest`;
  `SeedIsLabeledFictionalTest`.
- Seed authoring happens here (section 6 below) because the end-to-end test needs it.

### M7 — Change tracking and report
- `POST /rule-versions` with `supersedes_id` → `RULE_VERSION` change event; every `SIGNED`
  determination on the old version → `REOPENED`; old mappings marked stale. `POST
  /change-events/{id}/rerun` → new assessment for the jurisdiction against the new version; unchanged
  requirements (same `ref`, text and numeric value) carry over finding, evidence and current decision
  with a system review event noting the carry-over; changed ones are re-run and start undecided (D-015).
- New course version with `supersedes_id` → `COURSE_VERSION` change event; mappings citing the old
  version marked stale; determinations referencing it → `REOPENED` (D-029). Re-run re-verifies each
  quote against the new version's documents: found → mapping cites the new span, `stale = false`; not
  found → `EVIDENCE_MISSING`, `REOPENED` event, back to review (D-028).
- `POST /determinations/{id}/report` → `GENERATE_REPORT` job → PDF (header, outcome, signer, versions
  hash, each requirement with finding, quote and citation) stored via `FileStore`; `GET /reports/{id}`.
- Tests: change events for both kinds, reopen, stale marking, carry-over, both re-run outcomes, PDF
  contains expected text (parsed back with PDFBox).

### M8 — OIDC and hardening
- OIDC login under profile `oidc` with claim-to-role mapping into the same `app_user` rows; Bucket4j
  rate limiting per user and per IP; upload size and content-type limits; security headers (CSP, HSTS
  behind TLS, frame-ancestors, nosniff); input validation tests for every endpoint (oversized, wrong
  type, missing fields, bad UUIDs).
- Tests: role mapping from claims; limits and headers asserted; validation table test.

### M9 — Evaluation and infrastructure
- `eval/` golden set and thresholds; runner as a Spring profile `eval` in the backend jar that loads
  the seed manifest into a fresh tenant, runs the pipeline, compares with the golden set, prints the
  table, exits non-zero on threshold breach or quote verification below 100%. `make eval` (Fake) and
  `make eval-live` (Anthropic, only when `ANTHROPIC_API_KEY` is set), which also prints cost per
  assessment: input and output tokens from `llm_call` times `LEXVAY_LLM_PRICE_*_PER_MTOK` (D-033).
- Terraform under `infra/`: VPC, ECS Fargate service, RDS PostgreSQL 19, S3 (versioning, SSE), CloudFront
  for the Angular build, Secrets Manager references. `terraform validate` passes; nothing applied.
- Acceptance: `make eval` passes thresholds; `terraform validate` succeeds.

## 6. Seed data design (built in M6, used by M6 to M9)

Single source of truth `seed/manifest.yml` drives: tenant and users (Dana Whitfield, State
Authorization Director, SIGNER; Dr. Priya Raman, REVIEWER; Jordan Ellis, CONTRIBUTOR; demo passwords
only in local profile, D-030), program M.S. Clinical Mental Health Counseling 2026-27, courses COUN 605
to 660 (650 and 655 elective, 660 without a syllabus), documents (markdown sources rendered to text PDFs
with PDFBox and committed), program facts (60 credits, 100 practicum hours, 600 internship hours, each
with a quote), five "Demo rule set" rule versions (UT 9, CO 9, AZ 8, CA 8, TX 8 = 42), Colorado v2
(700 hours), COUN 625 syllabus v2, and `seed/fake-llm.yml` scripting the fake model's extraction and
matching outputs.

Target mix across the 42 requirements: 10 rules-engine (5 credit-hour, 5 supervised-hour), about 24
`EVIDENCE_FOUND`, about 5 `AMBIGUOUS` (addictions and psychopharmacology via electives 650/655; thin
career-development coverage), 3 `EVIDENCE_MISSING` (state law in Utah and Texas; telehealth in
California). California includes the telehealth gap so the end-to-end test exercises the gap note.
Colorado v2 turns the supervised-hours mapping into `EVIDENCE_MISSING` (600 >= 700 is false). COUN
625 v2 keeps one cited passage and drops another so both re-run outcomes can be shown.

Second tenant "Westbrook College" with one program, used only by isolation tests.

## 7. Evaluation harness design

Golden set entry: `ref`, `jurisdiction`, `expected_finding`, optional `expected_document`,
`expected_quote_contains`. Metrics: false `EVIDENCE_FOUND` rate (proposed `EVIDENCE_FOUND` whose golden
finding differs ÷ proposed `EVIDENCE_FOUND`), recall on true matches, quote verification rate, numeric
accuracy, requirement extraction agreement (ref and type match), tokens and wall time per assessment
(cost = tokens × configured prices, zero for Fake). Thresholds in `eval/thresholds.yml`:
`false_evidence_found_rate_max: 0.0`, `quote_verification_rate_min: 1.0`, `numeric_accuracy_min: 1.0`.

## 8. Test strategy

- Backend: JUnit 5 + Testcontainers Postgres 18.7, one container per test JVM, `FakeLlmClient` always. Product rule
  tests named `Rule<N>_*`. Architecture test for rule 3. JaCoCo check at 85% for the four packages.
- Frontend: Vitest unit tests; Playwright end-to-end with axe; `ForbiddenWordsTest` scan.
- `make test` runs all three suites; CI runs `make test` on every push.

## 9. Owner decisions received (2026-11-26)

D-003, D-004 (rationale corrected to 668.43(a)(5)(v) and 668.43(c)(1)), D-006 and D-029 confirmed.
D-024: build the `PUT` endpoint for unapproved versions with audit and tests. D-002: Spring Boot 4.1.x
and PostgreSQL 18.7. D-033: lower-cost default model, medium effort, cost per assessment in
`make eval-live`.
