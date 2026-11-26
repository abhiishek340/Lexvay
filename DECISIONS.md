# Lexvay — Decisions

Choices the specification did not make, or made ambiguously. Each entry names the alternative that
was rejected. All entries below are decided; the ones the owner confirmed or changed on 2026-11-26 say
so.

## Naming

**D-001 — Product name is Lexvay.** Replaces "Licentraz" (spec) and "LicenseGraph" (architecture
design, prototype). Env prefix `LEXVAY_`, Java root package `com.lexvay`, repo root `Lexvay/`.
Decided 2026-11-26 (owner instruction).

## Versions

**D-002 — Pinned versions (checked 2026-11-26).** Spring Boot 4.1.3 (owner chose the 4.1.x line as
primary; 4.2.0 is seven days old); Spring Security as managed by Boot 4.1.3; PostgreSQL 18.7 (owner
rule: use the newest major that AWS RDS supports in general availability; RDS offers 19 only in its
60-day Database Preview Environment as of 2026-11-26, while 18 is GA there); Flyway 13.13.0; PostgreSQL
JDBC 42.7.15; PDFBox 3.0.8; POI 6.0.0; OpenPDF 3.0.6; Anthropic Java SDK 3.14.0; networknt
json-schema-validator 3.0.9; Bucket4j 8.10.1; Testcontainers 1.21.4; JaCoCo 0.8.16; Node 26.13.1;
Angular 22.3.0; TypeScript 6.0.x; Playwright 1.65.1; @axe-core/playwright 4.14.1; Terraform 1.17.2.
Rejected: Spring Boot 4.2.0 (too new), PostgreSQL 19.0 (not GA on RDS), TypeScript 7.0.2 (outside
Angular 22's range). Boot-managed library versions are taken from the Boot 4.1.3 BOM; effective in M0:
Spring Framework 7.0.11, Spring Security 7.1.3, Flyway 12.4.0 (the BOM's version is used instead of the
13.13.0 found on Maven Central), PostgreSQL JDBC 42.7.14, HikariCP 7.0.2, Jackson 3.1.8, JUnit 6.0.3,
Testcontainers 2.0.5 (new artifact names `testcontainers-postgresql`, `testcontainers-junit-jupiter`),
Tomcat 11.0.27. Build tooling: Maven 3.10.2 via Maven Wrapper 3.3.4 (only-script), JaCoCo 0.8.16.
Frontend effective: TypeScript 6.0.3, Vitest 5.1.2, jsdom 31.0.1, rxjs 7.8.2. CI actions:
checkout v7, setup-java v6, setup-node v7. Added in M2 (not Boot-managed, checked 2026-11-26): PDFBox
3.0.8, POI 6.0.0, AWS SDK for Java 2.59.10 (`software.amazon.awssdk:bom`, module `s3`). Added in M3:
Anthropic Java SDK 3.14.0 (`com.anthropic:anthropic-java`), networknt json-schema-validator 3.0.9 (its 3.x
line depends on Jackson 3, matching Boot 4). Decided (owner, 2026-11-26).

## Spec ambiguities

**D-003 — Current decision and `REOPENED`.** The current decision on a mapping is its latest review
event; if that event is `REOPENED`, the mapping has no current decision. Rejected: the literal reading
of section 5 ("latest event that is not REOPENED"), under which a reopen would never remove a
decision, contradicting section 7. Confirmed by owner 2026-11-26.

**D-004 — Three signing outcomes.** `MEETS`, `DOES_NOT_MEET`, `NOT_DETERMINED`. The first two mirror
the public disclosure categories of 34 CFR 668.43(a)(5)(v) (states where the curriculum meets, and
does not meet, the educational requirements); the third mirrors the "has not made a determination"
case in 34 CFR 668.43(c)(1), which triggers direct notice to students. UI labels: "Determined to meet",
"Determined not to meet", "No determination made", so the forbidden phrase "meets requirements" never
appears. Rejected: free-text outcome. Confirmed by owner 2026-11-26 (rationale corrected).

**D-005 — Where a confirmed gap lives.** `mapping.finding` is constrained by the database to the three
system values. A confirmed gap is the review state derived from a `GAP_CONFIRMED` review event. The UI
shows four states (three findings plus confirmed gap) as the architecture design describes. Rejected: a
fourth value in the finding column, because then code could write it. Decided.

**D-006 — Program facts endpoint.** `POST /programs/{id}/facts` with `key`, `value`, `document_id`,
`page_number`, `quote`; the quote is verified by `QuoteVerifier` and produces the `evidence_span_id`.
`GET /programs/{id}/facts`. Not in the spec's API list but required for the rules engine to have input
outside the seed. Rejected: model-based extraction of numbers (rule 3 forbids the model touching
numbers; extraction is not arithmetic but it is the riskiest place to be wrong). Confirmed by owner
2026-11-26.

**D-007 — Target jurisdictions.** Table `program_jurisdiction(program_id, jurisdiction_id, tenant_id)`.
Rejected: an array column (harder to constrain and join). Decided.

**D-008 — Reviewers per program.** Not built. Any tenant user with `REVIEWER`, `SIGNER` or `ADMIN`
may review any program. No acceptance check needs assignment. Decided.

**D-009 — Rule text documents.** Rule text is stored as a `document` of kind `RULE_TEXT` with
`program_id NULL` and `course_version_id NULL`; CHECK constraint ties nullability to kind. Rule text is
tenant-scoped. Decided.

**D-010 — Rule version selection for an assessment.** The newest approved `rule_version` for the
jurisdiction and the program's license type with `effective_from <= current_date`. Rejected: the
newest regardless of effective date. Decided.

**D-011 — Rules-engine findings.** Comparison true → `EVIDENCE_FOUND`; false → `EVIDENCE_MISSING`
with the calculation shown; fact absent → `EVIDENCE_MISSING` with "No program fact recorded for
<key>". The engine never emits `AMBIGUOUS`. Rejected: `AMBIGUOUS` for a failed comparison (nothing is
uncertain about it; the reviewer confirms the gap). Decided.

