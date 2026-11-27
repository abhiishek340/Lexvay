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

**D-002 addendum (WS-SEC).** `com.bucket4j:bucket4j_jdk17-core` 8.22.0 (checked on repo1.maven.org 2026-11-26; 8.23.0 was four days old). `spring-boot-starter-security-oauth2-client` and `caffeine` 3.2.4 come from the Boot 4.1.3 BOM.

**D-074 — Per-item decision files.** In CTO mode each work item records its decisions in
`docs/decisions/<item>.md` with a reserved id range, so parallel branches never edit the same lines here.
This file keeps the cross-cutting decisions and this index: WS-UI D-080–089, WS-SEC D-090–099, WS-EVAL
D-100–109, WS-PERF D-110–119, F3a D-120–129, F4a D-130–139, F6 D-140–149, F9 D-150–159, F11 D-160–169,
F7 D-170–179, F12 D-180–189, F13 D-190–199, FIX-2 D-200–209, F3 D-210–219, F1 D-220–229, FIX-1 D-230–239, OPS-1 D-240–244, F3b D-245–249, F7c D-250–259, F4 D-260–269, F8 D-270–279, TEST-1 D-280–284. Once an item merges its file is folded into the section at the end of this file and deleted. Migration number V006 is intentionally unused (D-073 workflow); V008 WS-PERF, V009 F11, V010 F6, V011 F12, V012 FIX-1, V013 FIX-2, V014 F13 if needed, V015 F7c. Numbers follow merge order; a branch renumbers before merge if a lower number is still unmerged.

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

## Work-item decisions folded from `docs/decisions/` (D-074)
Each merged work item recorded its decisions in its own file while in flight; the orchestrator folds them here
after the merge and deletes the file. Ids keep the ranges assigned in D-074.

### WS-EVAL — Evaluation harness and Terraform (D-100 to D-109)

**D-100 — The evaluation is a Spring profile run by `scripts/eval.sh`.** `make eval` and `make eval-live` start an
isolated PostgreSQL through docker compose (project `lexvay-eval-<slot>`, port 5433 + `LEXVAY_E2E_SLOT`), package the
backend, run the jar with `--spring.profiles.active=eval` and tear the database down. The runner (`EvalRunner`) prints
the table and exits 0, 1 (a limit is broken) or 2 (the run itself failed). The scoring is in `EvalService` (no
profile), so the same code runs in `EvalServiceTest` against Testcontainers. Rejected: a JUnit-only harness (no
`make` entry point, no live mode); a separate Maven module (a second build for one runner); a CLI that talks to a
running server over HTTP (it could not substitute the golden list through the service layer, and the job queue would
have to be polled).

**D-101 — Scripting the fake model is a `ModelStrategy`.** `SeedLoader` no longer injects `LlmClient` or
`ObjectMapper` and no longer checks `instanceof FakeLlmClient`. `ModelStrategy` has three hooks:
`beforeExtraction(label)`, `afterExtraction(jurisdiction, ruleVersionId)` (as the reviewer, before approval) and
`beforeMatching(documentByFile)`. `ScriptedFakeModel` (reads `seed/fake-llm.yml`) is the strategy for `make seed`
and tests, `ModelStrategy.NONE` leaves a real model alone, `EvalModel` records extraction and, in live mode,
substitutes the golden list. Creating, extracting and approving a rule version is one private method used for the
initial and the superseding rule sets (it was written out twice). `load(seedDir, password, strategy)` replaces
`load(seedDir, password)`; `loadTenant` and `manifest` are public so the evaluation can seed one tenant. `YamlFile`
(`com.lexvay.common`) is the single YAML reader. `FakeLlmClient.replaceScript` replaces a key's queue: `script` appends,
so a second load of the same data in one JVM was answered with the first load's document ids. Rejected: a subclass of
the loader; a boolean `scriptModel` flag; leaving the `instanceof` check and adding a second loader for the evaluation
(duplicated loading code, which the spec forbids).

