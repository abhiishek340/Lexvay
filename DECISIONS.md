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

**D-002 addendum (UI2-0).** `@fontsource-variable/inter` 5.3.0 (published 2026-07-19) and
`@fontsource-variable/source-serif-4` 5.3.1 (published 2026-10-11), both the registry's `latest` on 2026-11-27 and more
than 14 days old; `@fontsource/ibm-plex-sans` and `@fontsource/ibm-plex-serif` removed, `@fontsource/ibm-plex-mono`
5.3.0 kept (docs/decisions/UI2-0.md, D-301).

**D-300 — Design system v2 (owner directive, 2026-11-26).** The owner judged the v1 interface not good enough and asked
for a world-class one: clean, few words, animated, impressive on first sight. `docs/DESIGN.md` v1 is replaced by v2:
one focal point per screen, a word budget with explanations behind InfoTips, depth instead of hairlines, a motion
system on native CSS, View Transitions and the Web Animations API (no `@angular/animations`, deprecated since
Angular 20.2), a US tile-grid StateMap as the product's visual identity, Inter and Source Serif 4 replacing IBM Plex
Sans and Serif, and a command palette. PrimeNG stays for behaviour and accessibility and is restyled completely.
Rejected: switching component library (every screen would be rebuilt for behaviour as well as looks, and PrimeNG's
accessible behaviour is what we keep); an animation library (native platform features cover every moment in the
motion table at no bundle cost). Work items UI2-0 to UI2-4 with id ranges D-301 to D-359.