**D-012 — Immutability vs determination status.** `determination` allows an update to `status` only
(`SIGNED` → `REOPENED`); a trigger rejects every other update and all deletes. All other immutable
tables reject every update and delete by trigger. Decided.

**D-013 — Worker and row-level security.** `job` has `tenant_id` and the standard policy, plus a
clause allowing `SELECT`/`UPDATE` when the session setting `lexvay.worker = 'on'`. Only the poller's
claim statement sets that flag; the stage then runs under the job's tenant. Rejected: a separate
database role with `BYPASSRLS` for the worker (too broad). Decided.

**D-014 — `llm_call` table.** Stores `assessment_id` (nullable), `requirement_id` (nullable),
`purpose`, `model`, `prompt_version`, `request` (jsonb), `response` (jsonb or text), `input_tokens`,
`output_tokens`, `duration_ms`. Required by spec section 6 step 5; not in the spec's table list. Decided.

**D-015 — Rule-version change handling.** A superseding rule version marks old mappings stale, reopens
signed determinations on the old version, and "re-run" starts a new assessment for that jurisdiction.
Requirements with the same `ref`, same text and same numeric value carry over finding, evidence and the
current decision via a system review event; changed or new requirements are re-run and start without a
decision. Rejected: editing old mappings in place to point at new requirements. Decided.

**D-016 — Passage unit.** A passage is a `document_page` row; the shortlist is up to 8 pages by
`ts_rank` over the program's non-rule documents. Rejected: sub-page chunking (adds a table and offsets
mapping without an acceptance check needing it). Decided.

**D-017 — DOCX pagination.** Stored as one page; rationale text says "Word documents have no fixed
pagination". Rejected: splitting on explicit page breaks (unreliable). Decided.

**D-018 — Audit actor for background work.** `audit_log.user_id` nullable; worker actions record the
job id and type in `detail`. Rejected: a synthetic system user per tenant. Decided.