**D-102 — Golden file.** `eval/golden.yml` is keyed by `jurisdiction/ref` (a ref may repeat across rule sets) and holds, for
each of the 42 requirements, `title`, `text`, `type`, numeric definition, `finding`, `document`, `quoteContains` and
a human `reason`. Title, text and numeric definition are needed because live mode replaces the model's list with
the golden one through `RuleVersionService.replaceRequirements`. It was written from `seed/rules` and `seed/docs`, not
from `seed/fake-llm.yml`; `GoldenFileTest` checks every cited file exists and every `quoteContains` appears in the
seed text. Fictional, labelled `fictional: true`, and `EvalService` refuses a file that is not. Expected mix: 33
EVIDENCE_FOUND (11 numeric by the rules engine + 22 by the matcher), 6 AMBIGUOUS, 3 EVIDENCE_MISSING. (The roadmap's
"22 evidence found" counts the matcher's findings; the 11 rules-engine ones are separate.) Rejected: generating the
golden set from the fake scripts (it would test nothing); only `ref -> finding` (nothing to score citations with, and no
list to substitute in live mode).

**D-103 — Metrics.** The five metrics are as defined in the roadmap. Details the definition leaves open: proposals with no
golden entry count as "golden differs"; the false rate counts every proposed EVIDENCE_FOUND, rules-engine ones
included (33 in the seed); numeric accuracy requires `method = RULES_ENGINE` and the golden finding; extraction
agreement compares the model's own list (captured before any substitution) by jurisdiction, ref and type; quote
verification runs `QuoteVerifier.find` over every stored `evidence_span` of the tenant (program-fact spans
included) against the stored `document_page`. One metric is added: evidence agreement (golden entries naming a
document whose cited file and quote substring match, out of those entries). Without it `document` and
`quoteContains` in the golden file would not be used and a correct finding citing the wrong passage would pass.
Every disagreement is listed under the table. Rejected: counting only matcher proposals in the false rate (hides
rules-engine errors); a fuzzy quote comparison (rule 2 forbids it).

**D-104 — Thresholds.** `eval/thresholds.yml` has `fake` and `live` sections. Quote verification is not in the file: it
must be 100 % and is fixed in code. `fake` is exact (false rate 0, everything else 100 %), so drift between the fake
scripts, the pipeline and the golden set fails the build. `live`: false EVIDENCE_FOUND 0 and numeric accuracy 100 %,
which need no measurement; recall 0.80, extraction agreement 0.90 and evidence agreement 0.80 are first guesses made
without a live run (no API key was available) and must be tightened after the first real run. An unmeasurable
metric (zero denominator) fails, except the false rate when nothing was proposed. Rejected: one threshold set
(a real model cannot be held to exact reproduction); thresholds as Java constants (the spec names the file).

**D-105 — Tokens, cost and time.** Input and output tokens and call count are summed from `llm_call` for the
evaluation tenant (row-level security scopes the query), extraction included. Cost = tokens times
`lexvay.llm.price.*` (`LEXVAY_LLM_PRICE_INPUT_PER_MTOK`, `LEXVAY_LLM_PRICE_OUTPUT_PER_MTOK`); when neither price is set it
prints "n/a" rather than a free-looking 0. Fake token counts are estimates from text length and the report says so.
Wall time is the assessment's `finished_at - started_at`, the pipeline stage the roadmap asks about, not process
start-up. There is one assessment per run, so the totals are per assessment. Rejected: placeholder default prices in
`application.yml` (invented money); timing the whole process (includes Flyway and JVM start).

**D-106 — Live mode.** `make eval-live` sets `LEXVAY_LLM_PROVIDER=anthropic` only when `ANTHROPIC_API_KEY` is set;
otherwise it prints why and exits 0. `make eval` unsets the key so a scripted run can never reach the network. The
live path (no scripts, golden substitution before approval) is covered by `EvalModelTest`, which drives the same
`EvalModel` with the scripted model; it has not run against the real model. Tests never call a real model.

**D-107 — Isolation.** Each run seeds the first demo tenant under a unique name and unique user emails
(`SeedManifest.Tenant.isolated`) and without the change-tracking demo, so it can run against a database that
already holds the demo seed. The database is a fresh compose project `lexvay-eval-<slot>` removed on exit; the
volume is removed with it. `scripts/eval.sh` repeats the six lines of port and cleanup convention from `scripts/e2e.sh`
instead of both sourcing a shared file, to avoid editing a script other work items touch; fold them together when
convenient. Eval and end-to-end use the same ports for the same slot, so they must not run at the same time in one
checkout.

**D-108 — Terraform design (`infra/`, AWS, no modules).** Plain resources, so every line is reviewable and the only
providers are `aws` and `random`.
- Network: one VPC, a public and a private subnet per zone (2 by default), one NAT gateway by default
  (`nat_gateway_per_az` for one per zone), S3 gateway endpoint, default security group emptied. Backend and database are
  only in private subnets. Rejected: private subnets without NAT (the backend must reach the model API and ECR);
  VPC flow logs and WAF (not in the spec, follow-up for F6).
- Entry: CloudFront serves the Angular build from a private S3 bucket through origin access control (versioned,
  SSE-S3, public access blocked) and sends `/api/*` to a public ALB so the browser has one origin (session cookie and
  CSRF keep working). The ALB serves HTTPS only (ACM certificate variable), accepts only CloudFront's address list,
  and returns 403 unless the request carries a secret header the distribution adds. A CloudFront Function rewrites
  extensionless paths to `/index.html`. Rejected: CloudFront custom error responses for SPA routing (they would also
  rewrite API 403/404 into the page); a CloudFront VPC origin with an internal ALB (newer, needs service-managed
  security groups that cannot be checked without applying); ALB open to the internet; HTTP listener.
- Database: RDS PostgreSQL 18, KMS-encrypted, not publicly accessible, Multi-AZ, 14-day backups, deletion
  protection and final snapshot by default, TLS enforced, master password managed by RDS in Secrets Manager.
  Rejected: a master password variable (secret in a variable file).
- Files bucket: versioned, SSE-KMS with the data key, public access blocked, TLS-only policy, `BucketOwnerEnforced`.
- Secrets Manager: `database` (random passwords of the owner and app roles, in Terraform state) and
  `anthropic-api-key` (entry only, value set with the CLI so the key is never in state). Injected as environment
  variables by the execution role.
- IAM: execution role (managed policy, read two secrets, decrypt the key via Secrets Manager only) and a separate task
  role (get/put objects in the files bucket, list that bucket, use the key via S3 only).
- Variables have descriptions and validation, none hold secrets or account ids (the account id is read from the
  provider where bucket names need it). `backend_image` rejects `:latest`.
- No remote state backend is configured: the choice (bucket, locking, who may read it) belongs to the deployer, and
  the state holds the generated passwords.
Not done and called out: creating the database roles. RDS has no `lexvay_owner` or `lexvay_app` until
`db/initdb/01-roles.sh` (or equivalent) is run with the administrator secret; the passwords it needs are in the
`database` secret. This is a deployment step, not Terraform, and it has not been tried.

**D-109 — Pins and what was verified.** Terraform 1.17.2 (releases.hashicorp.com lists it as newest; installed from the
official darwin_arm64 zip after checking its SHA-256 against the published `SHA256SUMS`, because
`brew install hashicorp/tap/terraform` stopped at "Command Line Tools are too outdated"; the signature could not be
checked, no `gpg`). `hashicorp/aws` 6.74.0 (registry, published 2026-11-23), `hashicorp/random` 3.9.1 (2026-09-11), both
exact in `versions.tf` and locked for linux_amd64, linux_arm64, darwin_arm64 and darwin_amd64 in
`infra/.terraform.lock.hcl`. CI: `hashicorp/setup-terraform@v4` (v4.0.1, 2026-05-12, runs on node24), with
`terraform_version: 1.17.2` and `terraform_wrapper: false`; a separate `infra` job so it runs beside the tests.
`make infra-validate` runs `fmt -check`, `init -backend=false`, `validate` and `terraform test`. The tests
(`infra/tests/stack.tftest.hcl`) use mocked providers, so they need no credentials and create nothing; they check the
variable validations and the private, encrypted, versioned and public-access-blocked properties. `plan` and `apply`
were never run. RDS: PostgreSQL 18 is generally available (18.7 is listed in the RDS release notes); the parameter
group family `postgres18` follows RDS naming and could not be checked without credentials; `db_engine_version`
defaults to the major version `18`. The CloudFront and ALB wiring has been validated for syntax and schema only, not
deployed.

### WS-UI — Design system and screen rebuild (D-080 to D-089)

### Dependency pins (verified on the npm registry on 2026-11-26)

| Package | Version |
|---|---|
| `primeng` | 22.1.5 |
| `@primeuix/themes` | 3.1.1 |
| `@ng-icons/core` | 36.1.0 |
| `@ng-icons/phosphor-icons` | 36.1.0 |
| `@fontsource/ibm-plex-sans` | 5.3.0 |
| `@fontsource/ibm-plex-serif` | 5.3.0 |
| `@fontsource/ibm-plex-mono` | 5.3.0 |

Transitive packages PrimeNG 22.1.5 brings in (resolved by npm, recorded in `package-lock.json`):
`@primeuix/styled`, `@primeuix/styles`, `@primeuix/utils`, `@primeuix/motion`, `@primeicons/angular`,
`@primeui/license-manager`.

### D-080 PrimeNG 22 requires a PrimeUI license key (needs an owner decision)
PrimeNG 22 is distributed under the PrimeUI License (`node_modules/primeng/LICENSE.md`): a Community License
(free for organisations under USD 1 M revenue, fewer than 5 developers and 10 employees; individuals and
non-commercial open source qualify; renewed yearly) or a paid Commercial License. At start-up
`providePrimeNG` verifies a key offline through `@primeui/license-manager`; without one it logs
"PrimeUI license is not configured" and shows a fixed "Invalid PrimeUI License" notice in the corner of
every page (it is visible in `docs/screens`). The license forbids removing that mechanism, so the notice is
left in place. Every 21.x release has no license check but requires Angular 21, so it cannot be used with
Angular 22.3.
Implemented: the key is read from the build-time constant `LEXVAY_PRIMEUI_LICENSE`
(`ng build --define "LEXVAY_PRIMEUI_LICENSE='<key>'"`, see `src/app/theme/license.ts`) and passed to
`providePrimeNG({ license })`; no key is committed. Rejected: hiding the notice with CSS (breaches the
license); switching libraries or downgrading (contradicts the design brief, and the brief is the CTO's call).
**Action for the owner:** register for the Community License (if eligible) or buy a Commercial License, then
supply the key at build time.

### D-081 Design tokens live once, in the preset, as light-dark() pairs
`@primeuix/themes` 3 expresses Aura's colour scheme with CSS `light-dark()`; PrimeNG emits
`color-scheme: dark` for `[data-theme="dark"]`. `LexvayPreset` writes every DESIGN.md token as a
`light-dark(light, dark)` pair, overrides the Aura semantic and component tokens with them, and publishes
the same values under `extend.lx` so application CSS reads `--p-lx-*`. Rejected: a second copy of the
palette as hand-written CSS variables in `styles.css` (two sources of truth); `colorScheme.light/dark`
blocks (mixed with Aura's `light-dark()` values the precedence is unclear).
`ThemeService` always writes a concrete `data-theme` (`light` or `dark`); "System" resolves it from
`prefers-color-scheme` and follows changes, so one selector drives both PrimeNG and the application CSS.

### D-082 Aura limited to the components Lexvay renders
`definePreset` starts from Aura's base tokens plus the token sets of the 22 components in use (listed in
`lexvay-preset.ts`). The full Aura object carries about 90 component token sets and cost 130 kB of the
initial bundle. Rejected: the full Aura import. Consequence: a component added to a template must be added
to that list, or it renders without its theme.

### D-083 Bundle budget measured on raw size
WS-UI asks for an initial bundle under 600 kB transferred. Angular budgets measure raw (uncompressed)
size, so the budget is set on raw size at 700 kB warning / 800 kB error; the production build measures
672.9 kB raw and 161.8 kB estimated transfer. The program switcher (Select), the account menu and the toast
are `@defer` blocks. Rejected: a 600 kB raw budget (Angular core, router, forms and common alone are about
400 kB raw; meeting it would mean dropping PrimeNG from the shell). The component style budget is raised to
10 kB / 14 kB because the review workspace styles three panes and two dialogs in one component.

Critical-CSS inlining is switched off in the production configuration (`optimization.styles.inlineCritical:
false`): it adds an inline `<script>` that swaps the stylesheet's `media`, which the backend's
Content-Security-Policy (`default-src 'self'`, no inline scripts) blocks, leaving the global styles unapplied.
The built `index.html` now has one external module script and one stylesheet link; fonts load from `./media`.
PrimeNG's runtime `<style>` elements rely on `style-src 'unsafe-inline'`, which the policy allows.

### D-084 Fonts: latin and latin-ext subsets only
The `@fontsource` packages ship cyrillic, greek and vietnamese subsets as well. Only `latin` and
`latin-ext` are imported for Sans 400/500/600, Serif 400 and 400 italic, and Mono 400. Rejected: the
all-subsets entry points (more files in the build for scripts the product does not show).

### D-085 One program context shared by every screen
`ProgramContext` loads the program, its latest finished assessment, determinations, change events,
jurisdictions and rule versions once per program; screens and the navigation badges read it, and mutations
call a targeted refresh. Rejected: each screen fetching its own copy (the previous code issued the same
four requests on every screen change, and the badges would need a third copy).

### D-086 Review view model as a plain signal class
`ReviewWorkspace` (filters, selection, J/K movement, advancing after a decision, progress) and
`shortcutFor`/`decisionBlocker` are plain TypeScript with Angular signals and no dependency injection, unit
tested with Vitest. A decision counts only while it is current: a stale mapping needs review again
(`isDecided`). After a decision the workspace moves to the next requirement in list order that still needs
one and matches the filter, wrapping around. Rejected: an injectable store (needs TestBed to test) and
keeping the logic in the component.

### D-087 Accessibility workarounds for PrimeNG markup
- Tables have no accessible-name input: names are set through pass-through (`[pt]="{ table: { 'aria-label' } }"`).
- `p-progressbar` sets `aria-level` on a `progressbar` role and has no name input, which axe reports;
  progress bars are `aria-hidden` and the progress is always stated in text next to them.
- Splitter gutters get `aria-label="Resize panes"` through pass-through; dialogs get `closeAriaLabel`.
- Navigation badges are `aria-hidden`; the count is a separate `aria-describedby` text so link names stay
  "Review" and "Changes".
- Tag labels use the `value` input so the icon precedes the word.
Rejected: forking or wrapping the PrimeNG components.

### D-088 Focus after navigation, and the live region
Moving to another screen focuses that screen's `h1`; the first page load and query-only navigation (the
jurisdiction tabs, the report's jurisdiction) do not move focus. Review results are announced in one
polite `role="status"` region in the review header (`data-testid="message"`) and also shown as a PrimeNG
Toast, as the brief asks. PrimeNG's toast is `role="alert"`, so a screen reader may hear a decision twice.
Rejected: dropping the toast (the brief asks for both).

### D-089 Evidence report jurisdiction and PDF
The report opens on the jurisdiction in `?jurisdiction=` or the first one, chosen with a PrimeNG Select.
"Generate PDF" is offered only for a signed determination because the existing endpoint renders a signed
determination; a draft explains that the PDF follows signing. Rejected: a client-side PDF of the draft.

### Screens and tests
- Screenshots: `docs/screens/*-light.png` and `*-dark.png`, produced by `e2e/screens.spec.ts`
  (`LEXVAY_SCREENSHOTS=1 LEXVAY_E2E_SLOT=1 make e2e`).
- Every end-to-end test fails on any request to a host other than localhost (`e2e/support.ts`).
- `expectAccessible` runs axe in the light and the dark theme with transitions disabled.
- Backend: `GET /api/v1/users` (`tenant/UserController`, `UserApiTest`), tenant-scoped by row-level
  security, readable by every role, not in the audited set because it is a GET.

### WS-SEC — OIDC and hardening (D-090 to D-099)

**D-090 — OIDC through `spring-boot-starter-security-oauth2-client`, only in profile `oidc`.** The starter is managed
by the Boot 4.1.3 BOM (Spring Security 7.1.3, `oauth2-oidc-sdk` 11.38.2, `nimbus-jose-jwt` 10.9.1). It is on the
classpath in every profile but does nothing without `spring.security.oauth2.client.registration.*`, which only
`application.yml`'s `oidc` section defines (`LEXVAY_OIDC_ISSUER_URI`, `LEXVAY_OIDC_CLIENT_ID`,
`LEXVAY_OIDC_CLIENT_SECRET`, scopes `openid,email,profile`, registration id `lexvay`, so browsers start at
`/oauth2/authorization/lexvay`). `OidcLogin` (`@Profile("oidc")`) configures `oauth2Login` on the one security chain;
`SecurityConfig` calls it through an `ObjectProvider`, so the default profile has no OIDC bean at all
(`AuthTest.singleSignOnIsOffUnlessProfileOidcIsActive`). After the provider authenticates, the success handler
replaces the `OAuth2AuthenticationToken` in the session with a `UsernamePasswordAuthenticationToken` whose principal is
the same `LexvayUser` that form login produces, so `CurrentUser`, `TenantContextFilter`, method security and audit
are unchanged, then redirects to `LEXVAY_OIDC_POST_LOGIN_URL` (default `/`) and audits `LOGIN_SUCCEEDED` with
`method: oidc` (form login now audits `method: password`). `/api/**` without a session keeps the 401 problem detail
because the explicit authentication entry point wins over the OAuth2 login redirect. Form login stays enabled in the
`oidc` profile (the spec says it remains the default); an organisation that wants SSO only should disable it in a
follow-up. Rejected: making `LexvayUser` implement `OidcUser` (it would carry token claims in every session and in
every form login); a resource-server (JWT bearer) setup (the browser flow is the requirement); a second
`SecurityFilterChain` for OIDC (two chains would have to repeat CSRF, headers, rate limiting and tenant binding).

**D-091 — Identity to account: verified email, roles from the database.** `LexvayOidcUserService` wraps Spring's
`OidcUserService` (so the user-info call, subject check and ID token validation are Spring's) and looks the `email`
claim up with `AppUserRepository.findForLogin`. It refuses with 403 problem detail "No Lexvay account for this
identity" when the claim is missing or blank, when `email_verified` is present and not `true` (providers send a boolean
or the string "false"; anything else than true fails closed), or when no `app_user` matches. Roles and tenant are
never read from token claims; a token carrying `roles: [ADMIN]` for a reviewer still yields a reviewer
(`OidcLoginTest.theRoleComesFromTheLexvayDatabaseAndNeverFromTheToken`). `findForLogin` returns empty for an email
containing NUL, which PostgreSQL cannot store (it used to raise a database error). Other failures (bad state, nonce,
issuer, signature) are 401 and logged without personal data. Rejected: mapping provider groups to roles (the roles
belong to Lexvay, per the spec); creating accounts on first login (an account carries a tenant and a role, which a
provider cannot decide); matching on `sub` (needs a new column and a linking flow, not in the spec).

**D-092 — Rate limiting: one filter, Bucket4j buckets in a bounded Caffeine cache.** `RateLimitFilter` is created
inside `SecurityConfig` (not a bean, so Boot does not register it a second time as a servlet filter) before
`CsrfFilter`, so a login attempt counts whether or not its CSRF token is valid, and the user is already known from the
session. Budgets, all per minute with greedy refill: login 10 per client address (counts `POST /api/v1/auth/login` and
the OIDC callback `GET /login/oauth2/code/*`), authenticated `/api/**` 600 per user, `POST /api/v1/programs/*/documents`
30 per user on top of the API budget. A refusal is a 429 problem detail with `Retry-After` (whole seconds until a token
is back). Settings are `lexvay.ratelimit.*` (`LEXVAY_RATELIMIT_LOGIN_PER_MINUTE`, `_API_PER_MINUTE`,
`_UPLOAD_PER_MINUTE`, `max-keys` 10000, `idle-expiry` 10m), validated positive. The filter takes its clock as a
`LongSupplier`, so refill and idle expiry are tested with a fake clock (`RateLimitFilterTest`, including that the
cache never exceeds `max-keys`). The test base sets the budgets to 100000 and the rate limit tests override them.
`scripts/e2e.sh` sets the login budget to 1000 because every browser test signs in from 127.0.0.1. Budgets are per
instance, so with N instances the effective budget is up to N times higher. Rejected: `bucket4j-spring-boot-starter`
(a third-party auto-configuration for what is one class); a filter before the security chain (the user is unknown
there); Redis or JDBC-backed buckets (the spec says in-memory, per instance); Spring's `HandlerInterceptor`
(does not see login requests, which are handled by a security filter).

**D-093 — Response headers, session cookie, forwarded headers.** `SecurityHeadersFilter` sets, on every response,
the exact `Content-Security-Policy` from the spec, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
`Permissions-Policy: camera=(), microphone=(), geolocation=()`, and `Strict-Transport-Security: max-age=31536000 ;
includeSubDomains` when `request.isSecure()`. It is a servlet filter at highest precedence rather than the Spring
Security headers DSL because the request firewall rejects some URLs (for example `;` in the path) before the security
chain starts, and those 400 responses must carry the headers too (`SecurityHeadersTest` covers 200, 400, 401, 403,
404, 405, 415, the firewall's 400 and 429). The DSL keeps `X-Frame-Options: DENY` and `Cache-Control: no-store`; its nosniff and
HSTS writers are disabled to avoid duplicates. Session cookie: `server.servlet.session.cookie` `http-only: true`,
`same-site: lax` (Lax, not Strict, because the OIDC callback is a cross-site top-level navigation), `secure` from
`LEXVAY_COOKIE_SECURE`. `server.forward-headers-strategy` comes from `LEXVAY_FORWARD_HEADERS` (default `none`).
Behind a load balancer it must be `native`: otherwise every client shares the balancer's address, one login budget
for everybody, and the scheme is always http (no HSTS). `native` uses Tomcat's `RemoteIpValve`, which trusts the
header only from private-range peers (`ForwardedHeadersTest`). `framework` was rejected: `ForwardedHeaderFilter` does
not change the remote address. Rejected: Spring Security's HSTS writer alone (misses firewall rejections); a CSP with
nonces (the API serves JSON, the Angular build is served elsewhere).

**D-094 — Session fixation and login CSRF stay on, with tests.** `sessionFixation().changeSessionId()` is now explicit
in `SecurityConfig` so it cannot be lost by accident, and CSRF is untouched. `SessionSecurityTest` uses a real
connection (MockMvc has no container session): it plants a session with an unauthenticated request, logs in with it,
and asserts the id changed, the new id is authenticated and the planted one is not. Changing the setting to `none()`
makes the test fail (checked). `OidcLoginTest.theSessionIdChangesAtOidcLoginOverARealConnection` proves the same for
the OIDC callback. Login without the CSRF header is 403 (`SessionSecurityTest`, `AuthTest`). Rejected: asserting the id
change with MockMvc (`MockHttpSession` does not follow the container's behaviour).

**D-095 — Upload limits.** (1) Size: Spring's multipart limit (`LEXVAY_UPLOAD_MAX_MB`, default 25) answers 413 problem
detail through `ResponseEntityExceptionHandler` (`UploadSizeLimitTest`, real connection, limit 1 MB for speed). (2) Type:
`FileType.detect` (extension and leading bytes) as before; tests cover every mismatch with a problem detail. (3) DOCX
zip bomb: `ContentLimits.requireSafeDocx` inflates the archive counting the bytes that really come out (declared sizes
can lie) and stops at the first byte beyond `min(50 MiB, 100 x file size)`; it runs at upload (400 problem detail,
nothing stored) and again in `TextExtractor` (any caller). An unreadable archive is left to the parser, so a corrupt
DOCX is still accepted and fails ingest as before. (4) PDF page cap 500: checked in `TextExtractor` after the PDF is
opened, so an over-long PDF is accepted with 202 and its ingest job fails with "PDF has 501 pages; the limit is 500",
like the other content failures; loading every PDF at upload just to count pages was rejected. Also: file names longer
than 255 characters or with control characters are refused with 400 (a NUL used to reach PostgreSQL after the file was
already stored). 50 MB is read as 50 MiB. Rejected: POI's own `ZipSecureFile` limits as the only guard (no total size
limit); `ZipFile` on a temporary file (disk writes for an in-memory check).

**D-096 — Input validation table test and the fixes it forced.** `InputValidationTableTest` lists 111 requests, at
least one per endpoint (bad UUID, missing field, oversized string, unknown enum, NUL character, malformed or deeply
nested JSON, wrong media type, non-JSON login) and asserts the expected 4xx with a problem detail. A second test fails
when `RequestMappingHandlerMapping` contains an endpoint without a row, so new endpoints cannot skip it (a work item
that adds an endpoint must add a row, like `Rule9_AuditCoverageTest`). It runs against records that exist (program,
document, unapproved rule version, mapping with an open evidence request) so validation below the controller is
reached too. Bugs found and fixed: (a) any string containing NUL (program name, rule text, review note, requirement
title) made PostgreSQL fail with SQLSTATE 22021 and the response was 409 or 500: `ApiExceptionHandler` now answers 400
for every SQLSTATE class 22 (data exception) and keeps 409 for class 23; (b) `numeric_value: 1e400` in the
requirements PUT threw `JsonNodeException` (500): `RequirementListParser` requires a finite positive number; (c) a
300 000-character requirement text, ref or title was accepted: limits 50, 200 and 20 000 characters in the parser (the
LLM output schema is not changed, structured-output schemas do not accept `maxLength`); (d) the mappings
`jurisdiction` filter accepted any string: `@Pattern` like every other jurisdiction input; (e) file names (D-095); (f) a
NUL in the login username or the OIDC email logged a database stack trace: `findForLogin` returns empty. Rejected:
whitelisting any 500; a global filter stripping NUL (it would silently change user data).

**D-097 — Dependency pins.** `com.bucket4j:bucket4j_jdk17-core:8.22.0` (published 2026-11-03). 8.23.0 is the latest but was
published on 2026-11-22, four days before this check; the release before it was chosen to let it settle. `com.github.ben-manes.caffeine:caffeine` 3.2.4 and `spring-boot-starter-security-oauth2-client` 4.1.3 come
from the Boot BOM, unpinned in the POM. `bucket4j_jdk17-core` has no transitive dependencies. Test-only code uses
Nimbus (already transitive of the OAuth2 client) and the JDK HTTP server.

**D-098 — OIDC tests use a loopback provider, not explicit endpoints.** The spec asks for explicit endpoints so tests
need no network. Boot always performs discovery when `issuer-uri` is set, and an empty issuer fails startup
("issuer cannot be empty"), so the explicit-endpoint form cannot coexist with the shipped `application.yml`. The test
instead starts `FakeIdentityProvider` (JDK `HttpServer` on 127.0.0.1, random port) serving discovery, a JWK set and a
token endpoint, and sets `LEXVAY_OIDC_ISSUER_URI` to it. Nothing leaves the machine, and the shipped configuration,
issuer validation, signature check, state and nonce are all exercised (an ID token with another issuer, nonce or state
is refused). Rejected: `MockMvc` with `oidcLogin()` post-processors (they skip the code exchange and the account
lookup, which is the part that matters); mocking `ClientRegistrationRepository`.

**D-099 — Test isolation.** The shared test base sets the three rate limit budgets to 100000 through
`@TestPropertySource`; tests that need tight budgets, a Secure cookie, forwarded headers, a 1 MB upload limit or the
`oidc` profile declare their own and get their own cached Spring context (five extra contexts, about 8 seconds each).
`spring-security-test`'s `csrf()` is still not used; `TestCsrf.csrf()` stays.

### WS-PERF — Read-path latency and read APIs (D-110 to D-119)

### D-110 One connection per read request; the tenant is bound on checkout only

Every service method that only reads is `@Transactional(readOnly = true)`, so a request checks out one
connection, binds the tenant once, and runs all its statements and its commit on it. `TenantAwareDataSource`
no longer wraps the connection in a proxy and no longer issues `RESET lexvay.tenant_id` on return: checkout
always runs `set_config` (tenant present) or `RESET` (no tenant), so the value an idle pooled connection holds
cannot be used before the next checkout replaces it. Tenant isolation stays fail closed (product rule 7).
`Rule7_PooledConnectionRebindsTest` proves it on one physical connection (a pool of size one lends the same
backend to tenant A, to no tenant and to tenant B). `ReadPathConnectionsTest` asserts exactly one checkout per
read endpoint.

Reads that run a single statement and have no service method (audit list, jurisdictions, jobs) stay in
autocommit: one checkout and no extra COMMIT round trip.

Rejected:
- Remembering the last bound tenant per physical connection to skip `set_config` when it matches. It saves a
  round trip but makes isolation depend on state the application tracks about the connection; anything that
  changes the setting behind its back (a bug, `RESET ALL`, a future SQL function) would leak. Not worth it.
- `connectionInitSql` or a pool-level hook for the tenant. The tenant changes per request; the pool cannot know it.
- Keeping the close-time `RESET` as a second line of defence. It costs a statement on every return and defends
  nothing, because the checkout rebind is the only protection that matters.

### D-111 Program overview: contents and package

`GET /programs/{id}/overview` returns `{ program, assessment, determinations, openChangeEvents, undecidedTotal }`
in one read-only transaction. `assessment` is the latest FINISHED one (null before the first run).
`undecidedTotal` is the sum over jurisdictions of `total - decided` from that assessment, which is exactly the
set the signing gate counts (stale, undecided or reopened mappings), so it needs no extra query.
`openChangeEvents` is a list of the same views as `GET /change-events`, limited to OPEN events that touch this
program: a new version of one of its courses, or a new rule version for one of its target jurisdictions and its
license type. The spec did not say whether the list is program-scoped; an unscoped list would show another
program's course changes on this program's screen. Confirmed by the orchestrator on review.

The service lives in a new package `overview` because it composes assessment, determination and change data and
nothing depends on it; putting it in `program` would create a package cycle (assessment already depends on program).

Rejected: a count instead of a list (the screen needs to link to the events); embedding mappings (too large).

### D-112 Document page caching

`GET /documents/{id}/pages/{n}` answers with `Cache-Control: private, max-age=31536000, immutable` and a strong
ETag `"<document sha256>-<page number>"`. Pages never change (the database rejects updates), so a year is safe.
`private` keeps shared caches and proxies out. `If-None-Match` is answered with 304 by Spring's
`HttpEntityMethodProcessor` from the ETag on the response entity; the 304 keeps ETag and Cache-Control. The header
is written as a literal string because the order of its directives is part of the specified contract. A 404 is
never marked cacheable.

Known edge: the browser cache is keyed by URL, not by user. A second user on the same browser profile who
requests the same document id would be served the first user's cached copy without a request, but only for a
document id the first user could read, and the second user cannot learn that id from the API unless they can read
it. Accepted; `Vary: Cookie` would remove it at the cost of losing the cache across logins.

Rejected: `public` (cross-tenant risk), `ShallowEtagHeaderFilter` (hashes the body, so it still reads and
serialises the page), a weak ETag (the content is byte-identical for a given document and page), `must-revalidate`
(defeats the point for an immutable resource).

### D-113 Review history

`GET /mappings/{id}/reviews` returns every review event of the mapping oldest first, including REOPENED events
that the system recorded (user id and display name null). An unknown id, or another tenant's, is 404 (row-level
security hides the mapping). No role restriction: like every GET it is open to any authenticated user of the tenant.

### D-114 N+1 removal

Done in SQL, not in Java: determinations aggregate course version ids with an `ARRAY(SELECT ...)` in the main
query; change event views are one statement with LEFT JOINs and correlated subqueries for "has a document" and the
reopened determination ids (it also serves the overview and the re-run precondition); the rule version list takes
requirement counts from a subquery and the latest extraction jobs from one `DISTINCT ON` statement. The benchmark
exposed the rule version list (13 connections per call before) beyond the two repositories the spec named.

Rejected: an in-process cache (a stale-tenant risk and not needed once queries are set-based); batch loading with
`IN` lists per child table (more round trips than one joined statement).

### D-115 Indexes

V008 creates nine indexes, each chosen by the planner for its query on a database of about 200 tenants
(`IndexUsageTest`, evidence in docs/PERF.md). Four of the fourteen candidates in the spec are not created:
- `document (program_id)`: covered by the unique index `(tenant_id, program_id, sha256)`, because every query
  also carries the row-level security condition on `tenant_id`.
- `requirement (rule_version_id)`: covered by the unique index `(rule_version_id, ref)`.
- `change_event (status, created_at DESC)`: the planner prefers the tenant index; a tenant has few events.
- `job ((payload->>'documentId'))` and `job ((payload->>'ruleVersionId'))`: usable without row-level security
  but never chosen with it. The job table's policy has an OR branch for the worker flag, and `jsonb ->>` is not a
  leakproof function, so the planner may not use it as an index condition below the policy.

`IndexUsageTest` creates the rejected candidates inside its own database and asserts the planner still ignores
them, so a future Postgres or policy change that makes one useful fails the test and says so.

Finding for the orchestrator, not implemented because it is a schema change outside the spec: the ingest and
extraction status lookups (`GET /documents/{id}`, rule version views) scan the whole job table under row-level
security, and jobs are never deleted. A generated column `payload_ref uuid` (the payload id, compared with a
leakproof `=`) with an index would make them index lookups.

### D-116 Audit filters

`GET /audit` takes optional `action`, `entityType`, `entityId`, `userId`, `from`, `to` (ISO-8601 instants, both
ends inclusive). The SQL text contains only the predicates that were given, so each combination gets its own
plan. The response shape is unchanged. Rejected: `(:x IS NULL OR col = :x)`, which forces a generic plan that
cannot use an index on the filtered column.

### D-117 Response compression

`server.compression` is on for `application/json` and `application/problem+json` from 1 kB. `spring.jackson`
settings are untouched. `CompressionTest` checks over real HTTP that a large response is gzipped and a small one
is not. On loopback compression costs CPU and saves no time; it pays on a real network.

### D-118 Benchmark method

`EndpointLatencyTest` sends `Accept-Encoding: gzip` like a browser, so the "after" numbers include compression
CPU on the 30 to 45 kB responses. It signs one jurisdiction through the API first so the determination, review and
change views have data (the seed's course and rule changes leave the other jurisdictions stale, which blocks
signing). It runs twice: on the two seeded tenants, then after loading 30 renamed copies of both (62 tenants).
Connections per call are counted by wrapping the data source. Each result in docs/PERF.md is the median of three
runs, chosen by the sum of its p95 values; other agents were building on the same machine, so absolute numbers
vary and the comparison between before and after is what matters.

### D-119 Job payload key inside the SQL text

`JobRepository.latest` used a bind parameter for the payload key (`payload->>:key`), which the planner cannot
match to an expression index. The key is now part of the SQL text, restricted to letters, and the same helper
builds the set-based variant. Rejected: one method per key (more API for no benefit).

### F3a — Rule version diff API (D-120 to D-129)

**D-120 — One definition of "same requirement": `RequirementComparison`.** `com.lexvay.rule.RequirementComparison.changes(a, b)`
returns an `EnumSet` of `TITLE`, `TEXT`, `TYPE`, `NUMERIC`. Text and title are compared after
`QuoteVerifier.normalizeWhitespace` (case stays significant, as before). `NUMERIC` compares field, operator and value, the
value with `BigDecimal.compareTo` (600 equals 600.00; a missing value differs from any present one). Each `Aspect` carries
`affectsEvaluation` (false for `TITLE` only); `sameForEvaluation(a, b)` is true when no changed aspect affects evaluation.
`RerunChangeHandler` calls it directly and its private `unchanged` method is deleted, so carry-over is true exactly when
the set contains none of `TEXT`, `TYPE`, `NUMERIC`, and a title-only change still carries over (identical to the previous
behaviour; `RuleVersionChangeTest` is unmodified and green). The diff service uses the same `changes`.
Rejected: keeping a boolean `unchanged` beside a second set-based comparison for the diff (two definitions would drift);
a `Requirement.equals` override (the record's equality includes ids and must stay structural); comparing title without
whitespace normalization (a re-spaced title is not a change a reviewer cares about; the spec only fixes text, so this is
my reading).

**D-121 — One text diff utility: `TextDiff`, Myers via java-diff-utils.** `com.lexvay.common.TextDiff.words` and `.lines`
return merged segments `{op: EQUAL | DELETE | INSERT, text}`. Tokens are `\w+|\W+` so that joining reproduces the input;
the pattern is compiled with `UNICODE_CHARACTER_CLASS` so accented letters in names and legal text stay inside words
(without it `café` would be split at the `é`). Lines are split after each `\n` and keep their terminator, so the
reconstruction property (EQUAL+DELETE gives `a`, EQUAL+INSERT gives `b`) holds for lines as well, including `\r\n` and a
missing final newline. A replaced block becomes DELETE then INSERT for words and is refined with `words` on the joined
block for lines. One private walker serves both; adjacent segments with the same op (including those produced by refining
neighbouring blocks) are merged and empty segments are dropped. No cap on input size: rule texts are a few pages and the
Myers implementation is O(ND); revisit if a rule document of hundreds of pages ever needs a diff.
Rejected: hand-written diff (forbidden by the spec); character diff (unreadable on legal text); `DiffRowGenerator`
inline mode (HTML-oriented, wraps tags around text, no reconstruction guarantee); `MyersDiffWithLinearSpace` (smaller
memory but not the library default; no need at this size).

**D-122 — Requirement matching: `RequirementMatcher`.** Package-private class used by `RuleVersionDiffService`, split out so it
is unit tested without a database. (a) Pair by identical `ref`. (b) Among the requirements whose ref exists in only one
version, compute the Jaccard similarity of lower-cased `\w+` word sets of the texts for every removed x added pair, keep
pairs with similarity >= 0.80 (the boundary is inclusive and tested at exactly 0.80), sort by similarity descending
(ties by older position, then newer position, so the result is deterministic), and pair greedily so each requirement is
used once; those pairs are `refChanged: true`. (c) The rest is ADDED or REMOVED. Two empty word sets have similarity 0,
never 1, so blank texts do not pair. A pair is `UNCHANGED` only if the ref is the same and the change set is empty, so a
renumbered requirement is always `CHANGED` even when nothing else changed. Output order: newer version's order, then
removed ones in the older version's order. "Order" is the repository order (`created_at, ref`); because the requirements of
a version are inserted in one transaction, this is effectively ref text order (`CO-10` sorts before `CO-2`). That ordering
is pre-existing; the F3 UI may want a natural sort.
Rejected: optimal assignment (Hungarian) instead of greedy (the spec says greedy; at 0.80 conflicts are rare);
similarity on titles; matching on the numeric fields.

**D-123 — Endpoint contract.** `GET /api/v1/rule-versions/{id}/diff?against={otherId}`. `id` is the newer side (`to`),
`against` the older (`from`); when omitted `against` is the version `id` supersedes. 400 problem detail when there is no
superseded version and no `against`, and when jurisdiction or license type differ; 404 when either version is not visible
in the tenant (RLS), including `against`; 401 unauthenticated. Any authenticated role may read it, like the other rule
version reads. Comparing a version with itself is allowed (all UNCHANGED). The lists need not be approved: a reviewer
looks at the diff before approving the new list. Response: `from`/`to` `{id, label, effectiveFrom}`, `summary`
`{added, removed, changed, unchanged}`, `requirements` `[{status, refChanged, before, after, changes, textDiff}]`
(`before`/`after` are the existing `Requirement` JSON, null on the missing side; `textDiff` only for CHANGED rows, else
null; `changes` is an array in the order TITLE, TEXT, TYPE, NUMERIC) and `ruleTextDiff` (`TextDiff.lines` over the two rule
texts). `RuleVersionDiffService.diff` is `@Transactional(readOnly = true)`: two version lookups, two requirement queries and
two rule-text queries in one transaction, no per-requirement queries.
Rejected: `from`/`to` query parameters (the spec fixes the shape); 404 instead of 400 for the mismatch (both versions
exist, the request is wrong); a diff of approved lists only (blocks the review use case).

**D-124 — Tests.** Unit: `TextDiffTest`, `RequirementComparisonTest`, `RequirementMatcherTest`. API:
`RuleVersionDiffSeedTest` (seeded Colorado revision: one CHANGED CO-5 with `TEXT` and `NUMERIC`, eight UNCHANGED;
default `against`, explicit `against`, reverse direction, 400 without a superseded version, 400 across jurisdictions, 404
for another tenant, 401) and `RuleVersionDiffApiTest` (renumbered, edited, added and removed requirements on unapproved
lists, title-only change, 400 across jurisdiction and license type, malformed `against`, tenant isolation including the
`against` parameter, 401). The seed test loads a copy of `seed/manifest.yml` with its own tenant name and email domain
(symlinks to the shared `rules/`, `pdf/` and `fake-llm.yml`), because `SeedLoaderTest` asserts that its load is not
skipped and the two classes share one database container in any order.

**D-125 — Dependency pin.** `io.github.java-diff-utils:java-diff-utils:4.18`, the `latest` and `release` entry in
`https://repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/maven-metadata.xml` on 2026-11-26 (last updated
2026-10-18). jar sha1 `65e544c565591a97fd6a02150eb294e7e0a426ed`, no transitive dependencies. Declared in
`backend/pom.xml` through the `java-diff-utils.version` property, next to the other pins. API used, read from the jar with
`javap`: `com.github.difflib.DiffUtils.diff(List, List)` (default algorithm `MyersDiff`, verified in the static
initializer), `Patch.getDeltas()`, `AbstractDelta.getType()/getSource()/getTarget()` and `Chunk.getPosition()/getLines()/size()`.
Not used: `DiffRowGenerator`, `UnifiedDiffUtils`, `patch`/`unpatch`. Licence: Apache-2.0 (parent pom); the jar's only
dependencies are test-scoped.
Rejected: Google `diff-match-patch` (character oriented, not the library the spec names), an older java-diff-utils (no
reason to pin behind the latest stable).

### F4a — Evidence requests API (D-130 to D-139)

**D-130 — A response is attached to the mapping; it never edits it.** `evidence_response` is a new append-only
table. The mapping's `finding`, `method`, `rationale` and `evidence_span_id` stay exactly as the system proposed
them (product rules 1 and 5). The response only adds a verified span and a `REOPENED` review event, which makes
the requirement "needs review" again (D-003); the reviewer then decides.
Rejected: replacing the mapping's span with the human-supplied one (rewrites the system's proposal and loses
what the reviewer originally saw); a new `EVIDENCE_PROVIDED` review kind (the schema CHECK and the UI's reading
of review events would both change, and the spec fixes `REOPENED`).

**D-131 — A person's quote passes the model's verifier, through one shared service.** The document, page and
quote checks that `ProgramFactService` already did are extracted into `assessment.ProgramCitationService`, which
both program facts and evidence responses call. It also rejects `RULE_TEXT` documents with their own message
(they have no program, so they previously fell under "different program"). Spans are inserted only after the
verifier accepts, inside the caller's transaction, so a later failure stores nothing.
Rejected: copying the checks into the new service (two places to keep in step for product rule 2); putting the
helper in `document` (it needs `EvidenceSpanRepository`, which lives in `assessment`).

**D-132 — At most one response per request (`UNIQUE (review_event_id)`).** The spec table has no such
constraint; it follows from the loop: a response reopens the mapping, so no open request remains and a second
answer needs a new request. The constraint makes the rule hold under concurrent submissions: the second insert
waits for the first to commit and then fails with 409 ("conflicts with existing data").
Rejected: locking the mapping row (more code, same effect); allowing several responses to one request (the
state model has nothing to show for it).

**D-133 — ANSWERED is derived, not stored.** Rows come from the latest review event of the mapping (D-003):
OPEN when it is `EVIDENCE_REQUESTED`; ANSWERED when it is a `REOPENED` that carries a user and the latest
response answers the latest request. People cannot send `REOPENED` (`ReviewService`) and system reopenings
(`RerunChangeHandler`, course changes) have no user, so a `REOPENED` with a user can only come from a response.
A system reopening after an answer is neither OPEN nor ANSWERED: the requirement is back in normal review.
Rejected: a status column on the request (review events are immutable and a request is an event, so it would
need a second table or an update); marking the reopening event by note text (fragile).

**D-134 — Response count and latest response are per mapping, not per request.** After a second request the
row is OPEN again but still shows the earlier answer, which is what a contributor needs to see before answering
again. The list is the latest finished assessment's current mappings only (D-066, `MappingRepository.CURRENT`
is now public and reused), newest request first, in one query with lateral joins (no N+1).
Rejected: counting only responses to the latest request (always 0 or 1 under D-132, no information).