**D-074 — Per-item decision files.** In CTO mode each work item records its decisions in
`docs/decisions/<item>.md` with a reserved id range, so parallel branches never edit the same lines here.
This file keeps the cross-cutting decisions and this index: WS-UI D-080–089, WS-SEC D-090–099, WS-EVAL
D-100–109, WS-PERF D-110–119, F3a D-120–129, F4a D-130–139, F6 D-140–149, F9 D-150–159, F11 D-160–169,
F7 D-170–179, F12 D-180–189, F13 D-190–199, FIX-2 D-200–209, F3 D-210–219, F1 D-220–229, FIX-1 D-230–239, OPS-1 D-240–244, F3b D-245–249, F7c D-250–259, F4 D-260–269, F8 D-270–279, TEST-1 D-280–284, F2 D-285–294, PERF-FE-1 D-295–299, design v2 D-300, UI2-0 D-301–319, UI2-1 D-320–329, UI2-2 D-330–339, UI2-3 D-340–349, UI2-4 D-350–359, FIX-3 D-360–369, F5a D-370–379, FIX-4 D-380–389, F10a D-390–399, SEC-2 D-400–409, FIX-5 D-410–415, F10b D-416–425, F5b D-426–435, FIX-6 D-436–441, F12b D-442–449, FIX-7 D-450–452, F14 D-453–462, INFRA-2 D-463–470, FIX-8 D-471–474, design v3 D-479, UI3-0 D-480–499, UI3-1 D-500–509, UI3-2 D-510–519, UI3-3 D-520–529, SEC-3 D-530–537, F15 D-540–549, real-data track D-550, RG-1 D-551–559, MODE-1 D-560–565, RG-2 D-566–575, RG-3 D-576–585, AC-1 D-586–595, AC-2 D-596–605, AC-3 D-606–615, EV-1 D-616–620, EV-2 D-621–630, UX-1 D-631–640, OPS-2 D-641–643, OPS-3 D-644–646, OPS-2b D-647 (supersedes D-642's mkdir mutex: the mutex is a noclobber file). Once an item merges its file is folded into the section at the end of this file and deleted. Migration number V006 is intentionally unused (D-073 workflow); V008 WS-PERF, V009 F11, V010 F6, V011 F12, V012 FIX-1, V013 FIX-2, V014 F7c (F13 needed no migration), V015 F5a, V016 FIX-4, V017 FIX-6, V018 F14, V020 F15, V022 MODE-1, then by merge order: SEC-3 takes V023 (it was V019 before V020 and V022 merged), RG-1 renumbers from V021 at merge, AC-1 V024, later items take the next free number at merge (SEC-2 needed none: it found `audit_log_newest_idx` already in V001). Numbers follow merge order; a branch renumbers before merge if a lower number is still unmerged.

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

### TEST-1 — Scripted fake model in the end-to-end backend (D-280 to D-284)

**D-280 — `lexvay.llm.fake-scripts` loads fake-llm.yml files at startup.** The property is a list of paths, empty by
default (`LEXVAY_LLM_FAKE_SCRIPTS`, comma-separated, in `.env.example` and `application.yml`). `FakeLlmStartupScripts`
(a component in `com.lexvay.seed`, `@PostConstruct`) reads the files in order through `ScriptedFakeModel`, the loader
`SeedRunner` uses: `ScriptedFakeModel.load` now takes a list of files, the seed's single-directory overload delegates
to it, and `FakeLlmScripts.overriddenBy` merges the files (a key in both takes the later file; a file may hold only
`extraction` or only `match`). There is no second parser. The loader scripts every extraction at once
(`scriptAllExtractions`, which calls `beforeExtraction` per label, so `replaceScript` keeps a repeated load from
queueing an earlier answer). It does not script `match`: a match script names a document by file and needs the
document's id, which exists only after the seed has uploaded it, so matching at startup stays the seed's job. With any
provider other than `fake` the property is ignored and one warning is logged (the model's id is in it); an unknown
provider still fails startup in `LlmConfig`. Nothing sets the property in profile `prod`; it is active there only if an
operator sets it. A missing file fails startup naming the path.

**D-281 — The e2e backend scripts the seed file and a test-only fixture.** `scripts/e2e.sh` exports
`LEXVAY_LLM_FAKE_SCRIPTS=seed/fake-llm.yml,frontend/e2e/fixtures/fake-llm.e2e.yml` (the paths are relative to the
repository root, where the script runs the backend). The seed process inherits the variable too; its own
`beforeExtraction` replaces each script for its labels, so the result is the same. The fixture holds the extraction for
"Demo rule set - Nevada (fictional) 2027": NV-1 total credits >= 60 (numeric), NV-2 ethics, NV-3 human growth, NV-4 a
jurisdiction-specific course (fictional). Its header says it is fictional and test-only.

**D-282 — The rules spec keeps both extraction outcomes.** The contributor test enters the Nevada version and ends with
it at *Needs approval* and its four requirements listed (contributors see no edit or approve buttons). A separate test
enters "Demo rule set - Oregon (fictional) 2027", which has no script, and covers *Extracting* then *Extraction failed*,
the plain-words message and the collapsed technical detail. The reviewer test enters the Oregon list by hand (OR-1,
OR-2) and approves it. Nevada stays at *Needs approval*, so the navigation badge reads "2 rule versions waiting for
approval" after the reviewer saves the Oregon list and "1 rule version" after approving it. The *Extracting* state is
asserted only for Oregon: a scripted extraction can finish within one poll interval, but a failing one retries for
several seconds. Both axe checks stay (extracting, failed) and one is added for the Nevada *Needs approval* state.

**D-283 — Tests.** `FakeLlmStartupScriptsTest` (no database, no network): two files are scripted in order with the later
file winning a shared label; loading one file twice answers with the script once; no or blank paths script nothing; a
missing file fails naming it; an unknown provider fails startup (`ApplicationContextRunner` with `LlmConfig`); a client
that is not the fake one is never called, the properties are ignored and exactly one warning is logged (`anthropic`
provider through the same runner, and a stub client that throws if called); `ScriptedFakeModel.load` refuses a client
that is not the fake one.

**D-284 — Rejected.** Scripting `match` at startup by resolving document names through the database (couples the
startup path to tenant data; the e2e tests that run an assessment use seeded results). A `lexvay.llm.fake-scripts`
profile file `application-e2e.yml` instead of an environment variable (the e2e stack is configured by variables in
`scripts/e2e.sh` everywhere else, and the roadmap's "profile e2e" does not exist in this repository).

### F8 — Determination register (D-270 to D-279)

**D-270 — Package `com.lexvay.determination.register`.** The register reads determinations, programs, jurisdictions,
rule versions, users and the audit log, and nothing depends on it; the signing package (`determination`) stays about
signing. A subpackage keeps the dependency one way (`register` uses `Outcome` and `DeterminationStatus`, never the
reverse) and gets its own controller, service, repository and records. It is added to the JaCoCo package rule (85 %
lines) next to `com.lexvay.determination`. Rejected: a `RegisterRepository` inside `determination/` (the package would
mix the write path that product rule 6 locks with a read model that joins five other tables).

**D-271 — Register read: one statement, filters only when set.** `GET /api/v1/determinations/register` runs one SELECT
over `determination` joined to program, jurisdiction, rule version and signer, with the course version count as a
correlated `count(*)` on the `determination_course_version` primary key (no N+1). Optional filters become predicates
only when present, through the new `common/WhereClause` (extracted from `AuditRepository`'s private `filterBy`, which
now uses it too; D-116 reasoning: one plan per filter shape). `current` defaults to true (`status = 'SIGNED'`); false
drops the status predicate so REOPENED rows appear with the outcome and versions they were signed with. Order: program
name, program id (two programs may share a name; without it their rows would interleave), jurisdiction code, signed
time descending, id. Unknown or foreign program ids give an empty list, as for any filter that matches nothing. The
service method is `@Transactional(readOnly = true)`; the endpoint is in `ReadPathConnectionsTest` (two filter shapes)
and `EndpointLatencyTest` (all signed, and one program with reopened ones). Validation: `@Pattern("^[A-Z]{2}$")` on
`jurisdiction`, enum binding on `outcome`, UUID binding on `programId`, boolean on `current`; ten rows in
`InputValidationTableTest` (both register endpoints) plus two for the lists.

**D-272 — Program lists: one statement, the partition decided only by what was signed.**
`GET /api/v1/programs/{id}/determination-lists` is one SELECT from `program` LEFT JOIN its target jurisdictions, with
two `LATERAL` subqueries on `determination (program_id, jurisdiction_id, status)` (index from V008): the current SIGNED
determination (newest if, against the service's rule, there were two), and only when there is none the newest
REOPENED one. `DeterminationLists.of` places each target: MEETS → `determinedToMeet`, DOES_NOT_MEET →
`determinedNotToMeet`, NOT_DETERMINED → `noDeterminationMade` with `SIGNED_NO_DETERMINATION` (and the signature
fields), no current signature → `noDeterminationMade` with `NO_CURRENT_SIGNATURE` (and `reopenedDeterminationId`
when one exists). Targets come from `program_jurisdiction`, so a non-target never appears; every target appears
exactly once (tested on a fixture with each case, including a re-signature after the reopening, and on the seed).
Two additions to the spec's entry, both needed by point 4: `reopenedAt`, because the screen must say "Reopened on
<date> after a change" and the determination row keeps no reopening time; it is the `created_at` of the
determination's `DETERMINATION_REOPENED` audit entry (written in the same transaction as the status change, found
through `audit_log_entity_idx`). `program` is a summary (`id, name, licenseType, catalogYear`) read in the same
statement rather than the full `Program` from `ProgramService.get`, which would be a second statement.
`generatedAt` is the statement's `now()`, the transaction start, so it names the snapshot the lists describe. An
unknown or foreign program is 404. Rejected: reading the reopening time from `change_event` (it has to be re-joined
through rule or course versions, as `ChangeEventRepository` does, to find which determination it reopened); a
`reopened_at` column (a schema change outside the spec).

**D-273 — `CsvWriter` and the export format.** `common/CsvWriter` is the one CSV writer: RFC 4180 with every field
quoted, quotes doubled, CRLF after every record (including the last), UTF-8 without a byte order mark (the spec asks
for UTF-8; a BOM would be the first bytes of the header), null as an empty field, and a `'` before any field that
starts with `=`, `+`, `-`, `@`, tab or carriage return. Unit tests: quoting, CRLF, formula prefix, null, non-ASCII.
Columns follow point 1 in order with human labels; outcome and status values are the signing dialog's words
("Determined to meet", "Reopened"), which now live once in the backend on `Outcome.label()` and
`DeterminationStatus.label()`; `ReportService` uses `Outcome.label()` instead of its own switch. Times are ISO-8601 as
stored (UTC). `Content-Type: text/csv;charset=UTF-8`, `Content-Disposition` built with Spring's `ContentDisposition`
and the server's UTC date. The CSV is built in memory from the register query (bounded by programs times
jurisdictions). Rejected: streaming (no size that needs it, and the audit row must carry the row count).

**D-274 — The CSV export is audited although it is a GET.** Handing signed records out of the system is an action
someone is accountable for (product rule 9: who, when, what), while reading them on screen is not audited, like every
other read. `RegisterService.exportCsv` is `@Transactional` (read and audit in one transaction, one connection) and
records `REGISTER_EXPORTED` with entity type `determination_register`, no entity id (an export is not one record),
and the detail `{ programId?, jurisdiction?, outcome?, current, rowCount }`. `Rule9_AuditCoverageTest` gains
`AUDITED_READ_ENDPOINTS` (GET endpoints that must stay audited, each with the test asserting its row) and a test that
each still exists; the mutating-endpoint guard is unchanged. The audit screen labels the action "Determination
register exported". Rejected: a POST that returns the file (a download link is the natural browser action and keeps
the CSV a plain file download).

**D-275 — Screen, route and rail.** `/register` is a top-level route inside the shell with `authGuard`, its own lazy
chunk. The rail is shown on program screens and on `/register`; its first item, set apart by a hairline, is
"Determination register" (`phosphorBookBookmark`), then the program's screens. On `/register` those link to the
program last worked on (`ProgramContext.program()`), or are absent when none was opened yet, so the register never
needs a program. Every rail item, the register included, is one `NavLink` with its router path. Two PrimeNG Tabs: Register and By program. Register
filters (program, jurisdiction, outcome Selects; "Include reopened" ToggleSwitch with a visible label) refetch from
the server, the latest request wins; the table sorts client-side on every column but the hash; outcome and status are
Tags with icon and word (`OUTCOME_ICON`, `STATUS_LABEL`, `STATUS_ICON` added to `labels.ts` next to `OUTCOME_LABEL`);
the hash shows 12 characters in mono with the full value as title and a "Copy versions hash" button (toast "Copied");
clicking a row opens `/programs/<id>/report?jurisdiction=<code>`, and the jurisdiction name is a link to the same
place so the action is keyboard operable. "Download CSV" is a link to `registerCsvUrl(filter)`. By program defaults to
the program last worked on, else the first; each list is a short table with an empty-state sentence; rows without a
current signature span signer, date and rule version with "No current signed determination" (and "Reopened on <date>
after a change"); signed "no determination made" rows keep their signature and add "Signed as no determination
made". View model: `core/register.ts` (`registerQuery`, `registerCsvUrl`) and `screens/register/register-view.ts`
(`disclosureSections`), both with Vitest specs.

**D-276 — Print.** The shell's own stylesheet hides the skip link, top bar and rail under `@media print` and lets the
page flow (no fixed-height scroll container); the fictional-data notice stays, black on white with a border. This
applies to every screen: navigation is never worth printing. `styles.css` sets `color-scheme: light` for print, so
every `light-dark()` token resolves to its light value whatever the theme, gives the body white, and hides the toast.
The register hides filters, tab list, buttons and sort icons, sets all its text black on a transparent background,
keeps table rows whole (`break-inside: avoid`), and shows a print-only header with the tenant name, the program (the
open list's program, or the register's program filter, or "All programs") and "Generated <date time>" (the lists'
`generatedAt`; for the register tab, when its rows arrived). The disclosure paragraph is printed.

**D-277 — Lazy theme and icons; bundle.** The ToggleSwitch is new and only the register renders it, so its token set
joins the active preset from the register chunk (`screens/register/register-theme.ts`, `updatePreset` on first use),
the pattern `rules-theme.ts` set for the Rules screen, with Lexvay colours over Aura's: the track is the form-control
colour unchecked and primary checked; the handle is the card colour on the grey track and the on-primary colour on the
primary track, so track against card and handle against track reach 3:1 in both themes. `lexvay-preset.ts` exports its
token object (`lexvayTokens`) for that. Likewise `phosphorCopy` and `phosphorPrinter`, shown only by the register, are
provided by the component (`viewProviders: [provideIcons(...)]`) instead of the global registry; `phosphorBookBookmark`
(the rail) is global. The disclosure view model lives in the screen's folder (`register-view.ts`); only the query
helpers used by `ApiService` are in `core/register.ts`. No new npm dependency. Initial bundle: 703.1 kB raw, 169.4 kB
estimated transfer; `origin/main` at 05fd4ce measures 698.9 kB, so this item adds 4.2 kB (the rail entry and the
shell's print rules, one icon, labels and the query helpers) and crosses the 700 kB warning (error 800 kB). The budget
needs an orchestrator decision; this item does not change it.

**D-278 — The register end-to-end spec runs after the suite.** The suite shares one seeded stack and a jurisdiction
can be signed once; `review-and-sign.spec.ts` already reviews and signs California ("Determined not to meet") and
asserts every step of it. Point 5 asks `register.spec.ts` to sign California too, which the two specs cannot both
do. `playwright.config.ts` now has two projects: `suite` (every spec but the register's) and `register`, which
depends on `suite`, so the register spec always runs afterwards and reads that signature; the remaining four targets
are then unsigned, which is what it asserts under "No determination made". Rejected: signing in `register.spec.ts`
when California is still unsigned (each spec's outcome would depend on which ran first); changing
`review-and-sign.spec.ts` to sign another state (another work item's test, and its assertions are about California).

**D-279 — Seeded test tenant and `SeedCopy`.** The "partition on seeded data" test loads the real seed's Northfield
tenant under its own name and email domain, the approach `RuleVersionDiffSeedTest` already used; the copying moved to
the shared test helper `seed/SeedCopy` and both tests use it (the follow-up asking for one shared seeded fixture
still stands). The test checks the five targets before any signature (all `NO_CURRENT_SIGNATURE`), signs the first
jurisdiction whose mappings are all current, and checks the partition again and the register row.

### FIX-2 — Job entity id column and index (D-200 to D-209)

**D-200 — `job.entity_id uuid NOT NULL` holds the record a job works on.** Every job payload carried exactly one
id (`documentId`, `ruleVersionId`, `assessmentId`, `changeEventId`, `reportId`, and `ruleSourceId` from F12), and
the lookups compared that value with `payload->>'key'`. That comparison is not leakproof, and the policies of `job`
have an OR branch for the worker flag (V002, V011), so the planner could not use an expression index below them and
scanned the whole table, which only grows because jobs are never deleted (D-115). `entity_id = :id` is leakproof.
It is a stored column written by the enqueuer, not a generated column over the payload: a generated column would
need the key name per job type, which is the string key this item removes. Rejected: one column per record kind
(six nullable columns for one value).

**D-201 — Migration `V013__job_entity_id.sql`.** `NO FORCE ROW LEVEL SECURITY` on `job`, `ADD COLUMN entity_id uuid`,
backfill, `SET NOT NULL`, `FORCE ROW LEVEL SECURITY`, then the index; all in Flyway's transaction, so a failure
leaves neither the policy weakened nor the column half filled (the pattern of V012, D-231). The exemption is needed
because the backfill touches rows of every tenant and the owner is otherwise bound by `FORCE`. The backfill is
`(SELECT value::uuid FROM jsonb_each_text(payload) LIMIT 1)` for rows where `jsonb_typeof(payload) = 'object'`. A
job whose payload is not an object of one uuid value is left NULL and `SET NOT NULL` fails the migration loudly;
no such row can exist, because every enqueue path put one id in the payload object. The job triggers and policies
of V002, V003 and V011 are unchanged.

**D-202 — The index is `job_entity_idx (type, entity_id, created_at DESC)`, without `tenant_id` (orchestrator
decision, supersedes the key in the roadmap).** The roadmap specified `(tenant_id, type, entity_id, created_at
DESC)`. The implementation of FIX-2 measured it and the orchestrator accepted the change. Under the policy OR
branch (V002, V011) `tenant_id = current_tenant()` is a filter, not an index condition, so an index that leads with
`tenant_id` is walked with a skip scan over every tenant, and its cost grows with the number of tenants. Entity ids
are random uuids, so `tenant_id` adds no selectivity to the key. Measured on the `IndexUsageTest` database (about 200
tenants, 1304 pages of `job`), shared buffers of one ingest status lookup as the application role under row-level
security: 1304 for the sequential scan it replaces, 636 with the tenant-leading key, 21 to 25 with this key.
`IndexUsageTest` now records the buffers of each job lookup and fails above 100, so a key that walks every tenant
cannot return unnoticed. The tenant condition is still applied, by the policy, on the few heap rows the index
returns.

**D-203 — API.** `JobRepository.enqueue(JobType type, UUID entityId, Map<String, ?> payload)` writes both columns;
`latest(JobType, UUID entityId)` replaces `latest(JobType, String key, UUID value)`; `latestByEntity(JobType,
Collection<UUID>)` replaces `latestByPayloadValue` and keys its result by `Job.entityId()`, so it no longer selects an
extra payload column. `Job` gains `entityId` after `type`. There is no string payload key in any lookup, so the
helper that validated keys for use inside SQL text is deleted. `GET /jobs/{id}` (`JobView`) is unchanged.

**D-204 — The payload stays.** Handlers still read their input from it (`Map<String, Object> payload`), and a
future job may need more than one id. Call sites pass the id they already put in the payload: document upload,
rule version create, assessment run, change re-run, report generation, rule source check.

**D-205 — Every `payload->>` comparison in main code is replaced.** `JobRepository.latest`, `latestByEntity`,
`WorkQueueRepository.EXTRACTION_FAILED` (`x.entity_id = rv.id`) and F12's scheduler dedupe in
`RuleSourceRepository.ENQUEUE` (`j.entity_id = rs.id`; the statement also inserts `entity_id`, `rs.id`, beside the
unchanged `jsonb_build_object('ruleSourceId', rs.id)` payload). `grep -rn "payload *->>" backend/src` finds only the
migration comment. The job policies in V002 and V011 are untouched.

**D-206 — The scheduler's dedupe is served by `job_claim_idx`, not `job_entity_idx`.** `IndexUsageTest` runs
`EXPLAIN` of the exact `RuleSourceRepository.ENQUEUE` statement, as a tenant (manual check) and under the worker flag
without a tenant (`enqueueDue`, the scheduler). In both the planner reads the QUEUED and RUNNING jobs from
`job_claim_idx (status, created_at)` and filters the entity; those jobs are few, which is what the statement asks
for, and it never scans `job` sequentially. The roadmap expected `job_entity_idx` here. The test asserts what is
true and what matters (an index on `job`, no sequential scan of `job`) instead of forcing the planner's hand.

**D-207 — `IndexUsageTest`.** The two rejected payload candidates and their `EXPLAIN` rows are removed (the
statements no longer exist); `job_entity_idx` is a kept candidate for the ingest status, the extraction status and
the rule version list (`latestByEntity`); every statement fails the test on `Seq Scan on job`. `PerfSeed.addHistoryVolume`
adds finished jobs (20 re-ingests per document, 20 extractions per rule version, 30 checks per rule source), because
at the seed's few hundred jobs the planner correctly prefers a sequential scan even without row-level security
and the index could not be shown to be chosen. `ENQUEUE` and a new `DUE` predicate are public constants of
`RuleSourceRepository`, like `WorkQueueRepository.LIST`, so the test explains what the application runs. The
candidate for `requirement` named `requirement_rule_version_id_ref_key`, which V012 replaced with
`requirement_ordinal_key`; `make perf` had failed on `main` since FIX-1 and is repaired here (query `ORDER BY ordinal`).

**D-208 — The backfill is proven by running the migration's own statement.** `JobEntityIdBackfillTest` creates a
temporary table named `job` (it shadows the real one for that connection only, and is dropped in a `finally`
because the pool reuses connections), reads `V013__job_entity_id.sql` from the classpath, runs its `UPDATE`
unchanged and asserts: documentId, ruleVersionId and ruleSourceId payloads yield their value; an empty object, an
array and a string stay NULL and are not visited. `JobEntityLookupTest` covers the repository: newest job of a type
for a record, other types and records ignored, another tenant's job invisible, `latestByEntity` returning one job
per record and omitting records without one. `MigrationsApplyCleanlyTest` already counts migration files from the
classpath, so it counts V013 without change.

**D-209 — Measurements.** `docs/PERF.md` ("Job lookups (FIX-2)") has the before and after rows for
`GET /documents/{id}` and `GET /rule-versions` and the plans and buffer counts. At the seeded volume (under a
thousand jobs) the endpoints do not move beyond run-to-run noise; the change shows in the plans, which no longer scan
`job`, and in the buffer counts on the larger database.

### F4 — Evidence request screens (D-260 to D-269)

**D-260 — Route, rail entry and one source for the rows.** `programs/:programId/requests` is its own lazy chunk
(`screens/requests/`). The rail shows "Evidence requests" after Review with `phosphorTray`, for every role, with a
badge whose screen-reader text is "n open evidence request(s)". `ProgramContext` loads
`GET /programs/{id}/evidence-requests?status=ALL` beside the other program reads and keeps the rows in one signal; the
badge, the SelectButton counts and the table all derive from it, and the Open / Answered / All filter is applied in
the browser. The inbox fetches the rows again when it is opened on a program that is already loaded (requests are other
people's work), Review refreshes them after every decision, and `refreshAfterChange` after a re-run (a re-run can
unblock a request). Rejected: one request per filter (three fetches for the counts); a separate store for the inbox
(the badge would need a second copy, D-085).

**D-261 — Status derivation.** `requestState(row)` in `core/evidence-requests.ts` is the only derivation: an OPEN row on
a stale mapping is Blocked (`phosphorArrowsClockwise`, warn tag, the helper text from the spec, no action), any other
OPEN row is Open (`phosphorEnvelopeOpen`), an ANSWERED row is "Answered, awaiting review" (`phosphorCheckCircle`). A
blocked request is still an open request: it is listed and counted under Open and in the badge, because it still
needs an answer once the change is re-run. A stale ANSWERED row stays Answered: there is nothing to answer and the
reviewer's Review screen already says the mapping needs a re-run. Rejected: hiding blocked requests (the contributor
would not know a request exists).

**D-262 — `stale` on `EvidenceRequestView`.** One column (`m.stale`) in the existing statement of
`EvidenceRequestRepository`, after `status`. Tested in `EvidenceRequestApiTest`
(`aStaleMappingsRequestIsListedWithItsStaleFlagAndCannotBeAnswered` and the flag in the full loop).

**D-263 — `GET /api/v1/programs/{id}/documents` (orchestrator decision during F4).** No endpoint on `main` listed a
program's documents: the courses endpoint carries no documents and every document read takes a document id, so the
spec's fallback could not be built. The orchestrator decided to add the endpoint here, in the document package next to
the other document reads: `DocumentService.forProgram` checks the program (`ProgramService.get`, 404 for an unknown
or another tenant's program) and `DocumentRepository.viewsByProgram` runs one statement returning `{ id, filename,
kind, courseVersionId, courseCode, courseTitle, catalogYear, courseRequired, pageCount, current, createdAt }`,
ordered program-level documents first (course code null), then course code, newest catalog year, file name. `current`
is true for program-level documents and for documents of a course version no other version supersedes (the unique
index on `course_version.supersedes_id` serves the `NOT EXISTS`); the page count uses the `(document_id,
page_number)` key. Rule text has no program, so it never appears. Any role may read; it is a GET, so it is not
audited. Rows in `InputValidationTableTest` (bad id 400, unknown program 404), `ReadPathConnectionsTest` (one
connection) and `EndpointLatencyTest` (p95 5.5 ms seeded, 5.4 ms with 62 tenants); `ProgramDocumentsApiTest` covers
order, fields, the `current` flag, a document without pages, every role, anonymous 401, another tenant 404.

**D-264 — The document picker (orchestrator decision during F4).** A grouped PrimeNG Select: "Program documents" first,
then one group per course ("COUN 605 Professional Orientation and Ethical Practice"), in the server's order. Current
documents only by default; a ToggleSwitch "Include earlier catalog years" (shown only when there are any) adds the
others, labelled "catalog year 2026-27". Switching it off again clears a choice that is no longer listed. A document
with no pages is listed disabled with "No text available yet". The job table is not read for ingest status (FIX-2 makes
that cheap later). The grouping is the plain function `documentGroups` (Vitest). An option's accessible name is its
visible text (file name, then kind and year); the Select shows the same words, joined with commas, once chosen.

**D-265 — The answer drawer.** `EvidenceDrawer` is a `@defer (when answering())` block of the inbox, so its code
(Drawer, Select, ToggleSwitch, Textarea and their tokens) is a chunk of its own (`evidence-drawer`, 32.6 kB raw) loaded on
the first "Provide evidence". PrimeNG Drawer on the right, 70 % wide, 100 % under 1024 px, appended to the body.
PrimeNG renders the panel as `role="complementary"`; pass-through makes it `role="dialog"`, `aria-modal="true"`,
labelled by its "Provide evidence" heading (D-087 style). Its focus trap keeps Tab inside; the Drawer gives no initial
focus, so the heading takes it on show. Escape, the close button, the mask and Cancel return focus to the row's
"Provide evidence" button; after an answer that button no longer exists, so focus goes to the page heading next to
the status line. The inbox keeps the request it last opened while the drawer slides out. The Drawer runs with
`modal` false and the screen draws its own backdrop (click closes, `aria-hidden`), and Escape is handled by the
drawer for keys pressed inside it (`closeOnEscape` false): PrimeNG removes its own mask on `animationend`, which does
not reliably fire when animations are reduced to zero (`prefers-reduced-motion` in `styles.css`), and a mask left
behind made the whole page unclickable (seen in 1 of 3 end-to-end runs); its document-wide Escape listener also closed
the drawer when Escape only meant to close the viewer's range dialog above it. The rule text is not on the request row; the drawer
reads it from the existing `GET /assessments/{id}/mappings?jurisdiction=` (one request per opening) rather than
adding the text to every inbox row. Wide layout: request, picker, passage, comment and actions on the left, the page on
the right (sticky); under 1024 px everything stacks in reading order. The selection (mouse, "Use this passage" or the
keyboard range dialog) fills "Selected passage" in serif with page and character range in mono; `responseBody` sends
`{ documentId, pageNumber, quote, comment }` with the quote exactly as stored and a blank comment as null. A 400 is
shown under the passage with the server's detail; a 409 (or any other failure) in a Message above the actions, and the
inbox reloads. On success: toast "Evidence sent to review", a status-line sentence, the drawer closes, the row moves to
Answered.

**D-266 — `responseCount` on the mapping read (orchestrator decision during F4).** Option (a) of the review: a correlated
`(SELECT count(*) FROM evidence_response er WHERE er.mapping_id = m.id)` in `MappingRepository`'s shared select, served
by `evidence_response_mapping_idx`, exposed as `Mapping.responseCount`. No migration. `make perf`, p95 of
`GET /assessments/{id}/mappings`: by jurisdiction 4.92 → 2.71 ms (seed only) and 3.57 → 5.68 ms (62 tenants); all
mappings 9.38 → 5.77 ms and 9.27 → 11.14 ms. The differences are run-to-run noise in both directions and far under the
40 ms budget; `ReadPathConnectionsTest` still shows one connection for every read endpoint. The marker means "this
requirement has evidence provided by a person" and stays after a decision.

**D-267 — Review.** Responses are fetched with the selected mapping only when its `responseCount` is above zero, kept per
mapping in `ReviewWorkspace.responses` for the screen's lifetime, and fetched again when the count has grown since
(`evidenceView`, Vitest). The Evidence pane shows the system's evidence (or "No quoted evidence") first and unchanged,
then one block per response, newest first: a heading "Evidence provided by <name>, <date>" with `phosphorUser`, the
same file, course, page and character list, the elective warning, "Quote only", the `DocumentViewer` with the stored
span marked, and the comment. Both kinds use one `passageTpl` template; under 1024 px each has its own "Show on page"
(the shown set is a `linkedSignal` of the selection). The requirement list shows `phosphorChatCircleText` "evidence
provided" on mappings with responses; the note placeholder is "Note the evidence you relied on" while a person has
answered and no decision has been made. The system test ids (`evidence-meta`, `evidence-quote`) stay on the system's
block only.

**D-268 — Bundle.** Initial bundle raw: 703.67 kB on the `main` this item started from, 710.30 kB with F4 before
merging F3b and F8; after merging them, 703.12 kB on `main` and 707.30 kB with F4 (warning budget 700 kB, error
800 kB). The screen and the drawer are lazy (`requests`, and `evidence-drawer` at 32.6 kB raw). The Drawer and
ToggleSwitch token sets follow F3b's and F8's pattern and join the preset when the drawer's chunk first runs
(`evidence-drawer-theme.ts`, which reuses the register's ToggleSwitch definition rather than copying it). The 4.2 kB
left in the initial bundle are four icons in the single registry (`phosphorTray`, `phosphorEnvelopeOpen`,
`phosphorChatCircleText`, `phosphorUser`, about 1.7 kB) and the code the rail badge needs (`ProgramContext`,
`ApiService`, `core/evidence-requests.ts`). PERF-FE-1 moves screen icons into their chunks.

**D-269 — Tests.** Vitest: `core/evidence-requests.spec.ts` (status derivation including blocked, filters and counts),
`screens/requests/answer.spec.ts` (selection to request body, document groups, earlier years, disabled documents),
`review-workspace.spec.ts` (responses merged into the evidence view, loading, answered, cache). End to end,
`e2e/evidence-requests.spec.ts` follows the spec's scenario on Utah's missing requirement (Colorado and California are
used by other suites): request with a note, the contributor's badge and inbox, row detail from the keyboard, the drawer,
current and earlier documents, Escape closing only the range dialog, a passage typed into the range dialog, a tampered
quote refused through the API and through the screen with the server's words, submit, Answered, the reviewer's
"Needs review" filter with the marker, "Evidence provided by Jordan Ellis" and the marked quote, accept, nothing open.
A second test checks 400 px: the inbox, the full-width drawer and focus returning to the row action, a blocked row,
and the provided evidence behind "Show on page". The seed cannot make a requested mapping stale without a rule or
course change that other suites depend on (and a stale mapping cannot be reviewed), so the blocked row is produced by
setting `stale` on one row of the real list response in the test; the flag itself is covered by the backend test.
`e2e/support.ts` gains `apiPost` (a mutating call with the CSRF header) and `switchUser`. The range offsets are typed key by
key: Playwright's `fill` leaves PrimeNG InputNumber's model unchanged until the field loses focus.

### PERF-FE-1 — Icons and theme tokens per chunk (D-295 to D-299)

**D-295 — `SHELL_ICONS` is the only icon registry in `app.config.ts`.** It holds the 17 icons of the shell, the
sign-in page and the home screen (`theme/icons.ts`); `LEXVAY_ICONS` is deleted. `IconName` is now
`` `phosphor${string}` ``: it used to be `keyof typeof LEXVAY_ICONS`, which no longer exists as one registry, and the
tables that carry icon names (`FINDING_ICON`, `RULE_STATUS`, ...) are checked by `icons.spec.ts` instead of by the type.
Rejected: a union of every screen's constant (the label tables would import types from the screens they serve).

**D-296 — Each routed screen provides its own icons through its route; shared components use `viewProviders`.**
A route's `providers` are built when the app starts, so naming a screen's `<SCREEN>_ICONS` in `app.routes.ts` would
import the screen into the initial bundle. Each screen with icons beyond `SHELL_ICONS` (Overview, Review, Requests,
Changes, Rules, Report, Register) therefore has a `<screen>.routes.ts` (one route, `path: ''`, the screen as `component`, its
`providers: [provideIcons(<SCREEN>_ICONS)]`, its title and guards) that `app.routes.ts` loads with `loadChildren`; the
screen and its routes are one chunk, so there is no second request. `provideIcons` merges with the icons of the parent
injector, so a screen names only what `SHELL_ICONS` does not. `DocumentViewer`, `FindingLabel` and `DecisionState`
provide theirs in `viewProviders` (`DOCUMENT_VIEWER_ICONS`, `FINDING_LABEL_ICONS`, `DECISION_STATE_ICONS`). Login, Home
and Audit show nothing beyond the shell's icons and keep `loadComponent`. `confirmLeave` moved from `app.routes.ts` to
`core/confirm-leave.ts` so `rules.routes.ts` does not import the root route table. The label tables that held icon names
moved next to the one screen that shows them (`CHANGE_KIND` to `screens/changes/change-kind.ts`, `DIFF_STATUS` to
`screens/rules/diff-status.ts`, `RULE_STATUS` to `screens/rules/rule-status.ts`, `OUTCOME_ICON` and `STATUS_ICON` into
`register.ts`, `REQUEST_STATE` into `requests.ts`, `FINDING_ICON` and `DECISION_ICON` into `ui/finding.ts`): `core/labels.ts` and `core/rules.ts` are
imported by the shell, so icon names left there would belong to every chunk.
Rejected: component-level `providers` on each screen (works, but the spec says the route's).

**D-297 — The registration guard reads sources; the end-to-end suite watches the console.** `theme/icons.spec.ts`
collects every quoted `phosphor...` name in the files a screen reaches through relative imports and `templateUrl`
(stopping at a shared component that registers its own), and requires the registered set to equal the names shown
minus `SHELL_ICONS`, in both directions (so a stale entry fails too). It also fails if any source file that names an
icon is reached by no unit, so a new screen cannot forget to register. The spec asked for rendering each screen's
template; a rendered DOM covers only the states a fixture reaches (the loading skeleton shows no icon), while the source
covers every branch. The runtime half is `iconGuard` in `e2e/support.ts`: every end-to-end test fails on a
"No icon named" console warning. The spec reads files, so `@types/node` 26.6.11 (registry latest, checked 2026-11-27,
matching Node 26.13.1) is a dev dependency and `tsconfig.spec.json` lists `node` in `types`.

**D-298 — `registerComponentTokens(sets)` and what moved.** One helper in `theme/component-tokens.ts` replaces
`registerRulesTheme` and `registerRegisterTheme`: it calls `updatePreset` for the components not yet registered, so two
chunks naming the same component (Dialog, InputNumber, Textarea, ProgressBar) are safe. Each screen exports its sets as
`<SCREEN>_TOKENS` in `<screen>-theme.ts` and registers them from its constructor; `DocumentViewer` registers Dialog and
InputNumber itself. Moved out of the shared preset: Dialog, InputNumber, ProgressBar, ProgressSpinner, RadioButton,
Splitter, Textarea. F4's Drawer set and F8's ToggleSwitch set (with its Lexvay colours, now `theme/toggleswitch-tokens.ts`,
shared by Register and Requests) follow the same pattern; `evidence-drawer-theme.ts` is gone and the Requests screen
registers the drawer's sets. The Lexvay overrides of ProgressBar and Splitter moved with their sets with the same values
(`theme/progressbar-tokens.ts` is shared by Overview and Review); the Dialog override restated Aura's own title values
(1.125rem, 600), so it was dropped with no change of value. Disagreement with the spec's list: SelectButton and
ToggleButton stay in the preset because the shell's theme switcher renders them on every screen, and IconField stays
with Select (Select's filter uses it; 61 bytes).

**D-299 — The budget stays at 700 kB / 800 kB; the initial bundle is 695.0 kB, not under 640 kB.** See
`docs/PERF.md`, "Frontend bundle". The icon cut does not work as the spec assumes: `@ng-icons/phosphor-icons/regular`
is one module, and the bundler places a module in the chunk every importer shares, with the union of the exports used
anywhere. Because the shell imports it for `SHELL_ICONS`, the icons of every screen still ship in the initial chunk
(26.9 kB for 52 icons; the 17 shell icons are 7.5 kB). Proposed next cut, measured on a scratch build: give the shell
its own copy of its 17 icons (a generated `theme/shell-icons.ts` of SVG strings, checked against the package by a
test), which takes the initial bundle to 676.5 kB. Still short of 640 kB; the rest is in the PERF.md breakdown.

### F7c — Search terms proposed at extraction (D-250 to D-259)

**D-250 — Migration `V014__requirement_search_terms.sql`.** `requirement.search_terms text[] NOT NULL DEFAULT '{}'` with
`CONSTRAINT requirement_search_terms_valid CHECK (search_terms_valid(search_terms))`. `search_terms_valid(text[])` is an `IMMUTABLE`
`PARALLEL SAFE` SQL function: `cardinality(terms) <= 12` and no element that is null or has `length(btrim(t)) NOT BETWEEN 1 AND 40`. The
roadmap's V015 is renumbered V014 because F13 needed no migration. Unlike V012 the migration does not lift `FORCE ROW LEVEL SECURITY` or the
`requirement_locked` trigger: nothing updates an existing row (a constant default is stored without rewriting the table and fires no trigger)
and the CHECK is validated against the stored rows as an internal scan that row-level security does not filter, so approved requirements stay
untouched and locked throughout. The lock trigger is unchanged, so `search_terms` is part of the locked row: `Rule5_ApprovedRequirementsRejectEditsTest`
now also tries `UPDATE requirement SET search_terms`. `RequirementSearchTermsConstraintTest` proves 12 and 13 terms, 40 and 41 characters, empty,
blank and null elements, and the function called directly. Rejected: a `jsonb` column (no constraint without a function either, and `text[]` goes
straight into `to_tsvector(array_to_string(...))`); a child table (a join on the hot path for at most 12 short strings); a `bool_and` over `unnest` in the CHECK
itself (subqueries are not allowed in CHECK constraints, hence the function).

**D-251 — One BM25 statement, three lexeme sources; `Passage.matchedVia` and `matchedTerms`.** The `q` common table expression of D-170 becomes
`SELECT DISTINCT ON (lexeme) lexeme, boost, source, priority` over three selects: the title (boost 2.0, priority 1), the text (1.0, 2) and
`to_tsvector('english', array_to_string(CAST(:searchTerms AS text[]), ' '))` (1.0, 3), ordered by `boost DESC, priority`. A lexeme found in several
sources therefore keeps the highest boost and, on a tie, the requirement's own source (a term that is also in the text counts once, as TEXT).
Nothing else in the statement changes: `lexq`, `wanted`, `hits`, `df`, `bm25` and the excerpt's `ts_headline` already work from `q`, so the
excerpt of a page found through a term shows the term's sentence. The `bm25` expression gains
`(array_agg(q.source ORDER BY q.boost DESC, q.priority))[1] AS matched_via`: the source of the highest-weighted lexeme that matched the page,
`TERMS` only when no word of the title or the text is on the page. `MatchSource` is `TITLE`, `TEXT`, `TERMS`. `Passage` gains `matchedVia` and
`matchedTerms`: for a `TERMS` page, the outer select (which only sees the rows that survived `LIMIT`) lists the terms, in their stored order, whose
lexemes overlap the page's (`tsvector_to_array(to_tsvector('english', term)) && tsvector_to_array(tsv)`); other pages get `{}`. The `ts_rank`
baseline ORs the terms into its tsquery as the spec says, and has no weights, so for it the title and the text are one source: a page it finds is
`TEXT` unless only the terms matched. Rejected: a second statement for terms (the filters, the ordering and the limit would be copied, D-172); naming
the matched terms in Java (it cannot stem); labelling per page in a separate query (a second round trip per requirement).

**D-252 — `SearchTerms` is the one normaliser.** `SearchTerms.normalise(proposed, title, text)`: trim, collapse inner whitespace, lower-case,
drop duplicates (first wins, order kept), drop empty terms, drop a term with a digit, drop a term that names a US state or the District of
Columbia (whole words, case-insensitive: `west virginia board` goes, `oregano` stays), and drop a term whose every word is already a word of
the requirement's title or text (`RequirementMatcher.words`, lower-cased `\w+`; stemming is not available in Java, so `disorder` is kept when the
text says `disorders`). A phrase with at least one new word is kept whole, because the words are searched as a bag anyway and the phrase is what a
reviewer reads. The roadmap says "text"; the title is included because its lexemes are in the search too. Limits are constants beside it:
`MAX_PROPOSED = 8` (what the model is asked for; the extraction schema enforces it), `MAX_STORED = 12` (what a person may keep; the CHECK enforces it),
`MAX_LENGTH = 40`. `RequirementListParser` calls it for both paths. The model's output is schema-validated first (more than 8 or a term over 40
characters is a rejected output and the usual single retry applies). The replace endpoint skips the schema, so `search_terms` is optional there
(absent or null is an empty list) and the parser itself rejects a non-array or a non-string (`requirements[i].search_terms must be an array of
strings`), more than 12 (`requirements[i].search_terms must contain at most 12 terms`) and a term over 40 (`requirements[i].search_terms[j] must be
at most 40 characters`), each a 400 naming the row, with the stored list untouched. Abbreviations of states (`CA`) are not recognised; the prompt
forbids state names and the reviewer reads every term.

**D-253 — Prompt `extract-v2` and the schema.** `llm/extract-v2.system.txt` is v1 plus one rule for `search_terms` (up to 8 single words or two-word
phrases a syllabus or degree plan would use for the subject, synonyms and course-title vocabulary, none already in the requirement text, no state
names, no numbers, an empty list when there is nothing to add). v1 stays on disk. `ExtractRequirementsHandler.PROMPT_VERSION` is `extract-v2`, so
`llm_call` and the `REQUIREMENTS_EXTRACTED` audit entry record it. `requirement-extraction.schema.json` gains `search_terms`: an array of strings,
`maxItems` 8, items `maxLength` 40, listed in `required` for every requirement (the schema is strict, as the other nullable fields are). Every
hand-built extraction in the tests now supplies the member; `JsonSchemaValidatorTest` covers nine terms, a 41-character term, a non-string and a missing member.

**D-254 — `RequirementComparison.Aspect.SEARCH_TERMS`.** `affectsEvaluation` is false, like `TITLE`: a terms-only edit is not a change to the
requirement, so re-run carries decisions over (`sameForEvaluation` is true) and the diff still reports the row. Terms are compared as lists, so
a changed order is reported too. `RuleVersionDiffService` is unchanged: a row whose only change is `SEARCH_TERMS` is `CHANGED` with
`changes: ["SEARCH_TERMS"]` and, as for a title change, a word diff of the (identical) text. `Requirement` and `RequirementDraft` gain `searchTerms`
(last component); `REQUIREMENTS_REPLACED` audits the terms in `before` and `after`. `RuleVersionChangeTest` proves both halves on a real re-run
(all three decisions carried over, no new model call) and the diff.

**D-255 — Binding the terms.** The terms reach SQL as one `text[]` parameter: `.param("searchTerms", String[])` and `CAST(:searchTerms AS text[])`,
which pgjdbc sends as an array (a `List` would have been expanded by Spring into an `IN` list). The statement stays prepared once per mode, no
SQL is built from a term, and quotes and backslashes in a term cannot break the tsquery (`PassageSearchTermsTest`). The three source names are bound
from `MatchSource`, so the SQL holds no copy of the enum. `PassageSearch.search` takes `List<String> searchTerms` between the text and the limit;
`EvidenceMatcher` passes the requirement's own, `RetrievalService` passes them only for the third column. The latency test now runs with six terms per
requirement and its budget (p95 under 30 ms for 500 pages) is unchanged.

**D-256 — The matcher: one sentence, nothing else.** When the cited page was found only through terms, `EvidenceMatcher` appends
` Found through the search terms: <terms that occur on the page>.` to the model's rationale, before any downgrade text. The terms are not shown to the
model, the quote is verified against the stored page exactly as before and a page found by terms is subject to the same downgrade rules, so product rules
1, 2 and 4 are untouched: a term only widens the shortlist. `SearchTermsMatchingTest` covers the sentence, a discarded invented quote on such a page, no
terms (the page is not a candidate at all), and a page that shares a word with the requirement (no sentence).

**D-257 — Seed and fixtures.** Every requirement scripted in `seed/fake-llm.yml` (55 across six rule sets, Colorado twice) and in
`frontend/e2e/fixtures/fake-llm.e2e.yml` (4) carries 3 to 5 hand-written fictional terms by subject (credits: `program of study`, `degree plan`;
ethics: `code of ethics`, `confidentiality`; and so on). CO-5 and every other Colorado requirement have identical terms in both rule sets, so the
diff between them still shows exactly one `CHANGED` row (`RuleVersionDiffSeedTest` unchanged and green). A term the production normaliser would drop
(all its words in the title or text) was removed from the file rather than left to be dropped silently: `lifespan development` for the human growth
requirements, `appraisal` for CA-8, `practice sessions` and `accredited program` in the retrieval queries. `SeedSearchTermsTest` runs both files through the
real parser and asserts none is dropped, 3 to 8 each, the schema accepts them, and the Colorado terms match.

**D-258 — Evaluation.** `eval/retrieval/queries.yml` gains `searchTerms` (3 to 8, lower-case, normaliser-stable: `RetrievalFileTest` runs them through
`SearchTerms.normalise`) for all 35 queries. The file header says they were written for the requirement alone, as the extraction prompt asks, and are never to be
edited to raise a number. Honest limit of that claim: the expected passages' `contains` strings sit in the same file and were visible when the terms
were written; the terms use the vocabulary any counseling syllabus uses (`psychopathology`, `couples therapy`, `videoconferencing`, `progress notes`), but
the lexical-gap result below is the result of one author's one pass, not of a model's proposals, and `make eval-live` does not measure it (its live path
substitutes the golden requirement list, which has no terms). `RetrievalService` runs ts_rank and BM25 without terms and BM25 with them; `RetrievalReport`
shows three columns in the console and in `docs/RETRIEVAL.md` (and each query's terms). `Thresholds.Retrieval` gains `minGapRecallAtDeep: 0.66` (4 of 6; 4 / 6 passes and
3 / 6 does not) and `Thresholds.termsBreaches` checks BM25 with terms against the existing recall and coverage floors, against BM25's recall@8 and recall@3,
and the gap floor. Result (slot 3 run): counted recall@8 28 / 29 for all three columns, recall@3 25, 26 and 27 of 29, mean reciprocal rank 0.796, 0.766 and 0.839, excerpt
coverage 28 / 28; lexical gaps recall@8 2, 2 and 6 of 6, recall@3 1, 1 and 6 of 6. R13 stays outside the top eight in all three. The pipeline
on the seed is unchanged: 0 false `EVIDENCE_FOUND`, 33 / 33 recall, 42 / 42 extraction agreement, 39 / 39 evidence agreement, 36 model calls.
`RetrievalServiceTest` asserts the gap floor and that without the terms the same queries fail it.

**D-259 — Consequences and what was left alone.** (1) The requirement grid does not show the terms yet (UI2-4 adds the cell), so it passes them through: `RequirementView`
gains `searchTerms`, `RequirementDraft` gains `search_terms`, and `RequirementListEditor` keeps each loaded row's terms (a copy, so a discard returns to the server's list), sends them
for every row in the PUT body whatever else is edited or moved, and gives a new row none. Without this a save of the grid would have replaced the stored terms with none, since an absent
`search_terms` means an empty list. Only `core/models.ts` and the editor files changed; the editor spec covers load, edit title, move, change type, save body (terms preserved), discard, and
a new row (empty terms). Terms are not part of the editor's dirty check because the screen cannot change them. (2) The live evaluation substitutes the golden requirement list through the
replace path, which has no terms, so live retrieval is measured without them (roadmap follow-up). (3) `matchedTerms` covers terms that share a lexeme with the page. (4) The `eval` profile
now sets `management.server.port: -1` like `seed`, so `make eval` no longer collides with a development backend on :8090 (checked with one running). (5) The reviewer search, the
`RULE_TEXT` exclusion and the tenant scoping (row-level security) are untouched; no endpoint was added, so `Rule9_AuditCoverageTest` needs no entry, and the replace endpoint was already audited.

### FIX-3 — Work queue and evidence request follow-ups (D-360 to D-366)

#### D-360 `ASSESSMENTS_FAILED` lists failures after the latest finished assessment

A FAILED assessment is listed when its program has no FINISHED assessment, or when it was created after the creation
time of the program's latest FINISHED one (the `latest` CTE, D-195, now also exposes that `created_at`). A failure
followed by a successful run leaves the queue; a failure after a success appears again. Created time, not finished
time, because a failed run that started before the successful one was superseded by it, whichever ended first.
Tests: two failures without a success are both listed; a later success clears both; a failure after that success is
the only one listed (`WorkQueueApiTest`).

#### D-361 One "signed for this assessment and jurisdiction" predicate

`DeterminationRepository.signedFor(assessmentExpr, jurisdictionExpr)` returns the SQL `EXISTS` over SIGNED
determinations. It is a method, not a constant, because its users name the columns differently (bind parameters in
`hasSigned`, `m.` columns in the evidence request statement and in the work queue's `current_mappings`). The text of
the predicate exists once.
- `hasSigned` is `SELECT <predicate>` (a boolean, no `count`).
- `EvidenceRequestView.signed` is one more column of the existing statement; the status stays OPEN.
- Work queue: `current_mappings` gets `request_blocked` = latest event is an evidence request AND (stale OR signed).
  The `AND` runs the determination lookup only for mappings with an open request. `EVIDENCE_REQUESTS_OPEN` counts
  `request_blocked` mappings in `ids.blockedCount` and only the others as answerable items; a group with no answerable
  request is not listed. A signed determination covers every mapping of its assessment and jurisdiction, and a group is
  exactly that, so a signed group is never listed: `blockedCount` from signing alone is never visible, it only keeps
  the definition in one place.
- Answering still returns 409 (`EvidenceRequestService.respond`, unchanged).

#### D-362 One "decided" predicate

`AssessmentRepository.findingCounts` joins `MappingRepository.LATEST_EVENT` and counts `NOT UNDECIDED` as decided
(`NOT stale AND NOT NEEDS_DECISION`, De Morgan of the signing gate). `FindingCountsParityTest` compares it with the
previous statement (kept in the test) on the seeded tenant as seeded and after every kind of review history and a
stale mapping.

#### D-363 Measurement of `MappingRepository.CURRENT`

On the WS-PERF database (about 200 tenants, `IndexUsageTest`) one tenant got rule version v1 with 500 requirements, its
successor v2 with 500 more, an assessment with 500 mappings on v1 (v2 has no mappings in it: the historical case after
a rule change) and one with 500 on v1 and 500 on v2 (after the re-run, the 500 on v1 hidden). `EXPLAIN (ANALYZE,
BUFFERS)` of `GET /assessments/{id}/mappings` (`MappingRepository.BY_ASSESSMENT`) as the application role: 15.5 ms and
5 192 buffers before the re-run, 29.6 ms and 4 747 buffers after it (limits: 5 ms or 2 000 buffers). The plan is a
nested loop anti join with 250 000 and 500 500 rows removed by the join filter: quadratic, as D-199 predicted. The work
queue statement of that tenant took 265 to 285 ms (budget 40 ms). The filter was rewritten.

#### D-364 `MappingRepository.current(assessments)`

`CURRENT` (a correlated `NOT EXISTS` evaluated per mapping) is replaced by `current(String assessments)`:
`AND (m.assessment_id, r.rule_version_id) NOT IN (SELECT DISTINCT nm.assessment_id, nv.supersedes_id FROM mapping nm
JOIN requirement nr ... JOIN rule_version nv ... WHERE nv.supersedes_id IS NOT NULL AND nm.assessment_id <assessments>)`.
The subquery does not depend on the row, so PostgreSQL hashes it once per statement. `assessments` bounds the
assessments the statement reads: `= :a` for one, `IN (SELECT assessment_id FROM latest)` for the work queue. The result
set is identical (a mapping is hidden when a successor of its rule version has mappings in its assessment);
`RuleVersionChangeTest` compares the previous form (`LegacyCurrentFilter`, test only) with both new forms before the
change, after the successor was created and after the re-run, and the READY_TO_SIGN parity test (D-193) compares the
queue's form with the signing gate's. Result: the same statement takes 3.2 ms (3 530 buffers) and 3.5 ms (3 527
buffers); the work queue 2.0 to 5.2 ms. 3 500 buffers is above 2 000 but is per-row work (review events 1 000, evidence
spans 900, the set 1 500), linear in the rows; `IndexUsageTest` fails above 4 000.
Rejected: a per-mapping `EXISTS` with index probes (500 probes per mapping when the successor has no mappings: worse);
a `mapping.rule_version_id` column with an index (a migration for a read cost that is already linear); planner settings.

#### D-365 The manual "already queued?" check may use either job index

Since FIX-2 the planner may choose `job_entity_idx` for the rule source's manual check (one entity) or `job_claim_idx`
(queued jobs), depending on sampled statistics; both avoid a sequential scan of `job`. `IndexUsageTest` accepts either
for that statement (`Candidate.eitherOf`) and still fails on `Seq Scan on job`. `job_claim_idx` (V001) stays pinned for
the scheduler statement. Every V008 and V013 index must still be chosen by some statement.

#### D-366 Perf fixture

`PerfSeed.addLargeAssessments` builds the data of D-363; the plans are written to `target/perf/mappings-plan.txt`.

### F5a — User administration and audit export API (D-370 to D-379)

#### D-370 Users are never deleted; access is withdrawn with a flag

`app_user` gains `active boolean NOT NULL DEFAULT true` and `security_stamp integer NOT NULL DEFAULT 0` (V015). A user
who leaves is deactivated, not removed: every audit entry, review event and signature names its user (product rule 9),
and a foreign key to a deleted row would either break them or orphan them. There is no `DELETE` endpoint and no
`deleted_at` column. `app_user` keeps ordinary updates, with no immutability trigger: its role, activity, stamp and
password hash are meant to change, and every change is audited as an append-only event instead (rule 5 protects the
history, not the current state of an account).

Rejected: a soft-delete timestamp (a second flag meaning the same as `active = false`); making `app_user` append-only
with a new row per change (every `user_id` reference in the schema would need a "current version" join).

#### D-371 No email delivery: the administrator hands over the code

Creating a user returns a setup code in the response; nothing is sent. Lexvay is deployed inside a university's network
with no mail relay configured, and sending mail needs credentials in the configuration (`LEXVAY_*` secrets), templates,
bounce and retry handling, and a promise about deliverability that the product cannot keep. It would also put the
secret in a mailbox that outlives the 72-hour window. The administrator already knows the colleague and has a channel
(in person, the university's chat, a call). The same endpoint that issues a first code issues a replacement, which is the
password reset. The cost is that the administrator sees the code; the audit entry `USER_SETUP_CODE_ISSUED` records who
issued it and when, and `PASSWORD_SET` records that the person set a password, with the person as actor, so a code used
by somebody else is visible.

Rejected: an SMTP integration behind a `Mailer` seam (a second production path that no test or demo can exercise without
a server, for a feature the first customers do not ask for); emailing a link (same secret, more places it can be read);
letting the administrator choose the first password (the administrator would know it).

#### D-372 Setup codes: 32 random bytes, stored as SHA-256, valid 72 hours, one open per user

`SetupCodes.generate` returns 32 bytes from `SecureRandom` as 43 characters of unpadded base64url. The table
`user_setup_token` stores `token_hash` (lower-case hex SHA-256, unique, checked against `^[0-9a-f]{64}$`), `expires_at`,
`used_at`, `created_by` and the tenant. The code itself exists only in the response that creates it and in the person's
hands: it is never stored, logged or audited (`UserAdminApiTest` searches the whole audit log of the tenant for it).
SHA-256 is enough because the code has 256 bits of entropy, so there is nothing to brute force and no benefit from a slow
hash; it also gives a deterministic value to index and look up, which a salted bcrypt hash would not. Issuing a new code
marks every earlier unused code of the user as used in the same transaction, so at most one code works at a time. Rows
are immutable except `used_at` (`restrict_update_to_columns('used_at')`, and a delete trigger), like every other
history table (rule 5). The table has the tenant policy of V002 and a SELECT policy on the transaction-local
`lexvay.auth_lookup` flag, exactly as `app_user` has (D-034).

Rejected: bcrypt for the code (needs the id to find the row, so the code would have to carry it); a signed token such as
a JWT (cannot be revoked without a table anyway, and adds a key to manage); a six-digit code (guessable inside the
rate limit).

#### D-373 Setting a password: anonymous, one message, shared budget, 72 bytes

`POST /api/v1/auth/setup {code, password}` is open like login, needs the CSRF token like login, and draws on the login
rate-limit budget of the client address (`RateLimitFilter`), because guessing a code is guessing a password.

- The lookup is one `SELECT` joining the code to its user, under `lexvay.auth_lookup` (set in the same short transaction,
  as `findForLogin` does). Unknown, used and expired give the same empty answer and the same 400 "This setup code is not
  valid"; the response cannot tell which. The hash found is compared again with `MessageDigest.isEqual` in
  `SetupCodes.matches`. The database already matched on the hash, and the hash is a one-way image of a 256-bit secret, so
  the timing of an index probe reveals nothing usable; the second comparison exists so that no code path compares a secret
  with `equals`, and `SetupCodesTest` checks that none does.
- Then, under the code's tenant and in one transaction: claim the code (`UPDATE ... WHERE used_at IS NULL AND expires_at >
  now()`, so two requests with one code cannot both win; the loser gets the same 400), hash the password with the
  application's `PasswordEncoder`, set `password_hash`, increment `security_stamp` (every session the user has ends,
  D-374), and audit `PASSWORD_SET` with the user as actor and no detail. The endpoint does not sign anybody in: the client
  goes to the sign-in screen, so sessions are created in exactly one place (login and OIDC).
- A password that breaks a rule does not use up the code. Rules: at least 12 characters, not equal (ignoring case) to the
  email, and **at most 72 bytes in UTF-8**.

**Disagreement with the roadmap:** spec point 3 asks for 12 to 128 characters. The application's encoder is Spring's
delegating encoder with bcrypt as default, and bcrypt refuses more than 72 bytes (`password cannot be more than 72
bytes`, measured with Spring Security 7.1.3: 72 characters pass, 73 throw). A 128-character limit would turn every
password between 73 and 128 characters into a 500. The limit is therefore 72 bytes, and the 400 says so. Raising it
means choosing another default encoder (PBKDF2 or Argon2 behind the same delegating encoder, which would still verify
existing `{bcrypt}` hashes) or pre-hashing, which changes how logins verify; that is an architecture decision for the
orchestrator, not for this item.

Also not done, though point 1 lists it: the `auth_lookup` policy on `user_setup_token` covers SELECT only, not UPDATE.
Nothing needs the update: the claim runs under the tenant, in the transaction that sets the password. An UPDATE policy
open to a flag would be a door with nothing behind it (`Rule7_UserSetupTokenIsolationTest` proves that the flag reads and
cannot write).

Rejected: looking the code up by user and email (needs an identifier the person may not know, and enumerates accounts);
signing the person in at the end (a second place that creates sessions); a lower-case-only or digit-only code (entropy).

#### D-374 Session revocation: a stamp in the session, a 15-second per-instance cache

`LexvayUser` carries the `security_stamp` the user had when the session was issued. `TenantContextFilter` asks
`UserStampCheck` on every authenticated request whether `(active, role, security_stamp)` of the user still match. The
answer comes from a Caffeine cache (per instance, keyed by user id, `expireAfterWrite` 15 seconds, at most 10 000 users);
a miss runs one statement (`AppUserRepository.findAccess`) under the user's tenant. A mismatch, an inactive user or a
user that no longer exists invalidates the HTTP session and answers 401 "Your access changed. Sign in again."

- **Where the stamp moves:** role change, deactivation, reactivation, password set (a new password ends the sessions that
  the old one opened). The role is compared as well, so a role changed by hand in the database ends sessions even without
  a stamp change.
- **This instance:** the service evicts the user after the transaction commits (`evictAfterCommit`; evicting earlier would
  let a request that arrives before the commit cache the old values for a full window), so the next request is refused.
- **Another instance:** it learns after at most **15 seconds**. This is recorded in `docs/OPERATIONS.md`. Fifteen seconds
  because the cost is one statement per active user per instance per window (about four a minute, below any budget in
  `docs/PERF.md`), while the exposure is the time a withdrawn administrator keeps working through an instance that did not
  make the change; shortening it to a few seconds multiplies the lookups for no change in the kind of risk, and
  lengthening it past a minute stops being "within seconds". Not configurable: a setting would be a way to switch revocation
  off by mistake, and nobody has asked to tune it.
- **Reads stay at one connection:** the lookup is outside the request's transaction (a separate checkout) but at most once
  per user per 15 seconds. `ReadPathConnectionsTest` makes a warm-up request of the same user before every measured one and
  still asserts exactly one checkout for the read.
- The check also answers the session endpoint (`GET /api/v1/auth/session`) with the 401 for a stale session, as any
  request carrying one. The session is gone after that answer, so the next call reports `authenticated: false`. F5b should
  treat a 401 from the session call as "signed out".
- Login and logout are not affected: the login filter answers before this filter runs, so a user with a stale cookie can
  sign in again.

Rejected: checking the database on every request (a second connection per read, against D-110); a shared cache or message
channel between instances (new infrastructure for a 15-second gain); invalidating sessions from a session store (Tomcat's
sessions are in memory per instance and cannot be listed or reached from another); short-lived tokens (the session is a
cookie, by design).

#### D-375 Rules for changing a user

`PUT /users/{id}/role` and `PUT /users/{id}/active` (ADMIN only). An administrator cannot change their own role or
deactivate themselves (409 "You cannot change your own role", "You cannot deactivate your own account"). Setting a value a
user already has is not a change: 200 with the user, no audit entry, no stamp increment, so a repeated click ends nobody's
session. Each real change increments the stamp, is audited in the same transaction (`USER_ROLE_CHANGED` with `from` and
`to`; `USER_DEACTIVATED`; `USER_REACTIVATED`; each with the email) and evicts the user from the cache. An unknown id, or an
id of another tenant (row-level security hides it), is 404. A user may be created with any role, administrator included.
`GET /users` keeps today's four fields for every role and adds `active`, `createdAt` and `passwordSet` for administrators
only (`@JsonInclude(NON_NULL)` on one record, filled by two factories); it is ordered by display name, then email and id.
`findForLogin` ignores inactive users, so the login error stays the generic "Email or password is incorrect"; OIDC refuses
them with the existing 403 "No Lexvay account for this identity" (D-091). A user created but without a password has a null
hash and cannot sign in (`DelegatingPasswordEncoder.matches` answers false for it; tested).

A setup code can be issued for a user who already has a password, and for an administrator: that is the reset. It lets
an administrator set another person's password and so act as them. That power already exists in effect (an administrator
can change any role and create users with the ADMIN role), and here it is attributable: `USER_SETUP_CODE_ISSUED` names the
administrator and `PASSWORD_SET` shows the account owner's first sign-in after it.

#### D-376 The last active administrator cannot be removed, even by two at once

A role change away from ADMIN or a deactivation of an administrator is refused with 409 "At least one active
administrator is required" when the target is the only active administrator. Because one administrator can only remove
another (D-375), the rule is reached only when the caller is no longer an administrator (a stale session within the
revocation window) or when two administrators remove each other at the same moment. Each of those two would read "the
other one is still there" and both would commit. So every role or access change first runs `SELECT id FROM app_user
WHERE role = 'ADMIN' AND active ORDER BY id FOR UPDATE`, which holds all active administrators, and then locks the target
row. The second transaction waits, then re-reads under READ COMMITTED and sees the first one's result. Locks are always
taken in that order (administrators by id, then the target), so two changes cannot wait for each other. The statement
costs a few rows per tenant.

`LastAdministratorConcurrencyTest` runs 30 rounds of two threads on a barrier, each removing the other, half by role and
half by deactivation, and asserts after every round that exactly one administrator is left, one request answered 200 and
the other 401 (its session was already stale) or 409. With the lock on the administrators removed, the test fails (checked: PostgreSQL then reports a deadlock between
the two target locks, in the first rounds). `UserAdminApiTest` reaches the 409 deterministically by making the caller's role stale in the
database behind the cache, which is the cross-instance case.

Rejected: serializable isolation for these two statements (retries in the caller for a table that changes a few times a
year); a PostgreSQL advisory lock (a second mechanism where row locks do the job); a constraint trigger counting
administrators (a trigger on `app_user`, which D-370 keeps ordinary; and the application still has to explain the
refusal).

#### D-377 Audit export: streamed through `CsvWriter`, audited after the last row

`GET /api/v1/audit.csv` (ADMIN) takes the filters of the list (`action`, `entityType`, `entityId`, `userId`, `from`,
`to`), now bound once in `AuditFilter` (a record bound from the query string, with the predicates in one place), so the
file and the screen cannot disagree about a filter. `AuditRepository.streamForExport` is one `@Transactional(readOnly =
true)` statement over `audit_log LEFT JOIN app_user` executed with `JdbcClient.withFetchSize(500)` and delivered row by
row to a callback; the PostgreSQL driver then reads through a cursor, 500 rows at a time. `CsvWriter` gained a mode that
writes each record to an `Appendable` as it is added (the in-memory mode of F8 is unchanged), and `AuditExportService`
connects the two: each row is one CSV record written to the response's `OutputStreamWriter`. Nothing holds more than a
fetch window. Columns: time (ISO-8601 UTC, `Instant.toString`), actor (display name, "System" for work no person started),
action, record type, record id, details (the JSON as stored, as text). Newest first, as on the screen. `Content-Type:
text/csv;charset=UTF-8`, `Content-Disposition: attachment; filename="lexvay-audit-<yyyy-mm-dd>.csv"` (UTC date, built by
the same `CsvWriter.attachment` as the register's).

- The response is opened when the first record is ready, not before: a request that fails before any row (a filter value
  the database refuses, such as a NUL character, which is the 400 of D-096) is an ordinary problem detail and not an
  error body inside a download called `lexvay-audit-....csv`.
- Audited as `AUDIT_EXPORTED` (entity type `audit_log`, no entity id) with the filters, `rowCount` and `complete`, after
  the stream ends, in its own statement. When the stream fails midway (the client left, the connection dropped), rows have
  already left the system, so the entry is written with `complete: false` and the rows delivered; an export that failed
  before the first row writes nothing. This is the one detail beyond the roadmap's "filters and the row count". It does not
  list itself (it is written after the read).
- Runs on the request thread: `StreamingResponseBody` would run the stream on another thread, where the tenant, held in a
  thread-local, is not set and row-level security would show nothing (or, worse, bind the wrong tenant).
- Heap proof (`AuditExportMemoryTest`): 20 000 entries of 3.3 KB are a 63 MB file (measured: live heap 55 MB before, 56 MB at the highest sample while serving). The test measures live data (a
  `System.gc()` then `MemoryMXBean` used heap) before the request and repeatedly while it is served, and requires the
  largest difference to stay under 32 MB. The roadmap asks for before and after a GC, which would also pass for a writer
  that held everything and let go at the end; the peak catches that.

Known cost: audit rows have no index on `(tenant_id, created_at, id)`, so the database sorts the matching rows before the
first one is sent, as it does for the list. That is memory in PostgreSQL (it spills to disk), not in the application, and
not a hot path; an index is an easy follow-up if a tenant's log reaches millions of rows. Rejected: building the file in
memory (D-273's choice, right for the register, which is bounded by programs times jurisdictions, wrong for a log that only
grows); `COPY ... TO STDOUT` (bypasses row-level security reasoning and `CsvWriter`'s formula protection).

#### D-378 Where things live

`UserAdminService` (create, issue a code, role, active), `PasswordSetupService` (the anonymous setup), `SetupCodes`
(generate, hash, compare), `SetupTokenRepository`, `AppUserRepository` (extended; `findAll` replaced by `listAccounts`,
which has the new columns), `UserView`, `UserController`, `AuthController.setup` in `tenant` and `auth`;
`UserStampCheck`, `AccessState`, `TenantContextFilter` in `auth`; `AuditController` (list and export), `AuditExportService`,
`AuditFilter`, `AuditRepository` in `audit`. `UserSetup` (`{user, setupCode, expiresAt}`) is the response of both create
and issue-code, 201. `RegisterController` now uses `CsvWriter.TEXT_CSV` and `CsvWriter.attachment` instead of its own copies.
All five new mutating endpoints are in `Rule9_AuditCoverageTest`, the export is in `AUDITED_READ_ENDPOINTS`, and all new
endpoints have rows in `InputValidationTableTest` (including a signer refused, an anonymous refused, and every malformed
body of the setup endpoint).

#### D-379 What the tests prove, and what they cannot

Every rule of the roadmap has a test (`docs/progress/F5a.md` maps them). Two limits. (1) The 15-second delay between
instances cannot be waited for in a test run; `UserStampCheckTest` proves it with a fake clock (14 seconds: not seen, 16:
seen) and `UserAdminApiTest` proves the consequence at the service (a change made behind the cache). (2) The constant-time
comparison cannot be observed from a test; `SetupCodesTest` proves the behaviour (any position of a difference fails) and
reads the source to prove that no secret is compared with `equals`, which is a structural guard and nothing more.
Follow-ups outside this item: the screens (F5b); the password length limit of D-373.

### FIX-4 — Rule source backoff, live search terms, PDF page cap at upload (D-380 to D-389)

**D-380 — Migration `V016__rule_source_schedule.sql`: a source is due when `next_check_at` has passed.** `rule_source` gains
`next_check_at timestamptz NOT NULL` and `consecutive_failures integer NOT NULL DEFAULT 0 CHECK (>= 0)`. Existing rows are backfilled with
`coalesce(last_checked_at + check_interval_hours * interval '1 hour', now())` (a never-checked source is due at once), then `SET NOT NULL`
and `DEFAULT now()`, so a new source is due at once without `RuleSourceRepository.insert` naming the column. The backfill updates rows of
every tenant and the V011 trigger `rule_source_monitoring_only` would refuse it, so, as V013 does, the migration lifts `FORCE ROW LEVEL
SECURITY` and drops that trigger for its duration and restores both in the same transaction; the recreated trigger allows `active`,
`last_checked_at`, `last_content_hash`, `next_check_at` and `consecutive_failures` (the URL, label, license type and interval stay immutable,
`Rule5_RuleSourceImmutabilityTest`). The scheduler's predicate (`RuleSourceRepository.DUE`) is `rs.active AND rs.next_check_at <= now()`,
with the partial index `rule_source_due_idx ON rule_source (next_check_at) WHERE active`. The V011 policies are unchanged. The backfill
statement is tested from the migration file itself against a temporary table (`RuleSourceScheduleBackfillTest`, the V013 technique). The
migration is V016 because V015 is reserved for F5a; Flyway accepts the gap (V006 is also unused).

**D-381 — The schedule is set in the statement that records the check (`RuleSourceRepository.markChecked(id, hash, outcome)`).** One
`UPDATE` computes both columns from the stored row: after UNCHANGED, CHANGED and DRAFT_EXISTS (the page was read), `consecutive_failures = 0`
and `next_check_at = now() + interval`; after FETCH_FAILED, `consecutive_failures + 1` and `next_check_at = now() + least(interval, 15
minutes × 2^(failures − 1))`, which in the statement is `2^consecutive_failures` of the row before the update. The delays are 15, 30, 60,
120 minutes and so on, never longer than the source's own interval (a one hour interval retries after 15, 30, 60, 60). The exponent is capped
at 12 inside `power(2, ...)`: 15 minutes × 4096 is 1 024 hours, more than the longest interval the table allows (720 hours), so the cap
changes no result and keeps a source that has failed for years from overflowing a `double` (`aVeryLongRunOfFailuresStillWaitsTheIntervalAndDoesNotOverflow`
sets 5 000 failures). The base is `RuleSourceRepository.RETRY_BASE_MINUTES = 15`. Computing from the row, not in Java, means two checks of one
source cannot race a read-modify-write of the counter. `now()` is the transaction time, so `last_checked_at` and `next_check_at` differ by exactly
the delay, which the tests assert in minutes. A manual check (`POST /rule-sources/{id}/check`) changes nothing when it is queued; when it
runs, its outcome sets the schedule like any other check, so a manual success on a failing source clears the failures and a manual failure
extends the backoff. DRAFT_EXISTS counts as a success: the page was fetched and read; the draft waiting for review is a person's task, not a
fault of the source. This settles D-189 (3): a failed check no longer waits a full interval.

**D-382 — API.** `GET /rule-sources` adds `nextCheckAt` (never null) and `consecutiveFailures` to each source (`RuleSourceView`); nothing else
changes and no frontend file is touched. A deactivated source keeps its schedule columns; the scheduler skips it because of `rs.active`, and
re-activating it makes it due when `next_check_at` has passed.

**D-383 — Golden search terms.** `eval/golden.yml` entries gain `searchTerms` (3 to 8 terms) for all 42 golden requirements; the header says they
are fictional and written from the requirement text alone, to be changed only when a requirement changes, never to raise a number. The key is
camelCase like the file's other keys (`quoteContains`) and `eval/retrieval/queries.yml`; the roadmap writes `search_terms`, which is the name in
the replace endpoint's JSON, where `Golden.requirementList` does use it. `YamlFile` rejects unknown fields, so a misspelt key fails at once.
`GoldenFileTest` runs every list through `SearchTerms.normalise` (title and text of the entry) and asserts it comes back unchanged, so the golden
terms are exactly what extraction would store: nothing a person later sees dropped. Honest limit: the terms use the vocabulary any counseling
syllabus uses and overlap the scripted terms of `seed/fake-llm.yml` (both are natural vocabulary for the same subjects, `confidentiality`,
`site supervisor`, `group dynamics`); the file was written from the requirement texts and not copied from the seed, but its author had read the
seed. The scripted `make eval` does not use them: its model's terms are the seed's, so comparing the two would measure the seed against itself.

**D-384 — The live report: overlap of the model's terms with the golden terms.** `EvalModel` already replaces the model's extraction by the
golden list in live mode; it now also remembers, before replacing, the stored `searchTerms` of every requirement the model proposed
(`proposedTerms()`), and the golden list it substitutes carries the golden terms (`search_terms` in the replace body), so retrieval and matching in
the live run use hand-written terms and the evaluation of matching is not confounded by the model's vocabulary. `TermOverlap.measure` gives, per
golden requirement, the Jaccard index of the two sets of normalised terms (shared over all distinct; a requirement the model did not extract
proposes none and scores 0; both empty is 1, which the golden file never produces), and the mean. `EvalReport` prints the mean after the
metrics table and one line per requirement (percent and the model's terms) in live mode only; `termOverlap` is null for the scripted model.
There is no threshold: a different vocabulary is not an error (terms only widen the shortlist, D-256), and a limit would invite tuning the
prompt to the golden file. Rejected: scoring the model's terms by retrieval recall (a second live evaluation per run, and `make eval-live`
already measures what retrieval finds with the golden terms).

**D-385 — The PDF page cap is checked at upload.** `ContentLimits.requirePdfPages(byte[])` loads the PDF with PDFBox's temp-file-only stream
cache (`IOUtils.createTempFileOnlyStreamCache`), reads `getNumberOfPages()` and closes it; `DocumentService.upload` calls it for a PDF next to
the DOCX check, before the duplicate check, the file store and the job. An over-long file is the usual 400 problem detail "PDF has 501 pages; the
limit is 500", nothing stored, no document row, no `DOCUMENT_UPLOADED` audit row and no ingest job. The upload's bytes are already one array in
the heap (`MultipartFile.getBytes`); PDFBox reads from that array without copying it, and the stream cache that would otherwise hold decoded
streams in memory is on disk, so a 25 MB PDF does not sit in the heap twice. A file PDFBox cannot open at all is left to ingest, which reports
"File could not be parsed as a PDF" (as before, and like the DOCX check). Ingest keeps its own check (`TextExtractor.pdfPages`), because rule text
fetched by a rule source reaches the extractor without passing through upload; the same `requirePdfPages(int)` and message serve both. The old
test that expected a 202 and a FAILED ingest for 501 pages now expects 400 at upload; `ContentLimitsTest` keeps the extractor's own refusal.
Cost: a PDF is opened twice (structure only at upload, then fully at ingest).

**D-386 — `IndexUsageTest` covers the due query.** The scheduler's statement (`ENQUEUE` with `DUE`, as the application runs it) is planned under the
worker flag with all tenants, and `rule_source_due_idx` must be in the plan. The seeded database has one source per tenant, all due, which is too
few for the planner to prefer an index, so `PerfSeed.addHistoryVolume` adds 200 synthetic sources per seeded source (not due for 1 to 200 hours),
the situation of a long-running installation where a few sources are due among many. The test's kept-index assertion now names V016.

**D-387 — Tests.** `RuleSourceScheduleTest` (new source due at once; 15, 30, 60, 120, 120 minutes with a two hour interval and failures 1 to 5;
the hash is not moved by a failure; reset after CHANGED, UNCHANGED and DRAFT_EXISTS; 5 000 failures do not overflow; queueing a manual check
leaves the schedule alone and its outcome sets it; the API fields), `RuleSourceEnqueueTest` (the due predicate now reads `next_check_at`, plus a
failed source queued again only when its backoff has passed), `RuleSourceScheduleBackfillTest`, `Rule5_RuleSourceImmutabilityTest` (the two new
columns are updatable, the message lists five), `RuleSourceApiTest` (the two fields), `IndexUsageTest`, `ContentLimitsTest` and
`DocumentUploadTest` (501 pages refused at upload with nothing stored, 500 accepted), `GoldenFileTest` (terms 3 to 8 and normaliser-stable, the
replace list carries them), `TermOverlapTest` (Jaccard, per-requirement and mean, live and scripted report), `EvalModelTest` (the model's terms
are remembered and the golden terms are stored).

**D-388 — Not done, on purpose.** No frontend change (the sources screen does not exist yet; the two new fields are in the API for it). No
setting for the base delay: 15 minutes is a constant beside its explanation, and the interval is already per source. `make eval-live` could not be
run here (no `ANTHROPIC_API_KEY`), so the live report's text is proven by `TermOverlapTest` and `EvalModelTest`, not by a live run.

**D-389 — Open points for the orchestrator.** (1) The YAML key is `searchTerms`, not `search_terms` (D-383). (2) V016 with V015 absent from `main`
at the time of writing; if F5a is not merged when this one is, nothing in this branch needs renumbering (Flyway runs V016 on a database that has no V015). The
risk is the other order: if F5a merges after this item, a database that already applied V016 fails Flyway's validation on V015 (out-of-order is
not enabled in `application.yml`). Merge F5a first when it is close, or renumber one of them.

### UI2-0 — Design v2 foundation, shell and sign-in (D-301 to D-319)

**D-301 — Fonts: Inter and Source Serif 4, variable weight axis, latin and latin-ext.** `@fontsource-variable/inter`
5.3.0 (published 2026-07-19) and `@fontsource-variable/source-serif-4` 5.3.1 (published 2026-10-11), both the
registry's `latest` and more than 14 days old on 2026-11-27; `@fontsource/ibm-plex-mono` 5.3.0 stays (400, latin and
latin-ext). `@fontsource/ibm-plex-sans` and `@fontsource/ibm-plex-serif` are removed. The packages' CSS entry points
declare every subset (cyrillic, greek, vietnamese), so `styles.css` declares its own `@font-face` rules for the two
subsets the product shows (D-084), pointing at the packages' `wght` files: Inter 48 kB + 85 kB, Source Serif 4 51 kB +
42 kB, loaded only when a glyph in their range is on the page, `font-display: swap`. Fonts are media files, never in
the JS bundle. Rejected: the `opsz` files (Inter latin 73 kB instead of 48 kB for an optical-size axis the Display size
does not need at 40 px with -0.025em tracking); italic files (no screen sets italic type); the packages' `index.css`
(every subset).

**D-302 — Metric-matched fallbacks, measured.** Two `@font-face` fallbacks with `size-adjust`, `ascent-override`,
`descent-override` and `line-gap-override: 0%` keep text in the same box before and after the swap. Values measured in
Chromium (Playwright): the width of a 96-character sample at 450 in the web font against 400 in the fallback gives
`size-adjust`; `fontBoundingBoxAscent` and `Descent` at 1000 px divided by it give the overrides. Inter on Arial
(`local('Arial'), local('Liberation Sans')`): 107.47 %, 90.17 %, 22.43 %. Source Serif 4 on Times New Roman
(`local('Times New Roman'), local('Liberation Serif')`): 116.58 %, 88.87 %, 28.74 %. Liberation Sans and Serif are the
metric twins Linux ships. Rejected: Georgia for the serif (closer in width, 107.17 %, but absent from most Linux
installs); values copied from other projects (not verified against these exact files).

**D-303 — The token table as data, derived tokens, and the contrast test.** `theme/tokens.ts` holds the DESIGN.md
table as light and dark hex pairs (the only copy) plus derived tokens the table implies: `lineStrong` (form-control
boundary, #80868F / #6F7783, 3:1 against every page background; `line` is a hairline at 1.2:1), `onAccent`,
`accentHover` and `accentActive` (4 % and 8 % black over the accent: "buttons darken 4 %"), `foundTint`,
`ambiguousTint` and `missingTint` (10 % light, 16 % dark over surface, computed by `mix`), and `accentFill` (26 % /
24 % accent over surface, the in-review fill of a StateMap tile). `tokens.spec.ts` computes the WCAG ratio of every
text and background pair the product uses, in both themes: 4.5 for text, 3.0 for 18 px text, icons and control
boundaries. Two consequences of the test, both deviations from DESIGN.md wording, made because DESIGN.md also says
every text pair passes AA: placeholders use `text-2`, not `text-3` (placeholders are 14 px content and `text-3` reaches
only 3.17:1 on surface); and the codes on non-target StateMap tiles use `text-2` at weight 500, not `text-3` (2.80:1 on
`surface-2`). `neutral` is a fill only (not-reviewed ring segments): at 2.6:1 it is never text or an icon. Rejected:
colour-mix() in CSS for the tints (the test could not read the result).

**D-304 — Elevation as one value per level; CSS names as DESIGN.md spells them.** `light-dark()` takes colours only, so
each shadow layer carries its own `light-dark()` colour: light themes get the DESIGN.md shadows, dark themes get
transparent shadows, the 1 px `line` ring and the inner top highlight, in the same `--p-lx-e1/e2/e3` value. The preset
publishes `extend.lx` under kebab-case names (`surface2` becomes `--p-lx-surface-2`, `accentTint` `--p-lx-accent-tint`),
the names DESIGN.md uses. Every old name was renamed in one pass (hairline → line, text-secondary → text-2, primary →
accent, card → surface, sunken and hover → surface-2, selected → accent-tint, control → line-strong, `*-bg` → `*-tint`,
shadow → e3, notice-* removed); no old name is left in `src/`. Rejected: `colorScheme.light/dark` blocks (D-081).

**D-305 — Shared preset: Button, Badge, Popover and Tooltip; every other set from its chunk (PERF-FE-1 cut 2).** The preset
holds the semantic tokens (form fields, lists, overlays, navigation, radius, focus ring), which restyle every PrimeNG
component, plus the component sets the shell paints first: Button, Badge (work queue count), Tooltip (rail, program pill) and Popover (InfoTip,
StateMap card, the shell's menus). Lexvay's component overrides live in one file per set beside the preset
(`datatable-`, `dialog-`, `message-`, `progressbar-`, `skeleton-`, `tabs-`, `tag-`, `toggleswitch-tokens.ts` and
`segmented-tokens.ts` for SelectButton and ToggleButton), and each screen's `<screen>-theme.ts` (new: `audit-theme.ts`,
`report-theme.ts`; Home and Sign in register theirs in the component) names every set it renders, registered through
`registerComponentTokens`. Splitter and RadioButton overrides live in `review-theme.ts`, the Toast set in the deferred
`ShellToast`, the CommandMenu and Menu sets in the palette. IconField and Tooltip were in the preset but rendered
nowhere; they are gone. Drawer, Stepper and FileUpload have no Lexvay overrides beyond the semantic tokens yet: Drawer
is F4's (Aura set, Lexvay overlay tokens), Stepper and FileUpload get theirs with their first screen (UI2-4, F2).
Rejected: one module with every Lexvay set (each chunk would load all of them); keeping Lexvay overrides in the preset
while chunks register raw Aura sets (a later `updatePreset` with an Aura set replaces the override).

**D-306 — The shell's own copy of its icons (PERF-FE-1 cut 1); icons with the piece that shows them.** `SHELL_ICONS`
in `theme/icons.ts` is a generated copy of 13 regular-weight SVG strings (`node scripts/shell-icons.mjs` rewrites them
from the package; `icons.spec.ts` asserts each equals the package's), so `main` never imports
`@ng-icons/phosphor-icons`. It holds the rail's, the top bar's (search, bell, rail toggle, InfoTip) and Home's icons.
The account menu, work queue popover, command palette, toast, StateMap and StatusIcon each register the rest in
`viewProviders` (`ACCOUNT_MENU_ICONS`, `WORK_QUEUE_ICONS`, `PALETTE_ICONS`, `SHELL_TOAST_ICONS`, `STATE_MAP_ICONS`,
`STATUS_ICONS`), and `icons.spec.ts` has a unit for each. The work queue button uses `phosphorBell` because F4's
Evidence requests screen took `phosphorTray`. `paletteShortcut` moved to `command-palette/shortcut.ts` so the shell does
not reach the palette's command list.

**D-307 — Motion foundation.** `MotionPreferences` (`core/motion.ts`) is the one signal for reduced motion; the
sign-in story, count-ups and route transitions read it, and `styles.css` zeroes every duration token and animation
under the same media query. `lxEnter` is a directive with a host `animate.enter: 'lx-enter'` binding and a `--lx-i`
index; the keyframes stagger 30 ms and cap at the tenth item. `lxCountUp` drives its text from an effect-less
`Animation(new KeyframeEffect(null, [], { easing }))`, whose computed progress is already eased, counts only the
first value it receives, and reserves the final width in `ch` (tabular figures). The router uses
`withViewTransitions({ skipInitialTransition: true })` and skips a transition when motion is reduced or when the
navigation stays on one screen (`sameScreen` in `core/navigation.ts`: a review tab or filter must not cross-fade the
page). Browsers without the API fall back to an instant change (Angular's own check). In development builds Angular
logs each skipped transition ("AbortError: Transition was skipped") with console.error; production builds do not. Entrances of the largest text never fade from transparent (the sign-in
headline and form rise by transform only): a fade would hold Largest Contentful Paint until it ends (1.55 s measured,
224 ms without it). Rejected: `@angular/animations` (deprecated, DESIGN.md); a timer-driven count-up (not the Web
Animations API the spec names).

**D-308 — StateMap.** `ui/state-map/`: `us-tiles.ts` (the 51 tiles in the 8 × 12 grid with names, the only copy),
`state-map-model.ts` (pure: cells, accessible names, reviewed share, keyboard movement; Vitest), the component and its
CSS. A tile is a stack of layers (accent tint, a bottom-up fill scaled with `transform: scaleY`, the outcome fill or
the seal ring) whose opacity and transform alone change, so the wave entrance, the sign-in story and data changes run
on the compositor. Keyboard: one tab stop; arrows go to the nearest target within 45 degrees (along-axis distance plus
twice the across distance), Home and End to the first and last; Enter or a click runs the tile's action; Escape
closes the card. The card is a PrimeNG Popover with `focusOnShow` false, opened on hover (120 ms delay) or focus, and
kept open while the pointer travels into it. `interactive` false draws a picture hidden from assistive technology (the
sign-in story). Sizes: hero 44 px shrinking with its container (container query), compact 34 px (30 px read as cramped
next to a Display headline), mini 16 px without codes or card. No screen shows the interactive map before UI2-1, so a
development-only route `dev/ui` (`dev/ui-harness.ts`, dropped from production builds like F1's viewer harness, D-225)
shows the map at all three sizes with the rings, stats, seal and status icons, for the screenshot review and the items
that adopt them. Rejected: SVG for the tiles (text rendering and focus rings are crisper as HTML); a hand-built hover
card (PrimeNG provides Popover).

**D-309 — ProgressRing animates only stroke-dashoffset.** Segments are drawn last first, each as an arc from the top to
its cumulative end (`pathLength="100"`, `stroke-dasharray: 100 200`, `stroke-dashoffset: 100 - end`), so each earlier
arc covers the start of the next and the visible part of each is its own segment. The first-view sweep is a keyframe
from offset 100, delayed 90 ms per segment; later changes transition the offset. Single values use round caps,
segments butt caps.

**D-310 — StatusIcon mapping.** `requirementStatus(finding, decision, stale)` in `ui/status.ts`: undecided shows the
finding outlined (check, question, cross circle in its status colour), a decision shows filled (accepted: filled check
in found; evidence requested: filled paper plane in ambiguous; gap confirmed: filled cross in missing), stale shows
"outdated by a change". The label always names the finding and the decision ("Evidence found, accepted"); the test
asserts all twelve combinations have distinct labels.

**D-311 — Command palette on PrimeNG CommandMenu.** PrimeNG 22 ships `CommandMenu`, so the palette is that component
in a top-positioned modal Dialog, not a hand-built list. Matching is our `fuzzyScore` (exact dynamic programming over
subsequence placements: 1 per character, 8 at a word start, 10 for a run, 4 at the very start, shorter text wins a
tie) through CommandMenu's `filter`. Sources (`commands.ts`, pure): the screens from `navLinks` (the rail's own list),
"Review <state>" for the program's targets, the latest assessment's requirements (one `GET
/assessments/{id}/mappings` without a jurisdiction, the first time the palette opens for that assessment, listed only
while searching), and actions by role (Run assessment, any role, as the endpoint allows, followed by its job to a
toast; New rule version for contributors and administrators, through `rules?new=1`; Download register, the audited CSV
link). Recent commands (five, per user, `localStorage`) come first. The first match is always highlighted, so Enter
opens it. Two accessibility fixes: CommandMenu's input names a list id its list does not carry, so the list gets that
id through pass-through (until the dialog has rendered, a few hundred milliseconds, the reference dangles; the e2e test
waits for it before running axe); and the dialog is not closable (its close button would sit in the visually hidden
header), so Escape is handled on the menu. Review takes `jurisdiction` and `ref` query parameters as inputs, so a
link opens a state or selects a requirement even while Review is already open; Rules takes `new`. A declaration file
(`src/primeuix-commandmenu.d.ts`) types the Aura CommandMenu set, which @primeuix/themes 3.1.1 ships without one.

**D-312 — Shell.** Top bar: the mark and wordmark (link to Programs), the program pill (PrimeNG Select styled as a
pill up to 360 px; a longer name is cut after its last whole word, `truncateAtWord`, with the full name in a PrimeNG
Tooltip and in the accessible name "Program, <name>"; there is no short name in the model and deriving one from degree
prefixes would guess), the search field that opens the palette (with the platform's key hint), the Demo data pill with an
InfoTip holding the notice, the work queue button (bell, count from `GET /work-queue/count` after every navigation)
and the avatar (initials). The palette, work queue popover, avatar menu, program switcher and toast are `@defer`
chunks (on idle; the palette also when asked), each with its icons and token sets. The fictional-data notice stays
in the DOM as a print-only `aside` named "Fictional data notice", so paper keeps it (D-276). Work queue items link by
kind: reviews and ready-to-sign to Review on the state, evidence requests to F4's inbox, rule versions to Rules on the
first version, open changes to Changes in the first program they touch, failed assessments to the overview; the list
shows eight and its words put the count first ("8 to review"). The shell learns the URL at `ResolveEnd`, just before
the screen is created, and keeps its frame `visibility: hidden` until the first navigation has resolved, so the bar,
the rail and the screen arrive in one frame: a reload of the overview measured CLS 0.19 before (the frame painted, then
the bar pushed it down) and 0.00 after.

**D-313 — Rail.** The active item's pill is one absolutely positioned element moved by `transform: translateY`
(320 ms ease-out) to the active link's offset after each navigation and resize, so it glides. "Collapse navigation"
keeps icons only and is remembered in `localStorage` (`lexvay.rail.collapsed`); under 1200 px the rail is always
compact; under 640 px it is a bottom tab bar of icons with an accent underline. Counts are pills, hidden from
assistive technology, with the sentence in `aria-describedby` (D-087). A `NavLink` has a full `label` and a `short`
one the rail prints: only Evidence requests differs ("Requests", word budget), and its link keeps "Evidence requests"
as accessible name and tooltip. Rail tooltips (PrimeNG Tooltip, its token set in the shared preset) show the full name
where it differs and every name while the rail is collapsed.

**D-314 — Sign in.** `sign-in-story.ts` (pure, tested) gives the map at any moment of a six-second turn: sixteen states
start in review 180 ms apart from west to east, their fill rises over 2.3 s, each is signed with its outcome at 2.6 s,
and everything fades back at 5.4 s. The component advances it every 250 ms (CSS transitions smooth the steps), stops
it on the first input event, and under reduced motion shows the story's fullest frame still. Errors appear under the
field they concern. The stage is a container: the map spans 76 % of it with tiles up to 56 px (about 51 px in a
1440 px window, through the StateMap's `--lx-map-max-tile` hook), the map and the headline are centred together, and
the headline grows from 40/44 to 56/60 with the stage; the form is centred in the right column with the mark above it.

**Demo account chips: from the build, not from `/session`.** Point 6 shows them "only when `/session` reports
`demo: true`", but `GET /auth/session` computes `demo` from the signed-in user's tenant, so it is false before sign-in,
and no endpoint lists demo accounts to an anonymous caller. Following the coordinator's choice (a public endpoint or
the build's environment), the accounts come from the build: `core/demo.ts` reads the build-time constant
`LEXVAY_DEMO_ACCOUNTS` (email, name, role), which `angular.json` sets in the development configuration to the four
fictional Northfield accounts of `seed/manifest.yml` (the stack `make dev` and `make e2e` serve) and to an empty list
otherwise; a demo deployment passes `--define 'LEXVAY_DEMO_ACCOUNTS=[...]'`. Each chip names the role ("Signer"), its
accessible name adds the person, and it fills the email and moves focus to the password. Production builds carry no
account (checked: no seed email in the production bundle). Rejected: an anonymous endpoint listing a tenant's users
(a backend change that exposes account names before sign-in; worth an orchestrator decision if demo deployments
should not need a build flag).

**D-315 — Seal and mark.** `lx-seal` is an SVG ring in the seal colour: outcome and date set in caps on a circular path,
the signer's initials in the centre, the whole sentence as its accessible name; UI2-2 animates the stamp on the host.
`lx-mark` is the monogram: an accent tile with an L whose foot rises into a tick, in the on-accent colour.

**D-316 — Global styles that point 7 allows.** The shared classes follow the v2 type scale and word budget without
redesigning layouts: `h1` is the Title size, `.lx-label` and table headers are sentence case (no uppercase eyebrows
anywhere), document frames, the rules panes and the report sheet are separated by elevation instead of borders, and
radii follow DESIGN.md. Screen layouts are unchanged.

**D-317 — Bundle.** Initial bundle 606.77 kB raw, 155.87 kB estimated transfer (main 597.55 kB, styles 9.22 kB; the rail and pill tooltips cost 17.6 kB of it), down
from 695.03 kB after PERF-FE-1: cut 1 and cut 2 plus the deferred shell pieces. The budget is 680 kB warning and 760 kB
error, as the coordinator set for an initial bundle under 660 kB.

**D-318 — Merges.** F4 (evidence requests) and PERF-FE-1 merged while this item was in flight; both were merged into
the branch, F4's screens take the v2 tokens and the per-chunk pattern, and the rail and palette list Evidence requests.

**D-319 — End-to-end changes.** `e2e/shell.spec.ts` covers the sign-in story (plays, stops on typing, off under reduced
motion), the palette (shortcut, jump to Review for a state and for a requirement, recents, the shortcuts list), the work
queue popover, the avatar menu's theme across a reload, sign out, the rail collapse, and axe in both themes at 1440 and
400 px on sign-in and overview (plus the palette, work queue and avatar menu). `review-and-sign.spec.ts` checks the
Demo data pill and its InfoTip instead of the notice strip; its theme test moved to the shell spec (the theme control
left the top bar).

### F10a — Coverage matrix API (D-390 to D-397)

#### D-390 One statement, one row, the parts built as JSON

`GET /api/v1/programs/{id}/coverage?jurisdiction=XX` (any signed-in role) runs `CoverageRepository.MATRIX` in one
read-only transaction (`CoverageService`, D-110) and gets one row back: program, jurisdiction, whether the jurisdiction is
a target, the assessment id, the rule version, and `courses` and `requirements` as `jsonb` that Jackson reads into the
view records of `CoverageMatrix`. A matrix is a nested document (requirement, then cells, then program-level documents),
so the nesting is done where the data is, by `jsonb_agg` over CTEs, instead of by one query per level or by a result set
the Java code regroups. The CTEs are `target`, `latest`, `mappings`, `courses`, `evidence`, `cited`, `cell_rows`,
`program_rows` and `reach`.
- No row means the program does not exist for this tenant (row-level security, rule 7): 404. A row with `is_target =
  false` means the jurisdiction code is unknown or is not a target of the program: 400 (`IllegalArgumentException`). The
  statement then reads nothing else (`latest` is empty).
- The jurisdiction parameter is required and `^[A-Z]{2}$`, like the other jurisdiction filters (400 otherwise).
- Reused, not copied: `MappingRepository.current("= (SELECT id FROM latest)")` (D-364), `MappingRepository.LATEST_EVENT`
  (D-003). Two predicates the statement needed existed only as text inside other statements, so they were extracted
  (D-396).

#### D-391 What a cell is

A cell says that the verified evidence of a requirement cites a document of a course version. The evidence of a mapping is
its own span (`source = SYSTEM`, only for `method = MATCH`) and every evidence response of that mapping (`source =
PERSON`), whichever request it answered. There is one cell per (course version, source), so a course version that carries
both a system quote and a person's quote has two cells; `quoteCount` is the number of distinct evidence spans behind
the cell. Documents without a course version (degree plan, handbook, accreditation letter) are listed once per
(document, source) in `programLevel`. Cells follow the order of `courses` (required first, then code), `SYSTEM` before
`PERSON`; `programLevel` follows file name.

#### D-392 Risk is a description, with a fixed order of tests

`risk` is one of five fixed names, computed in the statement from the cited evidence of each mapping:
1. `NONE` when the mapping was checked by the rules engine (D-393);
2. `OUTDATED_EVIDENCE` when the mapping is stale or cites a superseded course version (D-394);
3. `NO_EVIDENCE` when nothing is cited;
4. `NONE` when any program-level document is cited (the evidence does not rest on a course);
5. `ELECTIVE_ONLY` when every cited course version is an elective (`required = false`);
6. `SINGLE_COURSE` when exactly one course version, a required one, is cited;
7. `NONE` otherwise.
An elective that is the only course is `ELECTIVE_ONLY`, not `SINGLE_COURSE` (test 5 runs first). The names are fixed and
neutral; a screen words them as "rests on an elective", "rests on one course", "evidence is on an outdated course version" and "no evidence cited". The response
never says that anything is sufficient, met or missing for a licence (rule 1); `finding` is the system's proposal as
stored and `decision` the latest review decision, `null` when nobody decided or the requirement was reopened (the
response of an evidence request reopens it).

#### D-393 Numbers checked by the rules engine have no cells, of any source

A mapping with `method = RULES_ENGINE` cites a program fact (a quote of the degree plan or handbook) as its span. That
span is the input of an arithmetic check, not curriculum evidence, so the matrix leaves it out: no cells, no program-level
documents, `risk = NONE`. An evidence response on such a mapping (possible when the credits are missing from the program)
is not shown either, so that the row never has cells (spec point 2). Rejected: showing the fact's document as
program-level evidence (a number row would then look like a document row).

#### D-394 Evidence on a superseded course version is outdated evidence, never "no evidence"

`courses` are the program's current course versions (no successor, `CourseRepository.SUPERSEDED`). Evidence (system or
person) that cites a document of a superseded course version is not a current-version cell; it is listed in the
requirement's `outdatedCells`: `{ courseVersionId, code, catalogYear, supersededBy, source, quoteCount }`, where
`courseVersionId` and `catalogYear` are the outdated version's and `supersededBy` is the course's current version, a
column of `courses` (not the next version of a chain), so a screen draws the cell under that column with an "outdated"
marker. `cells` keep only current versions.
`risk` gains `OUTDATED_EVIDENCE`, tested right after the rules-engine test and before every other value: it applies when
the mapping is stale (a rule or course change outdated it) or any of its evidence cites a superseded version. It outranks
`NO_EVIDENCE`, `ELECTIVE_ONLY` and `SINGLE_COURSE`, so "no evidence cited" never describes a requirement whose evidence
exists. A stale mapping is `OUTDATED_EVIDENCE` whatever it cites, including a proposal of `EVIDENCE_MISSING` that a rule
change outdated; a mapping that cites nothing and is not stale stays `NO_EVIDENCE` after a course change.
There is no `stale` field: the fact has one representation, the risk value, with the cells behind it. The seeded tenant
shows it: UT-7, AZ-4 and TX-8 cite the COUN 625 syllabus of 2026-27, which the change-tracking demo supersedes; each is
`OUTDATED_EVIDENCE` with one outdated COUN 625 cell.
Rejected: an extra column per old version (the matrix would stop being the current curriculum and grow with every catalog
year); moving the cells to the successor version (invents evidence the successor's documents have not been shown to
contain, rule 2).

#### D-395 Before the first assessment, and a rule change in flight

With no finished assessment the response is 200 with `assessmentId` and `ruleVersion` null, no requirements, and the
courses (a screen shows an empty state, as the overview does). `ruleVersion` is the rule version of the first
requirement of the current mappings. After a rule change the old list stays until the successor's mappings exist (D-066),
and every row except the rules-engine numbers is `OUTDATED_EVIDENCE`; afterwards only the successor's rows appear, with new mapping ids, in its `ordinal` order (FIX-1).

#### D-396 Two predicates extracted, nothing else touched

- `AssessmentRepository.LATEST_FINISHED` (`a.status = 'FINISHED' ORDER BY a.finished_at DESC, a.created_at DESC LIMIT 1`):
  used by `latestFinished` and by the matrix. The work queue's `latest` CTE (a lateral join with the same ordering) was not
  rewritten in this item; the texts agree and a follow-up may take it.
- `CourseRepository.SUPERSEDED` (`EXISTS (SELECT 1 FROM course_version n WHERE n.supersedes_id = cv.id)`): the document
  list's `current` flag (`DocumentRepository.viewsByProgram`, behaviour unchanged) and the matrix's `courses` use it.

#### D-397 Latency, plan and indexes (no migration)

`GET /programs/{id}/coverage?jurisdiction=CA` on the seeded program: p50 3.15 ms, p95 4.91 ms, p99 5.88 ms in the seed-only pass and p50 2.64, p95 4.15, p99 5.28 ms with 62 tenants (budget 40 ms), 1.0 connection per call, 1 666 bytes on the
wire (4 921 uncompressed). `IndexUsageTest` runs `EXPLAIN (ANALYZE, BUFFERS)` of `CoverageRepository.MATRIX` as the
application role on the roughly 200-tenant database and fails on a sequential scan of `mapping`, `evidence_span`,
`evidence_response` or `document` that is actually executed (plan in `backend/target/perf/coverage-plan.txt`): 0.31 ms
execution. The mappings use `mapping_assessment_jurisdiction_idx`, review events
`review_event_mapping_idx`, the latest assessment `assessment_program_status_finished_idx`, the superseded-version test
`course_version_supersedes_id_key`. The plan contains a `Seq Scan on evidence_response` that is "never executed": the
perf database holds no evidence responses, so the planner estimates one row and the join has nothing to probe. With rows
it can use `evidence_response_mapping_idx (mapping_id, created_at)`, which already serves this join; no index was added
because none can be shown to be needed. `ReadPathConnectionsTest` (one connection) and `EndpointLatencyTest` list the
endpoint; `InputValidationTableTest` has eight rows for it.

### SEC-2 — Argon2id passwords and audit list plans (D-400 to D-407)

#### D-400 The application encoder is Argon2id behind the delegating encoder, at 32 MiB, 4 passes, one lane

`PasswordEncoders.application()` (replacing `PasswordEncoderFactories.createDelegatingPasswordEncoder()` in
`SecurityConfig`) is a `DelegatingPasswordEncoder` whose encoding id is `argon2` and whose map holds exactly two
encoders, `argon2` and `bcrypt`. New hashes look like `{argon2}$argon2id$v=19$m=32768,t=4,p=1$...`. The default for
matching a value without a prefix is bcrypt, so a hash written before F5a added the prefix keeps verifying.

**Parameters, measured on this machine** (Apple silicon laptop, JDK 21.0.8, Spring Security 7.1.3 with Bouncy Castle
1.87, mean of 15 verifications after warm-up, other work running):

| Encoder | Verify |
|---|---:|
| bcrypt, cost 10 (what `{bcrypt}` hashes cost today) | 58 to 62 ms |
| `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` (16 MiB, 2 passes) | 14 to 17 ms |
| OWASP minimum, 19 MiB, 2 passes | 17 to 20 ms |
| OWASP alternative, 46 MiB, 1 pass | 29 to 44 ms |
| 46 MiB, 3 passes | 83 ms |
| 19 MiB, 7 passes | 70 ms |
| **32 MiB, 4 passes, 1 lane (chosen)** | **73 ms** |
| 64 MiB, 3 passes | 116 to 123 ms |

The Spring defaults are below the 50 ms floor the roadmap sets for this item, so they are not used. 32 MiB with 4 passes
has more memory and more passes than every combination OWASP's Password Storage Cheat Sheet lists (46 MiB with 1 pass,
19 MiB with 2, 12 MiB with 3, 9 MiB with 4, 7 MiB with 5; all with one lane), takes 73 ms, which is about what bcrypt
took, so sign-in latency and the test suite do not change, and needs 32 MiB of heap while it runs. The salt is 16 bytes
and the hash 32. The parameters are written inside every hash, so changing them later makes `upgradeEncoding` true for
older hashes and D-401 replaces them at the next sign-in; no migration is needed.

Only `argon2` and `bcrypt` verify. Spring's `createDelegatingPasswordEncoder()` also accepts `{noop}`, `{sha256}`,
`{MD4}`, `{ldap}` and others, which would make a value such as `{noop}secret` in `app_user.password_hash` a working
password for anyone able to write that column. `PasswordEncodersTest` proves that those, a plain-text value, an empty
value and null do not verify.

**Dependency (D-002 addendum).** `Argon2PasswordEncoder` needs Bouncy Castle's low-level API at run time and Spring Security
declares it optional. `org.bouncycastle:bcprov-jdk18on` is pinned to **1.87** (the newest release on repo1.maven.org,
published 2026-11-11, sixteen days before this work; `maven-metadata.xml` lists 1.81.1 to 1.87), as the property
`bouncycastle.version` in `backend/pom.xml`. No Bouncy Castle artifact was on the classpath before.

**Memory.** Each hash holds 32 MiB of heap while it runs, where bcrypt used 4 KiB; D-407 bounds how many run at once.

Rejected: Spring's defaults (14 to 17 ms, below the floor); 64 MiB with 3 passes (116 to 123 ms, twice the heap per
sign-in for a cost this application does not need); PBKDF2 (no memory hardness); scrypt (Spring's encoder is slower to
tune and OWASP ranks Argon2id first); keeping `createDelegatingPasswordEncoder()` and only changing its default (it
would keep every legacy verifier accepted).

#### D-401 A hash that is not the current one is replaced at the next successful sign-in, by Spring's own hook

`LexvayUserDetailsService` implements `UserDetailsPasswordService` as well as `UserDetailsService`. Spring Security's
`DaoAuthenticationProvider` calls `updatePassword(user, newEncodedPassword)` only after the password matched and only
when `PasswordEncoder.upgradeEncoding(storedHash)` is true: for a `{bcrypt}` hash, a hash with no prefix, and an `{argon2}`
hash made with other parameters. A hash of the current kind is left alone, so a second sign-in writes nothing.

The hook runs before the authentication filter's success handler and before any tenant is known, so `updatePassword`
sets the user's tenant, then in one transaction runs one statement and one audit entry:

- `UPDATE app_user SET password_hash = :new WHERE id = :id AND password_hash = :old`. The condition on the old hash is the
  guard against a password set between loading the account and now (a setup code used in that moment): the update then
  changes nothing, nothing is audited, and the sign-in carries on with the account as loaded. `security_stamp` does not
  move, because the person has not changed their password and other sessions of theirs should not end.
- `PASSWORD_REHASHED` on `app_user`, actor the user (`recordAs`, as `PASSWORD_SET` does), detail
  `{"from": "bcrypt", "to": "argon2"}`: algorithm names read from the hash prefix (`PasswordEncoders.algorithmOf`, which
  names an unprefixed hash bcrypt), never a hash or a password. `PasswordRehashTest` searches the whole audit log of the
  tenant for the new hash and the password.

A failure of this write fails the sign-in with the generic 401 and does not leave a half-written state; the database being
unavailable would have failed the audit entry of the success handler anyway. Not done: replacing the hash in a background
job (needs the password, which exists only during the sign-in); replacing it in the success handler (a second place that
knows about hashes, and after the session exists).

`PasswordRehashTest` covers: a prefixed bcrypt hash replaced and audited once, an unprefixed one replaced, a wrong
password changing nothing, the stamp not moving, a second sign-in writing nothing, and the concurrent change not being
overwritten.

#### D-402 Passwords are 12 to 128 characters; the 72-byte rule of D-373 is gone

Argon2id has no 72-byte limit, which was the reason D-373 departed from the roadmap. `PasswordSetupService` now requires
at least 12 and at most 128 characters, counted as code points (an emoji is one character, as for the minimum), and the
400 says "Password must have at most 128 characters". The cap is not a technical limit of the encoder: it bounds what one
request can ask the server to hash and keeps the message the same for the person whatever the script they type in.
Tests: 128 characters accepted, 129 refused (ASCII, two-byte and four-byte characters), a 128-character password of 2-,
3- and 4-byte characters (381 bytes) accepted and then signing in only with all of it, and the last character mattering.

The request size limit (`@Size(max = 1000)` UTF-16 units on the setup endpoint) is unchanged: 128 four-byte characters
are 256 units.

#### D-403 No audit index is added: the index the roadmap names has existed since V001

The roadmap asks for `audit_log (tenant_id, created_at DESC)` if the audit list or export sort without it. V001 already
creates `audit_log_newest_idx ON audit_log (tenant_id, created_at DESC)`, the same columns in the same order, so
`audit_log_tenant_created_idx` would be a second copy of it and V017 is not created (the number stays free).

`IndexUsageTest` now adds audit volume to the perf database through `PerfSeed.addAuditVolume` (300 entries for each of
about 200 tenants and 20 000 more for the tenant the statements run as: about 80 000 rows, inserted oldest first as a log
grows) and runs `EXPLAIN (ANALYZE, BUFFERS)` of the real statements (`AuditRepository.listSql` and `exportSql`, made
public for this) as the application role under row-level security. Plans are written to `backend/target/perf/audit-plans.txt`
and summarised in `docs/PERF.md`:

- **List** (limit 100) with no filter, with one action and with a 30-day range: `Index Scan using audit_log_newest_idx` and
  an `Incremental Sort` on `id` over the already ordered rows; 11 to 56 buffers, 0.06 to 0.09 ms. The test fails if a list
  statement scans `audit_log` sequentially or sorts every matching row, and `audit_log_newest_idx` is a candidate in the
  kept-index table.
- **Export** of the 20 000 rows of the tenant (25 percent of the table): `Bitmap Heap Scan` and a full in-memory `Sort`
  (3 MB, 8.6 ms in all); with a 30-day range the planner uses `audit_log_newest_idx` for the bitmap and sorts 3 500
  rows (1.6 ms). The sort is the planner's choice for reading a quarter of the table, not a missing index. A throwaway
  PostgreSQL 18.7 with the same shape of data and an extra `(tenant_id, created_at DESC, id DESC)` index chose the same
  plan, so that index would add write cost to the busiest append-only table for no change in the plan. The export reads
  every matching row by design and the sort costs memory in PostgreSQL (about 150 bytes a row, spilling to disk beyond
  `work_mem`), not in the application; D-377's known cost stands, now with numbers. The test fails if an export scans
  `audit_log` sequentially.

#### D-404 What is documented about administrators

`docs/OPERATIONS.md`, "Users and sessions", says that any administrator can issue a setup code for any user, a signer
included, and could therefore sign in as that person; that both actions are audited; and that production deployments should
use OIDC for SIGNER and ADMIN accounts. Nothing in the code changes: D-375 already records the power and its audit trail.

#### D-405 Where things live

`PasswordEncoders` (`auth`) builds the encoder and names the algorithm of a hash; `LexvayUserDetailsService` (`auth`) holds
the upgrade; `AppUserRepository.rehashPassword` is its statement; `LexvayUser.withPasswordHash` returns the principal the
session keeps. `AuditRepository.listSql` and `exportSql` and `AuditFilter.where` are public so that `IndexUsageTest` runs the
text the application runs. `PerfSeed.addAuditVolume` is test code.

#### D-406 What the tests prove, and what they cannot

`PasswordEncodersTest` (parameters in the hash, every legacy form, no other verifier, long and multi-byte passwords),
`PasswordRehashTest` (D-401), `PasswordSetupApiTest` (D-402 and `{argon2}` after setting a password), `SeedLoaderTest` (the
seed's users have `{argon2}` hashes and the demo password signs in), `IndexUsageTest` (D-403). The hash time is not
asserted: a timing assertion would fail on a loaded machine and say nothing about the cost parameters, which
`PasswordEncodersTest` pins by reading them out of the hash. The measurement in D-400 is a one-off.

#### D-407 At most N password hashes at once, behind one gate; a refused caller gets 503 and nothing is hashed

Each Argon2id hash holds 32 MiB (D-400). Without a bound, Tomcat's 200 request threads could ask for 6.4 GiB against a default
heap of about 1.5 GiB (2 GiB task, `MaxRAMPercentage=75`), and the JVM exits on out-of-memory (`ExitOnOutOfMemoryError`): a
few hundred distinct client addresses, each inside the per-address login budget (D-092), would take the instance down.

`PasswordHashGate` is a fair `Semaphore` with `lexvay.security.password-hash-concurrency` permits (environment variable
`LEXVAY_PASSWORD_HASH_CONCURRENCY`; unset means `max(2, availableProcessors())`; `@Positive` validated, so `0` or a negative
value stops the start). `GatedPasswordEncoder` wraps the whole delegating encoder, so every `encode` and every `matches` takes a
permit, Argon2 and legacy bcrypt alike (bcrypt is cheap, but one rule is simpler than two). `upgradeEncoding` only reads the
parameters inside a hash and takes none. The permit is acquired with `tryAcquire(2, SECONDS)` and always released. A caller that
waits two seconds gets `PasswordHashBusyException` before anything is hashed or compared:

- Sign-in: the exception is an `AuthenticationServiceException`, so it reaches the form login's failure handler, which answers 503
  with the problem detail "Sign-in is busy. Try again in a moment." and `Retry-After: 2` instead of the 401. An unknown email is
  refused the same way, because Spring hashes a dummy password for it (timing protection) through the same gate.
- Choosing a password (`POST /auth/setup`): `ApiExceptionHandler` answers the same 503 and header. The refusal comes at the hash,
  before the code is claimed, so the code is not used up.
- Sign-in with an old hash does two operations in turn (the comparison, then the new hash); each takes a permit in its turn and
  never both at once, so a caller cannot deadlock against itself. Under pressure the second may be refused after the password
  matched: the person sees 503 and signs in again, which upgrades the hash then.

**Sizing.** Heap needed is permits x 32 MiB on top of the application's normal use. The default (processors, at least 2) is 2 x 32 =
64 MiB on a one-processor task and 256 MiB on eight; both are small against the default 1.5 GiB heap, and CPU, not memory, is the
limit at that point (one hash is about 73 ms of one core). `docs/OPERATIONS.md` documents the figure next to the JVM options.
Fairness means callers are served in arrival order, so a burst delays everyone a little instead of starving the early callers.

Tests: `PasswordHashGateTest` (six callers on two permits finish and the encoder never sees more than two at once; a caller refused
after its wait hashes nothing and the permit comes back; a failing hash returns its permit; `upgradeEncoding` needs none; zero
permits and non-positive properties are refused; the default), `PasswordHashBusyApiTest` (real filter chain with two permits: a
single sign-in succeeds, a sign-in and an unknown-email sign-in with both permits held answer 503 with `Retry-After: 2` and compare
nothing, choosing a password shares the gate and keeps its code, six concurrent real sign-ins answer 200 or 503 and the gate
never holds more than two).

Rejected: sizing by Tomcat threads (`server.tomcat.threads.max`; it also limits requests that hash nothing); a queue without a wait
limit (threads pile up and every request times out at the proxy); a separate pool of hashing threads (a handoff for no gain, since
the request thread must wait for the result anyway); refusing immediately when no permit is free (a burst of a few more callers
than permits would be refused although they would be served in milliseconds).

### UI2-3 — Changes, audit trail and evidence report in v2 (D-340 to D-349)

**D-340 — Both timelines are PrimeNG Timeline, given list semantics.** Changes and the audit trail use `p-timeline`
(PrimeNG provides it, so it is not hand-built) with the screens' own marker and content templates. Timeline renders
plain `div`s, so pass-through gives the root `role="list"` and each event `role="listitem"`, which is what a screen
reader and the end-to-end tests navigate by (`list` "Change events", one `list` per audit day named by its heading). The
opposite column is hidden (`display: none`), the connector is a 1 px `line` rule (`theme/timeline-tokens.ts`, one set
shared by both chunks, registered by each). Expanding in place is a native disclosure button (`aria-expanded`,
`aria-controls` only while open, so it never names a missing element) whose details rise into view with a 200 ms
opacity and transform keyframe; height is never animated. Rejected: an Accordion per entry (a panel per timeline row,
heavier markup and a second focusable header) and `<details>` (its open state cannot be driven by the screen's signal,
and its marker differs per browser).

**D-341 — One sentence function per audited action, kept complete by reading the backend.** `audit-sentence.ts` holds
`AUDIT_SENTENCES`, one pure function per action, each starting with who acted ("Dana Whitfield accepted CA-3", "System
finished re-running COUN 625: 1 quote still present, 2 back in review"), and `ACTION_LABEL`, the short name of each for
the filter. `audit-sentence.spec.ts` reads `backend/src/main/java` for every `audit.record(...)` and `recordAs(...)`
action and requires both tables to hold exactly that set (37 actions, with SEC-2's `PASSWORD_REHASHED`), so a new backend action fails the frontend
unit suite until it has words. A missing or malformed detail falls back to a general noun ("a requirement"); the spec
runs every sentence with an empty and a malformed detail and fails on `undefined`, `null`, `NaN`, `[object` or a brace.
An unknown action reads "Dana Whitfield: Password rehashed" (its words, never its detail). `ACTION_LABEL` and
`actionLabel` moved out of `core/labels.ts`, which the shell imports, into the audit chunk.

**D-342 — The audit list filters on the server; the chips are Selects drawn as pills.** The list now asks `GET
/audit` with the filters on screen (`auditParams`: only the filters that are set, D-116) instead of loading 500 entries
and filtering them in the browser, so a filter reaches every entry, not the newest 500. Chips: Person (`userId`, from
`GET /users`), Action (every audited action, with a search box), Record type (every record type the backend writes),
Period (Today, Last 7 days, Last 30 days, sent as `from` at local midnight). The option lists are fixed rather than
derived from the loaded rows, because a server-filtered page holds only a slice. Each chip is a PrimeNG Select with the
pill look of the shell's program switcher, accent-tint once it holds a value, with its clear icon; the accessible names
are the dimension ("Action"), so the value never renames the control. Pages of 100 ("Show older" asks for the next
offset with the same filters); an answer to an earlier filter that arrives late is dropped. "Download CSV" is shown to
administrators only (`SessionService.isAdmin`, the endpoint's `hasRole('ADMIN')`) and links `GET /audit.csv` with the
same parameters from the same function (`auditCsvUrl`), so the file and the screen cannot disagree. Entries written by
background work cannot be chosen under Person: `userId` cannot express "no user".

**D-343 — Days are local calendar days.** `groupByDay(rows, now)` keeps the server's newest-first order and starts a
group at each new local date: "Today", "Yesterday", then "Friday 20 November" (day before month, the order the rest of
the product uses for dates), with the year only when it is not the current one. Times on the entries are local `HH:mm`;
the expanded details give the full local timestamp in mono.

**D-344 — Details in words; avatars from the Seal's initials.** `detailRows` words the detail keys the backend writes
(a table of labels and formats in reading order: "Decision: Gap confirmed", "Jurisdiction: California (CA)", "Before:
9 requirements"); a key not in the table still shows by its name in words when its value is plain text, a number, a yes
or no, or a list of those; nested objects never show (they stay in the CSV); ids and hashes are mono. The record type and
id lead the details. The avatar is a PrimeNG Avatar with `initials()` from `ui/seal.ts` (one implementation of
initials); background work shows a gear, a user id the tenant no longer lists shows a person icon and reads "Former
user". Avatars are `aria-hidden`: the sentence already names who acted. Sign-out is recorded after the session is
cleared, so its entry carries no user and read "System signed out"; for the actions a person takes on their own account
(sign-in, sign-out, setting a password), an entry without a user is attributed to the account it is recorded on.
The backend should record the actor of `LOGOUT` itself (rule 9: who); that is a backend change outside this item.

**D-345 — Change cards.** `change-card.ts` (pure, tested) builds each card. Titles: "Colorado rules, revision 2" (the
state's name and the words only the new label has) and "COUN 625, new catalog year". The two pills are the parts of
the two version names that differ (`versionPills`: drop the shared leading words but keep the last shared one when one
name extends the other, so "... 2026" and "... 2026 revision 2" read "2026" and "2026 revision 2"); the full names are
in the details. "What it affects" names the states of this program whose signed determination the change reopened and
counts the others ("Colorado, 1 in another program"), because the change list is tenant-wide and the screen holds only
this program's determinations. Newest change first. The one action is the re-run; when it cannot run yet, the reason
sits beside the disabled button (as before). While it runs, the card shows a spinner with the job's phase from `GET
/jobs/{id}` (Queued, Re-running, then Updating while the program reloads), then a toast. Status chips are Tags with an
icon and a word.

**D-346 — The report sheet.** An 880 px sheet at e2 in Document type: the jurisdiction as a 32/40 serif heading, the
program line under it, and at the top right the Seal (112 px) when the determination is signed or a dashed "Draft"
stamp when it is not. The Draft stamp keeps D-027's words: its accessible name is "Draft. Not a determination." and it
prints "Draft" over "Not a determination". The seal stamps once when it appears (scale 1.12 to 1 and -8° to 0 with
`--lx-spring` over 420 ms, one ink ring fading out over 600 ms), the motion table's signing moment, in this screen's
CSS; reduced motion zeroes both. The signature (outcome, signer, date, rule version, course versions locked and the
versions hash) stays under the heading, because rule 6 makes the report name its exact versions. Requirements are an
ordered list: ref, title (a heading, level 4), one StatusIcon with its words, the quote in Document type with its
citation, and the reviewer's note when there is one. The rule text of each requirement left the screen (word budget;
Review and the PDF carry it).

**D-347 — "Download PDF" is one action in a floating pill.** The pill (raised, e3, 999 px radius, no blur: the blur
stays the DecisionBar's) is sticky at the bottom of the view under the sheet. One click generates the PDF (the report
job), waits for it and saves it; later clicks on the same determination save the same file without a new job; a toast
says it is ready. `GET /reports/{id}` answers with the status as JSON while the job runs and with the PDF itself once
it is ready (D-069), so the poll asks for a Blob and `core/report-answer.ts` (`readReport`, tested) reads either: the
last poll already carries the file, which is saved under the server's `Content-Disposition` name through an object
URL. v1 polled with `Accept: application/json` and parsed the answer as JSON, so the poll that found the PDF ready
failed with "The PDF could not be generated." (found by the new end-to-end test; no earlier test generated a PDF). For a draft the button is
disabled and the pill says "After signing" (D-089: the PDF exists only for a signed determination). Rejected: the two
buttons of v1 (Generate, then Download).

**D-348 — Chunks and bundle.** The audit trail now shows icons, so it is loaded with `loadChildren` from
`audit.routes.ts`, which provides `AUDIT_ICONS` (PERF-FE-1); `theme/icons.spec.ts` checks the set. Token sets moved
with their screens: Timeline in Changes and Audit, Avatar (accent on accent-tint, a pair the contrast test already
covers through Tag) in Audit; DataTable left all three screens. `ACTION_LABEL` left the initial bundle (D-341). Initial
bundle: see `docs/progress/UI2-3.md`.

**D-349 — End-to-end changes.** `records.spec.ts` is a new project that runs after the suite (like the register spec),
because the signed report and the signing entry need the California signature of `review-and-sign.spec.ts`: audit
chips (the request carries `userId` and `action`, one entry remains, its details open), the administrator's CSV (file
name, header row, the one filtered row), the empty state's "Clear filters", the report (Draft stamp and disabled
download; seal, eight requirements, the PDF download checked by its `%PDF-` header, the toast), and axe in both themes
with no horizontal scroll at 1440 and 400 px on Changes, the audit trail and both report states.
`review-and-sign.spec.ts` follows the new names (lists, the seal's name, the note) and its re-run test runs from the
course card, holding job status answers back 400 ms so the card's progress is visible long enough to assert; `rules.spec.ts`
reads the audit sentences; `screens.spec.ts` adds the `ui2-3-*` screenshots.

### FIX-5 — Logout audit actor and work queue predicate (D-410 to D-415)

#### D-410 `LOGOUT` takes its actor from the `Authentication` Spring hands the success handler

`LogoutFilter` reads the authentication once, runs its handlers (`SecurityContextLogoutHandler` clears the security context
and the session), and then calls the success handler with the authentication it read first. The handler already used that
argument to find the user and the tenant, but wrote the entry through `AuditService.record`, which asks `CurrentUser` for the
actor, and by then the context is empty. The entry therefore had `user_id = NULL` (rule 9: "who" was missing). The handler now
calls `AuditService.recordAs(user.id(), "LOGOUT", "app_user", user.id(), Map.of())` inside `TenantContext.runAs(user.tenantId())`.
The actor and the record are the same person, as for `LOGIN_SUCCEEDED`.

Rejected: a `LogoutHandler` placed before the context-clearing one that stashes the user in a request attribute (a second place
that has to run in the right order, to hand over what the success handler already receives); reading `SecurityContextHolder`
in a handler that Spring does not guarantee to run first.

#### D-411 The work queue's `latest` CTE uses `AssessmentRepository.LATEST_FINISHED`

The CTE kept its own copy of "the program's latest finished assessment" (status FINISHED, newest `finished_at`, then newest
`created_at`). It now takes the predicate from `AssessmentRepository.LATEST_FINISHED` (`%7$s` in `SHARED`), as the coverage
matrix and the signing gate already do, so a change to the ordering reaches the queue with them. The lateral subquery keeps
the alias `a` that the constant expects. The statement the database runs is the same text as before, so `IndexUsageTest` and
the latency budget are unchanged. Not touched: `MappingRepository.latestAssessmentsUsing`, which has the same ordering as a
correlated subquery over `a2`; the constant carries `LIMIT 1` and the alias `a`, so it does not fit there, and it is outside
this item.

#### D-412 Parity test: the queue reads the same assessment as `latestFinished`

`WorkQueueSeedParityTest` loads the seeded Northfield tenant from a copy of the manifest and, for the SIGNER, REVIEWER and
ADMIN, asserts that every queue item naming an assessment names the one `AssessmentRepository.latestFinished` returns for its
program, that the badge total equals the sum of the list's counts, and that the check was not vacuous. The queue before and
after the switch was also compared on the same seed (item kinds, programs, jurisdictions, counts and id list lengths for
four roles: 18 lines, identical; see `docs/progress/FIX-5.md`). The test compares against the repository method, not a
frozen copy of the old ordering, so it stays valid when the ordering is changed on purpose. The READY_TO_SIGN parity test
(`WorkQueueApiTest.readyToSignAgreesWithTheSigningEndpointForEveryProgramAndJurisdiction`) is unchanged and green.

#### D-413 Older `LOGOUT` entries keep no actor; the audit view reads them as before, for `LOGOUT` only

The audit log is append-only (rule 5), so entries recorded before this fix keep `user_id = NULL`; nothing back-fills them.
`audit-view.ts` (UI2-3, D-340 to D-349) attributed an entry without a user to the account it is recorded on for three actions of
a person on their own account. Only `LOGOUT` ever lacked a user (`LOGIN_SUCCEEDED` and `PASSWORD_SET` are recorded with their
actor), so the rule is now narrowed to `LOGOUT` and documented as the reading of entries recorded before FIX-5; an entry that
carries its actor takes the first branch. `audit-view.spec.ts` covers a recorded sign-out, an old one, and that another action
without a user is still the System. `audit-sentence.ts` needed no change: it takes the actor's name as input.

#### D-414 Signing out without a session records nothing

A request to the logout URL without a session has no `Authentication`, no tenant and no user to name; the entry would have no
tenant to be recorded in. The handler still answers 204. `LogoutAuditTest.signingOutWithoutASessionRecordsNothing` asserts
this for a request with no cookie and one with an unknown session id.

#### D-415 Tests drain queued jobs before they clear the fake model

Several test classes began with `fake.reset(); runner.runPending();`. `runPending()` runs every queued job of every tenant,
including jobs other classes left behind, and each of those calls the fake model after the reset; a class that then asserted
a global `fake.callCount(...)` (`RuleVersionApiTest.createStoresRuleTextAndExtractsRequirements`: "expected 1 but was 4" in CI)
failed depending on the order the classes ran in. The two calls are swapped to `runner.runPending(); fake.reset();` in
`AbstractAssessmentTest` (which the other counting tests extend: `AssessmentApiTest`, `AssessmentUsageApiTest`,
`RuleVersionChangeTest`, `Rule1_FindingValuesTest`, `Rule3_NoModelCallForNumericTest`, `SearchTermsMatchingTest`),
`RuleVersionApiTest`, `Rule5_ApprovedRequirementsRejectEditsTest`, `EvalServiceTest`, `EvalModelTest` and the classes that load
the seed (`SeedLoaderTest`, `CoverageSeedTest`, `RegisterSeedTest`, `RuleVersionDiffSeedTest`, `FindingCountsParityTest`,
and this item's `WorkQueueSeedParityTest`). Draining first leaves the fake empty when the test starts, whatever ran before.

### UI2-1 — Program overview and determination register in v2 (D-320 to D-329)

**D-320 — The overview's view model is one pure file next to the screen.** `screens/overview/overview-view.ts` turns the
program, its latest assessment, its determinations, the signer flag and the work queue into what the screen shows:
`stateCards` (one per target, in the program's order), `mapStates` (the same facts for the map, so a tile and its card
never disagree), `heroStats` and `needsYou`. `core/overview.ts` keeps `overviewCounts` (the rail badge) and
`jurisdictionRows`, which now reports the stage and a `reopened` flag instead of v1 words; the words live with the
cards. The v1 table, the findings bar (`ui/findings-bar.ts`, `barSegments`) and the overview's ProgressBar, DataTable
and Tag-only token sets are gone with it: nothing else used them. Vitest: `overview-view.spec.ts`, `core/overview.spec.ts`.

**D-321 — A card's one action follows the stage.** Signed: "View report" (the evidence report on that state). All
requirements decided: "Sign" for a signer, "Review" for everyone else (the signing endpoint is the signer's; offering
Sign to a reviewer would lead to a refusal). Requirements waiting: "Review". Not assessed: no action, and the chip says
so. Sign and Review open Review on the state (`review?jurisdiction=`), where signing happens. The chip carries the
stage in words and an icon: the outcome label once signed (found, missing, or the seal colour for "No determination
made"), "Ready to sign", "<n> to review", "Not assessed", with "Reopened" first when a signature was reopened by a
change. The card's ring is the findings mix (found, ambiguous, missing) of the state's requirements; review progress is
in the chip and on the map tile's fill. Only "Sign" is a filled button; the others are text buttons, so five cards do
not shout. The action's hit area covers the card (a stretched link): the whole card is one link that lifts on hover,
and its focus ring outlines the card.

**D-322 — "Needs you": this program's items, what finishes work soonest first, at most three.** The overview fetches
`GET /work-queue` (role filtered in SQL, F13) alongside the program context and keeps the items that concern this
program: its own (`programId`), open changes whose `programIds` include it, and rule versions or failed extractions of
the states it targets (those kinds carry no program). Order: ready to sign, failed assessment, open changes (they make
decisions stale), evidence requests, reviews, rule versions to approve, failed extractions; within a kind the queue's
own order (oldest first). Each row reuses `workItemView` (icon, count-first title, screen) with the state's name as the
detail (the program's name says nothing on its own overview) and one verb-first action. A failed assessment's screen
is this overview, so its action is "Run again", which starts an assessment in place. The section is absent when nothing
concerns the program (no empty card under the hero); a failed fetch shows the server's message in the section.

**D-323 — The overview appears once the program and the queue are both in.** "Needs you" sits between the hero and the
cards, so drawing the page before the queue arrives would push the cards down (layout shift). The skeleton (title,
hero, five cards) stays until both have settled; the queue is one indexed statement (F13, 40 ms budget) fetched in
parallel with the program's six reads, so it adds no round trip.

**D-324 — `AssessmentRunner` is shared.** "Run assessment" lived inside the command palette; the overview needs it for
an empty program and for "Run again". It moved to `core/assessment-runner.ts` (start, follow the job, toast, refresh the
program context) and both call it; behaviour unchanged.

**D-325 — Shared-element transition: the departing element alone carries `state-<code>`.** The state's tile and its
card both live on the overview, and a `view-transition-name` must be unique in the document or the browser abandons the
transition. So neither carries it at rest: activating a tile (click, Enter, or its card's link) sets
`view-transition-name: state-<code>` on that tile (StateMap's `departing` signal), and a card's action sets it on that
card's head (name and ring). The name is in place when the router calls `startViewTransition` (checked in Chromium: the
names at that moment are `root` and `state-TX`). `<code>` is the upper-case postal code, `state-CA`; UI2-2 gives
Review's state header the same name. Reduced motion skips route transitions altogether (D-307).

**D-326 — StateMap gains a legend, a "different outcomes" status and progress-free states.** For the register's map
the shared component needed three small additions, all backwards compatible: `legend` (input, default off) draws the
one legend below the map with only the statuses on it ("Not a target" for the quiet tiles), from the pure
`legendEntries`; `TileStatus` gains `MIXED` (a dashed ring, `phosphorCircleHalf` in the card) for a state signed with
different outcomes in different programs; and a state with `total` 0 has no progress to show, so its card has no ring
and its accessible name no "not assessed" (the old name read "Utah, not assessed, not assessed"). ProgressRing gains a
`seal` tone for the hero's "Signed" ring.

**D-327 — The register's map shows what its table shows.** `registerMapStates` colours every state with a current
(`SIGNED`) row among the rows on screen by its outcome; reopened rows colour nothing ("current outcome"). Signed in one
program, the tile's card opens that program's evidence report; signed in several, the tile shows their common outcome
("Determined to meet in 2 programs") or `MIXED` ("Different outcomes in 2 programs") and has no action, since there is
no single report to open; the table lists each. The map follows the filters and keeps its last picture while a new
filter loads (it is never torn down, so the wave plays once). The filters moved into a panel beside the map with the
row count as a Stat (still `aria-live`); "Download CSV" is unchanged.

**D-328 — Explanations behind InfoTips; paper keeps the disclosure note.** The register's subtitle and the By program
paragraph moved into InfoTips ("About the register", "About these lists"); the overview's single explanation, that
findings are proposals and only a signer signs, is the hero's InfoTip ("About these figures"). The lists' paragraph is
regulatory context a printed list must carry, so a print-only copy (`DISCLOSURE_NOTE`, one constant) prints exactly as
before; the rest of the print view is unchanged in content (tenant, program, generated time, the three lists with
signer, date and rule version, the notes), the map and the filter panel are screen-only. The empty register says "No
signed determination matches these filters." with "Clear filters", or "Nothing is signed yet."

**D-329 — By program as three clean lists with seals; bundle.** Each list is a card (title and count) of rows: the
`Seal` of the signature (outcome of the list, signer's initials, date; its accessible name is the full sentence) or a
dashed placeholder where there is none, the state, and the signer with date and rule version or the note. They are
lists (`ul`), not tables. `initials` moved from `ui/seal.ts` to `ui/initials.ts`: the shell used it, and once the
register imported the Seal component the bundler put the whole Seal module in the initial chunk (+2.3 kB). Initial
bundle 605.46 kB raw (main 596.16 kB, styles 9.30 kB), 155.80 kB estimated transfer, measured after merging UI2-3; before it, this branch measured 606.36 kB against 606.77 kB on `origin/main`.
Also global (`styles.css`): a link button (`<a pButton>`) takes the button's colours and no underline. The global link
rule is unlayered, so it beat PrimeNG's layered button colours: a filled link button (the map card's action) drew
accent text on the accent fill. The rule now reads `a:not(.p-button)`.

### UI2-2 — Review and signing in design v2 (D-330 to D-339)

**D-330 — Review is split into parts, each with its own styles.** The screen (`review.ts`) keeps the state, the data and
the keys; `RequirementList` (the 288 px rail), `RequirementCard` (the hero), `EvidencePane` (the DocumentViewer and the
provided passages) and `SignDialog` live beside it in `screens/review/`, and `DecisionBar` is a shared component in
`ui/decision-bar/` as DESIGN.md lists it. One component with every pane's styles would cross the 10 kB component style
budget (D-083); each part now stays well under it. The parts have no `viewProviders`, so `REVIEW_ICONS` names their
icons; `DecisionBar` registers its own (`DECISION_BAR_ICONS`, a unit in `icons.spec.ts`) and its Textarea and Message
token sets. Splitter and ProgressBar are no longer rendered by Review and left `REVIEW_TOKENS`. Rejected: keeping the
Splitter between the centre and the evidence (its gutter is the hairline v2 removes, and the rail is a fixed 288 px).

**D-331 — Header: pills as tabs, the active pill as the shared element.** The states stay a PrimeNG `Tabs` list (arrow,
Home and End keys, `selectOnFocus`), restyled as pills: a 20 px ProgressRing (decided of total, from the assessment's
per-state summary, `stateProgress`) and the full name. The tab's accessible name is the full name ("California"); its
ring is hidden from assistive technology and its numbers are the tab's description ("6 of 8 reviewed") through
`aria-describedby` on hidden text outside the tab, so the name stays the name. The active pill carries
`view-transition-name: state-<code>`, the target UI2-1's map tile morphs into. The header progress is a 40 px ring
(hidden) beside "6 of 8" with " reviewed" for screen readers. The pills scroll sideways when they do not fit
(PrimeNG's scrollable list without navigator buttons). The page title stays "Review": the shell focuses `main h1` after
navigation, and the program name left the header (the top bar's pill shows it).

**D-332 — "Sign <state>" exists only once every requirement is decided.** Before that the header shows progress and
nothing to sign (v1 showed a disabled button with a help line). The button enters with one pulse (scale and opacity,
720 ms, `animate.enter`), then stays still. A reviewer who is not a signer sees it disabled with an InfoTip "Only a
signer can sign." A signed state shows its Seal (40 px, named in one sentence) with "Signed" and the outcome; a reopened
determination shows a "Reopened" chip with an InfoTip. Under 640 px the action takes its own row under the pills.

**D-333 — The DecisionBar and the note.** Accept (A), Request (R) and Gap (G) on the raised, blurred bar. It sits in
flow 24 px under the requirement and is sticky at 16 px from the foot of the centre pane, so it follows a short
requirement and sticks to the foot only when the requirement is longer than the pane (orchestrator review: pinning it
to the foot left a band of empty space under short requirements). Under 1024 px it is the last item of the one-column
layout, so it stays at the foot of the window while the list, the requirement or the evidence is in view. Visible words follow the spec; the accessible names
of Accept and Request stay full ("Accept evidence", "Request evidence") and contain the visible word. Gap, its key and a
note button open the note field in place; with the note open the third action reads "Confirm gap", and Accept and
Request send the note too. The note button ("Add note") exists because a request's note is what the program reads (F4)
and an accept after provided evidence asks which evidence was relied on (D-267): the spec opens the field only for
Gap, which would have left requests without a note. Where a person answered an evidence request the note opens by
itself. Escape in an empty note closes it and returns focus to Gap; a note with words stays open. A gap without a note
opens the field and says why, as before. Under 640 px the key hints are hidden (a phone has no keys) and the actions
keep one row.

**D-334 — The decision sequence, never in the way.** After the server records a decision: the decided row's StatusIcon
turns filled and pops in on the spring, an ink ring draws around it in its status colour (stroke-dashoffset, 240 ms)
and fades, the row settles (520 ms, `--lx-spring`), the next requirement's card slides in 16 px from the right (from
the left when the selection moved up the list: `ReviewWorkspace.travel`), and both rings advance by their stroke
transitions. Only transform, opacity and stroke-dashoffset move. The card is keyed by mapping (`@for` over the
selection) and enters with `animate.enter`; it starts at 20 % opacity rather than 0 so it is never invisible. Nothing
waits for motion: the bar is outside the keyed card and keeps focus, keys act during the entrance (the e2e test presses
K and J while the card slides in), and the global reduced-motion rule removes every animation (the e2e test runs the
whole California review under reduced motion and asserts that no animation is running after each decision).

**D-335 — The signing dialog and the stamp.** "Sign California": a subtitle "8 of 8 decided" with an InfoTip holding the
explanation that used to be a paragraph, the three outcomes as large cards (each a label around a PrimeNG RadioButton,
in a fieldset, with its own icon and colour, the chosen one ringed in the accent), and a preview of the Seal with the
signer's initials and today's date, faint and hidden from assistive technology (nothing is signed until the server says
so). Nothing is preselected. After the server signs, the dialog shows the recorded determination's Seal stamping down
(scale 1.12 to 1, -8 degrees to 0, `--lx-spring`, 420 ms, one ink ring fading over 600 ms), the outcome in words, who
and when, and Done, which takes the focus; the header behind it already shows the seal. `Seal` gained a `stamp` input
for this (the stamp is on the host, as D-315 foresaw). Rejected: closing the dialog at once (the moment would happen
off screen) and closing it on a timer (motion must not decide when the person moves on).

**D-336 — `softWrap`, display only.** `softWrap(text)` in `page-text.ts`: a newline becomes a space when the next
character is a lower-case letter (`\p{Ll}`), a digit (`\p{Nd}`) or "(" and the previous one is not ".", "!" or "?".
One refinement of the spec: a newline after another newline also stays, so a blank line between paragraphs is never
turned into a line break plus a space (a blank line is a paragraph, not a wrap). The viewer renders `softWrap(page.text)`
and cuts the mark out of that, at the stored offsets; selection offsets are measured on the shown text and the quote is
still sliced from the stored text, so a passage chosen across a joined line break carries the stored newline. Vitest
proves equal length, that only joined newlines differ, and that a DOM selection over the shown text with the mark maps
back to the stored text exactly; the viewer e2e test compares the page with `softWrap` of the stored page and selects
across a joined break on the harness. The "Quote only" disclosure still shows the stored quote as is.

**D-337 — Hero card and evidence pane, in few words.** The hero: ref chip (mono), title at Title size, rule text in the
Document type in a `surface-2` inset with its rule version, date and source link, the finding as a large tinted chip,
how it was checked in one line (`checkMethod`: "Rules engine: Internship hours >= 600", "Quote verified on page 1",
"No passage found"; the explanation behind an InfoTip "How it was checked"), the model's rationale in three lines with
"Show all" (`lxClamp`), and the current decision with who and when and its note. The evidence pane drops its "Evidence"
label (screen-reader heading only) and the "File" entry in the three-pane layout (the viewer names the file), and its
"No quoted evidence" reason moved to the hero's method line. The DocumentViewer header became an icon pager (Previous
page, "Page 1 of 2", Next page) and an icon link "Open original", all with their words as accessible names and
tooltips; the names are unchanged, so Rules and the evidence drawer keep working as before.

**D-338 — Filter as a segmented icon control.** Five icons (list, dashed circle, and the three finding icons the
StatusIcon uses before review, which a unit test pins). Each segment's name and tooltip put the count first ("7 to
review", "1 ambiguous", "9 requirements"): SelectButton gives the option label to its toggle as `aria-label`, so the
count reaches assistive technology without a second text. The list keeps the selected row in view by scrolling the
list itself, never the page (under 1024 px the page scrolls, and a decision there must not jump it to the list).

**D-339 — End-to-end changes.** Names that changed: the state tabs (full names; `stateTab(page, code)` and `STATE` in
`support.ts`), "Request evidence", "Gap" then "Confirm gap", "Sign California", the dialogs "Sign California" and
"Signed", the filter segments, `getByLabel('Note', { exact: true })` (the note button's name contains "note"). Flows
that changed with the design: a request with a note opens the note first ("Add note"); "Sign <state>" is absent until
everything is decided. New checks: reduced motion with no running animation after each decision, keys during the
entrance, Escape in the note, the bar staying in view at 400 px, the pill descriptions, the seal preview hidden from
assistive technology, the stamped seal and Done's focus, axe on Review at 1440 and 400 px, the gap note, the signing
dialog at 1440 and 400 px with and without an outcome, and the signed seal, each in both themes.

### F10b — Coverage matrix screen (D-416 to D-425)

**D-416 — The state lives in the URL only; one request per state.** `programs/:programId/coverage` is a lazy chunk
(`coverage.routes.ts`, `COVERAGE_ICONS`); the rail lists "Coverage" after Review with `phosphorGridFour`, added to the
generated `SHELL_ICONS` (`node scripts/shell-icons.mjs`), so the command palette lists it too. The selected state is a
`computed` of the `jurisdiction` query parameter (a target code, else the program's first target); choosing a pill only
navigates (`replaceUrl`), so there is no second copy of the state to fall out of step with the URL (Review needed an
"own navigation" guard for that echo, UI2-2). Without a parameter the first target opens and the URL is left as it is.
The selected state is requested first, then every other target once, in parallel, for the pill rings; answers are kept
while the screen lives and dropped when the program changes. A failed state is asked again when it is chosen again.
Rejected: one request on demand per pill (the rings of the other states would stay empty, and the spec asks for them).

**D-417 — `StatePills` in `ui/`, from UI2-2's header.** Review's pills (UI2-2, PR #37, not merged when this item
started) are extracted as `ui/state-pills.ts`: the PrimeNG `TabList` and its tabs, rendered inside the screen's own
`p-tabs` (Tab and TabList inject `Tabs` through the host element, so the screen keeps the panels and the ARIA tab
pattern stays whole), the 20 px ring hidden from assistive technology with its numbers as the tab's description, and
`view-transition-name: state-<code>` on the active pill. One addition: a pill opened from a link that lies beyond the
edge of a narrow window is scrolled into view by scrolling the list, never the page. UI2-2 merged first (813ae59), so
this item moved Review onto it, as the orchestrator directed: Review's header renders `<lx-state-pills>` inside its
`p-tabs`, its own pill markup, hidden descriptions and pill styles are deleted (one copy remains), and
`stateProgress` now returns the shared `PillProgress` (`value` is the decided count; "6 of 8 reviewed" unchanged).
Review's `selectJurisdiction`, `selectOnFocus`, the `?jurisdiction=` handling and its end-to-end tests are untouched;
the description ids moved from `pill-progress-XX` to `lx-pill-progress-XX`, which no test names.

**D-418 — The pill ring is the share of requirements without a flag.** `unflaggedShare`: requirements with risk `NONE`
over all requirements of the state, labelled "2 of 8 with no flag" (the tab's description). A state without
requirements (before its first assessment) shows no ring.

**D-419 — The matrix is a real table with two header rows.** Row headers are the requirements (ref in mono, title);
column headers the course codes in mono, wrapped at their space so twelve columns of 52 px fit beside the requirement
and flag columns at 1440 px, with the course title in a tooltip. Under the codes, a second header row of `colgroup`
headers marks the runs of required and elective courses ("Required" over a solid rule, "Elective" over a dashed one),
backed by `colgroup` elements so `scope="colgroup"` is valid. This is the spec's marker under the code drawn once per
run instead of once per column: a word under each 52 px code does not fit, and the API orders required before elective
(D-391), so each kind is one run (`columnGroups` handles alternation anyway). "Program" spans both header rows. Empty
cells show a faint 4 px point so the eye can follow a row or a column; rules-engine rows carry one cell across the
course columns with "Checked by the rules engine".

**D-420 — A cell is a shape first.** A 20 px mark in a 32 px button: filled accent for the system's quote, an accent
ring for a person's, a dot inside a ring for both, a dashed ring in the ambiguous colour with a 12 px clock badge for
evidence on a superseded course version only (under the `supersededBy` column, D-394). A cell with current evidence and
outdated evidence keeps the current mark and adds the clock. The quote counts are summed per source. The program
column's mark follows the sources of the cited documents; its card lists the file names. Accessible names state
everything the shape shows: "COUN 605, evidence from the system, 1 quote", "COUN 610, evidence from the system, 1 quote,
and from a person, 2 quotes", "COUN 625, outdated evidence from the system, 1 quote on the 2026-27 version",
"Program documents, evidence from the system, accreditation-letter.pdf".

**D-421 — Keyboard and the card.** One tab stop in the matrix (roving tabindex over the cells that hold evidence). Left
and right move along the row, up and down go to the nearest cell (by column) of the next row that has one, Home and End
to the ends of the row, Ctrl+Home and Ctrl+End to the first and last cell; empty cells and rules-engine rows are
skipped, as the StateMap skips non-target tiles (D-308). The card is one PrimeNG Popover (`focusOnShow` false): it
opens on focus or after 120 ms of hover and stays open while the pointer travels into it; Enter on a cell moves the
focus to "Open in Review" (`review?jurisdiction=XX&ref=REF`, the D-311 parameters); Escape closes it and returns the
focus to the cell without reopening it. A focused cell is scrolled clear of the sticky columns by measuring them
(`reveal`; `scroll-padding` was tried first, but Chromium counts a cell half under a sticky column as visible and does
not scroll), and the card opens in the next frame, after that scroll's event, because PrimeNG closes an open
Popover on any scroll of its target's containers. The cell under the pointer or the keyboard lights its row and column.

**D-422 — Flags: words, icon, colour; the filter dims by colour for words.** "Rests on an elective" (warning circle,
ambiguous), "Rests on one course" (book, neutral outline), "Outdated evidence" (clock, ambiguous), "No evidence cited"
(dashed circle, missing). The chips are a PrimeNG SelectButton with `allowEmpty`, drawn as separate pills, one per flag
that occurs, named "Rests on an elective, 1" and grouped as "Highlight a flag"; a polite live region states the result.
Deviation from point 3: in the dimmed rows the marks, points and icons fade to opacity .35 over 200 ms, but words step
back to `text-2` instead (and the flag chip loses its tint): at .35 opacity axe measured 1.71:1 for the requirement
titles, and DESIGN.md requires AA for every text pair. The rows keep their place and the matrix its shape.

**D-423 — Narrow windows.** The frame scrolls sideways at every width (the page never does); the requirement column is
sticky left and the flag column sticky right. Under 900 px the pills take their own row; under 640 px the chips scroll
in one row, and the requirement column is 164 px (orchestrator review: 132 px cut "Psychopharmacology"). Its title
wraps to two lines at most (`line-clamp: 2`, ellipsis, `hyphens: auto`, a longer word breaks rather than being cut),
and every row is 60 px whether its title takes one line or two. A title the clamp cuts gets a tooltip with the full
title (measured after each render and resize); the full text is always in the DOM for assistive technology. The flag
column shows the icon (its words stay
for assistive technology; the chips above pair each icon with its words), and the rules-engine note stays beside the
requirement column. One line outside this item: the bottom tab bar's items are 40 px instead of 44 px
(`shell/shell.css`), because the ninth screen made the bar scroll by 4 px at 400 px.

**D-424 — Motion.** Every cell carries its column index (`--lx-c`): the requirement column enters first, each course
column 20 ms after the one before it, at most twelve staggered (the rest with the twelfth), with `backwards` fill so the
dimming works once the entrance has ended; each mark scales in from .6 on `--lx-spring`. Hover scales a mark to 1.12.
Reduced motion removes all of it through the global rule in `styles.css`. Only opacity and transform move.

**D-425 — Tests and bundle.** Vitest (`coverage-view.spec.ts`): column order and groups, cell lookup by course version,
outdated cells under `supersededBy`, current-plus-outdated cells, the program column, rules-engine rows, flag counts and
matching, the pill share, keyboard movement. End to end (`coverage.spec.ts`, which runs before the suite that re-runs
the COUN 625 change): CA-2's COUN 605 dot in the COUN 605 column, CA-6 "Rests on an elective", Utah's UT-7 "Outdated
evidence" with a dashed cell in the COUN 625 column, the URL state across a reload, keyboard movement and the single tab
stop, Enter and Escape on the card, "Open in Review" landing on CA-2, the flag filter dimming rows, axe in both themes at
1440 and 400 px with the card open and a filter on, no sideways page scroll, a focused cell not under a sticky column.
The screen is its own chunk (36.9 kB raw); the initial bundle is 606.07 kB raw after merging `main` (warning 680 kB).

### FIX-6 — OCR checkpoints per page (D-436 to D-441)

**D-436 — One immutable table, no job per page.** `document_ocr_page` (V017) holds one row per page that was sent to
the OCR engine: `tenant_id`, `document_id`, `page_number`, the normalized `text`, `mean_confidence`, `render_dpi` and
`created_at`, primary key `(document_id, page_number)`. The primary key serves the only read (all checkpoints of one
document), so there is no other index. Row-level security is enabled and forced with the same `tenant_isolation`
policy as `document_page`; the application role gets its privileges from the default privileges of the role script, so
the migration has no `GRANT`. `forbid_change()` (V003) rejects UPDATE and DELETE (rule 5). Rows stay after the ingest
succeeds or fails for good: they record how a page's text was obtained. `JobRunner` and the job table are unchanged;
the retry that already exists is what resumes.

**D-437 — The checkpoint stores what the engine said, never a verdict.** Every page sent to the engine is saved, the
too-short ones and the ones with no words too (`text = ''`, `mean_confidence` NULL; `OcrPage.NOTHING` in code). Whether
the text is enough to count as read (`TextExtractor.hasEnoughText`) is decided in code from the stored text on every
extraction, so a checkpoint can never change an outcome and a later change of that threshold applies to old
checkpoints too. The confidence is saved after the same clamp and two-decimal rounding that `document_page` applies, so
a page read from a checkpoint carries exactly the confidence the first run gave it.

**D-438 — `OcrCheckpoints`, and `find` takes the render DPI.** The interface has `find` and `save` and the constant
`NONE`; `TextExtractor.extract(FileType, byte[])` delegates with `NONE`, so rule texts, rule sources and the existing
tests are unchanged. A page that needs OCR asks `find` first; on a hit it neither renders nor calls the engine; on a
miss it renders, recognises and saves before the next page starts. A checkpoint taken at another DPI than
`renderDpi(page)` (the DPI rule changed, D-164 family) is ignored and the page is recognised again; the insert is
`ON CONFLICT DO NOTHING`, so the first checkpoint of a page stays and this run uses its fresh result in memory. The
spec lists `find(int pageNumber)`, which cannot express that comparison, so the signature is
`find(int pageNumber, int renderDpi)` (see the report; this is the one deviation).

**D-439 — Commit per page with `PROPAGATION_REQUIRES_NEW`.** `DocumentOcrCheckpoints.forDocument(tenantId,
documentId)` loads every checkpoint of the document in one statement and returns a `ForDocument` that is used for one
ingest attempt. `save` runs `INSERT ... ON CONFLICT DO NOTHING` through a `TransactionTemplate` that always starts a new
transaction, so the row commits although the job's transaction rolls back later. The new transaction gets a new
connection from `TenantAwareDataSource`, which binds `lexvay.tenant_id` on checkout; nothing needs the worker flag
because the ingest job already runs as its tenant. This is proved, not assumed: a test saves inside a transaction marked
rollback-only and finds the row afterwards, and the isolation test shows tenant B can neither read nor write tenant A's
checkpoints through the same path.

**D-440 — Audit, no new action.** `DOCUMENT_INGESTED` gains `ocrPagesReused`, the number of pages read from a
checkpoint instead of the engine (`ForDocument.reused()`, counted in `find` when a checkpoint at the right DPI is
returned). It is a detail of an existing action, so `Rule9_AuditCoverageTest` and the frontend's `audit-sentence.spec.ts`
need no row. No endpoint changes. The handler reads the tenant before extraction because the checkpoints need it.

**D-441 — Tests.** `TextExtractorOcrTest` (where the fake-engine OCR tests of F11 already live) gains the checkpoint
cases with an in-memory `InMemoryOcrCheckpoints` that has the table's semantics: zero engine calls and equal pages on a
second extraction, a page saved before the next is read, a short checkpoint still falling back to the text layer, a page
with no words stored as nothing, and a DPI mismatch recognising again. `FakeOcrEngine.failOnCall(n)` throws once on the
n-th recognition. `DocumentOcrCheckpointIngestTest` runs the real upload and job path: the engine fails on page 3 of 4
on the first attempt only, pages 1 and 2 are committed after the rollback, the retry recognises pages 3 and 4 only, the
final `document_page` rows equal those of a clean run in another program, and the audit says `ocrPagesReused = 2`. It
also covers the rollback-only commit and the keep-first rule. `Rule5_OcrCheckpointImmutabilityTest` and
`Rule7_OcrCheckpointIsolationTest` cover rules 5 and 7; `Rule7_EveryTableHasPolicyTest` covers the table without a change.

### FIX-7 — One "latest finished assessment" ordering (D-450 to D-452)

#### D-450 `latestAssessmentsUsing` composes `AssessmentRepository.LATEST_FINISHED`

`MappingRepository.latestAssessmentsUsing` (the re-run handler's target list) was the last statement with its own copy of
"the program's latest finished assessment" (the correlated subquery aliased `a2`). It now reads

```sql
SELECT la.id AS assessment_id, p.id AS program_id, la.created_by
FROM program p
JOIN LATERAL (SELECT a.id, a.created_by FROM assessment a WHERE a.program_id = p.id AND <LATEST_FINISHED>) la ON true
WHERE EXISTS (SELECT 1 FROM mapping m JOIN requirement r ON r.id = m.requirement_id
              WHERE m.assessment_id = la.id AND r.rule_version_id = :rv)
```

the same shape as the work queue's `latest` CTE (D-411). D-411 said the constant did not fit this statement because it was
written for a correlated subquery over `a2`; the lateral form removes that obstacle, so no second constant is needed. The
method name, signature and result keys (`assessment_id`, `program_id`, `created_by`) are unchanged. The text is a public
constant, `MappingRepository.LATEST_ASSESSMENTS_USING`, so the index check explains exactly what runs (as
`BY_ASSESSMENT` does). The result has one row per program by construction, so the `DISTINCT` is gone.

Cleanup check: `grep -rn "finished_at DESC" backend/src/main` shows only `AssessmentRepository.LATEST_FINISHED` (the index
definition in migration V008 is not a query). The two follow-up lines about this (FIX-5 and F10a) are removed from the
ROADMAP.

#### D-451 Parity test: old statement as a literal, compared as sets

`LatestAssessmentsUsingParityTest` loads the seeded Northfield tenant (`SeedCopy`, as `WorkQueueSeedParityTest` does) and
keeps the old statement as a literal. For every rule version of the seed it asserts that the old statement and the new
method return equal sets. It adds three programs whose history separates the readings: the newest finished assessment does
not use the version (the older one does), the newest assessment is FAILED and finished later than the older finished one
that does use the version, and two finished assessments share a finish time (creation time decides). It then asserts the
expected assessments by name, so the parity cannot pass because both sides are empty. Fixtures are plain inserts under the
tenant's row-level security; nothing is mocked.

#### D-452 Index usage: same expectation as the work queue's `latest`

`IndexUsageTest` runs `EXPLAIN (ANALYZE, BUFFERS)` of `MappingRepository.LATEST_ASSESSMENTS_USING` on the perf seed (100
tenant copies, as the application role) for the first seeded rule version, writes the plan to
`target/perf/latest-assessments-using-plan.txt`, and fails on a sequential scan of `mapping` or `assessment` that was
executed. The plan and timings are in `docs/progress/FIX-7.md`.

### F5b — Administration screens (D-426 to D-435)

**D-426 — People: one tenant-wide screen for administrators, filtered in the browser.** `/people` is a tenant-wide
route like the register (`authGuard`, then `adminGuard` on the route in `people.routes.ts`, loaded with `loadChildren`
so `PEOPLE_ICONS` stay in its chunk). `adminGuard` sends anyone else to the overview of the program last worked on, or to
Programs, which opens the only program; the server refuses them in any case. `navLinks(programId, counts, role)` now takes
the role and lists People (`phosphorUsers`, added to `SHELL_ICONS` because the rail draws it) under the register for
administrators only; the command palette reads the same list, so "Go to People" follows. The shell shows the rail on any
tenant-wide link's path instead of naming `/register`. The list is one `GET /users` (the administrator's fields, D-375);
a tenant has tens of people, not thousands, so search (name or email, anywhere, ignoring case), the role chip and the
status chip filter in the browser through the pure `filterPeople` (`people-view.ts`, tested), and the count reads "1 of 6
people". Status: deactivated first, then "Setup pending" while `passwordSet` is false, else Active; each chip a Tag with
icon and word. The chips are the audit trail's pill Selects: their rules moved from `audit.css` to `styles.css`
(`.lx-chip`), unchanged, so both screens share one copy. Rows: avatar initials (the audit trail's Avatar set, moved to
`theme/avatar-tokens.ts`), name with a "You" pill on the administrator's own row, email, role chip, status chip, created
date, and a row menu (PrimeNG Menu, Phosphor icons through its item template). `personActions` decides the menu: Change
role, Deactivate or Reactivate, New setup code; the own row offers New setup code only (the server answers 409 to the
other two, D-375). Under 720 px each row becomes a block (who and the menu, then role, status and date), as the
register's table does. Rejected: server-side filters (an endpoint change for a list this small); hiding the menu on the
own row (the reset of one's own password is legitimate, D-375).

**D-427 — Add person and the one-time link live in one dialog, which alone holds the code.** `PersonDialog` has two
phases: the form (name, email, role; the role Select defaults to Contributor, the least a person can do, with an InfoTip
"About roles" giving one line per role as the server's permissions stand) and the link view. On 201 the dialog turns into
the link view: the person, the whole link `<origin>/setup#code=…` (D-435) in mono with "Copy link" (focus moves there), "Valid for
72 hours, until 30 Nov 15:04" (`expiryText`, local time, hours rounded) and "Shown once. Copy it now and hand it over
yourself." "New setup code" opens the same view for an existing person, issuing the code from the dialog and adding
"Earlier links stop working." (D-372). The code exists only in the dialog's private `setup` signal: People's list
receives the account without it, nothing is written to storage or the console, and closing the dialog (Done, Escape,
the close button) runs `reset`, which drops it. A 409 ("An account with this email exists") sits under the email field;
other failures above the form. Rejected: showing the code in a toast or on the row (it would outlive the view); an
email field the server already validates, checked again only for shape and presence.

**D-428 — Setup page: the sign-in frame without the story; the code leaves the address bar in the constructor.**
`ui/auth-frame.ts` (`lx-auth-frame`) is what sign-in and setup share: the grid, the form column with the mark and
wordmark, and the form vocabulary (`lx-auth-field`, labels, heading), with encapsulation off because that content is
projected; sign-in projects its map story with `lxAuthStory` and `[story]="true"`, setup shows the form alone. Sign-in
looks as before. `/setup` is anonymous (no guard) and stands alone like sign-in (`beforeSignIn` in `core/navigation.ts`,
used by the shell's top bar and by the access handling of D-430). The component reads `code` from the fragment
(`location.hash`, D-435) into a private field and calls `history.replaceState(history.state, '', '/setup')` in its constructor: the router has already
written the URL by then (it does so before activating the route), so the code is gone from the address bar and from
history before the first paint. Without a code (a reload after that) the page says "This setup code is not valid." with
"Ask your administrator for a new link." and "Go to sign in". The POST needs the CSRF cookie, so the session is loaded
first, as sign-in does. Answers: 204 goes to sign-in with the toast "Password set. Sign in."; a 400 whose detail names a
password rule ("Password must ...") stays under the form, since the code still works (D-373); every other 400 is the
invalid-code view (the server's one message for unknown, used and expired codes); a 503 is D-431.

**Deviation: "not the email" is checked as "not an email address".** The page holds only the code; no anonymous endpoint
turns a code into an email, and adding one is a backend change outside this item. So the live rule refuses any password
shaped like an email address (`^[^\s@]+@[^\s@]+\.[^\s@]+$`, after trimming), which includes the account's own: the client
is stricter than the server, never looser, and the server still checks its own rule. Rejected: a rule that shows as met
without knowing (a check the page cannot make would read as a promise); putting the email in the link (more personal
data in an address).

**D-429 — Live rules and a strength meter from length and variety.** `password-rules.ts` (pure, tested): the checklist
"12 to 128 characters" (code points, as the server counts, D-402), "Not an email address", "Both fields match", each
item an icon (empty circle, then a check in the found colour) and its words, with ", met" or ", not met" for screen
readers; both fields name it in `aria-describedby`. Submit stays enabled and says "Meet every rule above." when one is
not met (a disabled button would not say why); it is disabled only while a busy wait counts down. Strength: under 12
characters "Too short"; fewer than six distinct characters "Weak" whatever the length; otherwise one step at 12, two at
16, three at 20, plus one for three kinds of character among lower case, upper case, digits and anything else, capped at
"Strong" (four). Length counts most, so a long passphrase of lower-case words rates "Good". No dictionary library. The
meter is PrimeNG MeterGroup (role `meter`, named "Password strength" with the word as `aria-valuetext`), its colour going
with the word, never alone. The two fields are `pInputPassword` (PrimeNG's masking directive) in an IconField with a
real button in the icon slot ("Show password", `aria-pressed`): PrimeNG Password's own toggle is an SVG with a click
handler that a keyboard cannot reach.

**D-430 — A session the server ended signs out here: one interceptor in `core/access.ts`.** `accessInterceptor`
(registered with `withInterceptors` in `app.config.ts`) watches every answer for a 401 whose problem detail is exactly
"Your access changed. Sign in again." (`ACCESS_CHANGED`; the wrong-password 401 of sign-in has another detail and is left
alone). It calls `SessionService.end(notice)` (the session state is forgotten, so the next guard asks the server again,
and the sentence is kept in `notice`), clears the program context and navigates to sign-in, which shows the sentence in
a warning Message above the form until the next successful sign-in. The error still reaches the caller. It does not
navigate away from sign-in or the setup page. `SessionService.load()` treats a 401 from `GET /auth/session` as D-374
says: that answer ended the stale session, so it asks once more and gets the signed-out view, and the guard routes to
sign-in instead of failing the navigation. Rejected: matching every 401 (a session that timed out is a different story,
with no sentence to show); a polling check of the session (the next request already finds out).

**D-431 — Busy sign-in and setup: the server's Retry-After, counted down on the button.** `busyRetryAfter(e)` in
`core/busy.ts` is the seconds of a 503 that carries a numeric `Retry-After` (SEC-2 sends 2), kept between 1 and 60; a 503
without the header (a proxy without a backend) is not this case and shows the ordinary error. Sign-in and setup then show
"Sign-in is busy. Try again in a moment." (`BUSY`) under the form and disable the submit button for that many seconds
with the label "Retry in 2 s", "Retry in 1 s", counted by `Countdown` (a signal and one interval, stopped with the
component). On setup the code is not used up by a busy refusal (D-407), so the same form is submitted again.

**D-432 — Role change, deactivation and reactivation confirm in a ConfirmDialog that stays open on a refusal.** One
keyed `p-confirmdialog` with message and footer templates: the person, the new role Select for a role change ("Change
role" stays disabled until the role differs, since an unchanged role is no change, D-375), and the consequence in one
sentence with an icon: "Their current session ends." for a role change and a deactivation, "They can sign in again." for
a reactivation. The footer's buttons call the API themselves instead of ConfirmDialog's accept, so a 409 ("At least one
active administrator is required", "You cannot change your own role") appears in the dialog and the dialog stays open;
success closes it, updates the row from the returned account and toasts "Riley Chen is now a reviewer.", "... can no
longer sign in." or "... can sign in again.". Deactivate is the only danger-coloured button on the screen.

**D-433 — Shared changes.** `ui/auth-frame.ts` from sign-in (D-428); `.lx-chip` from `audit.css` to `styles.css` and the
Avatar set from `audit-theme.ts` to `theme/avatar-tokens.ts` (D-426, values unchanged); `@keyframes lx-rise` moved from
`login.css` to `styles.css` for the frame and the sign-in headline; `navLinks` takes the role (D-426); `beforeSignIn`
replaces the shell's `/login` test; `SessionService` gains `notice`, `end` and the 401 retry (D-430); `ApiService` gains
`accounts`, `createUser`, `issueSetupCode`, `changeRole`, `setActive` and `setPassword`; `models.ts` gains `Account`,
`UserSetup` and `NewUser`. No backend change: the F5a actions already have audit sentences (UI2-3, D-341), and the
backend neither builds nor documents the setup link (it returns the code alone; `docs/OPERATIONS.md` does not mention the
link). Review round 1 added: `register.html` takes `class="lx-table"` instead of `styleClass`, which `p-table` does not
have in PrimeNG 22, so the register's narrow-screen table rules (UI2-1) finally apply; `e2e/support.ts` gains `goTo`,
used where an administrator opens a screen that may sit under "More" at 400 px (`records.spec.ts`, `screens.spec.ts`).

**Bottom tab bar overflow (review round 1).** With F10b's Coverage and People, an administrator has ten screens, and the
bottom tab bar (under 640 px, 40 px a tab, 4 px padding each side) scrolled sideways at 400 px. Each `NavLink` now has a
`priority` (how much the screen is used: overview 90, review 85, coverage 80, requests 75, changes 70, register 60, rules
40, report 30, People 20, audit trail 10). `tabSlots(width)` counts the tabs that fit and `tabBarOverflow(links, slots)`
(pure, tested) returns the screens that go into a final "More" tab: none when all fit, otherwise all but the `slots - 1`
of highest priority, kept in rail order. At 400 px an administrator's People and Audit trail move; every other role still
fits nine. A new screen (UI2-4's Program setup) needs only its priority. The "More" tab is a button (`aria-haspopup`
menu, `aria-expanded`, the accent underline and "More screens, <screen> open" when the current route is one of its
screens) opening a PrimeNG Menu in a deferred chunk (`shell/more-menu.ts`), so arrow keys, Enter and Escape work and
focus returns to the tab. Hidden screens stay in the DOM (`display: none` under 640 px only), so the rail above 640 px
is unchanged. Rejected: a horizontally scrolling bar (the screens off the edge are invisible and their count unknown);
dropping labels further below 40 px (targets under 40 px).

**D-434 — What the tests prove.** Vitest: `screens/setup/password-rules.spec.ts` (limits in code points with four-byte
characters, the email shape, the match, every strength step, the distinct-character cap, case in other alphabets),
`screens/people/people-view.spec.ts` (status precedence, search, role and status filters and their combination, the
count, upsert order, the own-row menu, the link, the expiry sentence, the form checks), `core/access.spec.ts` (TestBed
with the real interceptor: sign-out, context cleared, sign-in route, the error still thrown; a wrong-password 401 left
alone; no navigation from setup; a 401 session call read as signed out; the notice cleared by a sign-in),
`core/busy.spec.ts` (header parsing, bounds, the countdown and its stop), `core/navigation.spec.ts` (People for
administrators only, `beforeSignIn`, the tab slots, which screens overflow into More and a new screen placed by priority), `commands.spec.ts` and `icons.spec.ts` (two new units). End-to-end
(`e2e/people.spec.ts`): the administrator adds a contributor, copies the link (clipboard read back) and the code is in no
browser storage; signs out; the link leaves the address bar; the password is set and the toast shown; the new person
signs in and has no People; the used link is refused with the invalid-code view. A second context signs in as another new
person; the administrator changes their role and their next request lands on sign-in with "Your access changed. Sign in
again."; they sign in again as Reviewer; deactivation ends their session and sign-in fails with the generic message;
reactivation lets them in; a new setup code shows the view with a different link. The own row offers only New setup code.
Filters and search. A reviewer has no People and `/people` sends them to the overview. axe in both themes at 1440 and
400 px without horizontal scroll on the list, the add dialog, the link view, the role confirmation and the setup page
with rules partly met. Two answers are fulfilled by the test instead of the server, because no honest sequence of
requests produces them on demand: the 409 inside the role confirmation (the last-administrator rule is reachable only
through a stale session or two administrators at once, D-376) and the 503 of the hashing gate (SEC-2's own tests hold
the permits in the backend). Both carry the server's exact problem detail and header.

**D-435 — The setup code travels in the fragment; bundle.** The link is `<origin>/setup#code=…` (orchestrator's decision
in review round 1, replacing point 4's `?code=`). A query string goes to the server with the page request, so any access
log in front of the application (a load balancer or CDN log, if one is ever enabled; none is in `infra/` today, and the
application's `ApiAccessLog` logs only `/api` paths and never a query string) would record live codes. A fragment is never
sent to any server: the page reads it from `location.hash`, removes it with `history.replaceState` in its constructor, and
the code leaves the browser only in the body of `POST /auth/setup`. One form only: nothing was deployed, so no `?code=`
link was ever handed over and the page does not read one. The end-to-end test records every request the browser sends
while the person opens the link and sets the password and requires that none of their URLs (fragment removed, as on the
wire) carries `code=` or the code. Initial bundle in `docs/progress/F5b.md` (the interceptor, `beforeSignIn`, the rail's
People and More icons and the More tab are the only initial additions; People, setup and the More menu are lazy chunks).

## Design system v3 (D-479)
**D-479 — Design v3 "Ledger" replaces v2.** On 2026-11-27 the owner reviewed v2 in the running app and rejected it as
AI slop. They named yellow and orange accents, icon-heavy cards and a generated-looking palette, and asked for
professional, human craft. The orchestrator audited every screen and found seven causes, all listed in
`docs/DESIGN.md`: a stock rainbow palette, pills everywhere, an icon per label, decorative rings and count-ups, a
gimmick tile map, card soup and undisciplined type. v3 fixes each one:
- near-monochrome ink on paper, with colour only for found, missing and the accent;
- hairlines and space instead of cards;
- status as a small mark plus a word;
- numbers shown plainly, with to-scale bars only where proportion matters;
- one type scale on a 4 px grid;
- 180 ms motion that never performs.

Amber is removed entirely; ambiguous, outdated and demo states are neutral and told apart by shape and words. The
state map, rings, count-ups, duotone icons and the DecisionBar blur are retired. Rolled out as UI3-0 (foundation,
shell, sign-in, overview), then UI3-1 to UI3-3 in parallel.


### INFRA-2 — Remote state, WAF, flow logs and private-range egress (D-463 to D-470)

**D-463 — Remote state is a partial S3 backend with native locking; the bucket is bootstrapped outside the stack.**
`versions.tf` declares `backend "s3" {}` with no values; `infra/backend.hcl.example` carries placeholders for `bucket`,
`key`, `region`, `encrypt = true`, `kms_key_id` and `use_lockfile = true` (the lock is an object beside the state, so
there is no DynamoDB table and no second resource to bootstrap). `backend.hcl` is in `.gitignore`. `make infra-validate`
keeps `init -backend=false`, so validation and the offline tests never need a bucket or credentials.
`docs/OPERATIONS.md` gives the one-time bootstrap (versioning, SSE-KMS with a rotated key, public access block,
TLS-only bucket policy) as AWS CLI commands. Rejected: a Terraform-managed state bucket (the stack would store its own
state in a bucket it can destroy); a DynamoDB lock table (deprecated by the backend's own lock file).

**D-464 — The web ACL is four rules in fixed priority order, with one override.** `waf.tf` creates the ACL with scope
`CLOUDFRONT`, default allow, through the provider alias `aws.us_east_1` (the only region CloudFront ACLs exist in).
Priorities 0 to 3: `AWSManagedRulesAmazonIpReputationList`, `AWSManagedRulesKnownBadInputsRuleSet`,
`AWSManagedRulesCommonRuleSet` (with `rule_action_override` of `SizeRestrictions_BODY` to `count`: uploads are up to
25 MB and the application enforces that limit), then the sign-in rate rule. The managed groups use
`override_action { none {} }`, so each keeps its own actions. The ACL is attached with `web_acl_id` on the
distribution. Every rule and the ACL emit CloudWatch metrics and sampled requests, named from `local.name`.

**D-465 — The sign-in rate rule counts per IP over 5 minutes on a normalised path.** `rate_based_statement` with
`limit = var.waf_login_rate_limit` (default 300, validated 100 to 20000 and whole), `evaluation_window_sec = 300`
written out rather than relying on the provider default, `aggregate_key_type = "IP"` (WAF sees the viewer's address
because it sits on CloudFront), action `block`. The scope-down is a byte match, `STARTS_WITH` `/api/v1/auth/` on the URI
path, with `URL_DECODE` then `NORMALIZE_PATH` applied first: the application decodes and normalises the path before
routing, so `/api/v1/%61uth/` or `/api/v1/./auth/` must not escape a prefix match on the raw string. WAF requires at
least one text transformation, so choosing them is part of the rule, not a change of the spec. The rule complements the
Argon2 gate and does not replace it.

**D-466 — WAF logs go to a log group with a resource policy of our own, and the group is not KMS-encrypted.** The group
is `aws-waf-logs-${local.name}` in us-east-1 (the prefix is mandatory), retention `var.log_retention_days`. Without a
policy of our own AWS WAF writes to a shared `AWSWAF-LOGS` policy that grows until it exceeds the size limit and then
fails every new ACL; `aws_cloudwatch_log_resource_policy` grants `delivery.logs.amazonaws.com` `CreateLogStream` and
`PutLogEvents` on this group only, conditioned on `aws:SourceAccount` and the account's us-east-1 log ARNs. The
`authorization` and `cookie` headers are redacted (lowercase names, as the provider documents). The data key is in the
stack's region and a KMS key cannot protect a log group in another region, so this group keeps CloudWatch's default
encryption. Rejected: a second key in us-east-1 (a second thing to operate for a log that never holds credentials).

**D-467 — Flow logs are encrypted with the data key, and the key policy is written out.** `aws_flow_log` on the VPC,
`traffic_type = "ALL"`, to a CloudWatch log group (`/<project>/<environment>/vpc-flow-logs`) with
`kms_key_id = aws_kms_key.data.arn` and the configured retention. The delivery role trusts only
`vpc-flow-logs.amazonaws.com`, with `aws:SourceAccount`, and may write only to that group. CloudWatch Logs needs the key
policy's permission to use the key, so `aws_kms_key.data` now has an explicit policy: the account-root statement that
the default policy gave (it is what lets the task roles, RDS, S3 and Secrets Manager use the key through IAM, so
dropping it would lock them out), and a statement for `logs.<region>.amazonaws.com` conditioned with `ArnEquals` on
`kms:EncryptionContext:aws:logs:arn` equal to the flow log group's ARN. That ARN is built from the group name in a
local, because reading it from the resource would make the key depend on the group and the group on the key.

**D-468 — The network ACL uses one resource per rule, and link-local stays outside it.** `aws_network_acl.private` is
associated with every private subnet; its rules are `aws_network_acl_rule` resources (an `egress` map keyed by rule
number, plus the two ingress rules), not inline blocks, so a rule change is a one-rule diff and the two styles never
fight. Outbound: 100 allow the VPC CIDR, 110 to 140 deny `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`,
`100.64.0.0/10`, 200 allow `0.0.0.0/0`. Inbound: 100 allow the VPC CIDR, 200 allow TCP 1024 to 65535 from
`0.0.0.0/0` (return traffic; ACLs are stateless). The VPC allow is first, so a VPC that itself sits inside one of the
denied ranges (the default `10.20.0.0/16` does) keeps working. Link-local addresses, the ECS credentials endpoint
included, never pass a network ACL; this is stated in a comment in `nacl.tf` and in `docs/OPERATIONS.md`, and
`SsrfGuard` remains the control for them. Rejected: a deny for `169.254.0.0/16` (it would have no effect and would
suggest a control that does not exist).

**D-469 — Both AWS providers share one `default_tags` local.** The us-east-1 alias must tag its resources like the
primary provider, so the map moved to `local.default_tags` in `versions.tf` and both provider blocks read it.

**D-470 — The offline tests compare overridden values, and the roadmap edit is limited to the Terraform sentence.**
A plan leaves generated ids and ARNs unknown, and the test runner refuses to evaluate a condition on two unknowns, so
`stack.tftest.hcl` gives the web ACL's ARN, the data key's ARN, the flow log group's ARN, the private network ACL's id
and the two private subnet ids a fixed value with `override_resource` and `override_during = plan`. The
aliased provider has its own `mock_provider`. Set-typed blocks (rules, policy statements) are checked with `for`
expressions, since sets cannot be indexed. The new runs cover the ACL attachment, rule order and the body-size
override, the sign-in rule, the log group and redaction, the rate-limit bounds (99 and 20001 rejected), the flow log and
its trust and key policy, and the network ACL rules and association. In `docs/ROADMAP.md` the F12 deployment note was
removed whole; from the WS-EVAL line only the Terraform sentence was removed, because the rest of that bullet (the
unmeasured thresholds, the database role bootstrap) is still open.

### UI2-4 — Rules, evidence requests and program setup (F2) in v2 (D-285 to D-294, D-350 to D-359)

UI2-4 restyles the Rules workspace and F4's evidence request screens to design v2 and builds F2 (program setup) in v2.
F2's decision range belongs to this item; the F2 decisions come first.

## Program setup (F2)

**D-285 — One route for a new and an existing program.** `programs/new` and `programs/:programId/setup` are one route
(`setupMatcher` in `app.routes.ts`, placed before `programs/:programId`, unit-tested), loaded with `loadChildren` from
`screens/program-setup/program-setup.routes.ts` like every screen with its own icons. Creating a program navigates to its setup URL with
`replaceUrl`, and the same `SetupWorkspace` instance goes on to Courses. A route guard sends anyone but a contributor or
administrator from `/programs/new` to the home screen; the title resolver names the page "New program" or "Program setup".
The rail lists "Program setup" (`phosphorWrench`, in `SHELL_ICONS`) after Program overview for every role; the home
screen shows "New program" to contributors and administrators, and the program switcher's footer has a "New program"
link for them. Rejected: two routes (Angular would rebuild the screen between them and the stepper would restart).

**D-286 — The Stepper's semantics are replaced.** PrimeNG's Stepper marks its root a `tablist` holding presentation steps
with `aria-current` and its panels as `tabpanel`s whose `aria-controls` name ids that do not exist; axe reports
`aria-required-children` (critical) and `aria-valid-attr-value`. Through pass-through the root has no role, the step
list is a `list` named "Setup steps", each step a `listitem`, and each step is a button with `aria-current="step"` when
open (the WAI pattern for a step sequence); a panel has no role, and the region inside it is named by its step's button.
Each step button carries number, name, a short status ("2 courses", "1 still reading", "2 missing", "Ready") and
"done"; a finished step shows a check, the open one the accent number. Steps are freely navigable once the program
exists; on `/programs/new` Details and States are open (both are the create form), the rest are disabled. The spec says
"only Details is enabled", but the target states are part of `POST /programs`, so States must be reachable before it.

**D-287 — Details and States.** Details: name, license type (an editable PrimeNG Select over the license types the
tenant's rule versions use, so another can be typed), catalog year; "Next" checks the required fields and lengths the
server would refuse. States: a MultiSelect of jurisdictions ("Arizona (AZ)") with filter and the StateMap `mini` beside
it as a picture of the choice (non-interactive, hidden from assistive technology; "2 of 51 chosen" is its caption). The
mini map is a picture rather than a second control: its 16 px tiles would be 51 more tab stops with no room for names,
and the MultiSelect is the accessible control. The MultiSelect shows the chosen states as text, not chips: PrimeNG's chip
remove icons are focusable inside `aria-hidden` (axe `aria-hidden-focus`, serious). After creation both steps are
read-only with "Fixed once created" and an InfoTip holding the spec's sentence.

**D-288 — Courses: one request per new course (disagreement with the spec).** The spec has the Add course dialog call
`POST /programs/{id}/courses` and then `POST /courses/{id}/versions`. `POST /programs/{id}/courses` already creates the
course with its first version for the program's catalog year (D-039), so the second call would give every new course two
versions and open a COURSE_VERSION change event, stale mappings and reopened determinations for nothing. The dialog
therefore shows the catalog year as the program's (fixed) and makes one call; "New catalog year" is the way to a later
year (it supersedes the current version, with the one-line warning that it opens a change). A 409 on a new course is
shown under its code ("SCHC 501 already exists in this program"); other server words appear in a Message.

**D-289 — Documents.** A target Select ("Program documents" or a course) and a PrimeNG FileUpload in custom mode (choose
button and drop zone; `accept=".pdf,.docx,.txt"`, no client size or type rule) feed an `UploadQueue` (plain signals,
Vitest): a file going into a course is a syllabus of the course's current version, into the program a degree plan; each
waiting file can change its kind and, for a syllabus, its course version. "Upload n files" posts them one after the
other; each row then follows its document through Uploading, Reading and Ready (n pages) or Failed (the server's words, or
the ingest job's error). A 409 naming `existingDocumentId` is "Already uploaded" with a link that opens the existing
document. Below, the documents are grouped like F4's picker (program documents, then each course, current year first)
and each ready one opens read-only in `DocumentViewer` inside a sheet (D-293).

**D-290 — Reading status.** `GET /programs/{id}/documents` has no ingest status (D-263), so a listed document without
pages counts as being read until `GET /documents/{id}` says otherwise; failed, or done without a page, is "Not readable"
(`documentState`, Vitest). `SetupStore` asks after every document still being read every 2 s while the tab is visible,
refreshes the list when one finishes, and stops when none is left. No backend change was needed (spec point 7).

**D-291 — Program facts.** The rows are the numeric fields used by the approved rule versions an assessment started today
would use for the program's states and license type: `assessedVersion` mirrors `RuleVersionRepository.findApprovedVersion`
(approved, in effect today, latest effective date then newest entry), and `numericFieldsUsed` reads their requirement
lists (one `GET /rule-versions/{id}/requirements` per state, fetched once). Each row shows the newest fact (value, quote in
Document type, file, page, date) with earlier ones under History, or "Not recorded". "Record" opens a sheet: the value,
the shared document picker, `DocumentViewer` with `selectable`; the passage must be selected on the page (mouse or the
keyboard range dialog), the server verifies it (rule 2) and a 400 is shown under the passage. When the passage does not
show the number as written (`quoteShowsValue`: its digits, with or without thousands separators) a warning appears; it
never blocks.

**D-292 — Readiness and the first assessment.** `programReadiness(program, courses, documents, facts, ruleVersions,
requirements, today)` is the one checklist (Vitest): rules for every state (blocking; names the states, links to Rules),
a ready document for every course's current version (blocking; names the courses), nothing still being read (blocking),
every number recorded (warning, with the sentence that the rules engine reports it as missing evidence). Two additions to
the spec's signature: the requirement lists (the facts line needs them) and today's date (the rules line needs it, and it
keeps the function pure). A program without courses gets a warning line "No courses yet" instead of a vacuous green line.
Every line has an icon and words plus a visually hidden "Done", "Blocks the run" or "Warning". "Run assessment"
(contributors, administrators and reviewers, as the spec lists) is disabled while a blocking line fails. The run is
followed every 2 s: `GET /assessments/{id}` carries per-state mapping totals, so the ring shows requirements assessed
out of the requirements of the assessed versions; it ends in a toast, a live-region sentence and "Open Review", and
`ProgramContext.refreshAfterChange` loads the new assessment for Review. No progress field was added to the backend.

**D-293 — A shared sheet and a shared document picker.** Three drawers now exist (answer evidence, read a document, record
a fact), so F4's drawer pattern (D-265: own backdrop, Escape only from inside, heading focused on show, pass-through to a
named modal dialog) is one component, `shared/sheet/sheet.ts`, in two sizes (wide 70 %, narrow 40 rem; full width under
1024 px); the evidence drawer uses it too. F4's grouped picker (`documentGroups` and the earlier-years switch) moved to
`shared/document-picker/` with its tests, used by the evidence drawer and the fact sheet.

**D-294 — End-to-end without a match script.** The e2e backend scripts only extractions; match scripts need document ids
that exist only after a seed (D-280), so any requirement that reached the model would fail the run. The fixture syllabi
(`e2e/fixtures/schc501-syllabus.pdf`, made from `fixtures/docs/` with `SeedPdfGenerator`, and `schc520-syllabus.docx`,
a minimal WordprocessingML file; both labelled fictional) share no word with Arizona's or California's subject
requirements, titles or search terms, so every such requirement has no candidate passage and is reported missing without
a model call, and the numeric ones are checked by the rules engine. That is what the product does with such documents.
`setup.spec.ts` runs as its own Playwright project after `register`: once it has created a second program, the home
screen lists programs instead of opening the only one, which the other specs' sign-in expects.

## Rules, requests and the rest (D-350 to D-359)

**D-350 — Rules as one hero.** The version list and the selected version share one raised panel: the list on the inset
colour with state-code tiles for groups and each version's status as a coloured icon and a word (`RULE_STATUS.tone`, one
table with the Tag severity the header uses); the selected version is lifted onto the surface. The detail header is the
focal point: state, the label at Title size, license, effective date and source as pills, the status Tag and the
approval line, and the requirement count as a Stat. Paragraphs moved into InfoTips (page, new-version dialog, edit
grid). Extraction states are empty states with a 32 px duotone icon (the spinner turns) and one sentence.

**D-351 — Search terms are editable chips (F7c point 5).** The edit grid's second line holds the text and a "Search
terms" cell: PrimeNG AutoComplete in multiple mode without suggestions (Enter, a comma or leaving the field adds a term).
`applyTerms` (Vitest) normalises as the server stores (trim, collapse spaces, lower case), splits pasted lists, drops
blanks and repeats, and refuses a term over 40 characters or a thirteenth with a sentence under the cell; the count reads
"3 of 12". Terms are part of the editor's dirty check now (D-259 had them pass through). After a save the screen counts the
terms the server's normaliser dropped (`droppedTerms`) and says so in the notice. The read-only table has a "Search terms"
column of pills. Enter in the cell must not submit the list form, so it is prevented on the cell. PrimeNG renders the
chips as an unnamed `listbox` of `option`s holding controls (axe `aria-input-field-name`, `nested-interactive`, both
serious); pass-through makes them a named list of items.

**D-352 — The title takes the free width (F3b follow-up).** One line needs 50.25 rem (ref 6, title at least 10, type 8.5,
check 17.75 and actions 6, four gaps); a narrower pane uses two lines, where the check controls grow with the row so the
field names are no longer cut. At 1440 px the pane is about 760 px, so the grid is two lines there; the e2e test still
checks no sideways scroll at 1024, 1280 and 1440 px.

**D-353 — `SEARCH_TERMS` in Compare.** A row whose changes include `SEARCH_TERMS` shows "Search terms" with the old list and
the new one as pills, removed terms struck through in `del` and added ones underlined in `ins` (status colour on its tint,
plus hidden "removed:" and "added:"), never colour alone (`termsDiff`, Vitest). The summary counts are four pills with
their icons and count up on first view.

**D-354 — Requests: signed requests are blocked (FIX-3).** `blockedReason(row)` is SIGNED (a signature outranks staleness,
since a re-run does not lift it) or STALE; both show the Blocked tag, a short reason in the row ("Signed", "Waiting for a
re-run") and the sentence behind an InfoTip. The rail badge (`awaitingAnswer`) counts open requests that are not signed:
a signed one can never be answered, as the work queue already treats it (D-361); stale ones still count. The inbox is
the screen's hero panel with the segmented filter and a "n blocked" note; rows enter with the stagger; the empty state is
one sentence with "Open Review". "Answered, awaiting review" became "Answered" with "With the reviewer" in the action
column (word budget).

**D-355 — The answer drawer in v2.** The requirement card (state tile, ref, title, rule text in Document type) with the
request as a quoted card with the asker's initials; then three numbered parts (document, passage, comment); the
selected passage in Document type on the accent tint; the empty page area is a duotone placeholder. Behaviour is F4's.

**D-356 — Token sets per chunk.** New Lexvay sets: Drawer (raised, e3, 20 px title), Stepper (accent number, line
separators, panels on the page), FileUpload (inset drop zone, accent edge), MultiSelect with its Checkbox and Chip sets,
Chip (pills). Setup registers Stepper, FileUpload, MultiSelect, ToggleSwitch, Drawer, Dialog, InputText, InputNumber,
Select, ProgressBar and the shared ones from `setup-theme.ts`; Rules adds AutoComplete and Chip; Requests uses the Lexvay
Drawer instead of Aura's. Nothing joins the shared preset.

**D-357 — Shared CSS.** `styles.css` gains `.lx-title-row` (title and InfoTip), `.lx-empty-state`, `.lx-state-code`, and
the sheet pieces two screens share (`.lx-numbered`, `.lx-passage`, `.lx-placeholder`). Links drawn as PrimeNG buttons
(`<a pButton>`, "Open Review", "New program") are no longer underlined (`a.p-button` in the `lexvay` layer); main's
merge already keeps their colours.

**D-358 — Bundle.** Initial bundle 608.88 kB raw, 156.43 kB transfer after merging main (606.77 kB on the main this item
started from; the wrench icon in the shell's copy, the rail entry, the switcher's footer and the shared CSS), under the
680 kB warning. Lazy: `setup-routes` 236.5 kB raw (43.7 kB transfer), `rules-routes` 278.6 kB. Nothing joined the preset.

**D-359 — Tests.** Vitest: `search-terms.spec.ts` (chip model, Compare marks, dropped count), `setup-model.spec.ts`
(assessed version, numeric fields, readiness, document state, first open step, quote check, history),
`upload-queue.spec.ts`, `evidence-requests.spec.ts` (signed and stale blocked, badge count), the editor's terms, the
moved picker tests, `app.routes.spec.ts` (setup matcher); `icons.spec.ts` has a setup unit. Playwright: `setup.spec.ts`
(the whole setup to Review, a duplicate upload, a reviewer's read-only pass, 400 px, axe at every step in both themes),
`rules.spec.ts` (terms editing, the dropped-term notice, a terms-only Compare row from a modified diff response),
`evidence-requests.spec.ts` (stale and signed blocked rows with their InfoTips and the badge), screenshots
`ui2-4-*` in both themes at 1440 and 400 px.

## Review round 1 (orchestrator, PR #43)

- **Rules opens on a version (amends D-350).** Without a version in the address, Rules navigates (with `replaceUrl`, the
  query kept) to `openingVersion(groups)`: the first version in list order that waits for a person (needs approval or
  extraction failed), otherwise the newest approved version of the first state with one. The empty detail remains only
  for a tenant without versions. Vitest covers the three cases; the e2e test checks the opened version and that Back
  does not return to an empty Rules.
- **Setup says what happened through the toast only (amends D-292).** Every step reports a `SetupNotice`; the workspace
  shows it as a toast and puts the same words in a visually hidden polite live region (`data-testid="message"`). Nothing
  stays between the title and the stepper.
- **Request cards name the answer count.** In the card layout (under 720 px) the count reads "No answers", "1 answer",
  "N answers" (`answersText`, Vitest); the table keeps the number under its "Answers" header.
- **Phone bar.** F5b's bottom-bar overflow (`NavLink.priority`, `tabSlots`, `tabBarOverflow`) decides what goes behind
  "More". Program setup has priority 65, just below Changes (70) and above the register (60): at 400 px it keeps its tab
  for every role, and Evidence report and Audit trail (and People for administrators) go behind More. Unit tests cover
  every role at 400 px and the shrinking order; the e2e suite checks the bar does not scroll for a reviewer, a
  contributor and an administrator. An interim menu of this item's own was dropped in favour of F5b's.
- **Folder.** F5b's password screen took `screens/setup/`, so program setup lives in `screens/program-setup/`
  (`SetupWorkspace`, `PROGRAM_SETUP_ICONS`, chunk `program-setup-routes`).

### F14 — Check a report file (D-453 to D-462)

**D-453 — The hash is set once, with the file, and old reports keep none.** V018 adds `report.content_sha256` (64 lower
hex characters, checked) and `report_content_sha_idx (tenant_id, content_sha256) WHERE content_sha256 IS NOT NULL`.
`GenerateReportHandler` hashes the exact bytes it hands to the `FileStore` (`DocumentService.sha256`, the one SHA-256 of
a byte array in the code base) and `ReportRepository.markReady(id, storageKey, contentSha256)` writes the status, the
key and the hash in one UPDATE. Reports generated before V018 keep a null hash and are never found by a check. Nothing
is deployed and the seed generates its reports anew, so there is no backfill; a backfill would also have to read every
stored file, which a migration must not do.

**D-454 — The match carries two fields beyond the spec's list.** `ReportCheck.Match` has every field F14 names plus
`programId` and `outcome`. The UI the spec describes needs both: "a link opens that determination's report" goes to
`/programs/{programId}/report?jurisdiction=…` (there is no report route without the program), and the Seal in preview
mode takes the outcome as a required input. Both come from the same row of the same statement, so the cost is two
columns. Called out in the pull request as a disagreement with the spec's field list.

**D-455 — One pass over the bytes, outside the transaction, under the one upload limit.** `PdfFingerprint.of` reads the
multipart stream once: the first five bytes must be `%PDF-` (otherwise 400 "Not a PDF file", also for an empty body),
then 64 KB buffers go through one `MessageDigest`. The file is never held as a whole and never stored. Hashing happens
before the transaction starts, so no pooled connection waits on a 25 MB upload; the lookup and its audit entry then
share one `TransactionTemplate` transaction. There is no size check in the endpoint: the existing multipart limit
(`LEXVAY_UPLOAD_MAX_MB`, 25 MB by default) refuses a larger body with the existing 413 problem detail before the
controller runs. `UploadSizeLimitTest`, which already runs with a 1 MB limit over a real connection, proves it for the
check as well. The endpoint is not added to the per-user upload rate budget (`RateLimitFilter.UPLOAD`); the spec does
not ask for it and the general API budget applies.

**D-456 — One statement, newest first, at most two rows.** `ReportRepository.CHECK` joins report, determination,
program, jurisdiction, rule version and the signer, filtered only by `r.content_sha256 = :h`; row-level security adds
`tenant_id = current_tenant()`, which is the leading condition of the new index, so another tenant's report is never a
candidate (rule 7). `ORDER BY r.created_at DESC, r.id DESC LIMIT 2`: the first row is the answer, and a second row only
tells that two reports share a hash, which is logged as a warning naming both. `generatedAt` is `report.created_at`; the
row keeps no separate generation time and the job runs right after the request. `reopenedAt` is the time of the
determination's `DETERMINATION_REOPENED` audit entry (the determination row keeps no reopening time), computed only when
the status is `REOPENED`. The register already had that subquery; it moved to `DeterminationRepository.reopenedAt(expr)`
and both statements use it.

**D-457 — Audit only what was checked.** A check that got an answer is audited as `REPORT_CHECKED`, entity type
`report`, entity id the matched report or null, detail `{match, sha256}`, actor the signed-in person, in the transaction
of the lookup. A refused file (400 not a PDF, 413 too large) is not a check and writes no audit entry; `ReportCheckTest`
and `UploadSizeLimitTest` assert that. The endpoint is in `Rule9_AuditCoverageTest` and `InputValidationTableTest`
(missing part, not a PDF, empty file). The audit sentence is "{actor} checked a report file: it matches" or "…: no
match"; the filter label is "Report file checked". `audit-sentence.ts` and its spec gain one entry each (the only edit
to that shared file).

**D-458 — The perf proof.** `PerfSeed.addHistoryVolume` gives every synthetic determination three reports: one hashed
(generated after V018), one ready without a hash (before V018) and one still queued, in each of the 100 tenant copies.
`IndexUsageTest` adds the candidate `report_content_sha_idx` for the real `CHECK` text and runs it with `EXPLAIN
(ANALYZE, BUFFERS)` for a hash that matches and one that matches nothing, writing `target/perf/report-check-plan.txt`;
it fails if either plan does not use the index, scans the report table or touches more than 60 shared buffers. Result:
Index Scan using `report_content_sha_idx` with both columns as index conditions, 17 buffers and 0.09 ms for a match, 3
buffers and 0.05 ms for no match.

**D-459 — The result views are a pure function.** `screens/report/report-check.ts` turns the answer into one of three
views (`signed`, `reopened`, `none`) with the exact sentences of the spec, and words errors: a file the browser does not
call a PDF (neither `application/pdf` nor a `.pdf` name) is refused before it is sent with the server's own sentence,
"Not a PDF file"; a 413 becomes "The file is larger than the upload limit" (no number, so the client holds no second
limit); anything else shows the server's problem detail. The SHA-256 is shown in Plex Mono, shortened to 16 characters,
with a copy button, in all three results: it is a fact about the file in each. Unit tests cover the three views, the
type error, the size error and the words "valid" and "authentic" (`ForbiddenWordsTest` already keeps "compliant" and
"meets requirements" out of every source, the spec included).

**D-460 — The dialog is deferred and brings its own icons and tokens.** The report's title row has a secondary text
button "Check a file". `@defer (when checkOpen(); prefetch on idle)` puts `ReportCheckDialog` with PrimeNG Dialog,
FileUpload and Message in a chunk of its own (53 kB raw, 13 kB transferred); the dialog registers its one icon (copy)
through `viewProviders` (`REPORT_CHECK_ICONS`, a unit in `icons.spec.ts`) and its token sets (`dialog`, `fileupload`,
`message`) from its constructor. FileUpload runs in advanced, custom, auto mode with an empty header template and a
content template that is the drop zone; `theme/fileupload-tokens.ts` removes its frame and padding. Initial bundle: 601
667 bytes on `main` (b28318a), 602 031 with F14 (+364 bytes, the `ApiService.checkReport` method).

**D-461 — Design v3 for the dialog, per the orchestrator on 2026-11-27.** The owner replaced design v2 with v3 "Ledger"
while F14 was in progress, and the orchestrator directed F14's dialog to follow it where it costs nothing; UI3-2
finishes the styling. So the spec's v2 parts change: no ProgressRing (the primary button reads "Checking…" and is
disabled while the server hashes, and the drop zone is disabled); no icons in the results, no tints and no pills (the
drop zone is a hairline frame, the fingerprint a mono line under a hairline); each result is a plain heading over meta
lines; the reopened sentence is `ink-2` (today's `text-2` token) words, never amber, with no Seal and no signature line;
the no-match explanation is one meta line instead of an InfoTip, since v3 allows one help affordance per screen and the
report already has it; the "Check a file" button has no icon. The Seal of a signed match stays in `preview` mode as it
is (hidden from assistive technology; UI3-0 redraws it); the facts it pictures are printed as text beside it. The spec's
"warning StatusIcon" is therefore gone: StatusIcon had no warning state anyway, and v3 replaces it with status marks.

**D-462 — The report screen follows its URL, and the end-to-end checks run after the suite.** The report screen reads
the `jurisdiction` query parameter as a router-bound input and reloads when the program's assessment or that parameter
changes (keyed by assessment and jurisdiction, so each pair is read once), so "Open report" works for any program and
state, also from the same screen. `report-check.spec.ts` runs in the `records` project after the suite, because it
checks the PDF of the California determination that `review-and-sign.spec.ts` signs: download, match with its facts, a
copy with one byte changed (no match), a text file and a renamed text file (both "Not a PDF file"), the link, and the
two audit sentences. axe runs on the drop zone and on each result in both themes at 1440 and 400 px. The reopened state
cannot be produced on the shared seeded stack without reopening California for the specs that run after, so that test
answers the check with the server's real response for the file with only the status and time changed (`page.route`); the
backend test proves the real reopened answer.

## Real-data validation track (D-550)
**D-550 — Master prompt v3 adopted as the next stage; free-first.** On 2026-11-27 the founder issued "Complete Master
Engineering Prompt v3 (free-first)". The orchestrator ran its G0 intake (`docs/INTAKE-2026-11-27.md`, main
`7b04d82`) and adopted it as follows.

The demo stays, clearly separated from real data (MODE-1). The next milestone is one authorized real-data vertical
slice:
- one Utah clinical mental health counseling pathway, from official sources;
- one public academic program, the University of Utah CMHC;
- acquisition, replay, temporal correctness, pathway reasoning and an evidence record with explicit missing data.

The intake confirmed two P0 defects in the rule-source monitor, both fixed in RG-1:
- the fetch date was used as the effective date;
- changes observed while a draft waited were discarded.

Budget: no paid data, connector, scraping, OCR or metered inference without the founder's explicit approval,
enforced in code by MODE-1's spend gate. Local inference is evaluated through an OpenAI-compatible adapter (EV-1)
before any model is chosen.

Architecture: no rewrite, no new services. The existing Angular, Spring Boot, PostgreSQL application, job table,
fetcher and evidence model are extended.

Blocked on the founder: a qualified licensure domain expert (DOM-1) and an authorized SharePoint test tenant (SP-1).
Until then, rule packs built in this repository are labelled demo-only or expert-review pending, never certified.
University handbook text is not committed to the repository until its rights are reviewed.

The design work (UI3) is bounded to restyling built screens.


### F15 — Evidence package (D-540 to D-549)

**D-540 — One immutable ZIP per request, built by a job.** `POST /api/v1/determinations/{id}/package` records an
`evidence_package` row and queues `BUILD_PACKAGE`; the ZIP is stored once and never changed. Every request makes a
new package (no deduplication): two requests an hour apart give two packages whose manifests differ in `generatedAt`,
and each one is a statement of what the system held at that time. Requesting, finishing (`PACKAGE_READY`, actor
null, the job) and downloading (`PACKAGE_DOWNLOADED`) are audited, and the download is on the list of audited reads in
`Rule9_AuditCoverageTest`. Any role that can read the determination may request and download; no role is added
(rule 1 is about signing, and a package signs nothing).

**D-541 — V020, with the status finished once by a trigger and a CHECK for the file.** V019 belongs to SEC-3 and was
not on `main` when this was written; the migration is numbered V020 as the spec says (Flyway accepts the gap, and
`MigrationsApplyCleanlyTest` counts files, not numbers). `evidence_package_finish_once` allows exactly QUEUED to READY
or FAILED and changes nothing but `status`, `storage_key`, `content_sha256` and `size_bytes`; `forbid_change` blocks
deletes. The CHECK `evidence_package_file_iff_ready` makes the file columns all set exactly when the status is READY,
so a package that is ready always has a file, hash and size, and one that failed has none. The spec said "like
`report`", but `report` has no trigger in V001 to V017; this is stricter on purpose and `report` is left alone. RLS is
enabled and forced with the same policy as every tenant table. Index `(determination_id, created_at DESC)`, plus the
usual `tenant_id` index. `created_by` references `app_user` (the job's rows are created by the person who asked).

**D-542 — `FileStore` streams.** `put(String, Path)` and `open(String)` join the byte-array methods. The local store
copies into a temporary file beside the target and moves it into place atomically (and now deletes that temporary
file when the copy fails, for the byte-array `put` too). The S3 store sends a file of at most 16 MiB with one
`PutObject` and a larger one as a multipart upload in 16 MiB parts, holding one part in memory; any failure aborts the
upload so no object appears under the key. `open` returns the `GetObject` stream. The task role gets
`s3:AbortMultipartUpload` (`infra/iam.tf`; Terraform is not available on this machine, so the one-line change was not
validated here). The S3 tests use the Mockito mock of `S3Client` like the existing ones (D-042).

**D-543 — The same inputs give the same bytes.** Entries go in a fixed order: `README.txt`, `report.pdf`,
`determination.json`, `requirements.csv`, `quotes.csv`, `audit.csv`, `documents/*` in filename order, `manifest.json`.
Every entry carries one timestamp, `generatedAt` truncated to whole seconds and read as UTC through
`ZipEntry.setTimeLocal`, which writes the DOS time without the extended-timestamp field; no comment, no extra field.
`generatedAt` comes from a `Clock` bean (`ClockConfig`, new) so a test can fix it. The compressed bytes also depend on
the JVM's zlib, so the guarantee is the one the spec asks for: the same code on the same runtime and the same inputs.
`content_sha256` is computed while the file is written, over the stored ZIP, and the stored size is its length.

**D-544 — Entry formats.** `requirements.csv` uses the stored codes, not labels (kind = the requirement type, check =
`RULES_ENGINE` or `MATCH`, finding, decision = `ACCEPTED`, `EVIDENCE_REQUESTED` or `GAP_CONFIRMED`), so a reader can
filter on exactly what the database holds; a requirement nobody has decided has empty decision fields, which is also
what the report says ("none recorded"). `quotes.csv` has the seven columns of the spec; the page hash is the SHA-256
(`DocumentService.sha256`) of the page text as stored, so anyone can recompute it from the stored text without
trusting the package. `determination.json` adds the outcome label next to the outcome code, and the state's name next
to its code. The cited documents are listed once each; two different documents with the same filename (the same
syllabus name in two courses) keep the first and prefix the other with the first eight characters of its id, and
control characters and path separators in a filename become `_`, so no path leaves `documents/`. `README.txt` is a
resource (`evidence-package/README.txt`), four plain sentences, and `ForbiddenWordsTest` now scans that folder.
`manifest.json` lists every other entry with its uncompressed size and SHA-256, plus `packageId`, `determinationId`,
`generatedAt`, `lexvayVersion` (from `build-info`, which the Maven plugin now generates) and the hash algorithm.

**D-545 — What `audit.csv` holds.** The entries whose record is the determination, any mapping of its assessment and
jurisdiction (superseded mappings included, because their decisions are part of how the final list was reached), any
report of the determination, or this package, newest first, in the columns and quoting of the F5a export. The two
exports share `AuditCsv` (header and row), so they cannot drift. The file holds `PACKAGE_REQUESTED` but never
`PACKAGE_READY` or `PACKAGE_DOWNLOADED`, which are written after it. SEC-3 (`seq` and `row_hash`) has not merged, so
those columns are absent; when it does, add them to `AuditCsv` and the one query. Unlike the audit export, a package
does not write an `AUDIT_EXPORTED` entry: it is audited as a package. The rows stream from a cursor of 500, so the
size of the log does not matter to the heap.

**D-546 — Failure.** The build writes into a temporary file, stores it with one `put`, marks the package READY and
deletes the temporary file in a `finally`; a failure at any step leaves no stored object. A package over
`lexvay.package.max-bytes` (`LEXVAY_PACKAGE_MAX_BYTES`, default 512 MB) fails while it is being written, because
`StoredFileOutput` refuses the write that would pass the limit, so an oversized ZIP is never finished. A cited
document whose stored bytes no longer hash to the SHA-256 recorded at upload fails the build the same way: a package
never vouches for bytes it cannot vouch for. Both are `PermanentJobFailure`: retrying cannot help. The job's
transaction rolls back on failure, so the package row cannot be marked FAILED from inside it; `JobHandler` gets a
default `onFailed(Job)` that `JobRunner` calls once, in a transaction of its own under the job's tenant, after the
job failed for good (a permanent failure, or the last attempt), never for an attempt that will be retried. A failure of
the hook is logged and does not change the job. `GET /packages/{id}` shows FAILED with the job's message in `error`;
the audit trail has the existing `JOB_FAILED` entry, so no fourth package action was added.

**D-547 — Preconditions.** Only a SIGNED determination is packaged (a REOPENED one is 409: its signed state no longer
holds), and a READY report must exist ("Generate the report first"). The report in the package is the newest READY
one, as `ReportRepository.findLatestReady` defines it; the build reads it again and fails for good if it is gone.
The request does not wait for a report that is still queued: it is refused with the same 409.

**D-548 — API details.** The request answers 202 with `{packageId, jobId}`. `GET /packages/{id}` returns the status,
size, SHA-256, creation time and, for FAILED, the error. `GET /packages/{id}/file` is 409 until the package is READY,
then streams `application/zip` with `Content-Length`, the SHA-256 as the ETag and the filename
`lexvay-<program-slug>-<state>-<signed-date>.zip`: the program name in lower case with each run of other characters
as one hyphen ("program" if nothing is left), the state code in lower case, the signing date in UTC. The download
is audited when it starts, in the request's transaction, before the file is opened, so a download that cannot start
leaves no entry; one the client abandons does, as with the exports (D-377).

**D-549 — Test support moved, not copied.** The CSV reader of `AuditExportApiTest` is now `TestCsv` and the
signing of the first current jurisdiction in `RegisterSeedTest` is `SeedSigning`, both shared with the package tests.
`PackageFixtures` holds the requests and the unzip. The seeded test packages a determination signed on the Northfield
seed with its real PDFs and checks every manifest hash.

### MODE-1 — Data mode and spend gate (D-560 to D-565)

Work item MODE-1, "Data mode and spend gate" (docs/ROADMAP.md). Folded into DECISIONS.md by the orchestrator when it
merges.

**D-560 — The data mode is a column of the tenant, set by the code that creates the tenant, changed only in the
database.** V022 adds `tenant.data_mode text NOT NULL CHECK (data_mode IN ('DEMO', 'LIVE'))` with default `LIVE`, so a
tenant created without saying otherwise is the cautious one. `TenantBootstrapService.createTenant` takes the mode as a
required argument: the seed loader and the evaluation corpus pass `DEMO`, the test helper `TestTenants` creates `DEMO`
tenants (the tests answer with the scripted model, which only `DEMO` may use) and can create `LIVE` ones. The column is
added with the `DEMO` default and then the default is switched to `LIVE`, which marks the tenants that exist today
(the seed tenants and tests' tenants; nothing is deployed, D-453) without an `UPDATE` that row-level security would
not let a migration run across tenants. `lexvay.demo-tenant-names` (D-064) is removed and `/auth/session.demo` is
`data_mode = 'DEMO'`. No API changes the mode. A change is the most consequential administrative act, so a trigger
(`tenant_data_mode_audited`) writes `TENANT_DATA_MODE_CHANGED` with the old and new mode and the database role, which
keeps product rule 9 true for an act made at a SQL prompt; the procedure is in docs/OPERATIONS.md. The audit sentence
spec now also reads migrations for audit actions, so the trigger's action cannot go without a sentence.

**D-561 — A `LIVE` tenant never meets the scripted model, the seed or a fictional rule.**
- `LlmClient` gains `scripted()` and `metered()` (both default false; `FakeLlmClient` is scripted, `AnthropicLlmClient`
  is metered), so the gateway decides from what the client is, not from a provider name. `LlmGateway.complete` refuses
  a scripted client for a `LIVE` tenant with "No model is configured for real data". The tenant's mode is read only
  when the client is scripted, so a call to a real model costs no extra query.
- `SeedLoader` refuses a `LIVE` tenant that holds a seed admin email, even though it would otherwise skip it quietly:
  an unexpected `LIVE` tenant under a seed address is a mistake someone should see.
- `RealDataApprovalGuard` is called by `approveRequirements`. A `DEMO` tenant approves as before. In a `LIVE` tenant the
  version needs a recorded dates basis that does not contain "Fictional demo data" (case-insensitive). RG-1 (V021,
  `dates_basis`) has not merged, so nothing records a basis and the guard's one private method, `datesBasis`, finds
  none: every approval in a `LIVE` tenant is refused with a 409. That is the failing-closed hook the spec asks for;
  RG-1 replaces the body of that one method with a read of the newest `rule_version_status` row and the guard's tests
  then also cover a real basis.
- Reports (the header block of the PDF), the register CSV and the audit CSV of a `DEMO` tenant carry "Fictional demo
  data" (D-564).

**D-562 — The spend gate decides before anything is sent, from configuration and from recorded cost.** `SpendGate`
applies to a client whose `metered()` is true. It refuses unless `LEXVAY_LLM_PAID_APPROVED` is `true` (default
`false`), the model has a price in `LEXVAY_LLM_RATES`, and recorded cost this UTC month plus this call's largest cost is
at most `LEXVAY_LLM_MONTHLY_BUDGET_USD` (default `0`; equal to the budget is allowed, and a budget of zero refuses every
call because a call always costs something).
- The price table is `model=inputPerMtok:outputPerMtok` entries in one variable (`LlmRates`). Spring's map binding
  from environment variables mangles model ids, and the variable is easy to read in `.env`. There are no built-in
  prices: a price in the code goes stale without anyone noticing, so the default table is empty and every cost is
  unknown. Prices must be positive, an entry that cannot be read stops the start, and a model that appears in this
  month's recorded calls without a price makes the month's cost unknown, so the call is refused (a model whose
  recorded calls carry no tokens, such as fixture rows, costs nothing whatever its price).
- A call's largest cost is `maxOutputTokens` at the output price plus one input token per UTF-8 byte of the system
  prompt, the user prompt and the output schema at the input price, rounded up to a millionth of a dollar. A token is
  never shorter than a byte, so the input figure is an upper bound; the model never counts anything (rule 3).
- "Recorded" is `LlmUsageRepository.thisMonthExcluding`: the sum of stored input and output tokens per model since the
  start of the UTC month, leaving out the scripted model, whose calls are free. The budget belongs to the deployment's
  one provider account, so it adds up every tenant. A second SELECT policy on `llm_call` (V022) is honoured only under
  the transaction-local flag `lexvay.spend_gate`, the same pattern as the job worker's flag (D-013). The query runs in
  its own transaction so the flag does not outlive it inside a job's transaction, and it reads totals only, never the
  stored input or output. `llm_call_created_idx` serves it.
- What the gate cannot see: a call that failed at the provider is not stored, and several instances can pass the budget
  together by the calls in flight. The budget is a guard rail; OPERATIONS says to set a spending limit with the
  provider as well.
- `LlmRates` and `TokenPrice` are the one price table and the one cost formula. `AssessmentUsageService` (the usage
  endpoint) and `EvalService` (the evaluation cost line) price the model of the assessment or the configured model from
  it, and the settings `LEXVAY_LLM_PRICE_INPUT_PER_MTOK` and `LEXVAY_LLM_PRICE_OUTPUT_PER_MTOK` (and the Terraform
  variables behind them) are gone: set `LEXVAY_LLM_RATES` instead, for example `fake-llm=2.00:10.00` to see an
  illustrative cost for the scripted model. Two things change in those displays and nothing else: a cost is rounded up to a
  millionth of a dollar (the evaluation line showed ten-thousandths, rounded to nearest), and `prices` in the usage view is
  `null` instead of two zeros when the model has no price.

**D-563 — A refused call is an audited, permanent failure that carries no content.** `LlmCallBlockedException` extends
`LlmRefusedException` with a closed `Reason` (`NO_MODEL_FOR_REAL_DATA`, `PAID_CALLS_NOT_APPROVED`, `COST_UNKNOWN`,
`OVER_BUDGET`). The gateway counts it as `refused` in the metrics, records `LLM_CALL_REFUSED` (reason, message, purpose
and model; entity type `llm_call`) in its own transaction through `LlmRefusalAudit`, so the entry survives the job's
rollback, and logs one warning line with the reason, purpose and model. `JobRunner` treats it like
`PermanentJobFailure`: the job fails on the first attempt (retrying cannot change the answer until someone changes
the configuration), with the message as its error and the usual `JOB_FAILED` entry. A refusal by the model itself
(`stop_reason: refusal`) is unchanged. `eval-live` goes through the same gate.

**D-564 — A `DEMO` tenant's outputs say so in their header.** The PDF report adds a first header line "Data: Fictional
demo data". The register CSV and the audit CSV start with one record holding that text, above the column labels. A
`LIVE` tenant's files are as before. The evidence package (F15) puts "Fictional demo data." as the first line of
`README.txt` and `"dataMode": "DEMO"` in `manifest.json`; for a `LIVE` tenant the manifest says `"LIVE"` and the README is
the text as it is. The mode is read once with the snapshot, so the same inputs and the same `generatedAt` still give the
same bytes. The package's `audit.csv` is the shared `AuditCsv` as before; the README and manifest carry the marker.

**D-565 — One startup line states the money posture.** `SpendGate` logs once when the application starts, for every
provider including `fake`: the provider, whether paid calls are approved, the monthly budget and the models that have a
price. It never logs a key or a price list value beyond the model names.

### OPS-2 — Cap concurrent local test stacks (D-641 to D-643)

**D-641 — A machine-wide semaphore of directory tokens in `scripts/lib/stack.sh`.** `stack_init` takes one of
`LEXVAY_STACK_CONCURRENCY` tokens (default 2) before it creates anything, so `make e2e`, `make eval` and
`make smoke` share one cap. A token is the directory `<lock dir>/<n>` (n from 1 to the cap) holding `pid` and `slot`.
The lock dir is `LEXVAY_STACK_LOCK_DIR`, default `/tmp/lexvay-stack-tokens-$(id -u)`, deliberately independent of
`TMPDIR`: a sandboxed agent that sets its own `TMPDIR` still shares one cap with every other process of the same user. Only `mkdir` is used for locking, because macOS has no `flock`. Waiting
polls once a second,
prints "<name>: waiting for a free test stack (n of N in use: slots ...)" at once and every 30 s, and gives up with
exit 1 after `LEXVAY_STACK_WAIT_MINUTES` (default 60; 0 means a single attempt). `LEXVAY_STACK_CONCURRENCY=0` skips the
lock. CI runs one stack on a fresh runner, so the default of 2 never makes it wait and CI needs no setting. Both
settings must be whole numbers; anything else is refused before anything starts.

**D-642 — Scans and reclaims run under a short mutex; owners are judged by `ps -p`.** Reclaiming a stale token with a
bare `rm` then `mkdir` lets two waiters race: one can delete the token the other has just created. The scan, reclaim
and creation therefore happen while holding `<lock dir>/.mutex` (another `mkdir`, held for milliseconds).
A mutex older than a minute belongs to a process that died inside that window and is removed. A token is stale when
its pid file is missing or empty (the creator died between `mkdir` and writing it) or when `ps -p <pid>` finds no
process. `ps -p` is used instead of `kill -0` because `kill -0` also fails for a live process owned by another user,
which would wrongly reclaim that user's token. A reused pid keeps a dead owner's token alive; the wait limit bounds the
damage, and nothing is reclaimed that a live process might own.

**D-643 — Release on every exit path, and never touch a stack that was not started.** `stack_init` installs
`trap stack_cleanup EXIT` and turns INT and TERM into `exit 130` and `exit 143`, so the EXIT trap runs for them and
cleanup has one code path. `stack_cleanup` gives the token back last, after the compose project is down, so the next
run cannot start a database on the slot's port before this one has gone. A run signalled while still waiting has
started nothing, so it does not run `compose down -v` (another run on the same slot may own that project) and has no
token to release. Known limit: bash runs a trap only after the foreground command finishes, so a TERM sent to the
script alone (not to its process group, as Ctrl-C does) takes effect when Playwright or Maven returns; a holder killed
with KILL is reclaimed by the dead-pid rule instead.

### UI3-0 — Design v3 foundation, shell, sign-in and overview (D-480 to D-497)

**D-480 — The v3 token table, value for value, with one derived shade and one elevation.** `theme/tokens.ts` holds the
DESIGN.md table (paper, surface, sunken, line, line-strong, ink, ink-2, ink-3, accent, accent-wash, found, missing) and
nothing else from v2: ground, surface-2, raised, text, text-2/3, accent-tint, ambiguous and its tint, the found and missing
tints, neutral, seal, on-accent, accent-hover/active/fill and e1/e2/e3 are gone. One shade is derived because a primary
button needs a pointer state: `inkHover`, ink eased 14 % toward paper. The preset publishes every token as `--p-lx-*`
plus `--p-lx-overlay`, the one elevation (`0 12px 32px -8px` at .14 light and .5 dark, and the 1 px line ring). PrimeNG's
`primary` is ink, so every primary control (buttons, checked toggles, checkboxes, the stepper) is ink on paper; the
accent stays for links, the focus ring and the selection rule. `tokens.spec.ts` checks AA (4.5:1) for ink, ink-2,
accent, found and missing on paper, surface, sunken and accent-wash, paper on ink and inkHover, and 3:1 for the marks and
the focus ring. A field's border identifies the control, so WCAG 1.4.11 asks 3:1 of it, which line-strong (1.5:1)
cannot give. After review the table gained `line-control` (#8C8F95 light, 3.2:1 on surface and 3.1:1 on paper; #6E7178
dark, 3.7:1 on surface and 3.9:1 on paper, so the suggested value needed no adjustment) for the borders of text inputs,
select, multiselect, autocomplete (the form-field token, so every field takes it) and the search field; a field's hover
border is ink-2. Secondary buttons, Labels and rules keep line-strong. `tokens.spec.ts` proves the 3:1 against paper and
surface in both themes. Checkboxes, radios and switches already used ink-2 or ink-3.

**D-481 — The source scan reads the whole application, `theme/` aside.** `src/app/source-scan.spec.ts` fails on: a hex
colour of 3, 4, 6 or 8 digits not followed by a word character (so `#main` and `#field` pass), an `rgb(`, `hsl(`, `hwb(`,
`lab(`, `lch(`, `oklab(` or `oklch(` call, and a CSS named colour inside a declaration (stylesheets, inline `styles`,
`style=""` attributes; strings and comments stripped); the retired names (`ProgressRing`, `StateMap`, `lx-stat`,
`count-up`/`CountUp`, `p-tag`, `p-chip`, `border-radius: 999`, `9999px`, `rounded-full`, `backdrop-filter`, `duotone`);
any `--p-lx-*` the preset does not publish; and any `var(--lx-*)` no stylesheet declares. The last two caught the dead
v2 tokens the build could not see. A self-test proves each pattern catches what it should and spares `lx-status`.

**D-482 — Restyling PrimeNG: semantic tokens first, a component set only where they cannot reach.** The preset's
semantic tokens (typography 13/20, form field, list, overlay, navigation, content, highlight, mask) restyle every
component, registered early or late. Component sets exist only where Aura hard-codes something: button (in the preset:
32 px, 6 px radius, 13/500, 12 px sides; `sm` equals the default so every button in the app is 32 px; `lg` is the 36 px
single primary action; secondary is surface with a line-strong border, danger is secondary with missing text, text
variants are ghosts), popover and tooltip (in the preset), datatable (40 px rows, header in meta type over line-strong,
no stripes), dialog and drawer (Section-type title), message (plain text in its colour, no box, no warning colour),
segmented SelectButton, skeleton (sunken, no shimmer), tabs (an underlined text row, 2 px ink bar), toggle switch and
the multiselect checkbox (ink, 3:1 boundaries), chip (Label-like), fileupload, metergroup (the Meter's 4 px bar), stepper
(numbers without discs) and toast (in `shell-toast.ts`). `tag-tokens.ts` and `progressbar-tokens.ts` are deleted with
their last callers. Heights are enforced once in the `lexvay` cascade layer (`min-height: 32px` on buttons, inputs,
selects), since PrimeNG derives height from padding and line height and the two differ per control.

**D-483 — StatusMark: one status type, the table's seven shapes, words always.** `ui/status-mark.ts` defines
`Status = Finding | DecisionKind | 'NOT_REVIEWED' | 'OUTDATED' | 'BLOCKED'`, so a finding or a decision is passed as it
is, and `STATUS_MARK` maps each to a 10 px SVG shape, a colour family and a word (from `FINDING_LABEL` and
`DECISION_LABEL`). `label` replaces the word where a screen says more; `quiet` keeps the word for assistive technology
only, for a dense row. `ui/status.ts` keeps one rule for a requirement row (`requirementStatus`: the finding's mark until
someone decides, then the decision's, "outdated" when stale, the words naming both) and adds `decisionStatus` for a
decision on its own. `finding.ts` (FindingLabel, DecisionState) and `status-icon.ts` are deleted; their bold and fill
icons were the "icon on every label" v2 failure.

**D-484 — Meter: segments drawn to scale, plus a neutral run where only a count exists.** `ui/meter.ts` draws found,
ambiguous (ink-2 at 55 %), missing and the empty remainder in that order, from `meterRuns`, which caps the runs at the
total. The overview's summary (`JurisdictionSummary`) carries how many requirements are decided but not decided as
what, so its Progress column uses a fourth tone, `decided`, one ink run; inventing a breakdown would break "honest data".
Review, which has every decision, draws the segmented Meter (`ReviewWorkspace.meter`). Recommendation for the
orchestrator: add accepted, requested and gap counts to the summary so the overview can show the segmented Meter too
(a backend change outside this item). The Meter's words take the type of their place, so in a table row they share the
row's baseline.

**D-485 — Help, terms and Labels.** `ui/help.ts` is the one help affordance: a 32 px ghost button reading "?" (named
"Help") at the end of the title row, opening a popover with the screen's terms as a definition list (`HelpTerm[]`, a
constant beside each screen). Every per-heading InfoTip moved into its screen's Help, including those of child dialogs
and steps (Rules: new version, requirement list; Program setup: fixed fields, license type, required course, facts;
People: what each role may do). An explanation that differs per row cannot live in a static list, so it becomes a term
(`.lx-term`, dotted underline, focusable, the sentence in its tooltip and its `aria-description`): Review's check
method, a blocked evidence request's reason. The People dialog shows each role's line under the role's name inside the
role options. `ui/label.ts` replaces every Tag: Required/Elective, roles, You, rule and request statuses, document states.
Outcomes in the register are plain words in found or missing, not tags.

**D-486 — The Seal is line art at 40 px, with a 24 px compact form for 40 px rows.** A double ring in ink, the
initials in Inter 11/600, the date in meta type under it, a 200 ms fade, no outcome colour, no stamp, no rotation. A 40 px
seal with its date is 60 px tall and cannot sit in the overview's 40 px rows or Review's header, so `compact` draws it at
24 px without the date there; the words beside it ("Signed by Dana Whitfield, 27 Nov") carry the date.

**D-487 — Shell.** The top bar is a flex row on paper with a hairline: the monochrome mark (paper strokes on an ink
tile) and wordmark at the rail's 16 px inset, the program switcher as a ghost Select (label and chevron only, 280 px at
most, cut at a word), then the 280 px bordered search field. Its key hint is in ink-2, not ink-3: the table says ink-3 is
never text, and at 3.2:1 it would fail AA. "Demo data" is meta text in a focusable `role="note"` whose name and tooltip
carry the notice (keyboard users reach it; print keeps the full notice). The work queue is a 32 px ghost icon button
with a 6 px accent dot while anything waits; the count moved into its popover. The account button holds a 24 px avatar on
sunken. The rail is two groups, "Workspace" and "Program", each a list named for screen readers with a visible meta
heading hidden from them; rows are 32 px edge to edge, the active one ink on sunken with a 2 px ink rule at its left
edge; counts are tabular meta text. Collapsed and under 1200 px the headings give way to a hairline between the groups.
Under 640 px the tab bar has 20 px icons and marks the active tab with a 2 px ink rule along its top. The rail's gliding
pill, the scrolled shadow and the badge are gone, and with them `pillTop`, `placePill` and `onMainScroll`.
`TAB_BAR_WIDTH` names the 640 px breakpoint for the icon size and the overflow.

**D-488 — Sign-in.** `AuthFrame` is one 360 px column centred on paper (the mark and wordmark, then the projected
form); the story input, the map and the headline are deleted with `sign-in-story.ts` and its test. Demo accounts are a
quiet list of 32 px text buttons, role at the left in ink, name at the right in ink-2, named "Signer, Dana Whitfield".
"Fictional demo data." shows only when the build declares demo accounts, which is exactly when the demo tenant is
offered. The setup page shares the frame.

**D-489 — Program overview, the reference screen.** A two-column grid title row (the name in Title type with its
meta line "license · Catalog 2026-27 · 5 target states", the primary action and Help on the right), the three Figures
24 px below separated by 1 px rules, then States and Needs you as sections 40 px apart with a hairline. The primary
action is Run assessment before the first finished assessment, and "Sign <state>" for a signer once no state waits for
review and one is ready (`primaryAction`); otherwise none. States is a semantic `<table class="lx-table">` (no sorting
or paging, so no DataTable and its 60 kB) whose rows are 40 px: State, Rules (the signed version's label, else the
newest approved version for the program's license, else "None"), Progress (Meter and "6 of 8 decided"), Evidence found
(%), Determination (compact Seal and "Signed by …, 27 Nov", "Reopened, not signed" or "Not signed") and one text action
(Review, Sign, Report). Under 720 px each row becomes a block. Needs you shows five rows, title, place and action, no
icons. An end-to-end test measures the 40 px padding, the 24 px to the figures, the 40 px sections and the 40 px rows.

**D-490 — Motion.** `--lx-ease` and `--lx-t-fast` (120 ms) / `--lx-t` (180 ms) replace the v2 scale and the spring. A
route change fades in and rises 4 px over 180 ms; overlays (popover, select, menu, dialog, toast) fade and rise 4 px
over 150 ms; the Meter scales in over 150 ms; the Seal fades in over 200 ms. Deleted: `Enter` (every staggered entrance,
in every screen), count-ups, ring sweeps, the seal stamp and its ink ring, the sign-in story, the map wave, Coverage's
column stagger and mark pop, Review's landing ring and spring settle, the Sign button's pulse, and `.lx-spin` (a looping
animation). Review's sideways slide to the next requirement stays for UI3-1, whose spec replaces it. Reduced motion
zeroes both durations and every animation, as before.

**D-491 — Shared vocabulary in `styles.css`.** Type roles: `.lx-meta` (12/16 500, renamed from `.lx-label` so the
Label component and the class do not share a name), `.lx-ui`, `.lx-figure`, `.lx-doc` (16/26, absorbing `.lx-serif`) and
`.lx-mono`; h1 24/32 600, h2 15/24 600; body 14/20 400. Removed: `.lx-display`, `.lx-number`, `.lx-small`, `.lx-card`,
`.lx-card-link`, `.lx-hero`, `.lx-panel`, `.lx-chip` (audit and People filters are ordinary Selects), `.lx-ambiguous`,
`.lx-enter`. Added: `.lx-section` (40 px and a hairline), `.lx-title-actions`, `.lx-table` and `.lx-row-action`, and v3
forms of the globals UI2-4 introduced (`.lx-empty-state` without its icon, `.lx-numbered` without the disc,
`.lx-passage` on an accent rule instead of a tint, `.lx-placeholder` as a dashed frame, `.lx-state-code` as meta text).
`:focus:not(:focus-visible)` draws no outline, so the page title takes focus after navigation without a ring.

**D-492 — Moving the callers: the plain equivalent, never a redesign.** Every screen compiles and passes on v3 parts:
ProgressRing became a Meter (Review header, setup readiness), Stat a Figure (register count, rule version), StatusIcon a
StatusMark, InfoTip the screen's Help or a term, StatePills `StateTabs` (state names, "3 of 8" in meta, the progress as
the tab's description), StateMap nothing (overview, sign-in, register; setup keeps "2 of 51 chosen" as text), Tags
Labels or words, duotone and fill icons nothing (empty states are one sentence), and an icon inside a button that has
words is gone everywhere. Cards lost their shadows: where a section was a card it is now on paper (lists, tables,
audit, changes, coverage's matrix on surface between hairlines); UI2-4's panels keep a 1 px line frame until UI3-3. The
work queue's kind icons moved from `core/work-queue.ts` into the menu, the only place that draws them, so the overview no
longer reaches icon names it never shows. The DecisionBar is a sticky footer of Review's centre column: a hairline, Accept
(ink), Request, Gap (missing words), the note button at the end, the keys in the buttons' tooltips; no blur, no chips.

**D-493 — Screenshot tests.** `screens.spec.ts` replaces the UI2-0 test with two UI3-0 tests: sign-in, the overview and
its Help at 1440 and 400 px, the collapsed rail, the palette, the work queue, the account menu, the component harness and
the 400 px More menu; then one capture of every other screen as it now starts (`ui3-0-before-*`). The UI2-1 overview and
register test is removed: its subject (the map, its card, the rings) no longer exists. The other v2 screenshot tests keep
working with updated selectors but are not retaken.

**D-494 — Print.** Paper is ink on white through the tokens (the print media query forces the light scheme), so no
literal black or white remains; the register test now expects the heading in ink, rgb(20, 21, 23).

**D-495 — Bundle.** The initial bundle (main and styles) went from 611.74 kB raw / 157.27 kB transfer at the branch point
to 581.62 kB / 151.38 kB after merging UI2-4, F14 and F15: the map, the rings, the count-up, the badge and the InfoTip left the shell, and the shell
registers one icon fewer (the InfoTip's).

**D-496 — Loading skeletons are one named status each.** Every container of skeleton bars is `role="status"` with
`aria-busy="true"` and one accessible name ("Loading the report"), and every bar is `aria-hidden="true"`. A generic `div`
with `aria-label` is prohibited ARIA, which axe reported intermittently when it caught a screen mid-load
(`records.spec.ts` at 400 px, the draft report). The bars are sunken, 4 px radius, without a shimmer.

**D-497 — The More menu's keyboard step in `people.spec.ts` waits for the focused item.** Under load the popup list
takes focus after its overlay shows, and an ArrowDown that arrives first moved nothing, so the following Enter did
nothing. The test now presses Home until "People" carries `p-focus`, then Enter: the same keys a person uses, no longer
racing the overlay. It is F5b's test; the change is limited to that step.

**D-498 — Moving to another screen focuses its heading only if the person has not moved focus meanwhile.** The shell
waits up to 3 s for a screen's `h1` after a navigation. On a loaded machine the overview's data arrived after the More
menu had opened, and the heading took focus from the menu, so the keys pressed next went nowhere (seen once in
`people.spec.ts` after D-497). A person who opens a menu or starts typing while a screen loads would lose their place
the same way. `focusHeadingWhenRendered` now records the focused element at navigation and gives up once focus is on
anything other than that element or the page body; `shell-focus.spec.ts` covers both paths.

### FIX-8 — No console errors (D-471 to D-474)

**D-471 — A view transition is never skipped; its animation is switched off instead.** The roadmap's first design
attached handlers to `ready`, `finished` and `updateCallbackDone` and ignored `AbortError`. It cannot work: in
development builds Angular's router already attaches its own `.catch(console.error)` to all three promises
(`createViewTransition`, guarded by `ngDevMode`) before it calls `onViewTransitionCreated`, so a skipped transition is
logged whatever the application does with the promises. The error never reached `provideBrowserGlobalErrorListeners`,
and it is absent from production builds, which is why only a running development app showed it. `app.config.ts` therefore
no longer calls `skipTransition()`. For a change within one screen (a tab, a filter; `sameScreen`) it sets
`data-transition="none"` on `<html>`, and `styles.css` sets the `::view-transition-group/old/new(*)` animations to
`none` under that attribute: the transition runs, finishes and resolves. Every new transition sets or removes the
attribute itself, so a stale value never reaches the next one (no cleanup on `finished`, which would race with a
following transition). Reduced motion needs no script: the media query in `styles.css` already sets the same
animations to `none !important`, so the `MotionPreferences` check in `app.config.ts` was a duplicate and is gone.
Rejected: wrapping `document.startViewTransition` or `console.error`; a global allowlist for the message.

**D-472 — One user action is one router navigation.** A second transition that starts while the first still runs aborts
the first ("New ViewTransition started"), which Angular logs. The end-to-end run showed two chains, both removed
instead of tolerated:
- Signing in, and any visit to `/`, navigated to `/` and then, once the programs arrived, from the `Home` screen to the
  overview of the only program. `landingGuard` (`core/auth.guard.ts`, after `authGuard` on the `''` route) now fetches the
  programs and returns the overview's `UrlTree` for a single program; the redirect resolves inside the same navigation,
  before any transition is created. With several programs, none, or a failed request the guard lets `Home` render, which
  lists them or shows the failure from its own request. `Home` no longer navigates.
- Review and the evidence report wrote their default state into the address (`?jurisdiction=AZ`) right after opening.
  `needsJurisdictionParam` (`core/navigation.ts`) says whether choosing a state must change the URL: an absent
  `?jurisdiction=` already means the program's first state, as Coverage always read it, so the default is applied when
  reading and the URL changes only when the person picks another state.

- Opening Rules without a version in the address (UI2-4) chose the version to open in a component effect and navigated
  again once the program had loaded, which aborted the first transition. `openingVersionGuard`
  (`screens/rules/opening-version.guard.ts`) does the same inside the opening navigation: it awaits the program, then
  redirects the bare address to the version that opens (the query travels along). `ProgramContext.ruleVersionGroups`
  is the one place the versions are grouped, for the guard and the list.

The rule workspace's `?new=1` (the command palette's "new rule version") is also followed by a navigation that removes
the parameter; no test overlaps it, so it is left as it is and noted in the pull request.

**D-473 — `consoleGuard` fails any test that logs a console error.** An automatic fixture in `e2e/support.ts`, beside
`iconGuard`, collects every `console` message of type `error` and every `pageerror` and fails the test if any was
recorded. A test that deliberately causes an error declares it with `allowConsoleErrors(page, /pattern/)`; the pattern is
matched against the message text and the URL it came from, so it names one response by status and URL. There is no
global allowlist.

**D-474 — The eight declared errors.** Chromium logs every 4xx or 5xx response as "Failed to load resource". Eight tests
cause one on purpose:
- `review-and-sign.spec.ts` (sign-in page): the wrong password is answered with 401 by `/api/v1/auth/login`.
- `evidence-requests.spec.ts` (answer drawer): a tampered quote submitted through the screen is answered with 400 by
  `/api/v1/mappings/<id>/evidence-responses`.
- `rules.spec.ts` (hand-entered list): the blank title is answered with 400 by
  `/api/v1/rule-versions/<id>/requirements`.
- `people.spec.ts` (a used setup link): 400 from `/api/v1/auth/setup`.
- `people.spec.ts` (own row, last administrator): the stubbed 409 from `/api/v1/users/<id>/role`.
- `people.spec.ts` (busy sign-in): the stubbed 503 from `/api/v1/auth/login`.
- `setup.spec.ts` (the same syllabus uploaded twice): the 409 from `/api/v1/programs/<id>/documents?kind=SYLLABUS` that refuses the copy.
- `report-check.spec.ts` (F14): a text file named `renamed.pdf` is answered with 400 by `/api/v1/reports/check`.

Every other error the suite found was the view-transition `AbortError`; no screen logged anything else.

### SEC-3 — Tamper-evident audit trail (D-530 to D-537)

**D-530 — The chain is built after the fact, and the queue is written by the audit statement itself.**
`AuditRepository.insert` is one statement, `WITH entry AS (INSERT INTO audit_log ... RETURNING id, tenant_id,
created_at) INSERT INTO audit_unsealed SELECT ... FROM entry`, so an entry and its place in the sealer's queue commit
together or not at all and no entry can escape sealing. Chaining inside the business transaction was rejected by the
spec (a per-tenant lock held until commit by every audited write would serialise the tenant and invite deadlocks) and
stays rejected. `Rule9_SingleAuditWritePathTest` reads `src/main` and fails if any other file contains
`INSERT INTO audit_log`, or if that statement stops queueing. V023 backfills `audit_unsealed` from every existing
`audit_log` row, with row-level security switched off on the two tables for the migration and forced again at its end
(the pattern of V016), so the sealer's first runs seal the history. `AuditSealBackfillTest` runs the migration's own
statement against shadow tables, because no test can load rows and then migrate. `audit_seal` has no `created_by`:
the spec lists its columns and a seal is made by the system.

**D-531 — The canonical form is a contract, written in `docs/OPERATIONS.md`, and checked against hashes computed
outside the code.** `AuditCanonical` joins the ten fields of the spec with U+001F and hashes the UTF-8 bytes with
`DocumentService.sha256`, the one SHA-256 of a byte array in the code base (no second digest helper). The time is
formatted with a fixed six-digit fraction, because `Instant.toString` drops trailing zero groups; PostgreSQL stores
microseconds, so the form loses nothing. `detail` is read as `jsonb::text` and never re-serialised in Java.
`AuditCanonicalTest` pins two hashes computed with Python's `hashlib`; `AuditSealerTest` recomputes a whole chain with
the SQL recipe in the operations guide, and `AuditExportApiTest` recomputes it from the exported CSV alone with
`AuditCanonical`. A field is not length-prefixed, so a value containing U+001F could in theory be ambiguous; actions
and record types are constants in the code and `detail` is JSON, where U+001F is always escaped.

**D-532 — Seal order is the order sealed.** An entry that commits late, with an earlier `created_at` than entries
already sealed, takes the next `seq`; the chain proves the order entries were sealed in, not the order they were
created in. Within one batch the order is `created_at, audit_id`. `AuditSealerTest.anEntryCommittedLateTakesTheNextNumber...`
inserts such an entry. The queue's own `created_at` (copied by the statement that writes the entry) is what the sealer
sorts on, so the read is an index range scan on `audit_unsealed (tenant_id, created_at, audit_id)` and not a sort of
the tenant's whole backlog.

**D-533 — `AuditSealer` seals, `AuditSealPoller` schedules; the lock is per tenant and per transaction.** The spec puts
`@Scheduled` on `AuditSealer`. A scheduled bean cannot be switched off in tests while its logic stays callable, so the
timer is a separate component, as `JobPoller` is to `JobRunner`: `AuditSealPoller` is `@ConditionalOnProperty
lexvay.audit.sealing-enabled` (default true, off in `AbstractDatabaseTest` and in the seed and eval profiles), runs
every `lexvay.audit.seal-interval-ms` (default 2000, `LEXVAY_AUDIT_SEAL_INTERVAL_MS`), begins no batch once the context
is closing, and carries its own `@EnableScheduling` so it works with job polling off. `AuditSealer.sealWhile` finds the
tenants with queued rows under the worker flag (V023 adds one policy, `worker_queue_select`, a SELECT on
`audit_unsealed`; D-013), then for each tenant runs batches of 500 in separate transactions as that tenant. A batch takes
`pg_try_advisory_xact_lock(hashtextextended('audit-seal:' || tenant, 0))` and skips the tenant when it is held, reads
the head, reads the queue, hashes in Java, inserts the seals in one `unnest` statement, and deletes the queue rows that
now have a seal in one `DELETE ... USING audit_seal` (no id lists). A tenant whose batch throws is logged with the
exception class only and left for the next run, so it cannot hold up the others. `AuditSealPollerTest` is the
shutdown test. The restart test seals one batch, builds a second `AuditSealer` and finishes the queue.

**D-534 — Verification walks the stored seals, stops at the first that does not hold, and says what it cannot see.**
`AuditVerificationService.verify` is one read-write transaction (it also writes the `AUDIT_VERIFIED` entry): pages of
1000 seals joined to their entries by keyset on `(tenant_id, seq)`, each checked by `AuditChainCheck` (pure, tested
without a database) in this order: the next number (a gap names the number that is missing), the link to the previous
`row_hash` (genesis for the first), and the recomputed hash. `sealed` is the number of seal rows, `pending` the
queue's length, `lastSealedAt` and `headHash` those of the newest seal. A seal removed from the end of the chain leaves
no gap and cannot be seen from the database alone; `docs/OPERATIONS.md` says to keep `headHash` and `sealed` outside
the database administrator's reach, and every `AUDIT_VERIFIED` entry carries them. Deleting a sealed `audit_log` row is
refused by the foreign key even with its trigger disabled; deleting a seal row with its trigger disabled reports the gap
(tests as the container's superuser). Over 20 000 sealed entries the walk took 52 to 75 ms on a quiet machine and 139 to 221 ms on a busy one (D-537, budget 2 s).

**D-535 — The export gains four columns, not two.** The spec adds `seq` and `row_hash`. An exported trail cannot be
recomputed from those alone: the hash covers the entry id and the actor's id, and the export shows the actor's display
name. So the columns are `Seq`, `Row hash`, `Entry ID` and `Actor ID`, after `Details` (existing columns keep their
positions), written by `AuditCsv`, which the F15 evidence package's `audit.csv` shares, so both carry them. A pending entry has an empty `Seq` and `Row hash`. The tenant id is not a column (one export is one
tenant) and is passed to the check; the previous hash is the previous row's, so the recipe needs an export without
filters. The Python recipe in `docs/OPERATIONS.md` was run against a real export, intact and with one word changed.
`AuditExportApiTest` fails if the file stops being enough to recompute every hash. The end-to-end header assertion in
`records.spec.ts` follows. Called out in the pull request as a spec deviation.

**D-536 — `AUDIT_VERIFIED` records the break, and the sentence never claims more than the entry says.** The spec's
detail is `{intact, sealed, headHash}`; a broken result also records `firstBrokenSeq`, because the audit screen's
sentence names the entry ("broken at entry N") and the entry list is the only place that sentence can read it from.
`headHash` is left out when nothing is sealed. The sentence says "intact" only for `intact: true`; an entry with no
usable detail reads "verified the audit trail" with no verdict. The action is audited as a read that hands out a
verdict (listed in `Rule9_AuditCoverageTest.AUDITED_READ_ENDPOINTS`, with rows in `InputValidationTableTest`), and is
ADMIN only like the export.

**D-537 — What the tests and the budget say.** Tests: `AuditCanonicalTest`, `AuditChainCheckTest`,
`AuditSealPollerTest` (no database); `AuditSealerTest` (contiguous chain, SQL recipe, restart, 1 200 entries over three
batches, eight writer threads against two competing sealers, late entry, every tamper), `AuditVerificationApiTest`
(ADMIN only, 401 and 403, the audit entry, pending, broken, another tenant), `AuditSealBackfillTest`,
`Rule5_AuditSealImmutabilityTest`, `Rule7_AuditSealIsolationTest`, `Rule9_SingleAuditWritePathTest`. Tests that edit or
delete behind the application connect as the container's superuser, disable the trigger for one statement and enable it
again in a `finally`. `AuditSealPerfTest` (`make perf`, tag `perf`) queues 20 000 entries of the perf seed's audit
volume, seals them with the real sealer, times five verifications after a warm-up (budget 2 s) and fails unless the
queue read uses `audit_unsealed_tenant_idx` with entries fetched by `audit_log_pkey`, and the dequeue finds its rows by
`audit_unsealed_pkey`; plans are printed in the run.

### OPS-2b — Stack lock race on Ubuntu 26.04 runners (D-647)

**D-647 — The mutex in `scripts/lib/stack.sh` is a file created with the shell's own `O_EXCL` open, not a `mkdir`.**
D-641 and D-642 used `mkdir` as the lock because macOS has no `flock`. The CI runner image is now Ubuntu 26.04, whose
`mkdir` is uutils coreutils 0.10.0, and that `mkdir` is not atomic: it first checks that the path is free, then calls
`mkdirat`, and when `mkdirat` fails with `EEXIST` because another process created the directory in between, it looks
at the path again, finds a directory and exits 0. Two processes calling `mkdir` on the same new path at the same moment
can therefore both succeed. `strace` of six racing calls shows the losing one doing `statx = ENOENT`,
`mkdirat = EEXIST`, `statx = directory`, then exit 0; 16 racing calls produced two winners in about one round in ten
(GNU `mkdir` on the same image, `gnumkdir`, produced none in 40 rounds).

Effect in `scripts/lib/stack-lock-test.sh`: holders a and b start together and both took the mutex, both scanned an empty
token directory, both went for token 1 (the second `mkdir "$dir"` failed with "File exists", but `stack_try_token` runs
as the condition of an `until`, which switches `set -e` off, so nothing stopped it) and both wrote their pid and slot
into it, the later write winning. Token 2 was
never created, so the third holder found one live token and one free slot and started at once: "third holder does not
start while both tokens are held", "says it is waiting" and "exactly two tokens are held again" failed. The same flaw
would let two real runs share a token, so the cap of D-641 was not enforced either. It passed on macOS and on
Ubuntu 24.04 (GNU coreutils), and on 26.04 only when the race was not lost (1 failure in 6 runs).

The mutex is now the file `<lock dir>/.mutex`, taken with `( set -C; : > "$STACK_MUTEX" )`. With `noclobber` the shell
itself opens the file with `O_CREAT|O_EXCL`, one system call that exactly one contender wins, on every kernel and with
whatever coreutils are installed; bash 3.2 on macOS does the same. The stale-mutex rule is unchanged (older than a
minute, removed with `rm -f`). Tokens stay directories holding `pid` and `slot`; they are created only while the
mutex is held, so their `mkdir` never races. Nothing else in the design changes, and D-642's description "another
`mkdir`" is replaced by this decision.

Test: a new first section puts a `mkdir` that reports success for an existing directory (what uutils does when it loses
the race, made certain instead of rare) first on `PATH` for the second contender, holds the mutex in the test shell and
checks that the second contender stays blocked until the mutex is released. It fails on the old code on every run and
passes on the new. No retry and no longer sleep was added to the existing scenarios: the failure was in the lock, not in
the test's timing.

Rejected: `flock` (absent on macOS); another coreutils command as the lock (the cause is trusting an external command for
atomicity, and the shell's own redirection is the one primitive that needs none); making
the test retry or wait longer (the failure is a shared token, not slowness); pinning the CI runner to `ubuntu-24.04`
(hides a real defect that also affects 26.04 developer machines).