**D-019 — Extra packages.** `com.lexvay.job` (poller, runner) and `com.lexvay.common` (problem details,
ids, time) in addition to the twelve spec modules. Decided.

**D-020 — Persistence style.** Spring JDBC `JdbcClient` with hand-written SQL and Java records; no
JPA. Reason: RLS session settings, triggers, FTS and `SKIP LOCKED` are all explicit SQL; an ORM hides
them. Decided.

**D-021 — Local passwords.** `app_user.password_hash` (bcrypt, nullable) for the local form-login
profile. OIDC users have NULL. Decided.

**D-022 — Forbidden finding in matching output.** The proposal is discarded; the mapping is
`EVIDENCE_MISSING` with rationale "Model output rejected". For requirement extraction: one retry, then
the job fails (spec). Decided.

**D-023 — Thin-evidence rule.** An `EVIDENCE_FOUND` proposal whose normalized quote is shorter than
`LEXVAY_MIN_QUOTE_CHARS` (default 40) is downgraded to `AMBIGUOUS` with the reason stated. Implements
rule 4 in code rather than only in the prompt. Decided.

**D-024 — Editing extracted requirements before approval.** Build `PUT /rule-versions/{id}/requirements`
(REVIEWER or above) that replaces the whole requirement list of an **unapproved** rule version. The
request is validated like an extraction result (schema, unique `ref`, numeric fields by type). Every
edit writes an audit row with the before and after lists in `detail`. An approved rule version rejects
the call with `409`, enforced in the service and by the requirement immutability trigger. Tests:
replace succeeds before approval; approved version rejects edits (API `409` and direct SQL update
rejected by trigger); audit row written. Rejected: per-requirement PATCH (more endpoints, same
outcome). Decided by owner 2026-11-26.

**D-025 — `determination.assessment_id`.** Added so a signed determination names the assessment whose
mappings were reviewed. Decided.

**D-026 — `report` table.** `report(id, tenant_id, determination_id, storage_key, status, created_at,
created_by)`; `GET /reports/{id}` streams the PDF when ready. Decided.

**D-027 — Draft reports.** The Evidence report screen renders the draft on screen with the label
"Draft. Not a determination". PDF download exists only after signing, as the spec's API implies.
Decided.

**D-028 — Course-version re-run when a quote is gone.** Mapping becomes `EVIDENCE_MISSING` with
`evidence_span_id NULL`, rationale naming the new course version, a `REOPENED` review event, `stale =
false`. Decided.

**D-029 — Course-version changes reopen determinations.** Signed determinations whose
`determination_course_version` references the superseded version become `REOPENED`. Section 7 only
says this for rule versions, but milestone 7's acceptance row says "reopen determinations" for both
kinds and rule 6 implies it. Confirmed by owner 2026-11-26.

**D-030 — Demo passwords.** Seeded users get the password from `LEXVAY_DEMO_PASSWORD` (default
`lexvay-demo` in the local profile only). Never seeded outside the local profile. Decided.

**D-031 — Documents without a course.** Degree plan, handbook and accreditation letter have
`course_version_id NULL`; the "not required course → AMBIGUOUS" rule does not apply to them. Decided.

**D-032 — Backend test database.** Testcontainers `postgres:18.7`, one container per test JVM,
started from the base test class and removed by Ryuk at exit; Flyway on context start. The compose
role script runs inside the container so tests use the same two roles as development. Rejected: one
container per test class (Spring caches the application context across classes, so the cached
datasource pointed at a stopped container, found in M0); sharing the compose database (state leaks
between runs). Tests isolate their data by creating their own tenants and rows. Decided.

