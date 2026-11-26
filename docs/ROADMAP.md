# Lexvay — Roadmap and work items

Owned by the orchestrator (CTO). Agents implement one work item each, exactly as specified, on the named
branch, and open a pull request. Status values: `queued`, `in progress`, `in review`, `merged`.

## Status board

| Item | Branch | E2E slot | Model | Status |
|---|---|---|---|---|
| WS-UI Design system and screen rebuild | `ui-foundation` | 1 | Opus 5.5 | in progress |
| WS-SEC OIDC and hardening (spec milestone 8) | `sec-oidc-hardening` | 2 | Sonnet 5.5 | in progress |
| WS-EVAL Evaluation harness and Terraform (spec milestone 9) | `eval-and-infra` | 3 | Sonnet 5.5 | in progress |
| WS-PERF Read-path latency and read APIs | `perf-read-path` | 4 | Sonnet 5.5 | in progress |
| F3a Rule version diff API | `feat-rule-diff-api` | 5 | Sonnet 5.5 | in progress |
| F4a Evidence requests API | `feat-evidence-requests-api` | 6 | Sonnet 5.5 | in progress |
| F6 Production packaging and observability | `ops-packaging` | 7 | Sonnet 5.5 | queued (ready) |
| F9 Curriculum search API | `feat-curriculum-search` | 8 | Sonnet 5.5 | queued (ready) |
| F1 Evidence viewer with in-context highlight | `feat-evidence-viewer` | 1 | Opus 5.5 | queued (after WS-UI, WS-PERF) |
| F2 Program setup workspace | `feat-setup-workspace` | 2 | Sonnet 5.5 | queued (after WS-UI) |
| F3 Rule workspace screens (uses F3a) | `feat-rule-workspace` | 3 | Opus 5.5 | queued (after WS-UI, F3a) |
| F4 Evidence request screens (uses F4a) | `feat-evidence-requests` | 4 | Opus 5.5 | queued (after WS-UI, F4a) |
| F5 Administration and audit tools | `feat-admin-audit` | 5 | Sonnet 5.5 | queued |
| F7 Matching quality | `match-quality` | 3 | Sonnet 5.5 | queued (after WS-EVAL) |
| F8 Determination register | `feat-determination-register` | — | Sonnet 5.5 | queued |
| F10 Coverage matrix | `feat-coverage-matrix` | — | Opus 5.5 | queued (after WS-UI) |

---

## WS-UI — Design system and screen rebuild (frontend only)
Follow `docs/DESIGN.md` exactly. Scope: `frontend/` only.
1. Add PrimeNG, `@primeuix/themes`, `@ng-icons/core`, `@ng-icons/phosphor-icons`, `@fontsource/ibm-plex-*`
   with exact versions verified on the registry. `providePrimeNG` with `LexvayPreset`; remove the
   hand-written component CSS that PrimeNG replaces and the Unicode glyphs in `core/labels.ts` (replace
   `FINDING_GLYPH` with an icon-name map).
2. Rebuild shell, sign-in and the five screens as specified. Keep `ApiService`, `SessionService`,
   `core/overview.ts` logic; extend rather than duplicate. Lazy-load each screen route (`loadComponent`).
3. Review workspace: Splitter panes, filter chips, keyboard shortcuts, live region, toast, sticky sign bar,
   signing dialog. View-model logic lives in a plain class or signal store with unit tests.
4. Audit trail resolves actor names: the backend has no user list endpoint yet, so add
   `GET /api/v1/users` (id, displayName, role, email) to `backend` (`tenant` package, tenant-scoped by RLS,
   any authenticated role) with a test. This is the only backend change allowed in this item.
5. Update `frontend/e2e` for new markup; keep all three flows and add: keyboard shortcut review of one
   requirement, theme switch persists across reload, 400 px viewport renders the review screen without
   horizontal page scroll. Axe: zero serious or critical on every screen in light and dark themes.
6. Production build must pass with budgets: initial bundle under 600 kB transferred; update `angular.json`
   budgets accordingly. No third-party network requests (assert in an e2e test by failing on any request
   whose host is not localhost).
Done when: `LEXVAY_E2E_SLOT=1 make test` is green, the production build passes, and screenshots of each
screen in both themes are attached to the pull request description as files under `docs/screens/`.

