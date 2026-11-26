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
checkout v7, setup-java v6, setup-node v7. Decided (owner, 2026-11-26).

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