**D-033 — Model configuration.** `LEXVAY_LLM_PROVIDER` (`fake` default, `anthropic`),
`LEXVAY_LLM_MODEL` (default `claude-sonnet-5-5`, the lower-cost current-generation model at $2/$10 per
million tokens on 2026-11-26; `claude-haiku-5-5` and `claude-opus-5-5` are valid alternatives through
the same variable), `LEXVAY_LLM_EFFORT` (default `medium`), `ANTHROPIC_API_KEY`. Structured outputs
through `output_config.format`; no sampling parameters; thinking left at the model default. `make
eval-live` reports cost per assessment from token counts stored in `llm_call` multiplied by
`LEXVAY_LLM_PRICE_INPUT_PER_MTOK` and `LEXVAY_LLM_PRICE_OUTPUT_PER_MTOK` (defaults 2.00 and 10.00,
matching the default model). Rejected: `claude-opus-5-5` default (owner asked for lower cost).
Decided by owner 2026-11-26.

**D-034 — Login before the tenant is known.** Emails are unique across tenants. The login lookup runs
in a transaction that sets the transaction-local flag `lexvay.auth_lookup = on`; only the `SELECT` policy
on `app_user` honours it. Nothing else can read across tenants. Rejected: a `SECURITY DEFINER` function
(the owner is also bound by `FORCE ROW LEVEL SECURITY`, so it would not help) and tenant-qualified
logins (worse for users). Decided.

**D-035 — Column-restricted updates on otherwise immutable rows.** A generic trigger compares
`to_jsonb(NEW)` and `to_jsonb(OLD)` minus the allowed columns and rejects any other change:
`determination.status`, `change_event.status`, and the two approval columns on `rule_version` (which may
only go from NULL to set, once). `requirement` rows are replaceable until their rule version is approved
and immutable afterwards. All raise SQLSTATE `LX001`, which the API maps to `409`. Decided.

**D-036 — Program facts are append-only, latest wins.** `program_fact` rows are immutable; re-entering a
fact adds a row and the rules engine uses the newest row per key. The history stays visible. Rejected:
a unique constraint on (program, key) with in-place updates. Decided.

**D-037 — Row-level security errors.** PostgreSQL reports a policy violation as SQLSTATE 42501
(insufficient privilege), which Spring maps to `BadSqlGrammarException`; the message is in the root
cause. Tests assert on the root cause. No API endpoint can trigger it on purpose because every write
uses the current tenant, so no special API mapping is added. Decided.