## WS-SEC — OIDC and hardening (backend only)
1. **OIDC** behind Spring profile `oidc` (form login stays the default). Dependencies through the Boot BOM.
   Configuration: `LEXVAY_OIDC_ISSUER_URI`, `LEXVAY_OIDC_CLIENT_ID`, `LEXVAY_OIDC_CLIENT_SECRET`, scopes
   `openid email profile`. Roles never come from the token: after the provider authenticates, look up
   `app_user` by the verified `email` claim (`AppUserRepository.findForLogin`); if `email_verified` is
   present and false, or no user exists, respond 403 problem detail "No Lexvay account for this identity".
   The principal becomes the existing `LexvayUser`, so tenant binding, method security and audit are
   unchanged. Audit `LOGIN_SUCCEEDED` with `method: oidc`. API requests without a session still get the 401
   problem detail (no redirect for `/api/**`); browsers start login at `/oauth2/authorization/lexvay`.
   Tests must not need a network: configure the client registration with explicit endpoints.
2. **Rate limiting** with Bucket4j (in-memory, per instance): login 10 attempts per minute per client IP;
   authenticated API 600 requests per minute per user; uploads 30 per minute per user. Exceeding returns
   429 problem detail with `Retry-After`. Limits configurable (`lexvay.ratelimit.*`). One filter, keyed
   buckets in a bounded Caffeine cache (10 000 keys, 10 minute idle expiry).