**D-135 — Audit entity is the mapping.** `EVIDENCE_PROVIDED` is recorded on `mapping` like `REVIEW_RECORDED`, so a
mapping's audit trail reads in one place; the response id is in the detail with requirement ref, jurisdiction,
document id, page and the verified quote. The actor is the contributor (the REOPENED event also carries them as
`user_id`, note `Evidence provided by <display name>`).
Rejected: entity `evidence_response` (splits one requirement's history over two entities).

**D-136 — Checks run in the spec's order and use the mapping's current state.** 404, stale 409, signed 409
(`DeterminationRepository.hasSigned`), no open request 409, then the 400s of the citation. The open request is
read from the newest review event (`ReviewRepository.latest`), the same source as the mapping's decision, and
its id is stored as `review_event_id`.

**D-137 — Roles.** POST is open to all four roles as specified (so only unauthenticated callers are refused);
both GETs follow the other read endpoints (any authenticated user of the tenant). Cross-tenant ids are 404
through row-level security, as everywhere else.

**D-138 — Migration count test.** `MigrationsApplyCleanlyTest` now compares applied migrations with the
`db/migration/V*.sql` files on the classpath instead of a hard-coded number, because V006 belongs to another
work item and the version numbers have a gap. Applied count is 6 here (V001 to V005 and V007); it is 7 once V006
merges, with no test change.

### F6 — Production packaging and observability (D-140 to D-149)

**D-140 — One image, three stages, exact pins.** `Dockerfile` at the repository root: `node:26.13.1-bookworm-slim`
builds the Angular production bundle (Node 26.13.1 is the D-002 pin); `eclipse-temurin:21.0.13_9-jdk-noble` builds
the jar through the Maven wrapper with the bundle copied to `src/main/resources/static` and extracts Spring Boot's
layers (`-Djarmode=tools extract --layers`); `eclipse-temurin:21.0.13_9-jre-noble` is the runtime. Digests (checked
2026-11-26) are in `docs/OPERATIONS.md`. The runtime image holds the layered jar and nothing else: no source, no
tests (`.dockerignore` also keeps `backend/src/test` out of the build context), no Maven or npm cache (they live in
the build stages), no secrets. It runs as uid 10001, files and temporary files are under `/var/lexvay`
(`LEXVAY_FILESTORE_PATH`, `-Djava.io.tmpdir`, `-XX:-UsePerfData`) so `read_only: true` works, and
`ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]` keeps the JVM as PID 1 so SIGTERM reaches it.
`HEALTHCHECK` calls `/readyz`. Rejected: Alpine runtime (musl, not tried: PDFBox and POI font handling is the likely place
for a surprise, and the size saving was not measured or asked for), Spring Boot buildpacks (`bootBuildImage`
needs a daemon socket in CI and hides the pins), a fat jar run as is (no layer reuse: every code change would
re-ship 126 MB of libraries), `jlink` runtime (smaller, but a custom module list to keep in step with PDFBox, POI,
the AWS SDK and the Anthropic SDK; revisit if size matters), BuildKit cache mounts (the Colima setup on this
machine has no buildx; the build stages are discarded anyway).

**D-141 — The backend serves the app.** `SpaResourceConfig` registers two resource handlers over
`classpath:/static/`. Files with a content hash (`/*.js`, `/*.css`, `/media/**`; Angular's `outputHashing: all`)
get `Cache-Control: max-age=31536000, public, immutable`. Everything else is `no-cache` (revalidate with the
validators), so a new release is picked up at once. A GET that matches no file falls back to `index.html` when the
last path segment has no extension and the path is not under `/api` or `/actuator` (`SpaRoutes`); `/api/...`
therefore stays a 404 problem detail, and `/missing.js` stays a 404. The resource chain is not cached
(`resourceChain(false)`) because a cache keyed by path would grow with every probed URL. Security: GET requests
outside `/api` and `/actuator` are public (the sign-in screen has to load before anyone can sign in; the bundle holds
no data); every other method and every service path keeps requiring authentication. One line in `SecurityConfig`
(`requestMatchers(SpaRoutes.APP_REQUESTS).permitAll()`). Rejected: a `@Controller` forwarding a fixed list of
patterns (Spring's path patterns cannot express "any depth, no dot"), a 404 handler that forwards (it hides real
missing files from the API's error handling), nginx in front (a second process and image for a static folder).

**D-142 — Management port and health.** `management.server.port` is `LEXVAY_MANAGEMENT_PORT` (default 8090).
Spring Boot moves every actuator endpoint to that port; there is no property that keeps `/actuator/health` on the
main one (additional health paths accept a single segment). The Boot-supported way to keep health on the main port
is `management.endpoint.health.probes.add-additional-paths`, so the main port serves `/livez` and `/readyz`, public,
and the container `HEALTHCHECK`, `scripts/e2e.sh` and `HealthEndpointTest` use `/readyz`. The readiness group is
`readinessState,db`, so an instance that cannot reach PostgreSQL stops being ready. The management port exposes only
`health` and `prometheus` (`management.endpoints.web.exposure.include`). `/actuator/health` on the main port is
therefore not served (404). **This departs from the roadmap's "health stays on the main port" in its literal
reading (the aggregate `/actuator/health`); the probes do stay on the main port.** The Spring Security chain applies
on the management port as well, so `ManagementSecurityConfig` adds one chain, `EndpointRequest.toAnyEndpoint()`
permitAll, which Spring Boot evaluates only in the management server's own context, and which is not even created
when the management port equals the main port (`@ConditionalOnManagementPort(DIFFERENT)`). Result, tested: the main
port answers 401 for `/actuator/prometheus`; the management port answers prometheus and health without a session and
401 for anything else. The seed profile sets the port to -1 so `make seed` cannot collide with a running app.
Rejected: a hand-written `/actuator/health` controller on the main port (duplicates what Boot provides), publishing
the management port to users (it is bound to 127.0.0.1 in compose and documented as private).

**D-143 — Request correlation.** `RequestIdFilter` runs before Spring Security (`HIGHEST_PRECEDENCE + 10`): it
accepts `X-Request-Id` only when it matches `[A-Za-z0-9-]{8,64}` (anything else, including log-injection attempts,
is replaced by a UUID), echoes it on the response and sets MDC `requestId`. MDC `tenantId` is set by
`TenantContext.runAs` (so web requests and job runs get it from one place) and `userId` by `TenantContextFilter`;
`JobRunner` sets `jobId`. `LogContext` owns the four keys and restores the previous value on close, so nothing leaks
between requests on a pooled thread. `ProblemDetailRequestId` is a `ResponseBodyAdvice` that adds `requestId` to every
`ProblemDetail` the MVC layer writes, whichever handler made it (including Spring's own 404 and 400 handlers); the two
problem details written by the security entry points call the same `stamp` helper. `ApiAccessLog` writes one INFO
line per `/api/**` request that reached a handler (method, path, status, duration; never the query string, which can
hold search text) so the ids are visible in logs without an error. Rejected: a servlet-filter access log (runs after
the security context is cleared, so tenant and user are gone), Micrometer tracing (not asked for; it brings trace
ids and propagation that nothing consumes yet).

**D-144 — Structured logs.** Profile `prod` sets `logging.structured.format.console: ecs` and
`logging.structured.ecs.service.name: lexvay` (Spring Boot's built-in formatter, no extra dependency). MDC entries
appear as top-level fields (`requestId`, `tenantId`, `userId`, `jobId`). Locally the normal pattern is kept and
`logging.pattern.correlation` adds `[requestId or jobId]`. The image sets `SPRING_PROFILES_ACTIVE=prod`. What is never
logged: document text, quotes, rule text, prompts, model output, emails. To make that true, `JobRunner` no longer
logs the failure message (it can carry model output or document text); the log has the job type, the attempt and the
root exception class, and the message is stored on the job row and audit entry as before.

**D-145 — Metrics.** `micrometer-registry-prometheus` (managed by the Boot BOM through `micrometer-bom` 1.17.3, on
`prometheus-metrics` 1.7.0). All meters are created in `LexvayMetrics`; call sites pass facts (enums, booleans,
durations). Names and tags are as the roadmap lists them: `lexvay.llm.calls` (purpose, model, outcome:
success | refused | error), `lexvay.llm.tokens` (direction: input | output), `lexvay.llm.duration`,
`lexvay.job.duration` (type, outcome: done | retried | failed), `lexvay.job.queue.depth` (gauge),
`lexvay.quote.verification` (result: verified | rejected), `lexvay.mapping.finding` (finding, method). Tag values are
enum names or fixed words; a test scrapes the endpoint and fails on any label outside that set, on UUID-shaped
values, and on any occurrence of an id, an email or quote text. The model call is wrapped by `LlmGateway`
(delegates to the configured `LlmClient`, times the call, reports success, refusal or error) and the two call sites
use it instead of the client; rejected: wrapping the `LlmClient` bean (the tests cast the bean to
`FakeLlmClient`) and metering inside `LlmCallRecorder` (it only sees successes). Mapping findings are counted after
the per-requirement transaction commits and only when the row was inserted, so a retried run does not double count.
The queue depth is `SELECT count(*) FROM job WHERE status = 'QUEUED'` under the worker flag, answered by
`job_claim_idx`, evaluated at scrape time. `lexvay.llm.duration` carries `purpose` (the roadmap listed no tags; added at review so extraction and
matching latencies are not blended).

**D-146 — Graceful shutdown.** `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase` and
`spring.task.scheduling.shutdown.await-termination(-period)` all take `LEXVAY_SHUTDOWN_GRACE` (default 30 s; compose
`stop_grace_period` is 40 s). `JobPoller` listens for `ContextClosedEvent` and stops claiming; `JobRunner.runWhile`
checks that flag between jobs, so the job in hand finishes (its transaction commits) and the scheduler, which now
waits for its task, joins. A job that outlives the grace period is released by the existing ten-minute re-claim rule
in `JobRepository.claim`. Rejected: interrupting the running job (leaves a half-written run; D-052 makes resuming
safe but wasteful), draining the whole queue on shutdown (unbounded).

**D-147 — Usage endpoint.** `GET /api/v1/assessments/{id}/usage`, any signed-in user of the tenant, returns calls,
input and output tokens, `modelTimeMs` (sum of call durations), `wallTimeMs` (the assessment's own run time, null
until it has finished), `estimatedCostUsd`, the `prices` used, and the same totals by purpose. One aggregate over
`llm_call` grouped by purpose; totals are summed in Java from those rows; the assessment is looked up first through
the existing tenant-scoped `AssessmentService.require`, so another tenant's id is a 404. Extraction calls have no
assessment id (D-047) and are not part of it. Prices are `lexvay.llm.price.*` from WS-EVAL
(`LEXVAY_LLM_PRICE_INPUT_PER_MTOK`, `..._OUTPUT_...`, default 0 meaning not set); with no price the cost is `null`,
not 0, the same rule as the evaluation report. Migration `V010__llm_call_assessment_index.sql` adds
`llm_call (assessment_id)` (the table had no index for this lookup; V006 and V008 are WS-PERF, V007 is F4a, V009 is F11).

**D-148 — Compose and smoke.** `docker compose --profile app up` adds the `app` service: read-only root filesystem,
`cap_drop: ALL`, `no-new-privileges`, a named volume for `/var/lexvay`, the management port published on 127.0.0.1
only. Configuration is `LEXVAY_*` variables with the same development defaults as the `db` service; nothing secret
is in the image or the repository. `scripts/smoke.sh` (`make smoke`, slot-aware like `e2e.sh`) builds the image,
seeds the demo tenants with the same image (profile `seed`, seed directory mounted read-only), starts the app and
checks: non-root user, read-only root, no source or caches in the image, the app shell on `/` and a deep link, cache
headers, sign-in through the container with the CSRF cookie flow, an API read, the 404 problem detail with its
request id, the usage endpoint, the scrape (including a failed job counted by outcome), ECS logs with request and job
ids, and a graceful stop. The e2e script now exports `LEXVAY_MANAGEMENT_PORT` as 8091 plus the slot (slot 7: 8098).

**D-149 — Test shell for the SPA.** `backend/src/test/resources/static/` holds a stand-in `index.html` and a hashed
asset so the fallback and cache rules are tested without a frontend build. Test sources are excluded from the image
build context, so the stand-in can never reach production. `AbstractDatabaseTest` sets the two price properties so
the usage tests have prices to compute with.

### F9 — Curriculum search API (D-150 to D-159)

**D-150 — Query syntax.** The reviewer's text goes to `websearch_to_tsquery('english', :q)` as a bound
parameter, so quoted phrases, `OR` and `-term` work and no input can raise a syntax error. Rejected:
`to_tsquery` with sanitising in Java (reimplements the parser and loses phrases and negation) and
`plainto_tsquery` (no operators). The requirement shortlist keeps `to_tsquery` with OR-ed words (D-051).
Decided by the CTO in the roadmap.

**D-151 — Ranking and headline cost.** Pages are ranked by `ts_rank_cd(tsv, query, 32)` (cover density,
normalised to 0..1 by rank/(rank+1)), ties by document creation time, page number and document id so the order
is stable. The query keeps the top `limit` rows in a subquery first; `ts_headline` is computed only in the outer
select over those rows (`MaxFragments=2, MaxWords=40, MinWords=15`). Rejected: headline in the same select as the
filter (PostgreSQL may evaluate it for every matching page before the cut).

**D-152 — No markup from the database.** `StartSel` and `StopSel` are U+0001 and U+0002. The server splits the
headline on them into `segments: [{text, match}]` and drops empty runs; the control characters never leave the
server and the client renders `text` as text. Page text is passed through `translate(text, U+0001 U+0002, '')`
before `ts_headline`, so a page that itself contained those characters (a hostile or damaged PDF) cannot forge a
match or unbalance the split. Rejected: `<b>` delimiters with HTML escaping (a second parser on the server and an
invitation to `innerHTML` on the client), and returning character offsets (the headline is a computed excerpt,
not the stored page text, so offsets would not point into anything the client holds).

**D-153 — One query shape for both uses of the seam.** `PassageSearch` gains
`searchWithHeadlines(programId, query, kind, courseVersionId, limit)` returning `PassageHit`.
`PostgresPassageSearch` builds both statements from one SQL template (the joins, tenant-program and non-rule
filters, ranking subquery, top-N cut and final ordering); the two `Mode` constants supply only the tsquery
function, the rank function and the outer projection (`h.text` for the matcher, `ts_headline(...)` for search).
The matcher's behaviour is unchanged (same words, same `ts_rank`; its order gains a final document-id tiebreak).
Rejected: a second method with its own copy of the joins, and switching the matcher to `ts_rank_cd` (would
change shortlists and therefore findings; out of scope for a read feature).

**D-154 — Where it lives.** `com.lexvay.search`: `SearchController`, `SearchService`, `SearchHit`, `Segment`,
`HeadlineSegments`. The passage SQL stays in the `assessment.match` seam, where the shared joins are. The service
calls `ProgramService.get` first so another tenant's (or an unknown) program is a 404 from row-level security, never
an empty list. The transaction is `@Transactional(readOnly = true)`: one transaction, one query, no audit row
(nothing is created or changed; reading is not audited anywhere else in the API either). Any authenticated role
may search.

**D-155 — Parameter rules.** `q` is required, not blank, at most 500 characters (an addition to the roadmap: a
bound ceiling on the work one request can ask the parser for). `limit` defaults to 20 and must be 1 to 50; a value
outside that range is a 400, not silently clamped, the same contract as `GET /audit`. `kind` is a `DocumentKind`;
`RULE_TEXT` is a 400 because rule text is not part of a program's documents. `courseVersionId` that matches
nothing returns an empty list. Rejected: clamping the limit (hides a client bug) and ignoring `kind=RULE_TEXT`
(an empty list would look like "nothing found").

**D-156 — Response shape.** A JSON array of rows `documentId, filename, kind, courseCode, courseRequired,
pageNumber, rank, segments`, best first. `courseCode` and `courseRequired` are null for documents without a
course. `rank` is the raw `ts_rank_cd` score as a number: comparable inside one response, not across queries.
Rejected: a wrapper with a total (counting all matches costs a second pass and the screen does not need it).

**D-157 — No new index or migration.** `document_page_tsv_idx` (GIN on `tsv`) already serves
`tsv @@ query`; `document_page` is also reached by the tenant policy's index. On the seeded tenant (24 pages)
`EXPLAIN ANALYZE` of the filter runs in 0.03 ms with a tenant index scan, and the measured p95 of the endpoint
is a few milliseconds (D-158), so no index would change anything. Migration numbering is untouched.

**D-158 — Latency check.** `perf/SearchLatencyTest` (tagged `perf`) runs 50 warm-up and then 200 timed requests
over six queries against a fresh tenant holding the real seed documents and asserts p95 at most 40 ms. No
`EndpointLatencyTest` exists on main and the build has no surefire exclusion, so it runs in the normal
suite. It measures MockMvc in process (controller, security, transaction, SQL, JSON), not the network.

**D-159 — Test data.** The search tests ingest the seed PDFs of the first demo university into a new tenant
through the upload API, using the course and document lists from `seed/manifest.yml`, instead of calling
`SeedLoader`. The loader creates the shared demo tenants once per database and `SeedLoaderTest` asserts that
first load, so a second test calling it would make that test depend on class order. The search tests need no
model and no scripts.

### F11 — OCR for scanned documents (D-160 to D-169)

**D-160 — Engine seam and selection.** `OcrEngine { boolean available(); Optional<OcrPage> recognize(BufferedImage) }`
with `NoOcrEngine` (the default, `LEXVAY_OCR=off`) and `TesseractCliOcrEngine` (`LEXVAY_OCR=tesseract`, binary
`LEXVAY_OCR_TESSERACT_PATH`, language `LEXVAY_OCR_LANGUAGE`). `OcrConfig` picks the bean like `FileStoreConfig` and
rejects an unknown value at start. Decided by the CTO. Rejected: a Java binding (JavaCPP or Tess4J: native
libraries inside the JVM, a crash takes the server down, a larger supply chain) and a cloud OCR service (student
and institutional documents leave the deployment; no network in tests).

**D-161 — The command line and its safety.** `tesseract <png> stdout -l <lang> --psm 3 tsv`, an argument array
(never a shell), a private temporary directory per call that is always deleted, standard error redirected to a file
in that directory, 60 s timeout then `destroyForcibly`, `OMP_THREAD_LIMIT=1` so concurrent runs do not oversubscribe
cores. The language is validated against `[A-Za-z0-9_]+(+...)*` so a configuration value cannot become an option.
`available()` runs `<binary> --version` once (10 s timeout) and caches the answer; a configured but missing binary
is logged once at WARN and behaves as "not enabled". Decided by the CTO; validation and the thread limit are
additions.

**D-162 — No page text in logs or messages.** Neither the engine nor the extractor logs recognised text. A
failure message carries the exit code and the first line of Tesseract's standard error (at most 200 characters),
which reports configuration problems such as a missing language file, not page content. `OcrFailedException` is
retryable (the job runner requeues it up to `lexvay.jobs.max-attempts`), because a timeout or a killed process can
be transient. `recognize` returning empty means "no words found", not "failed".

**D-163 — TSV parsing.** Recorded from a real run (`src/test/resources/ocr/tesseract-5.5.5-page.tsv`, 32 words).
Words are level-5 rows with `conf >= 0` and non-empty text; control characters are dropped from words (PostgreSQL
`text` rejects NUL); words of one (block, paragraph, line) are joined by a space, lines by a line feed, paragraphs by
a blank line. The mean confidence is the plain average over the words kept. Decided by the CTO.

**D-164 — Per-page ingest rule.** A PDF page keeps its text layer when it has at least 20 non-whitespace characters
(`TEXT_LAYER`). Any other page is rendered at 300 DPI in grayscale and read when the engine is available; the result
replaces the text layer only when it also has at least 20 non-whitespace characters (`OCR`, confidence rounded to two
decimals to fit `numeric(5,2)`). In every other case (no engine, or too little recognised) the page keeps its text
layer, however short, as `TEXT_LAYER`. The first version of the spec emptied such pages; the CTO changed it after
review because discarding extracted text silently loses data that the product could cite (a short heading, a figure
such as "60 credits"), and nothing is gained by it: the length threshold exists to decide when OCR is worth trying,
not to delete text. The "no extractable text" rejection therefore applies only when every page's text layer is
blank. A page whose image would exceed 25 megapixels is rendered at the largest DPI that stays under the cap.
`TextExtractor` became an injected component (engine and page cap) instead of a static utility; DOCX and TXT are
unchanged. Rejected: always running OCR and preferring the better text (slow, and a text layer is more reliable
than any recognition) and emptying thin pages (data loss).

**D-165 — Page cap and the "scanned" hint.** At most `lexvay.ocr.max-pages` (200) pages per document are sent to
OCR. The check counts the pages that need OCR and runs before any recognition, so an oversized upload fails at
once with `TooManyScannedPagesException`, a permanent job failure, and no CPU is spent. A document whose pages are
all empty still fails with "Document has no extractable text"; for a PDF whose engine is unavailable the message
continues ". It looks scanned; OCR is not enabled on this server". Decided by the CTO.

**D-166 — Schema (V009).** `document_page.text_source` (default `TEXT_LAYER`, `CHECK IN ('TEXT_LAYER','OCR')`),
`ocr_confidence numeric(5,2)` and `CHECK ((text_source = 'OCR') = (ocr_confidence IS NOT NULL))`, as specified;
one addition, `CHECK` that the confidence lies in 0..100. The default makes existing rows `TEXT_LAYER` without a
backfill. `document_page` stays immutable (V003 trigger); the columns are written only on insert. V006 and V008
remain reserved. The repositories read the confidence with `CAST(... AS double precision)` because the PostgreSQL
driver does not convert `numeric` to `Double`.

**D-167 — Product rule 4 for OCR pages.** Evidence cited from an OCR page with confidence below
`lexvay.matching.min-ocr-confidence` (80, exclusive: exactly 80 passes) cannot be `EVIDENCE_FOUND`; it becomes
`AMBIGUOUS` with "the cited page was read by OCR with NN % confidence; check the original". NN is rounded down so a
page under the threshold never displays as the threshold. The quote is verified against the stored OCR text exactly
as against a text layer (rule 2); a quote that is not in the OCR text is still `EVIDENCE_MISSING`. The threshold has
no `LEXVAY_*` alias on purpose; it is a safety setting.

**D-168 — Downgrade rules as an ordered list.** `DowngradeRule` is a functional interface returning an optional
reason for a passage and a verified quote; `DowngradeRule.ordered(minQuoteChars, minOcrConfidence)` lists
`notRequiredCourse`, `thinQuote`, `lowOcrConfidence`. `EvidenceMatcher` walks the list for an `EVIDENCE_FOUND`
proposal; every rule that applies appends " Downgraded to ambiguous: <reason>". This differs from the two `if`
blocks it replaces in one visible way: when two rules apply (an elective course and a thin quote) the rationale now
names both, where the second used to be hidden behind the first. The finding is the same either way. A fourth
rule is one more list entry.

**D-169 — Read models and audit.** `PageSummary` (document view) and `Mapping.Evidence` expose `textSource` and
`ocrConfidence` (null for text layers). `MappingRepository` joins `document_page` on (document, page number) to
fill them; `Passage` carries the same two fields from the one shared search template. `DOCUMENT_INGESTED` audit
detail gains `ocrPages`. Docker: no `Dockerfile` exists on `main` at the time of writing, so there is no runtime
stage to extend; whoever adds one must install Tesseract (`apt-get install tesseract-ocr`) and set
`LEXVAY_OCR=tesseract`.

**Content limits (after WS-SEC).** OCR goes through the existing `ContentLimits`: `TextExtractor.pdfPages` calls
`ContentLimits.requirePdfPages` (500 pages) before any text is read or any page is rendered, and DOCX still passes
`requireSafeDocx`. The OCR cap (`lexvay.ocr.max-pages`, 200) is not a parallel copy of that limit: it counts only the
pages that need recognition, which is where the CPU cost is. F11 adds no endpoint, so `InputValidationTableTest`
needs no new row (upload and document read are already covered).

### F12 — Rule source monitoring (D-180 to D-189)

**D-180 — One seam for the network, one for DNS; fakes are `@Primary` beans imported by the test base.** `HttpFetcher`
(`Fetched fetch(URI)`: status, content type, bytes) is the only code that opens a connection for the monitoring job.
`JdkHttpFetcher` is the production bean. `FakeHttpFetcher` (scripted by URL; a URL nobody scripted is a `FetchRejected`)
and `FakeHostResolver` (IP literals resolve to themselves, three named test hosts resolve to a public, a private and the
metadata address, anything else is unresolvable) are annotated `@Primary` and imported by `AbstractDatabaseTest`, so every
database test uses them and none needs the network or DNS. No Mockito. Because the fetcher is shared by all tests of a
JVM, each test uses URLs of its own (`AbstractRuleSourceTest.newUrl`). Rejected: `@MockitoBean` (the project uses scripted
fakes, like `FakeLlmClient`); a per-test `@TestConfiguration` (would give every test class its own Spring context).

**D-181 — `JdkHttpFetcher`.** `java.net.http.HttpClient`, connect timeout 10 s, and 30 s for the whole response, body included: the request timeout
alone covers only the wait for the headers, so the call is `sendAsync` with a 30 s `get` deadline that cancels the request.
Redirects are set to `NEVER` and followed by hand: at most 3, and the `UrlPolicy` runs before every hop, including the first. A
fourth redirect is a `FetchRejected`. The 20 MB
cap is a small `BodySubscriber` that cancels the download at the first byte over the cap (`BodyHandlers.limiting` does
not exist in the JDK 21 this project targets); a server that streams forever costs 20 MB of memory at most. A status of 400
or more is returned, not thrown, so the check can record it; connection failures, timeouts, the cap and a refused address are
`FetchRejected`. The job's database transaction stays open while the fetch runs (the job runner wraps every handler), so a
slow server holds one connection for up to about 40 seconds. That is acceptable at one check per source per interval and
was kept rather than redesigning `JobRunner`. The real fetcher is tested against a `com.sun.net.httpserver` server on
loopback with a permissive policy (the policy has its own tests), plus one test that the real policy refuses that server.

**D-182 — SSRF guard.** `UrlPolicy.requireAllowed(URI)` (production: `SsrfGuard`, over a `HostResolver`) refuses any scheme
but http and https, a missing host, a host that does not resolve, and a host with any resolved address that is: any-local
(`0.0.0.0`, `::`), loopback, link-local (this covers `169.254.169.254`), site-local (RFC 1918), multicast, "this network"
`0.0.0.0/8`, carrier-grade NAT `100.64.0.0/10`, IPv6 unique-local `fc00::/7`, or an IPv4-compatible IPv6 address whose
embedded IPv4 address is any of those (IPv4-mapped addresses are already IPv4 for the JDK). It runs at registration (a 400
problem detail, nothing stored) and on every hop at run time (a `FETCH_FAILED` check). A host that does not resolve is
refused at registration so a typo is caught at once. **Known residual risk:** the policy resolves the name, then the JDK client
resolves it again to connect, so a DNS record that changes between the two (DNS rebinding) is not caught. Closing it needs
the connection to be made to the checked address, which `java.net.http` does not allow without a JVM-wide resolver SPI. Deployments
should also deny egress to private ranges at the network level (infrastructure). Rejected: a blocklist of host names
(`localhost`), which is bypassed by any alias; checking only at registration.

**D-183 — HTML text, pagination and the content hash.** `FileType.HTML` exists for fetched content only: `FileType.detect`
(uploads) never returns it, so an HTML upload is still refused; `FileType.detectFetched(contentType, bytes)` decides for
fetched content (PDF and DOCX by leading bytes whatever the server says, HTML by `text/html` or a leading
`<!doctype html`/`<html`, TXT by `text/plain` and valid UTF-8, anything else is refused). `HtmlText` (jsoup) removes
`script, style, nav, header, footer`, makes every block element and `<br>` a line, collapses white space, drops blank lines,
and groups lines into pages of at most 4 000 characters at line boundaries; a line longer than a page is cut at the last
space that fits, or at 4 000 characters when it has none. The charset comes from the byte order mark or the meta tag (UTF-8
otherwise). The hash is SHA-256 of `QuoteVerifier.normalizeWhitespace` over the pages joined by a newline (the same join as the
offsets, D-041), using the existing `DocumentService.sha256`. Whitespace, markup, pagination and navigation changes leave it
unchanged; one changed word changes it. Hashing the downloaded bytes was rejected: timestamps and tokens in a page would
report a change at every check. **Disagreement with the spec text:** it says pagination works "exactly like the TXT path does
today", but the TXT path makes one page (D-017). I implemented the stated rule (4 000 characters, paragraph boundaries) and
left TXT alone; offsets, the newline join and the empty-document rule are shared with the TXT path.

**D-184 — The check job.** `CHECK_RULE_SOURCE` payload `{ruleSourceId}`. Every check writes one `rule_source_check` row and an
audit row `RULE_SOURCE_CHECKED` (system actor, job id in the detail), and sets `last_checked_at`.
- *Failure is a check, not a failed job.* A status outside 2xx, a `FetchRejected`, an unsupported content type, an empty or
  unparsable document each give `FETCH_FAILED` with the status (when there was one) and a message of at most 1 000
  characters. The job ends DONE. `last_checked_at` moves on a failure too, so a broken source is retried after its interval
  rather than at every scheduler poll (5 minutes), which would hammer the remote server and fill the table; a person can retry
  at once with `POST /rule-sources/{id}/check`. `last_content_hash` never moves on a failure.
- *UNCHANGED* when the hash equals `last_content_hash`. The first check has no hash, so it is always CHANGED.
- *Which version is superseded.* `RuleVersionRepository.findCurrentApproved`: approved for the jurisdiction and license type
  and not itself superseded by an approved version (latest `effective_from` first); none means the draft supersedes nothing
  and no change event is created. `findDraft` finds an unapproved version that supersedes that one (or, when there is none,
  one that supersedes nothing). A draft means `DRAFT_EXISTS` (the detail names it) and nothing is stored.
- *The baseline does not move on `DRAFT_EXISTS`.* The new text is not forgotten: once the waiting draft is approved the
  next check sees the difference and drafts it. The cost is one extra draft whose text may equal the just approved one when
  nothing changed again after the approval; it is unapproved and shows an empty diff. Moving the baseline instead would lose a
  rule change that arrived while a draft waited, which is the worse error for this product.
- *CHANGED.* The bytes are stored as a `RULE_TEXT` document with its pages (`DocumentService.storeRuleText`, extracted pages
  passed in so a large PDF is parsed once), then `RuleVersionService.createFromDocument` creates the version: label
  `<source label> fetched <yyyy-mm-dd>` (UTC date), `sourceUrl` the source URL, `effectiveFrom` the check date, `supersedesId` as
  above. Same validation, audit (`RULE_VERSION_CREATED`), change event, reopening of signed determinations and
  `EXTRACT_REQUIREMENTS` job as `POST /rule-versions`. Nothing approves anything.
- *Same bytes seen before.* Content this tenant already holds (a page that returned to an earlier text) is a
  `DuplicateDocumentException`; the handler reuses that document instead of failing. `storeRuleText` is deliberately not
  `@Transactional`, because an exception passing a transactional proxy would mark the job's transaction rollback-only.
- *System actor.* `CurrentUser.idOrNull()` replaces three copies of `CurrentUser.get().map(...).orElse(null)` and is used where a
  job now runs code written for a signed-in user: `RuleVersionService`, `DocumentService.storeRuleText`,
  `ChangeService.onRuleVersionSuperseded`. Their rows get `created_by` null, like other job-created rows.
- *Audit of the stored document.* A rule text stored through `POST /rule-versions` has no `DOCUMENT_UPLOADED` row (D-044); the
  fetched one is identical: the document id is in `RULE_VERSION_CREATED` and `RULE_SOURCE_CHECKED`.
`RuleVersionService.create` now delegates to the same private insert as `createFromDocument`; behaviour and error order of the
existing endpoint are unchanged.

**D-185 — Scheduler and the worker flag.** `RuleSourceScheduler` (`@ConditionalOnProperty lexvay.rule-sources.enabled=true`,
no `matchIfMissing`, so off by default; `LEXVAY_RULE_SOURCES_ENABLED`, `LEXVAY_RULE_SOURCES_POLL_INTERVAL_MS`, default 300 000)
calls `RuleSourceRepository.enqueueDue()`: in one transaction it sets the transaction-local `lexvay.worker` flag and runs one
`INSERT ... SELECT` for active sources that were never checked or whose `last_checked_at` is older than
`check_interval_hours`, skipping any with a `CHECK_RULE_SOURCE` job QUEUED or RUNNING. The manual path uses the same
statement with a different predicate (`rs.id = :id`, whether or not active or due), so "already queued or running" means the same
thing in both and the 409 comes from the insert returning no row. Cross-tenant queueing needs two policies the spec did not list;
V011 adds them: `worker_due_select` on `rule_source` (SELECT when the flag is on) and `worker_enqueue_insert` on `job` (INSERT
when the flag is on), next to the poller's existing job policies (D-013). Nothing else honours the flag; a tenant session
cannot set it (`Rule7_RuleSourceIsolationTest`). The scheduler class lives in `com.lexvay.rule.source`, not beside `JobPoller`
in `com.lexvay.job`: it needs `RuleSourceRepository`, and `rule.source` already depends on `job` (the handler), so the other
placement would create a package cycle. It carries its own `@EnableScheduling` because `JobPoller` is itself conditional.
Two instances of the service may both run it; the dedupe predicate keeps it to one job per source in the common case (a rare
duplicate is harmless: the second check is UNCHANGED).

**D-186 — API, roles, seed.** `POST /rule-sources` (ADMIN, REVIEWER; 201 `{id, jobId}`), `GET /rule-sources` (one statement
with a lateral join to the latest check), `GET /rule-sources/{id}/checks?limit=` (default 50, 1 to 200; 404 for an unknown
source, still one statement, by left-joining the checks to the source), `POST /rule-sources/{id}/check` (ADMIN, REVIEWER; 202
`{jobId}`; 409 when queued or running), `PUT /rule-sources/{id}/active` (ADMIN; 200 `{id, active}`). Audit actions:
`RULE_SOURCE_CREATED`, `RULE_SOURCE_CHECK_REQUESTED`, `RULE_SOURCE_ACTIVE_CHANGED`, `RULE_SOURCE_CHECKED` (by the job). A
manual check works on a deactivated source: deactivating stops the schedule, not a person's explicit request. Registration
validates the address with the policy; `RuleSourceService.register` (no address resolution, no first check) exists for the
seed only, because the seed address `example.invalid` cannot resolve and the demo must stay offline. The seed registers one
source for the one demo tenant (Northfield, Colorado) and none for the isolation fixture tenant (Westbrook has no rule
versions); `SeedManifest.Tenant` gets `ruleSources`, which the evaluation copy leaves out. Nothing queues a job, so the source stays
inert until a person asks or the scheduler is switched on.

**D-187 — Schema (V011).** As specified, with these additions: CHECK constraints on lengths, on `url` (`^https?://[^[:space:]]+$`,
at most 2 000 characters), on the hash format, on `http_status` (100 to 599), and two on `rule_source_check` so that only a
CHANGED check carries a document and a rule version and a CHANGED check carries both; `rule_source` has no DELETE (trigger,
`forbid_change`) because checks reference it. The label is limited to 150 characters so that the draft's label (plus
" fetched yyyy-mm-dd") stays under the 200 of a rule version. `rule_source_check` has no `created_by`: the job writes it and the
audit row names the job. Rejected: a unique constraint on (tenant, URL), which the spec does not ask for and which would turn
a harmless duplicate into a 409.

**D-188 — Tests.** `SsrfGuardTest` (every range and edge, no DNS), `JdkHttpFetcherTest` (loopback server: body, status,
redirects and their limit, per-hop policy, the 20 MB cap exactly, unreachable server), `HtmlTextTest`, `FileTypeTest`,
`CheckRuleSourceTest` (CHANGED, system audit, extraction without approval, first check, UNCHANGED, markup-only change,
DRAFT_EXISTS and its pickup after approval, document reuse, PDF, seven kinds of FETCH_FAILED with the job DONE, baseline kept
after a failure), `RuleSourceApiTest`, `RuleSourceEnqueueTest`, `RuleSourceSchedulerLoopTest` (scheduler enabled, its own
context, closed afterwards so no later test class has a scheduler running), `Rule7_RuleSourceIsolationTest`,
`Rule5_RuleSourceImmutabilityTest`, rows in `InputValidationTableTest` and `Rule9_AuditCoverageTest`, the seed assertion in
`SeedLoaderTest`.

**D-189 — Open points for the orchestrator.** (1) Pagination wording in the spec (D-183). (2) The scheduler's package (D-185).
(3) A failed check delays the retry by a full interval (D-184); say so if a shorter retry is wanted, it is a one-line change in
the due predicate plus a column. (4) DNS rebinding (D-182). (5) A frontend screen for sources is not part of this item.

### F7 — Retrieval quality (D-170 to D-179)

**D-170 — Requirement candidates are ranked by BM25 inside the one statement.** Query lexemes come from the database:
`tsvector_to_array(to_tsvector('english', title))` with boost 2.0 and the same for the text with boost 1.0; a lexeme in both takes
the larger boost (2.0), not the sum. `N`, `df(t)` and `avglen` are computed over the program's non-rule pages in common table
expressions of the same statement (the row-level security policy scopes them to the tenant); `len(p)` is `length(tsv)` (the number
of distinct lexemes, as the spec defines it); `tf(t,p)` is `cardinality(positions)` of `t` in the page's `tsv`. `k1 = 1.2`, `b = 0.75`,
`idf = ln(1 + (N - df + 0.5) / (df + 0.5))`, which is never negative. Ties break by document creation time, page number, document id as
before. To read positions without unnesting every lexeme of every page, the statement cuts each page's `tsvector` down to the query
lexemes with `ts_filter(setweight(tsv, 'A', lexemes), '{a}')` and unnests only that: 500 pages cost 6 ms this way against 26 ms for a
plain `unnest`. The candidates are the pages that contain at least one query lexeme, which is exactly the set the OR of the lexemes
matches; the OR itself is still built, as a `tsquery` cast from single-quoted lexemes (`'lexeme'` with quote and backslash doubled, joined
by ` | `, no stemming, no operators) in the same statement, and is what `ts_headline` gets (D-174). No tsquery is built from user text in
Java on this path. Rejected: scoring in Java (every candidate's text would cross the wire and a second statement would be needed for
`df`); the `pg_search` or ParadeDB extensions (a dependency the database image does not have); `ts_stat` (it takes its query as a
string, so it would have to be built with string concatenation); a plain `unnest` of all pages (4 times slower); `ts_rank_cd` with
normalization flags (no document frequency, which is the point).

**D-171 — The search takes the requirement's title and text separately.** `PassageSearch.search(programId, title, text, limit)` replaces
`search(programId, query, limit)` because a title lexeme counts double under BM25. The `ts_rank` baseline still searches
`title + " " + text` as before, so its behaviour is unchanged. Rejected: boosting by position in a concatenated string (it would be a
convention nobody could see in the type).

**D-172 — The ranker is a property.** `lexvay.matching.ranker` (`LEXVAY_MATCHING_RANKER`) is `bm25` (default) or `ts_rank`, read in
`PostgresPassageSearch` into `PassageSearch.Ranker`; any other value stops the application at start. A second overload takes the ranker
explicitly so the evaluation can put the same queries to both in one run. The reviewer search keeps `ts_rank_cd` and its code path is
unchanged apart from the shared statement. One SQL template serves all three: `Mode` now carries, besides the tsquery, the prelude (the BM25
common table expressions), an extra join, the match condition, the rank and the projection. Rejected: a second statement for BM25 (it
would have copied the filters, the ordering and the limit); choosing the ranker by a request parameter (a matching run must be reproducible
from configuration).

**D-173 — Latency budget and what it depends on.** One BM25 statement for a requirement on a synthetic 500-page program (pages of 2,000 to
3,500 characters, policy sentences repeated on every page, so nearly every page is a candidate), headlines of the long pages among the top
eight included: p50 9 ms and p95 15 ms against the 30 ms budget (`PassageSearchLatencyTest`, tag `perf`). The test runs `ANALYZE` as the
schema owner after loading, because autovacuum would have done it for a real program and without statistics the planner chose nested loops
that took p95 37 ms. No schema change was needed and none is planned: no per-page statistics column was added. Rejected: a stored
`len` and `tf` table (a migration, and the generated `tsv` already holds both).

**D-174 — Excerpts.** `PassageExcerpter` is pure. A page of at most 2,500 characters is sent whole. A longer page is sent as its header
(the last whole line that ends within the first 300 characters; a first line longer than that is cut at 300) plus the fragments of
`ts_headline` for the same lexeme query with `MaxFragments=3, MaxWords=120, MinWords=60, FragmentDelimiter=U+0003` and empty selectors.
PostgreSQL rejects `StartSel=, StopSel=`; an empty selector must be written `StartSel="", StopSel=""`, which is what the constant holds.
Each fragment is located in the stored page with `QuoteVerifier.find` (so differences in whitespace do not matter), widened to whole lines,
merged with anything it overlaps or touches (including the header), emitted in page order and separated by a line holding only `[...]`. If a
fragment cannot be located, or the headline is blank, the first 2,500 characters are sent instead; an excerpt that would not be shorter than
the page is the page. The headline is computed only for the (at most eight) shortlisted pages longer than 2,500 characters, in the outer select,
so it costs nothing for short pages. `Passage` gains `excerpt` (what the model sees) and `rank`; `text` stays the full page and the quote is
verified against it (rule 2 is untouched). Because every part is a verbatim slice of the page, a quote taken from within one part verifies; one
that spans `[...]` is not in the page and is discarded as "No verifiable passage found" (`ExcerptMatchingTest`). Rejected: cutting the header at
a fixed length (it would split lines and words); a model-written summary of the page (the model must never see text that is not the page's).

**D-175 — Prompt `match-v2`.** A new resource beside `match-v1`, which stays on disk. It says that a passage may be an excerpt, that `[...]` marks
omitted text, that absence from an excerpt is not absence from the document, and that a quote must lie within one continuous part and never
include or straddle the marker. Assessments record `match-v2`.

**D-176 — The retrieval corpus.** `eval/retrieval/docs` holds 13 added syllabi (COUN 701 to 765), each of four pages: description and outcomes,
a weekly schedule of 3,000 to 3,500 characters (so it is excerpted), assignments, and a policy page (academic integrity, accommodations,
grading scale, attendance, professional dispositions, nondiscrimination) that is word for word the same on every one. With the 14 seed documents
that makes 27. Their PDFs in `eval/retrieval/pdf` are made by `make seed-pdfs` (now generating both folders) and `RetrievalFileTest` fails when a
PDF no longer says what its markdown says. Everything is labelled fictional like the seed. `eval/retrieval/queries.yml` holds 35 queries:
15 in the words of a requirement against the added syllabi (four of them in the everyday words that the policy page also uses), 14 that the
demo seed's requirements put to the matcher, and 6 marked `lexicalGap: true` (for example "marital" against "couples", "telehealth" against
"secure videoconferencing"). Each names the expected PDF, page and a substring of the evidence; the test checks the substring is on that page of
the committed PDF. Lexical gaps are reported separately and never count toward a limit. Rejected: copying the seed documents into the retrieval
folder (they are read from `seed/pdf`, so there is one copy); a page-per-syllabus corpus (the excerpter would never run).

**D-177 — Metrics and limits.** `RetrievalScorer` (pure): recall@8 and recall@3 (the expected page is within that many of the shortlist), mean
reciprocal rank (`1/rank` within the first eight, 0 otherwise), and excerpt coverage (among the recalled queries, the share whose expected substring
is inside the excerpt that would be shown to the model). Computed for the `ts_rank` baseline and for BM25 over the same queries, the counted queries and the
lexical gaps apart. `eval/thresholds.yml` gains `retrieval: {minRecallAtDeep: 0.95, minExcerptCoverage: 1.0}`; `make eval` also fails when BM25's
recall@8 or recall@3 is below the baseline's. Mean reciprocal rank is reported and has no limit. The baseline is measured with excerpts too, so
that the two rankers differ in nothing but the ranking. The limits do not depend on the model, so they apply to `make eval` and `make eval-live` alike.

**D-178 — How the evaluation reaches the corpus.** `SeedLoader.loadCorpus` creates a fresh, uniquely named tenant with the demo program and uploads
the given PDFs through the same services as the seed (its private `upload` now takes a path instead of a seed directory and a file name).
`RetrievalService` loads the corpus and puts every query to the production `PassageSearch` once per ranker. `EvalRunner` runs it after the pipeline
evaluation, prints a second table, writes `docs/RETRIEVAL.md` (no date inside, so an unchanged result changes no line) and exits non-zero when either
evaluation breaks a limit. The existing pipeline metrics on the seed are unchanged.

**D-179 — One stack script.** `scripts/lib/stack.sh` holds what `scripts/e2e.sh` and `scripts/eval.sh` repeated: the slot arithmetic, the compose
project, the isolated file store, the cleanup trap, starting the database and packaging the backend. The e2e script keeps its ports and the seed and
Playwright steps; the evaluation script keeps its model switch. Both now also delete the temporary file store on exit.

### F13 — Work queue API (D-190 to D-199)

#### D-190 One statement per endpoint, role filtered in SQL

`GET /api/v1/work-queue` and `GET /api/v1/work-queue/count` each run exactly one SQL statement in one read-only
transaction (D-110; `ReadPathConnectionsTest` asserts one checkout). The statement is a shared CTE block
(`latest`, `current_mappings`, `affected`) and one `UNION ALL` branch per kind. The caller's role is bound as
`:role` and each branch carries its own visibility test (`CAST(:role AS text) IN (...)`), so a branch the caller may
not see is a constant-false filter and is not executed. Java does not branch on the role. The `ids` field is built
in SQL (`jsonb_build_object`, `array_agg(... ORDER BY ...)[1 : 20]`), never from a second query.

Rejected: a visibility table (`VALUES (kind, role)`) joined at the end (every branch would run for every role);
one query per kind (more connections or round trips); assembling `ids` in Java from extra queries (N+1).

#### D-191 What `count`, `counts` and `total` mean

`count` of an item is the number of records waiting: requirements, rule versions, change events, assessments, and 1
for a jurisdiction ready to sign. `counts[kind]` is the sum of the item counts of that kind and `total` of
`/work-queue/count` is the sum of all of them. Accepting one of three requirements therefore moves the badge.
`counts` omits kinds with nothing waiting (absent means zero); adding zero entries would need role knowledge in Java.

Rejected: `total` as the number of items (accepting one requirement of three would not move the badge); a `counts`
map with every visible kind (needs the role matrix duplicated in Java).

#### D-192 The shared predicate

`MappingRepository` now holds three public SQL constants: `LATEST_EVENT` (the latest review event of mapping `m` as
`le`, D-003), `NEEDS_DECISION` (never reviewed, or reopened) and `UNDECIDED` (`stale OR NEEDS_DECISION`, what blocks
signing). `countUndecided` and the queue both use them; `CURRENT` (D-066) is reused as is.
`REQUIREMENTS_TO_REVIEW` is `NOT stale AND NEEDS_DECISION` (stale mappings cannot be reviewed, the review endpoint
answers 409, so they wait under `CHANGES_OPEN`); `READY_TO_SIGN` uses `UNDECIDED`.
`ChangeEventRepository.AFFECTS_PROGRAM` is extracted the same way from `findOpenViewsForProgram` (behaviour
unchanged) and gives `CHANGES_OPEN` its affected programs.
Not touched: `AssessmentRepository.findingCounts` still has its own copy of the "decided" test; it can use
`LATEST_EVENT` and `UNDECIDED` in a follow-up.

#### D-193 READY_TO_SIGN follows the signing code, not the roadmap wording

`DeterminationService.sign` refuses unless: the jurisdiction is a target of the program; the program has a latest
FINISHED assessment (`latestFinished`, newest `finished_at`); that assessment has at least one current mapping for
the jurisdiction; none is undecided; and no SIGNED determination exists for the program and jurisdiction
(`hasSignedForProgram`, not `hasSigned(assessment, jurisdiction)` as the roadmap text says). The queue implements
exactly that, so it also hides a jurisdiction that is signed for an older assessment, which the signing endpoint
refuses too. An open evidence request counts as decided for the gate, and so for the queue. A test signs every
program and jurisdiction pair of a tenant and asserts 201 for exactly the pairs the queue lists and 409 for every
other, then asserts the items are gone.

#### D-194 Kind details

- `REQUIREMENTS_TO_REVIEW`: per program and jurisdiction; `oldestAt` is the earliest of the reopening time or the
  mapping's creation; `ids` = `assessmentId`, `mappingIds`.
- `EVIDENCE_REQUESTS_OPEN`: latest event is an evidence request, all roles. Stale ones are only counted in
  `ids.blockedCount`; a group with nothing answerable is not listed. `ids` = `assessmentId`, `mappingIds`,
  `blockedCount`. An answer reopens the mapping (D-003), so it moves from this kind to `REQUIREMENTS_TO_REVIEW`.
- `RULE_VERSIONS_TO_APPROVE` (REVIEWER, SIGNER) and `EXTRACTION_FAILED` (ADMIN, REVIEWER): per jurisdiction, program
  fields null, `ids.ruleVersionIds`. Extraction failure is "a FAILED `EXTRACT_REQUIREMENTS` job and no requirements".
  A rule version has one extraction job (a retry requeues the same row), so that equals the latest-job reading of
  `RuleVersionService`. The test uses the `(status, created_at)` index and filters the payload on the few failed jobs,
  because the payload expression index is unusable under row-level security (D-115).
- `CHANGES_OPEN` (REVIEWER, SIGNER, ADMIN): one item per change kind (the roadmap says "per kind"), program and
  jurisdiction null; `ids` = `changeKind`, `changeEventIds`, `programIds` (the programs the open events touch).
- `READY_TO_SIGN` (SIGNER): count 1; `oldestAt` is when the last decision made it ready; `ids.assessmentId`.
- `ASSESSMENTS_FAILED` (ADMIN): per program, `ids.assessmentIds`. Literal to the roadmap: a failed assessment stays
  listed after a later run succeeds (see the report: recommended refinement).
Order: kind order, then `oldestAt` ascending, then program name and jurisdiction.

#### D-195 Latest assessment

All program-scoped kinds read each program's latest FINISHED assessment, found with a lateral `LIMIT 1` over
`assessment_program_status_finished_idx`, the same row `latestFinished` returns.

#### D-196 Performance and indexes

Both endpoints are in `EndpointLatencyTest` (40 ms p95 budget) and `ReadPathConnectionsTest`. `IndexUsageTest` runs
`EXPLAIN (ANALYZE, BUFFERS)` of the exact statement (`WorkQueueRepository.LIST`) as the application role for every
role on about 200 tenants with synthetic review history (`PerfSeed.addHistoryVolume` now adds review events: without
them `review_event` is empty and the planner scans it) and fails on a sequential scan of `mapping`, `review_event`
or `job`. None occurs, so no migration: `V014__work_queue_indexes.sql` is not created. Numbers and plan in
`docs/PERF.md`.

#### D-197 Slice syntax

The id cap is written `[1 : 20]` with spaces because Spring reads `:20` as a named parameter.

#### D-198 Roles and audit

Read-only and open to every signed-in user; the SQL decides what each role sees. Nothing is added to
`Rule9_AuditCoverageTest` (no mutation). `InputValidationTableTest` has a 200 row (hostile query parameter ignored)
and a 401 row (no session) for each endpoint; its harness gained an anonymous actor.

#### D-199 Known cost of `CURRENT`

`MappingRepository.CURRENT` joins every mapping of the assessment to every mapping again per mapping (the planner
starts from `nm`), so it is quadratic in the mappings of one assessment. At the seeded size it costs well under
1 ms; at several hundred mappings per assessment it is the first place to look. Not changed here because it is
shared by every mapping query.

### F3 — Rule workspace screens (D-210 to D-219)

**D-210 — Route, rail entry and one screen instance.** `programs/:programId/rules` and
`programs/:programId/rules/:ruleVersionId` are one route (`rulesMatcher` in `app.routes.ts`), so choosing a version
keeps the same `Rules` instance and the keyboard focus in the list; the version id arrives as the `ruleVersionId`
input. The screen is its own lazy chunk. The rail shows "Rules" after Changes with `phosphorScales` and a badge
whose screen-reader text is "n rule versions waiting for approval". The shell now moves focus to the `h1` only
when the screen changes (the path up to `programs/:id/<screen>`), not when a version id changes inside Rules.
Rejected: two routes (Angular rebuilds the component between them and focus is lost on every selection); a
`?version=` query parameter (the spec fixes the path).

**D-211 — One status function.** `ruleVersionStatus(view, all)` in `core/rules.ts` is the only derivation, used by
the list, the detail header and the shell badge (`versionsWaitingForApproval`, through `ProgramContext.rulesWaiting`).
Precedence: Superseded (another version's `supersedesId` names it, approved or not, matching the moment the server
opens the change event) → Approved → Needs approval (requirements exist, whatever the extraction job did, because a
reviewer may enter the list by hand after a failed extraction) → Extracting (job QUEUED or RUNNING) → Extraction
failed (job FAILED). An unapproved version with no requirements and no failed or running job (not produced by the
current server) shows Needs approval so it can still be corrected. Each status has its own Phosphor icon and word;
the Tag colour only repeats them. The badge counts Needs approval versions with at least one requirement.

**D-212 — Grouping and ordering.** Groups follow the program's target jurisdictions in the program's order, then the
other jurisdictions by code; inside a group the newest effective date first, then the newest entry. Groups with no
version are not shown. The list is a PrimeNG Listbox with groups (`role="listbox"`, `aria-selected`, arrow keys,
Enter or Space to open). `metaKeySelection` keeps a click on the selected version from clearing it; the option's
accessible name carries code, label, effective date and status; the Listbox's hard-coded `aria-multiselectable="true"`
is overridden to `false` through pass-through (D-087 style). Rejected: a plain `role="list"` with `aria-selected`
(invalid ARIA; axe reports `aria-allowed-attr`); selecting on focus (every arrow key would fetch a version).

**D-213 — Edit grid as a plain model.** `RequirementListEditor` (signals, no injection, Vitest tests) holds the rows
with client keys, add, move, delete, update, `dirty` (content or order differs from the server list), restore and the
PUT body (`toDrafts`, snake case, numeric fields sent only for numeric types and kept on the row otherwise). The
table is the same PrimeNG Table with every cell a control in place (`rowTrackBy` on the key so a row keeps its DOM
and focus while it changes); no `editMode="row"`. "Edit list" enters editing; "Save list" (the form's submit) and
"Discard" leave it. "Approve requirements" is hidden while editing and while extracting. The grid has a
`min-width` and scrolls horizontally inside its focusable table container at narrow widths (axe
`scrollable-region-focusable`). Each control's `aria-label` names the ref (or "Row n" while the ref is empty) and
the column.

**D-214 — Server problems mapped to rows.** `requirementProblems(detail, rows)` splits the parser's
"Requirement list is invalid: …; …" detail on "; ", places `requirements[i]` messages under the row that was at
position i when sent (by key, so the message follows the row if it is moved), places a message that only quotes a
ref under the row with that ref, and shows everything else above the table. No client-side copy of the parser's
rules: the server's words are the single source. A 409 (approved meanwhile) is shown as a Message and the list and
versions reload. The new-version dialog checks only required fields, the URL scheme and the 500 000 character limit
before sending, and maps POST problem details onto a field by the words they contain (`problemField`).

**D-215 — Word diffs.** `lx-diff-text` renders `TextDiff` segments as `ins` (underline, `found` tokens) and `del`
(line-through, `missing` tokens), plus visually hidden "[inserted: … ]" and "[deleted: … ]" markers so a screen
reader hears the change. Title, type and numeric-check changes are shown as a deleted old value and an inserted new
value with a Phosphor arrow and the hidden word "becomes". Compare rows are ordered Changed, Added, Removed,
Unchanged, keeping the server's order within each status. The rule text diff is in a PrimeNG Accordion panel.

**D-216 — Polling.** While `anyExtracting(versions)` is true and the document is visible, `GET /rule-versions` is
fetched every 5 s through `ProgramContext.refreshRuleVersions` (the same signal feeds the badge). The effect re-arms
on every answer and is cleaned up when nothing extracts or the tab is hidden; there is no polling otherwise.

**D-217 — Rule text from the WS-PERF page endpoint.** When this item started, `origin/main` had only
`GET /documents/{id}`, whose page summaries carry no text, so the fallback named in the spec could not show the
rule text. WS-PERF merged during the work; the tab uses `GET /documents/{documentId}/pages/{n}` (page text, page
count) with previous and next page buttons. No backend change was needed. When F1's `DocumentViewer` merges, this
tab should use it (read-only) instead of its own frame.

**D-218 — Shared styles and PrimeNG additions.** The document frame (`.lx-document*`), the status line under a page
header, the spinner animation and the three-line clamp (`lxClamp` directive, used for long requirement text with a
"Show all" control) live in `styles.css`; Review and Changes now use the shared copies. The preset adds the Aura token
sets of Listbox, DatePicker, InputNumber and Accordion (D-082); the initial bundle grows by about 14 kB raw to
693.8 kB, still under the 700 kB warning.

**D-219 — End-to-end scenario adapted to the seed and the fake model.** The spec's scenario assumes the seeded
Colorado revision is unapproved and that FakeLlmClient extracts a version created in the browser. Neither holds on
`main`: the seed approves the revision (`SeedLoader.createApprovedRuleVersion`), and the e2e backend's
FakeLlmClient has no scripts (they are loaded only in the seed process), so an extraction started from the UI fails
after its three attempts. `e2e/rules.spec.ts` therefore checks the approved revision read-only (no edit controls,
the Compare tab's single CO-5 change with `ins`/`del` and the numeric change, the rule text), has a contributor
enter a Nevada version and see Extracting then Extraction failed, and has a reviewer enter that list by hand, hit a
server row error, save, edit a title, reload, and approve it through the dialog, then find the audit entries. Nevada
is not a program jurisdiction, so no assessment or change event used by the other suites is touched. Making the
spec's exact scenario possible needs an orchestrator decision: leave the seeded revision unapproved, or script
FakeLlmClient at runtime from `seed/fake-llm.yml` in the e2e profile.

### F1 — Evidence viewer with in-context highlight (D-220 to D-229)

**D-220 — One `DocumentViewer`, its state in signals, its logic in plain functions.** The component lives in
`frontend/src/app/shared/document-viewer/` (standalone, inputs `documentId`, `pageNumber`, `highlight`, `selectable`,
output `selection`). The page on screen is a `linkedSignal` of the inputs, so previous and next move away from the cited
page and any new input brings it back; the page is an `rxResource` over `DocumentPageCache`, so a newer page request
cancels an older one. Everything that can be wrong without a browser is a plain function in `page-text.ts` with Vitest
tests: `splitForHighlight(text, start, end)`, `rangeOffsets(container, range)` and `ocrNote(page)`. Rejected: a store
service per viewer (state that dies with the component belongs in it); testing the offset logic through the component
(slow and indirect).

**D-221 — `GET /documents/{id}/file`.** One statement (`DocumentRepository.findById`, the existing row lookup,
now also used by `GET /documents/{id}`) and one file store read. `Content-Type` comes from the stored file type, which is
derived from the stored file name exactly as ingest does (`FileType.fromFilename`; `FileType` now carries its media
type). The spec says "from the stored kind", but `DocumentKind` (SYLLABUS, HANDBOOK, ...) says nothing about the format;
the file type is the stored fact that does. `Content-Disposition` is built by Spring's `ContentDisposition` with UTF-8,
which writes the plain `filename="..."` and the RFC 5987 `filename*=UTF-8''...`. `Cache-Control` is the controller's
existing `IMMUTABLE_PRIVATE` constant and the ETag is `"<document sha256>"`, both applied through one helper shared
with the page endpoint. `If-None-Match` is checked with `WebRequest.checkNotModified` before the store is read, so a
revalidation costs one statement and no file read; the 304 carries the same ETag and Cache-Control. Any authenticated
role may read, another tenant gets 404 from row-level security, it is a GET so it is not audited
(`Rule9_AuditCoverageTest` unchanged), it has three rows in `InputValidationTableTest` and a row in
`ReadPathConnectionsTest` (one connection). The body is a `byte[]` (bounded by the 25 MB upload limit) rather than a
stream because `FileStore` has only `get`; adding a streaming method to both stores was not worth it at that size.
Rejected: letting `HttpEntityMethodProcessor` answer the 304 from the response entity (it would read the file first);
`attachment` (the browser should show the PDF).

**D-222 — The highlight.** One `mark` element between two text nodes, all produced by Angular interpolation inside the
`pre` (never `innerHTML`, never a search in the browser: the server verified the quote, the viewer shows the stored
offsets). The `pre` keeps the stored whitespace (`white-space: pre-wrap`), and the template puts no whitespace of its own
inside it. The mark is styled with the warning tint (`--p-lx-ambiguous-bg`) and a 2 px underline in the warning colour
(`--p-lx-ambiguous`), so it does not depend on colour alone; it has `tabindex="-1"` and a visible focus ring. When the
offsets do not fit the page (`splitForHighlight` returns null) the page is shown unmarked with the warning
"The stored quote position does not match this page".

**D-223 — Scrolling and focus.** The page text scrolls inside the frame (`.lx-document-body`, focusable and named
"Page text" so keyboard users can scroll it and axe's scrollable-region rule holds), so the Evidence header and the
"Quote only" disclosure stay in place. After the cited page renders, and whenever the cited span changes, the mark is
scrolled into view with `block: 'center'`. Focus moves to the mark only "when the viewer was opened for that quote":
this needs a fifth input, `focusHighlight`, because a viewer cannot tell whether it was opened on request. Review sets
it below 1024 px, where the viewer exists only after "Show on page"; in the three-pane layout the viewer is always on
screen and taking focus would pull it out of the requirement list and the jurisdiction tabs on every change. When a
page button becomes disabled at the first or last page while it has focus, focus moves to the other page button
instead of falling to the document body. The page position ("Page n of N") is a polite status, so paging is announced.

**D-224 — `DocumentPageCache`.** `frontend/src/app/core/document-page-cache.ts`, one per app. A `Map` keyed
`documentId:pageNumber` in least-recently-used order (a hit is moved to the end, the first key is evicted past 40). The
stored value is the observable itself with `shareReplay({ bufferSize: 1, refCount: false })`, so a request in flight is
shared by returning the same observable. A failed request is removed so the next ask retries. Rejected: caching values
and a separate in-flight map (two structures for one job); `refCount: true` (an unsubscribed viewer would cancel the
request another viewer is waiting for).

**D-225 — Passage selection.** With `selectable`, releasing the pointer or a key over the page text measures the
selection (`rangeOffsets`: the length of `Range.toString()` from the start of the `pre` to each boundary, so crossing
the mark, element boundary points and stored whitespace all count exactly) and takes the quote from the stored text by
those offsets, not from the DOM. Read literally, the spec emits `selection` when the person selects and again when they
click "Use this passage"; that is what is built (a repeat of the same offsets is not re-emitted). A selection that is
collapsed or reaches outside the page text is ignored. The toolbar sits inside the scrolling page body so it moves
with its text; Escape or a selection that leaves the page removes it. The keyboard alternative is a PrimeNG Dialog with
two InputNumber fields (start and end offset, end exclusive, both limited to the page length) that shows the resulting
quote live and emits the same event. Because no screen uses `selectable` yet, the end-to-end test drives it on a harness
route, `programs/:programId/dev/document-viewer`, registered only when `ngDevMode` is set; production builds define
`ngDevMode` as false, so the route and its chunk are removed (checked: the production bundle does not contain it).

**D-226 — Review layout.** The Evidence pane keeps its header as a definition list (file, course with required or
elective, page, characters), then the elective warning, then "Quote only" (collapsed), then the viewer. The disclosure
is the native `details`/`summary` with a Phosphor caret: PrimeNG's toggleable Panel renders its title and its toggle
button with the same id, which its content region references, and Accordion is a multi-section widget. Below 1024 px a
PrimeNG Button "Show on page" (`aria-expanded`, `aria-controls` on a container that always exists) shows the viewer;
the viewer collapses again when the selected requirement changes (`linkedSignal` on the selection). In the
three-pane layout the viewer fills the rest of the pane (at least 16 rem). The document frame styles moved from
`review.css` into the viewer; the review keeps only its header list and disclosure styles.

**D-227 — Overview endpoint.** `ProgramContext` loads `GET /programs/{id}/overview` (program, latest finished
assessment, determinations) beside programs, change events, jurisdictions and rule versions: five parallel requests
instead of seven plus a dependent assessment request. `refreshAfterChange` uses it too. `ApiService.latestAssessmentId`
and the program lookup it replaced are deleted; `audit()` remains for the Audit trail screen. The Changes badge still
counts all open change events of the tenant, as before; scoping it to the program (the overview's `openChangeEvents`)
is a behaviour change outside this item.

**D-228 — OCR pages (F11 merged first).** The page view now carries `textSource` and `ocrConfidence` from the same
statement, and the viewer header says "Read by OCR, NN % confidence" (rounded, warning colour with an icon) for a page
whose text came from OCR. The spec said the WS-PERF `PageView` is used as is; the two fields were added because the
coordinator asked for the OCR note in the viewer header and the viewer can page to pages the evidence does not
describe.

**D-229 — No new dependency; tokens and icons.** No npm package was added. The InputNumber token set was added to the
preset (D-082) and five Phosphor icons were registered (caret left and right, cursor text, eye, quotes). The initial
bundle is 677.5 kB raw (budget 700 kB warning).

### FIX-1 — Requirement order column (D-230 to D-239)

**D-230 — The order of a rule's requirements is stored, as `requirement.ordinal`.** The rows of one rule version
are inserted in one transaction and share `created_at`, so the old order `created_at, ref` was the text order of
the ref (`CO-10` before `CO-2`). `ordinal integer NOT NULL` is the position in the list the extraction or the
reviewer produced, counted from 1, with `UNIQUE (rule_version_id, ordinal)`. That constraint's index also serves
`ORDER BY ordinal` within a version, so no extra index is created. The existing `UNIQUE (rule_version_id, ref)`
stays. Rejected: sorting refs naturally in SQL or Java (refs are free text from the rule, and the reviewer may
order them as the rule does); `ORDER BY id` (random uuids).

**D-231 — Migration `V012__requirement_ordinal.sql`.** In the migration: `NO FORCE ROW LEVEL SECURITY` and
`DISABLE TRIGGER requirement_locked` on `requirement`, `ADD COLUMN ordinal integer`, backfill, `SET NOT NULL`,
the unique constraint, then the trigger is enabled and `FORCE ROW LEVEL SECURITY` restored. All of it runs in
Flyway's transaction for the migration, so a failure leaves neither the lock nor the policy weakened. The
exemption is needed because the backfill must touch rows of every tenant and of approved rule versions: the
owner role is otherwise bound by `FORCE`, and the trigger rejects any update of an approved list. Product rule 5
is unaffected: no existing value changes, and the new column is derived from the existing order. V006 stays
unused; V009 to V011 are reserved by branches that merge before this one (D-074).

**D-232 — Backfill expression.** `row_number() OVER (PARTITION BY rule_version_id ORDER BY created_at,
(substring(ref from '(\d+)$'))::int NULLS LAST, ref)`: creation time first, then the number at the end of the
ref compared as a number, then the ref itself; refs with no trailing number sort last. For the rows that exist
today (one transaction per version) this is the numeric order of the refs, which is the order the extraction
returned them in.

**D-233 — The backfill is proven by running the migration's own statement.** Flyway applies all migrations
before the application starts, so a test cannot load rows and then migrate. `RequirementOrdinalBackfillTest`
creates a temporary table named `requirement` (it shadows the real table for that connection only, and is
dropped in a `finally` because the pool reuses connections), reads `V012__requirement_ordinal.sql` from the
classpath, runs its `UPDATE` unchanged and asserts the result: shuffled `CO-1` to `CO-12` with a shared
timestamp, refs without a number last (ordered by ref), an earlier `created_at` first, numbering restarting at 1
per version. Rejected: copying the expression into the test (it would drift from the migration).

**D-234 — Writing the order.** `RuleVersionRepository.insertRequirements(tenantId, ruleVersionId, drafts,
createdBy)` replaces the single-row `insertRequirement`; it is the only writer and assigns `ordinal` = list
position from 1. Both paths use it: `ExtractRequirementsHandler` (model output order) and
`RuleVersionService.replaceRequirements` (submitted order, after the delete of the unapproved list).
`RequirementDraft` stays a plain record: the list carries the order, not each draft.

**D-235 — Reading the order.** `Requirement` gains `ordinal` (last component), so the requirements endpoint and
the diff's `before`/`after` expose it. `RuleVersionRepository.requirements` orders by `ordinal`;
`MappingRepository` orders by `r.ordinal` (`j.code, r.ordinal` for the all-jurisdictions list). Nothing else
orders requirements. The mapping's own `RequirementView` is unchanged: mappings arrive in order and do not need
the number.

**D-236 — The diff follows the rule's own order.** `RequirementMatcher` already emits pairs in "the newer
version's order, removed ones last in the older version's order" (D-122); that order is now the list order of
each version because the repository returns it. No change to the matcher, `RuleVersionDiffService` or the
response shape. The D-122 remark that the order is "effectively ref text order" no longer applies.

**D-237 — Tests.** `RequirementOrderApiTest`: twelve requirements `CO-1` to `CO-12` through the requirements
endpoint (with `ordinal` 1 to 12), the mappings endpoint and the diff (all UNCHANGED, newer order); replace
keeps a submitted order that is neither numeric nor text order (`CO-3`, `CO-1`, `CO-10`), numbers it from 1, and
approval does not reorder. `RequirementOrdinalBackfillTest` as D-233. `MigrationsApplyCleanlyTest` counts files
and needed no change. The `Rule5_*` tests are unchanged; `SchemaFixtures` now supplies `ordinal` in its direct
`INSERT INTO requirement`. `AssessmentFixtures` gains `ruleVersion` (create and extract, unapproved) and
`approvedRuleVersion` calls it, so the creation request exists once. `InputValidationTableTest` is untouched: no
endpoint was added.

**D-238 — Compatibility.** The PUT body and the extraction schema are unchanged: clients send the list in the
order they want and receive `ordinal` in addition. The frontend needs no change; F3 may use `ordinal` instead of
a natural sort of the ref.

### OPS-1 — Image build and smoke in CI (D-240 to D-244)

**D-240 — A third job, `image`, after `test`.** `.github/workflows/ci.yml` gains `image` with `needs: test`, so an
image is only built for a change whose suites already passed. It runs on the same triggers (pull requests and pushes
to `main`), inherits the workflow's `concurrency` group (a newer commit cancels the older run, image job included),
runs on `ubuntu-latest` and has `timeout-minutes: 30` like `test`. The smoke script fails the job on the first
failed check (`set -e` and `fail`).

**D-241 — Action pins.** `docker/setup-buildx-action@v4` and `docker/build-push-action@v7`, pinned by major tag like
the other actions in the file. Latest releases verified from the GitHub API on 2026-11-26: setup-buildx-action
v4.4.1 (published 2026-09-16), build-push-action v7.4.0 (published 2026-09-15). A major tag follows minor releases
by design; to move to a new major, change the tag and re-read the action's release notes.

**D-242 — One build, tagged by commit, never pushed.** `build-push-action` builds the root `Dockerfile` with
`load: true` (the image lands in the runner's Docker daemon), `push: false`, tag `lexvay:ci-<full sha>`,
`cache-from: type=gha` and `cache-to: type=gha,mode=max` (every stage is cached, including the Node and Maven
stages that never reach the final image). `build-args: LEXVAY_PRIMEUI_LICENSE=` passes an empty license key: CI has
no key and the app then shows its license notice (Dockerfile comment; D-140).

**D-243 — `scripts/smoke.sh` tests an image it is given.** `scripts/smoke.sh <image-tag>` runs against an existing
image and builds nothing, so CI tests exactly the cached build above and not a second, uncached one; without an
argument it builds `lexvay:local` through compose as before (`make smoke`). `docker-compose.yml` reads the image name
from `LEXVAY_IMAGE` (default `lexvay:local`). The script has no Colima dependency: it uses whatever daemon the
`docker` CLI already points at (`DOCKER_HOST` when set, otherwise the default socket). The slot, the PostgreSQL port,
the compose project (`lexvay-smoke-<slot>`), the cleanup trap and the temporary directory come from
`scripts/lib/stack.sh`. That file did not exist on `main`; it is the same file F7 (PR #17) adds, copied unchanged
except for the header comment, so F7 merges onto it without a conflict in the code. `scripts/e2e.sh` and
`scripts/eval.sh` adopt it in F7. Slot 0 is free on a CI runner (ports 5433, 8081, 8091).

**D-244 — Image size in the job summary.** A step after the build writes one line to `$GITHUB_STEP_SUMMARY`:
`Image lexvay:ci-<sha>: <n> MiB`, taken from `docker image inspect --format '{{.Size}}'`. `docker image ls` has no
content-size field on the runner's Docker (the field exists only with the containerd image store), and `inspect`
reports the same number the smoke script already prints, so one definition is used everywhere. On the containerd
store this is the compressed content size (227 MB locally, matching `docs/OPERATIONS.md`); on a classic store it is
the unpacked size, so compare numbers from the same environment only. The first CI run's value is recorded in
`PROGRESS.md (OPS-1)`.

### F3b — Rule workspace review fixes (amends D-213, D-217, D-218; adds D-245, D-246)

**D-213 (amended) — Edit grid as two-line form rows.** Each requirement is two rows of the same PrimeNG Table body:
line one has ref (mono), title (flexible), type, the numeric check controls when the type is numeric, and the move
and delete icon buttons; line two has the text Textarea across the full width, with the row's server error directly
under it. Each control has a visible label and keeps its `aria-label` naming the ref and the column. A container query
on the form lays line one out by the pane's own width. At 740 px or more it is one line, with a fixed check column
so the fields of every row line up. From 480 to 739 px it is two lines: ref, title and actions, then type and check.
Under 480 px each field stacks, which covers every window under 640 px. The table has no `min-width` and no scroll
container, and the e2e test checks it at 1024, 1280 and 1440 px. In a 1440 px window the pane is about 760 px wide,
so the title field is the narrowest part (about 7 rem) of a numeric row. Rejected: a horizontally scrolling grid (the
first review); one cell per column (does not fit the 676 px pane at 1280 px).

**D-217 (amended) — Rule text through DocumentViewer.** The Rule text tab renders `lx-document-viewer` (F1)
read-only with no highlight, on page 1 of the version's document. F3's own `rule-text.ts` is deleted, so there is
one way to render a page. Pages come through `DocumentPageCache`.

**D-218 (amended) — Extraction failure in plain words.** The detail header says "Requirement extraction did not
produce a list. A reviewer can enter the list by hand with Edit list." The stored job error sits in mono inside a
native `details` element, "Technical detail", collapsed by default. The version list row shows only the
*Extraction failed* Tag. The option's accessible name never contained the error.

**D-245 — Unsaved-changes guard.** The Rules screen provides a screen-scoped `UnsavedChanges` signal, which the
Requirements tab sets while it is editing and the list is dirty. The route's `canDeactivate` (`confirmLeave` in
`app.routes.ts`, typed by a `ConfirmsLeave` interface so the routes do not import the lazy screen) asks the screen.
`leaveNeedsConfirmation(dirty, currentUrl, nextUrl)` is pure and unit-tested. It asks only when edits are unsaved and
the path changes. The screen's route serves every version, so choosing another version also goes through the guard.
The question is a PrimeNG ConfirmDialog: "Discard unsaved changes to this list?", with "Discard changes" (danger,
outlined) and "Keep editing" (primary, focused by default). ConfirmDialog does not pass `closeAriaLabel` to its
dialog, so the close button is named "Close and keep editing" through pass-through. When the person keeps editing,
the Listbox selection goes back to the current version through a `linkedSignal`. `beforeunload` calls
`preventDefault()` while edits are unsaved, so a reload or tab close gets the browser's own question.

**D-246 — Tokens of Rules-only components load with the Rules chunk.** Accordion, ConfirmDialog, DatePicker and
Listbox are rendered only by the Rules screen. Their Aura token sets move out of `LexvayPreset` and are merged into
the active preset with `updatePreset` from `@primeuix/themes` when the Rules component is first constructed
(`rules-theme.ts`), before any of those components exists. The initial bundle goes from 704.6 kB to 698.9 kB raw,
under the 700 kB warning (D-083). InputNumber stays in the preset because F1's DocumentViewer uses it on Review too.
Rejected: `@defer` around the edit grid's dialogs and the new-version dialog. They are already in the lazy `rules`
chunk, so deferring them does not change the initial bundle; the initial bundle carries the preset, not the dialogs.