**D-038 — CSRF in tests.** `spring-security-test`'s `csrf()` post-processor swaps the live
`CsrfFilter`'s repository for an `HttpSessionCsrfTokenRepository` inside the shared application context,
so later tests (and the real HTTP port) no longer exercise the cookie-based configuration Angular needs.
Tests therefore use `TestCsrf.csrf()`, which sets the `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header exactly
as a browser does, and one test runs the complete session → cookie → login flow over real HTTP. The
production configuration is explicit (`CookieCsrfTokenRepository` with Angular's names plus the
documented SPA request handler) rather than `csrf.spa()`, whose internals could not be verified.
Decided (found in M1).

**D-039 — Course endpoints.** The spec's API list has no way to create courses, yet documents cite
course versions and section 7 adds course versions with `supersedes_id`. Added
`POST /programs/{id}/courses` (code, title, required; creates the course and its first version for the
program's catalog year) and `GET /programs/{id}/courses`. `POST /courses/{id}/versions` arrives in M7.
Rejected: creating courses implicitly from uploaded syllabi (guessing codes from filenames). Decided.

**D-040 — Document read shape and ingest status.** `GET /documents/{id}` returns the document, a summary
per page (number, offsets, length) and the latest `INGEST_DOCUMENT` job's status and error. Documents
have no status column (they are immutable); the job carries it. Page text is not returned by this
endpoint. Rejected: a mutable status column on `document`. Decided.

**D-041 — Offset conventions.** `document_page.char_start/char_end` locate the page in the concatenated
document text, pages joined by one newline. `evidence_span.char_start/char_end` are relative to the
page's own text, so a quote is verified and displayed against one page. Text is normalized to `\n` line
endings with trailing whitespace stripped before storage; verification normalizes whitespace anyway.
Decided.

**D-042 — S3 test double.** `S3FileStoreTest` uses a Mockito mock of `S3Client` to verify bucket, key
and body. No test contacts AWS. Rejected: LocalStack via Testcontainers (a second container for one
adapter class). Decided.

**D-043 — Ingest failures do not retry.** Unparsable or text-less files raise `PermanentJobFailure`, which
fails the job on the first attempt; infrastructure errors retry up to three times. The document row and
file remain (immutable) with the failed job visible on the document. Decided.

**D-044 — Rule text entry.** `POST /rule-versions` takes the rule text as a JSON string (spec section 3:
"rule text, source link, effective date, jurisdiction"). The text is stored at once as a `RULE_TEXT`
document (file in the store, `document` row, one `document_page`) in the same transaction, and
requirement extraction runs as a job. Identical text already stored in the tenant is a 409 (same
content-hash rule as uploads). Rejected: uploading rule PDFs (a second ingest path with no acceptance
check) and chaining an ingest job before extraction. Decided.

**D-045 — Schema validation library.** networknt json-schema-validator 3.0.9 (`SchemaRegistry`,
`Schema`, `Error`), which uses Jackson 3 like Boot 4. Error messages are prefixed with the instance path
so the retry prompt and reviewers see where the problem is. Rejected: a hand-written validator (two
places to keep in step with the schema file). Decided.

**D-046 — Prompts and schemas as classpath resources.** `llm/extract-v1.system.txt` and
`llm/requirement-extraction.schema.json`; the prompt version is the file name and is stored on every
`llm_call`. Changing a prompt means a new file and a new version string. Decided.

**D-047 — `llm_call.rule_version_id`.** Added in `V005` so extraction calls link to their rule version
(the spec's `llm_call` idea only had assessment and requirement). Decided.

**D-048 — Schema subset for the model.** The schema sent as the structured-output format uses only
`type`, `properties`, `required`, `additionalProperties`, `enum`, `items` and nullable type arrays.
Checks the model-facing schema cannot express reliably (non-blank strings, unique refs, numeric fields
exactly for numeric types, positive values) live in `RequirementListParser`, which also serves the
reviewer's `PUT`. Decided.

**D-049 — Model refusals.** A `refusal` stop reason raises `LlmRefusedException`; the job fails with the
category and explanation after the normal retry budget. No fallback model is configured because a
different model silently answering a declined request is not what a compliance record should contain.
Decided.

**D-050 — Assessment start gate.** `POST /programs/{id}/assessments` is refused with `409` unless every
target jurisdiction has an approved rule version for the program's license type effective today, and
the problem detail names the jurisdictions that lack one. The run handler re-checks and fails
permanently if a version disappeared. Rejected: running the jurisdictions that are ready and skipping
the rest (a partial assessment is easy to mistake for a complete one). Decided.

**D-051 — Passage search query.** The requirement's title and text are split into words of three or
more letters or digits; the distinct words are joined with ` | ` into `to_tsquery('english', ...)` over
the program's non-rule pages, ranked by `ts_rank`, top 8. No candidate → `EVIDENCE_MISSING` "No
candidate passages found" without a model call. Rejected: `websearch_to_tsquery` (ANDs terms and
returns nothing for most requirements) and sub-page chunking (D-016). Decided.

**D-052 — Per-requirement transactions in a run.** The run handler commits each mapping in its own
transaction (`REQUIRES_NEW`) and skips requirements that already have a mapping, so a crash keeps the
finished work and a retry continues. Assessment status is updated in its own transactions
(RUNNING → FINISHED or FAILED). Decided.

**D-053 — Mapping list shape.** `GET /assessments/{id}/mappings?jurisdiction=XX` returns one object per
requirement with the requirement (ref, title, text, type, numeric fields), finding, method, rationale,
stale flag and, when present, the evidence (document, filename, kind, course code, whether the course is
required, page, page-relative offsets, exact quote). The review decision is added in M5. Decided.

**D-054 — Rejected model output for matching.** Any of: schema failure, a finding outside the three
values, a document or page not among the offered passages, a non-missing finding without a quote →
the proposal is discarded and the mapping is `EVIDENCE_MISSING` with rationale "Model output rejected:
<reason>". No retry (spec section 6: reject). The raw output stays in `llm_call`. Decided.

**D-055 — No review on stale mappings.** A mapping marked stale (it cites a superseded rule or course
version) returns `409` on review until matching is re-run (M7). Rejected: letting reviewers decide on
outdated evidence. Decided.

**D-056 — Versions hash recipe.** SHA-256 over `rule_version:<id>` followed by `course_version:<id>` for
every course version id in sorted order, newline separated. Deterministic and recomputable from the
stored determination. Decided.

**D-057 — No review after signing.** Once a SIGNED determination exists for the mapping's assessment and
jurisdiction, new review events are refused with `409`; a rule or course change reopens the
determination (M7) and with it the mappings. Rejected: allowing late edits that the signed record would
no longer reflect. Decided.

**D-058 — One signed determination per program and jurisdiction.** Signing again while a SIGNED
determination exists returns `409`. After a change reopens it, a new determination can be signed; the old
one stays as history with status REOPENED. The determination snapshots the newest version of every
course in the program at signing time. Decided.

**D-059 — Who can start an assessment and review.** Any tenant user may start an assessment (it only
proposes). Reviews need REVIEWER, SIGNER or ADMIN. Signing needs SIGNER only; ADMIN cannot sign. Decided.

**D-060 — Seed as a Spring profile.** `make seed` runs the backend with profile `seed` (no web server,
no polling), loads `seed/manifest.yml` through the real services with a programmatic security context for
the seeded users, and exits. It requires `LEXVAY_LLM_PROVIDER=fake` and scripts the fake model from
`seed/fake-llm.yml`. A tenant whose admin email already exists is skipped, so the command is idempotent.
Rejected: SQL fixtures (would bypass validation, audit and the pipeline). Decided.

**D-061 — Documents by file name in scripted outputs.** `seed/fake-llm.yml` names documents by file; the
loader resolves them to the uploaded document ids before scripting the fake model. A unit test checks
every scripted quote exists verbatim on the named page of the committed PDF, so seed drift fails the
build before anyone sees a demo. Decided.

**D-062 — Overview counts computed in the client.** The four counts and the jurisdiction table derive
from `GET /programs/{id}`, `GET /assessments/{id}` (per-jurisdiction finding and decision counts) and
`GET /programs/{id}/determinations`. The latest finished assessment is found from the audit trail's
ASSESSMENT_STARTED/FINISHED entries. Rejected: a dedicated overview endpoint (one more surface for the
same data). Decided.

**D-063 — Change events: read in M6, write in M7.** `GET /change-events` ships with the Changes screen;
creating events and the re-run action arrive with change tracking in M7. Decided.

**D-064 — Demo banner flag.** `GET /auth/session` returns `tenantName` and `demo`, true when the tenant
name is in `lexvay.demo-tenant-names` (default "Northfield University"). The permanent fictional-data
banner shows whenever `demo` is true. Rejected: a column on `tenant` (schema change for a demo concern).
Decided.

**D-065 — Isolated end-to-end stack.** `scripts/e2e.sh` starts its own compose project (`lexvay-e2e`,
PostgreSQL on 5433), seeds it, runs the packaged backend on 8081 and the Angular dev server on 4300 with a
proxy to 8081, runs Playwright with axe, and tears everything down. The developer database on 5432 and
servers on 8080/4200 are never touched. `make test` runs backend, frontend unit and end-to-end suites.
Decided.

**D-066 — Current mappings after a rule change.** Lists, counts and the signing gate consider a mapping
"current" unless its rule version has a successor whose mappings already exist in the same assessment.
So a jurisdiction keeps showing the old (stale, unreviewable) list until the new version is approved and
re-run, and shows only the new list afterwards. The old mappings stay in the database as history.
Rejected: starting a whole new assessment on re-run (other jurisdictions would lose their decisions).
Decided.

**D-067 — Report renderer.** PDFBox with a small paginated text writer (`PdfTextWriter`): header fields,
then one block per requirement with finding, decision, quoted evidence and citation, then a footer saying
who decides. No second PDF library. Characters outside WinAnsi are replaced. Rejected: OpenPDF (a second
dependency for tables the report does not need). Decided.

**D-068 — Course versions.** `POST /courses/{id}/versions` (catalog year, required, optional
`supersedesId`) supersedes the newest version by default and refuses a version that was already
superseded. It creates the `COURSE_VERSION` change event in the same transaction. Decided.

**D-069 — Report readiness.** `POST /determinations/{id}/report` returns `202 {reportId, jobId}`.
`GET /reports/{id}` returns the PDF (`application/pdf`, attachment) when READY, and `202` with a JSON
status while QUEUED (`200` with status FAILED if generation failed). Reports may be generated for
reopened determinations too; the title says so. Decided.

**D-070 — Re-run preconditions.** `POST /change-events/{id}/rerun` returns `409` when the event is already
HANDLED, when a rule-version event's new version is not approved, or when a course-version event's new
version has no ingested document. Otherwise it queues `RERUN_CHANGE` and returns `202`. Carried-over
decisions are written as review events in the original reviewer's name with a note starting
"Carried over from <old label>". Decided.

## Owner directives of 2026-11-26 (CTO mode)

**D-071 — CTO mode supersedes the build prompt on scope and stack.** The owner directed continuous
improvement toward a production-grade product, parallel agent work in separate worktrees, a real UI
library, low latency, and depth over breadth. Section 1 ("delete", "no extra features") and section 4
("no UI kit beyond Angular CDK") of the build prompt no longer bind. The nine product rules, the fictional
labeling of seed data and the forbidden wording remain binding. Work items and their exact specs live in
`docs/ROADMAP.md`; the orchestrator decides architecture and algorithms, agents implement.

**D-072 — UI stack.** PrimeNG 22.1.5 with `@primeuix/themes` 3.1.1 and a custom `LexvayPreset`; Phosphor
icons via `@ng-icons/core` 36.1.0; IBM Plex Sans, Serif and Mono self-hosted via `@fontsource` 5.3.0.
Chosen over Angular Material 22.3.0 (fewer data-dense components: no splitter, stepper-with-content,
file upload or timeline, and a recognisable stock look), Carbon for Angular 6.0.1 (new major published
one day earlier), Taiga UI and ng-zorro (weaker token theming for a custom identity). All candidates were
checked on the npm registry for Angular 22 peer support on 2026-11-26. Design contract: `docs/DESIGN.md`.

**D-073 — Git workflow in CTO mode.** One branch and pull request per work item, opened by the agent,
reviewed and merged by the orchestrator after CI. No `Co-Authored-By` trailers or AI attribution anywhere.
End-to-end stacks are slot-based (`LEXVAY_E2E_SLOT`) so several checkouts can test at once.

**D-074 — Per-item decision files.** In CTO mode each work item records its decisions in
`docs/decisions/<item>.md` with a reserved id range, so parallel branches never edit the same lines here.
This file keeps the cross-cutting decisions and this index: F3a D-120–129 (`docs/decisions/F3a.md`), F4a
D-130–139 (`docs/decisions/F4a.md`), WS-UI D-080–089, WS-SEC D-090–099, WS-EVAL D-100–109, WS-PERF
D-110–119, F6 D-140–149, F9 D-150–159. Migration number V006 is intentionally unused (D-073 workflow).

## Spec concerns (section 13, item 5)

- Section 5 vs section 7 on `REOPENED` (D-003, resolved by owner).
- The API has no way to create program facts (D-006) or to correct an extracted requirement list
  before approval (D-024); both endpoints are added by owner decision.
- The data model has no place for a program's target jurisdictions (D-007) or for raw model
  input/output (D-014) even though sections 3 and 6 require both.
- Section 9 says "stacked findings bar" per jurisdiction; with findings shown by shape and text as well
  as colour, the bar will carry a text legend with counts, which makes it wider than a plain bar.
- "Upload size and type limits" are listed under milestone 8 but uploads ship in milestone 2; the
  limits are configured in M2 and tested in M8.