3. **Security headers** on every response: `Content-Security-Policy: default-src 'self'; style-src 'self'
   'unsafe-inline'; img-src 'self' data:; font-src 'self'; object-src 'none'; base-uri 'self';
   frame-ancestors 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
   `Permissions-Policy: camera=(), microphone=(), geolocation=()`, HSTS one year with subdomains when the
   request is secure. Session cookie: HttpOnly, SameSite=Lax, Secure when `LEXVAY_COOKIE_SECURE=true`.
4. **Upload limits**: tests for a file over the limit (413 problem detail), wrong type, zip bomb guard for
   DOCX (reject when uncompressed size exceeds 50 MB or ratio exceeds 100), PDF page cap 500.
5. **Input validation table test**: one parameterized test listing every endpoint with one malformed input
   each (bad UUID, missing field, oversized string, unknown enum) asserting a 400 problem detail and no
   500. Fix whatever it finds. Any 500 found is a bug to fix, not to whitelist.
6. Session fixation protection and login CSRF stay on; add a test that the session id changes at login.
Done when: `LEXVAY_E2E_SLOT=2 make test` green; `docs/RULES.md` and `PROGRESS.md` (milestone 8 section with
real evidence) updated.

## WS-EVAL — Evaluation harness and Terraform
1. `eval/golden.yml`: for each of the 42 seeded requirements the expected finding and, where relevant, the
   expected document file and a substring of the expected quote. Written by hand from the seed, independent
   of `seed/fake-llm.yml` (the two are compared; drift fails `make eval`).
2. Runner: Spring profile `eval` in the backend. It loads the seed into a fresh, uniquely named tenant on
   an isolated database (reuse `scripts/e2e.sh` conventions: slot 3, compose project `lexvay-eval-<slot>`),
   runs the pipeline, compares with the golden set and prints one table: false `EVIDENCE_FOUND` rate
   (proposed `EVIDENCE_FOUND` whose golden finding differs ÷ proposed `EVIDENCE_FOUND`), recall on golden
   `EVIDENCE_FOUND`, quote verification rate (stored spans whose quote is found by `QuoteVerifier` in the
   stored page ÷ stored spans), numeric accuracy (rules-engine mappings equal to golden), requirement
   extraction agreement (extracted ref and type equal to golden ÷ golden), input and output tokens, cost
   and wall time per assessment (tokens from `llm_call` × `LEXVAY_LLM_PRICE_*_PER_MTOK`).
   Exit non-zero when the false `EVIDENCE_FOUND` rate exceeds `eval/thresholds.yml` or quote verification is
   below 100 %.
3. `make eval` uses `FakeLlmClient`. `make eval-live` uses `AnthropicLlmClient` and runs only when
   `ANTHROPIC_API_KEY` is set (otherwise prints why and exits 0). In live mode extraction agreement is
   measured on the model's own extraction, then the golden requirement list replaces it through the
   existing replace-requirements path before approval so matching is scored by ref. Refactor `SeedLoader`
   so scripting the fake model is a strategy, not a precondition; no duplicated loading code.
4. Terraform in `infra/` (AWS): VPC with private subnets, ECS Fargate service for the backend behind an
   ALB, RDS PostgreSQL 18 (encrypted, private, not publicly accessible), S3 buckets for files (versioning,
   SSE, public access blocked) and for the Angular build behind CloudFront with origin access control,
   Secrets Manager entries for database and model credentials, least-privilege task role. Variables with
   descriptions and validation; no hard-coded account ids or secrets. `terraform fmt -check` and
   `terraform validate` pass (install Terraform 1.17.2 with Homebrew if missing). **Never run plan with real
   credentials, never apply.** Add `make infra-validate`.
5. CI: add `make eval` and `make infra-validate` to the workflow.
Done when: `make eval` prints the table and exits 0, `make infra-validate` passes, `LEXVAY_E2E_SLOT=3 make
test` green, `PROGRESS.md` milestone 9 section has the real output.

## WS-PERF — Read-path latency and read APIs (backend only)
Measure first, then change, then measure again; put both tables in `docs/PERF.md`.
1. **Benchmark** `backend/src/test/java/com/lexvay/perf/EndpointLatencyTest`, JUnit tag `perf`, excluded
   from the default run, executed by `make perf`. It loads the seed with `SeedLoader`, signs in over real
   HTTP (random port), warms up with 50 requests per endpoint, then times 300 sequential requests per
   endpoint and prints p50, p95, p99. Budget assertion: p95 ≤ 40 ms for each read endpoint below.
2. **One connection per request.** Today every `JdbcClient` call outside a transaction checks out a
   connection, and `TenantAwareDataSource` adds a `set_config` before and a `RESET` after it. Make every
   read service method `@Transactional(readOnly = true)` so a request uses one connection, and remove the
   close-time `RESET` in `TenantAwareDataSource` (checkout already always sets or resets the tenant, so a
   pooled connection can never carry a stale tenant into use). Prove it: a test that borrows a connection
   as tenant A, returns it, borrows with no tenant and sees zero rows on the same physical connection.
3. **Overview endpoint** `GET /api/v1/programs/{id}/overview` → `{ program, assessment (AssessmentView or
   null, the latest FINISHED one), determinations, openChangeEvents, undecidedTotal }` in one transaction.
   Also `GET /api/v1/programs/{id}/assessments` (newest first).
4. **Document page** `GET /api/v1/documents/{id}/pages/{pageNumber}` → `{ documentId, filename, kind,
   courseCode, courseRequired, pageNumber, pageCount, text }` with `Cache-Control: private,
   max-age=31536000, immutable` and a strong `ETag` (document sha256 + page number); honour
   `If-None-Match` with 304. Pages are immutable, so this is safe.
5. **Review history** `GET /api/v1/mappings/{id}/reviews` → events oldest first with kind, note, user id,
   user display name (null for system), timestamp.
6. **N+1 removal**: `DeterminationRepository` (aggregate course version ids in the main query),
   `ChangeEventRepository.enrich` (set-based for the whole list), anything else the benchmark exposes.
7. **Indexes** in a new Flyway migration (next free version): `mapping (assessment_id, jurisdiction_id)`,
   `mapping (requirement_id)`, `mapping (evidence_span_id)`, `determination (program_id, jurisdiction_id,
   status)`, `determination (rule_version_id)`, `document (program_id)`, `document (course_version_id)`,
   `requirement (rule_version_id)` if not already covered by the unique index, `evidence_span
   (document_id)`, `audit_log (entity_id)`, `assessment (program_id, status, finished_at DESC)`,
   `change_event (status, created_at DESC)`, `job ((payload->>'documentId'))`, `job
   ((payload->>'ruleVersionId'))`. Verify each with `EXPLAIN` on the seeded data and drop any the planner
   never uses; list the kept ones with their query in `docs/PERF.md`.
8. **Audit filters**: `GET /audit` gains optional `action`, `entityType`, `entityId`, `userId`, `from`,
   `to`; response shape unchanged.
9. **Compression** for JSON over 1 kB (`server.compression`), and `spring.jackson` settings unchanged.
Done when: `make perf` meets the budgets, `LEXVAY_E2E_SLOT=4 make test` green, `docs/PERF.md` shows before
and after, every new endpoint has tenant-isolation and role tests.

## F3a — Rule version diff API (backend only)
Purpose: when a state revises a rule, the reviewer must see exactly what changed, requirement by
requirement, before approving the new list and re-running. Slot 5. No migration.
1. Dependency: `io.github.java-diff-utils:java-diff-utils` (latest stable from Maven Central, pinned) for
   the Myers sequence diff. Do not hand-write a diff algorithm.
2. `RequirementComparison` in `com.lexvay.rule`: the single definition of "same requirement" used
   everywhere. `changes(a, b)` returns the set of changed aspects among `TITLE`, `TEXT`, `TYPE`, `NUMERIC`
   (text compared after whitespace normalization with `QuoteVerifier.normalizeWhitespace`; numeric compares
   field, operator and value with `BigDecimal.compareTo`). `RerunChangeHandler.unchanged` must delegate to
   it (carry-over stays true exactly when the set contains none of `TEXT`, `TYPE`, `NUMERIC`; a title-only
   change still carries over). Delete the duplicate logic.
3. `TextDiff` in `com.lexvay.common`: `words(String a, String b)` tokenizes into word and non-word runs
   (regex `\\w+|\\W+`, so joining tokens reproduces the input exactly), diffs the token lists and returns
   merged segments `{op: EQUAL | DELETE | INSERT, text}` (adjacent segments with the same op are merged; a
   replace becomes DELETE then INSERT). `lines(String a, String b)` does the same on lines and, for each
   replaced block, refines with `words` on the joined block. Property to test: concatenating EQUAL+DELETE
   segments gives `a`, EQUAL+INSERT gives `b`.
4. Matching algorithm (`RuleVersionDiffService`): (a) pair requirements by identical `ref`; (b) among the
   unpaired, compute Jaccard similarity of lower-cased word sets of the texts for every removed × added
   pair, sort descending, greedily pair those with similarity ≥ 0.80 and mark them `refChanged: true`;
   (c) what remains is `ADDED` or `REMOVED`. A pair with an empty change set and the same ref is
   `UNCHANGED`, otherwise `CHANGED`. Order the result by the newer version's order, removed ones last in
   the older version's order.
5. `GET /api/v1/rule-versions/{id}/diff?against={otherId}`; `against` defaults to the version this one
   supersedes (400 problem detail if there is none); both versions must be the same jurisdiction and
   license type (400 otherwise). Response: `{ from: {id, label, effectiveFrom}, to: {...}, summary: {added,
   removed, changed, unchanged}, requirements: [{ status, refChanged, before, after, changes, textDiff }],
   ruleTextDiff: [segments] }` where `textDiff` is `TextDiff.words(before.text, after.text)` for CHANGED
   rows and `ruleTextDiff` is `TextDiff.lines` over the two rule texts. Read-only, one transaction.
6. Tests: unit tests for `TextDiff` (including the reconstruction property on several inputs and empty
   strings), `RequirementComparison`, the matcher (renumbered requirement detected, below-threshold pair not
   merged); API tests using the seeded Colorado revision (exactly one CHANGED: CO-5 with `NUMERIC` and
   `TEXT`, eight UNCHANGED), tenant isolation 404, 400 cases. `RuleVersionChangeTest` and
   `CourseVersionChangeTest` must stay green unmodified.

## F4a — Evidence requests API (backend only)
Purpose: "Request evidence from program director" must start a real loop: the contributor sees the open
request, supplies a passage from a program document, and the requirement returns to the reviewer with the
human-supplied evidence attached. The system's proposed finding is never altered by this. Slot 6.
Migration file name: `V007__evidence_response.sql` (V006 is reserved for WS-PERF).
1. Table `evidence_response` (append-only: immutability trigger; `tenant_id` + FORCE RLS + policy exactly
   like the other tables in V002; listed in the existing schema tests automatically): `id`, `tenant_id`,
   `mapping_id` → mapping, `review_event_id` → the `EVIDENCE_REQUESTED` event it answers,
   `evidence_span_id` → evidence_span, `comment` (nullable, ≤ 4000), `created_at`, `created_by` → app_user.
2. `POST /api/v1/mappings/{id}/evidence-responses` (CONTRIBUTOR, REVIEWER, SIGNER, ADMIN) with
   `{ documentId, pageNumber, quote, comment }`. Checks in this order: mapping exists in the tenant (404);
   not stale (409); no SIGNED determination for its assessment and jurisdiction (409, reuse
   `DeterminationRepository.hasSigned`); the mapping's current decision is `EVIDENCE_REQUESTED` (409 "No
   open evidence request for this requirement"); the document belongs to the mapping's program and is not
   rule text (400); the page exists (400); the quote passes `QuoteVerifier` against that page (400 naming
   the page; product rule 2 applies to people too). Then, in one transaction: insert the `evidence_span`
   (exact text and page-relative offsets from the verifier), insert `evidence_response`, insert a
   `REOPENED` review event on the mapping with note `Evidence provided by <display name>` and the
   contributor as `user_id` so the requirement returns to "needs review", and audit `EVIDENCE_PROVIDED`
   (mapping id, requirement ref, jurisdiction, document id, page, quote). Returns 201 with the response view.
3. `GET /api/v1/mappings/{id}/evidence-responses` → newest first: `{ id, reviewEventId, documentId,
   filename, kind, courseCode, courseRequired, pageNumber, charStart, charEnd, quote, comment, providedBy,
   providedByName, createdAt }`.
4. `GET /api/v1/programs/{id}/evidence-requests?status=OPEN|ANSWERED|ALL` (default OPEN) over the
   program's latest finished assessment, current mappings only (reuse the D-066 filter in
   `MappingRepository`, do not copy it): OPEN = current decision is `EVIDENCE_REQUESTED`; ANSWERED = the
   latest event is the `REOPENED` written by a response and no decision since. Each row: mapping id,
   jurisdiction, requirement ref and title, the request note, who asked and when, response count, latest
   response summary (who, when, filename, page).
5. `ReviewService` currently rejects user-sent `REOPENED`; keep that. The response path writes its
   `REOPENED` through the repository like `RerunChangeHandler` does.
6. Tests: full loop (reviewer requests → contributor lists OPEN → responds with a real quote → mapping has
   no decision, request is ANSWERED, response listed with exact offsets that reproduce the quote from the
   page text → reviewer accepts → request no longer listed); fabricated quote 400 and nothing stored;
   document from another program 400; no open request 409; stale 409; signed 409; roles; tenant isolation
   404 on all three endpoints; immutability of `evidence_response` (update and delete rejected);
   `Rule9_AuditCoverageTest` extended; `Rule7_EveryTableHasPolicyTest` passes with the new table.

---

## F6 — Production packaging and observability (ready to start; slot 7)
1. **One image.** Multi-stage `Dockerfile` at the repo root: Node 26 stage builds the Angular production
   bundle; Maven stage builds the backend with the bundle copied to `static/` and extracts the layered jar;
   runtime stage on a pinned Temurin 21 JRE image, non-root user, read-only root filesystem compatible
   (files and temp under `/var/lexvay`), `HEALTHCHECK` on readiness. The backend serves the single-page app
   with a fallback to `index.html` for every non-`/api`, non-`/actuator` GET that has no file extension.
   `docker compose --profile app up` runs database plus image; a smoke script proves sign-in through the
   container. Image size and cold start time recorded in `docs/OPERATIONS.md`.
2. **Request correlation.** A filter accepts or generates `X-Request-Id` (UUID; accept only
   `[A-Za-z0-9-]{8,64}` from clients), puts `requestId`, `tenantId` and `userId` in the logging MDC, echoes
   the header, and adds `requestId` to every problem detail. Job runs get a `jobId` in the MDC.
3. **Structured logs** under profile `prod` using Spring Boot's built-in structured logging (ECS format);
   human-readable locally. Never log document text, quotes, rule text, prompts or model output.
4. **Metrics** with Micrometer and the Prometheus registry on a separate management port
   (`LEXVAY_MANAGEMENT_PORT`, default 8090, health stays on the main port): `lexvay.llm.calls` (purpose,
   model, outcome), `lexvay.llm.tokens` (direction), `lexvay.llm.duration`, `lexvay.job.duration` (type,
   outcome), `lexvay.job.queue.depth` (gauge from one indexed count), `lexvay.quote.verification` (result:
   verified | rejected), `lexvay.mapping.finding` (finding, method). Metric code lives in one
   `LexvayMetrics` component; call sites pass facts, not meter names.
5. **Usage per assessment** `GET /api/v1/assessments/{id}/usage` → model calls by purpose, input and output
   tokens, wall time, and estimated cost from the configured prices; one SQL aggregate over `llm_call`.
6. **Graceful shutdown**: `server.shutdown=graceful`; the job poller stops claiming on shutdown and lets the
   running job finish within the grace period.
Tests for the SPA fallback (deep link returns index, `/api/unknown` stays a 404 problem detail), request id
rules, metrics presence after a pipeline run, usage endpoint with tenant isolation. Decisions in
`docs/decisions/F6.md` (D-140 to D-149).

## F9 — Curriculum search API (ready to start; slot 8)
Reviewers must be able to look for a passage themselves instead of trusting the shortlist.
1. `GET /api/v1/programs/{id}/search?q=&limit=&kind=&courseVersionId=` (any authenticated role; `limit`
   default 20, maximum 50; blank `q` is a 400). Query with `websearch_to_tsquery('english', :q)` so quoted
   phrases, `OR` and `-term` work. Rank with `ts_rank_cd(tsv, query, 32)`. Select the top rows first, then
   compute `ts_headline` only for those rows (`MaxFragments=2, MaxWords=40, MinWords=15`,
   `StartSel` and `StopSel` set to the control characters U+0001 and U+0002).
2. The server converts each headline into `segments: [{ text, match }]` by splitting on the control
   characters; the client never receives or renders markup from the database.
3. Row: `documentId, filename, kind, courseCode, courseRequired, pageNumber, rank, segments`. Rule text
   documents are excluded. Extend the `PassageSearch` seam with this method; `PostgresPassageSearch`
   implements both and shares the joins, no copied SQL.
4. Tests: phrase and negation queries on the seed, ordering by rank, segments reconstruct the headline,
   filters, limit cap, 400 for blank, tenant isolation, and a latency check (p95 ≤ 40 ms over 200 requests
   on the seeded tenant) following the pattern of `EndpointLatencyTest` if it exists on `main`.
Decisions in `docs/decisions/F9.md` (D-150 to D-159).

## Decisions already made for the screen work that follows WS-UI
The orchestrator writes the full spec when each item starts; these choices are fixed.
- **F1 Evidence viewer**: one reusable `DocumentViewer` component: page text in serif, the quote
  highlighted from the stored character offsets (a `mark` element, never string search in the browser),
  scrolled into view, previous and next page, page fetched from the immutable page endpoint with a small
  in-memory LRU cache, "Open original" through a new `GET /documents/{id}/file`. Text selection in the
  viewer yields `{ pageNumber, quote }` for the features that need a human-chosen quote.
- **F2 Program setup workspace**: PrimeNG Stepper: details → jurisdictions → courses → documents (upload
  with per-file ingest status from the job) → program facts (pick the number, then select its quote in the
  `DocumentViewer`) → readiness check (which states lack an approved rule version) → run assessment with
  job progress. Needs `GET /programs/{id}/documents`.
- **F3 Rule workspace**: rule versions by state with status (extracting, needs approval, approved,
  superseded); entry form; editable requirement table backed by the replace endpoint with server
  validation messages shown per row; approval confirmation; diff view from F3a with inserted and deleted
  words marked by `ins` and `del` elements plus underline and strike-through, never colour alone.
- **F4 Evidence request screens**: a contributor inbox of open requests; answering opens the
  `DocumentViewer` to select the quote; reviewers see supplied evidence, labelled with who provided it,
  next to the model-proposed evidence.
- **F5 Administration and audit tools**: user management (create, change role, deactivate; never delete),
  audit filters, CSV export streamed from the server.
- **F7 Matching quality** (after WS-EVAL so every change is measured): paragraph-level passages with
  neighbour context while quotes stay verified against the stored page; a consistency check that asks the
  model twice with the passages in a different order and downgrades to ambiguous on disagreement; prompt
  caching for the system prompt; new metric "shortlist recall" in the evaluation table.
- **F8 Determination register**: export of signed determinations per program and state (outcome, signer,
  date, rule version, versions hash) as CSV and as a printable page.
- **F10 Coverage matrix**: courses against requirements for one state, showing which course carries the
  evidence for which requirement and where a requirement rests on a single elective.
