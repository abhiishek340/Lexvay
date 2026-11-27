# Lexvay — Roadmap and work items

Owned by the orchestrator (CTO). Agents implement one work item each, exactly as specified, on the named
branch, and open a pull request. Status values: `queued`, `in progress`, `in review`, `merged`.

## Owner decisions needed
- **PrimeUI license key (D-080).** PrimeNG 22 is distributed under the PrimeUI License and verifies a key offline.
  Without one every page shows an "Invalid PrimeUI License" badge. The Community License is free for individuals
  and organisations under USD 1 M revenue with fewer than 5 developers (renewed yearly); register at
  https://primeui.dev, claim the key, and pass it at build time: `ng build --define
  "LEXVAY_PRIMEUI_LICENSE='<key>'"` (never commit it). Alternative if declined: Angular 21 with PrimeNG 21 (MIT),
  a framework downgrade the orchestrator does not recommend.

## Status board

| Item | Branch | E2E slot | Model | Status |
|---|---|---|---|---|
| WS-UI Design system and screen rebuild | `ui-foundation` | 1 | Opus 5.5 | merged (PR #15) |
| WS-SEC OIDC and hardening (spec milestone 8) | `sec-oidc-hardening` | 2 | Sonnet 5.5 | merged (PR #12) |
| WS-EVAL Evaluation harness and Terraform (spec milestone 9) | `eval-and-infra` | 3 | Sonnet 5.5 | merged (PR #11) |
| WS-PERF Read-path latency and read APIs | `perf-read-path` | 4 | Sonnet 5.5 | merged (PR #14) |
| F3a Rule version diff API | `feat-rule-diff-api` | 5 | Sonnet 5.5 | merged (PR #9) |
| F4a Evidence requests API | `feat-evidence-requests-api` | 6 | Sonnet 5.5 | merged (PR #8) |
| F6 Production packaging and observability | `ops-packaging` | 7 | Sonnet 5.5 | merged (PR #16) |
| F9 Curriculum search API | `feat-curriculum-search` | 8 | Sonnet 5.5 | merged (PR #10) |
| F11 OCR for scanned documents | `feat-ocr-ingest` | 6 | Sonnet 5.5 | merged (PR #13) |
| FIX-1 Requirement order column | `fix-requirement-ordinal` | 5 | Sonnet 5.5 | merged (PR #18) |
| FIX-2 Job entity id column and index | `fix-job-entity-index` | 5 | Sonnet 5.5 | in progress |
| F1 Evidence viewer with in-context highlight | `feat-evidence-viewer` | 1 | Opus 5.5 | merged (PR #21) |
| F2 Program setup workspace | `feat-setup-workspace` | 2 | Sonnet 5.5 | queued (after WS-UI) |
| F3 Rule workspace screens (uses F3a) | `feat-rule-workspace` | 10 | Opus 5.5 | merged (PR #22); review fixes in F3b |
| F4 Evidence request screens (uses F4a) | `feat-evidence-requests` | 4 | Opus 5.5 | in progress |
| F5 Administration and audit tools | `feat-admin-audit` | 5 | Sonnet 5.5 | queued |
| F7 Retrieval quality (BM25 ranking, excerpts, retrieval metrics) | `retrieval-quality` | 3 | Sonnet 5.5 | merged (PR #17) |
| F3b Rule workspace review fixes (edit layout, error text, unsaved-changes guard, viewer reuse) | `feat-rule-workspace-fixes` | 10 | Opus 5.5 | merged (PR #24) |
| F7b Answer stability and prompt caching | `match-stability` | — | Sonnet 5.5 | queued (needs a live model key to measure) |
| F7c Search terms proposed at extraction | `search-terms` | 3 | Sonnet 5.5 | queued (after F7, FIX-1) |
| OPS-1 Image build and smoke in CI | `ops-ci-image` | — | Sonnet 5.5 | merged (PR #20) |
| F8 Determination register | `feat-determination-register` | 2 | Opus 5.5 | in progress |
| F10 Coverage matrix | `feat-coverage-matrix` | — | Opus 5.5 | queued (after WS-UI) |
| F12 Rule source monitoring (fetch, detect change, draft version) | `feat-rule-sources` | 8 | Sonnet 5.5 | merged (PR #19) |
| F13 Work queue API | `feat-work-queue` | 9 | Sonnet 5.5 | merged (PR #23) |

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

## F1 — Evidence viewer with in-context highlight (slot 1; Opus 5.5; after WS-PERF)
Purpose: a reviewer must see a quote where it sits on the page, not only the quote. One reusable
`DocumentViewer` serves the review screen now and the evidence-request and program-setup screens later. The
quote is highlighted from the stored character offsets; the browser never searches text (the server verified
the quote; the viewer only shows what was verified). Decisions D-220 to D-229 in `DECISIONS.md (F1)`.
1. **Backend** (small, with tests): `GET /api/v1/documents/{id}/file` streams the original bytes from
   `FileStore` with `Content-Type` from the stored kind (`application/pdf`, DOCX media type, `text/plain`),
   `Content-Disposition: inline; filename="<stored filename, RFC 5987 encoded>"`, `Cache-Control: private,
   max-age=31536000, immutable` and the same ETag scheme as the page endpoint (document sha256); 404 for
   another tenant; any role; read-only so no audit entry; a row in `InputValidationTableTest`; one
   statement plus the store read. The `PageView` from WS-PERF is used as is.
2. **`DocumentViewer`** (`frontend/src/app/shared/document-viewer/`, standalone): inputs `documentId`,
   `pageNumber`, `highlight: { charStart, charEnd } | null` (page-relative offsets exactly as `Evidence`
   carries them), `selectable: boolean`. Renders the page text in serif inside the document frame used by
   the review screen (move that frame's styles into the viewer so there is one copy), with the file name,
   page `n of N`, previous and next page buttons (PrimeNG Button, icon and text, disabled at the ends), and
   "Open original" linking to the file endpoint in a new tab (`rel="noopener"`, external icon). The
   highlight is a single `mark` element wrapping `text.slice(charStart, charEnd)` with the rest as text
   nodes (never `innerHTML`); after the page renders the mark is scrolled into view (`block: 'center'`)
   and receives `tabindex="-1"` focus only when the viewer was opened for that quote; `mark` is styled with
   the warning tint plus a 2 px underline so it does not rely on colour alone. If the offsets fall outside
   the text (should not happen; the server verified them), the viewer shows the page without a mark and an
   inline Message "The stored quote position does not match this page".
3. **Page cache**: `DocumentPageCache` service (one per app) with an LRU of 40 pages keyed by
   `documentId:pageNumber`, in front of `GET /documents/{id}/pages/{n}`; requests in flight are shared
   (same observable); the browser cache does the rest (WS-PERF headers). Vitest tests for eviction order and
   in-flight sharing.
4. **Selection** (`selectable` true): when the user selects text inside the page element, the viewer emits
   `selection = { pageNumber, quote, charStart, charEnd }` computed from the DOM Range against the page text
   (offsets measured within the page `pre`/`p` element, whitespace exactly as stored); a small floating
   toolbar (PrimeNG Button "Use this passage") appears next to the selection and also emits on click;
   Escape clears it. Keyboard alternative: a "Select passage by character range" Button opens a Dialog
   with start and end InputNumber fields that shows the resulting quote live and emits the same event.
   Vitest tests for the offset computation on a fixture page (selection inside one text node, across the
   mark boundary, and collapsed selection ignored).
5. **Review screen**: the Evidence pane keeps its header (file, course, required or elective, page,
   characters) and replaces the blockquote with `DocumentViewer` showing the cited page with the quote
   highlighted; the quote stays available above the viewer in a collapsed "Quote only" disclosure for
   screen readers and small screens (under 1024 px the viewer is collapsed by default behind "Show on
   page"). The viewer in Review is read-only (`selectable` false).
6. **Overview**: replace the audit-log route to the latest assessment with WS-PERF's
   `GET /programs/{id}/overview` (follow-up from WS-UI). Delete the old code path.
7. **Tests**: Vitest for the cache, the offset computation and the highlight splitting (pure function
   `splitForHighlight(text, start, end)`); end-to-end (`frontend/e2e/viewer.spec.ts`, slot 1): the cited
   page opens with the quote marked and scrolled into view (assert the `mark` text equals the evidence quote
   from the API), next and previous page work and are announced, "Open original" responds 200 with
   `application/pdf`, keyboard range selection emits the quote (exercised through a test harness route or
   the first screen that uses `selectable`), axe in both themes at 1280 and 400 px. Backend tests for the file
   endpoint (headers, 404 other tenant, 304 on If-None-Match).
Done when: `LEXVAY_E2E_SLOT=1 make test` green; screenshots of the review screen with the viewer in both themes
in `docs/screens/`; `DECISIONS.md (F1)` and `PROGRESS.md (F1)` written.

## F3 — Rule workspace screens (frontend; slot 10; Opus 5.5; uses F3a, WS-UI)
Purpose: today a reviewer can approve a requirement list only through the API. The rule workspace is where a
jurisdiction's rule versions are entered, the extracted list is corrected and approved, and a revision is
compared with the version it supersedes. The product rules apply on screen exactly as in the API: the model
proposes the list, a person approves it; nothing approved is ever edited. Decisions D-210 to D-219 in
`DECISIONS.md (F3)`. Everything is built from PrimeNG 22 components with the existing `LexvayPreset`, Phosphor
icons, the tokens in `docs/DESIGN.md`, and the patterns WS-UI set (skeleton loading, inline `Message` for
problem details, toast for completed operations, focus on the heading after navigation, `data-testid="message"`).
1. **Route and navigation**: `programs/:programId/rules` ("Rules" in the rail, icon `phosphorScales`, placed
   after Changes; badge = number of unapproved versions that have requirements, text "n rule versions waiting
   for approval"). Lazy chunk. Also `programs/:programId/rules/:ruleVersionId` opens one version.
2. **Version list** (left pane, 320 px, PrimeNG Listbox or a plain list with `role="list"`): grouped by
   jurisdiction code in the order of the program's target jurisdictions, then other jurisdictions. Each row:
   jurisdiction code (mono), label, effective date, and one status Tag with icon and text: *Extracting*
   (extraction job QUEUED or RUNNING, `phosphorSpinner` animated), *Extraction failed* (job FAILED, with the
   error as helper text), *Needs approval* (requirements present, not approved), *Approved*, *Superseded*
   (another version's `supersedesId` points at it). Data from `GET /rule-versions` (one request; the API
   already returns `extraction`, `approval`, `requirementCount`, `supersedesId`). The list polls every 5 s
   only while a version is *Extracting*, then stops.
3. **New version** (PrimeNG Dialog, from a "New rule version" button; CONTRIBUTOR and ADMIN; others do not see
   the button): fields jurisdiction (Select over the tenant's jurisdictions, code and name), license type
   (InputText, default the program's license type), label, source URL, effective date (DatePicker, ISO
   output), supersedes (Select listing approved versions of the chosen jurisdiction and license type, optional,
   with helper text "Choose the version this one replaces; a change event opens when it is approved"), rule
   text (Textarea, mono, counter of characters against 500 000). Submit → `POST /rule-versions` (202) → close,
   toast "Extraction started", select the new version, which shows *Extracting*. Server validation problems
   are shown inline under the dialog's fields when the problem detail names a field, otherwise in a Message.
4. **Version detail** (right pane, scrolls): header with label, jurisdiction name and code, license type,
   effective date, source link (external, icon), the status Tag, and the approval line ("Approved by Dana
   Whitfield on 2026-11-26" or "Not approved"). Tabs (PrimeNG Tabs): **Requirements**, **Rule text**,
   **Compare**.
   - **Requirements tab**: PrimeNG Table of `GET /rule-versions/{id}/requirements`, columns ref (mono),
     title, type (Tag, text only), check (for NUMERIC and SUPERVISED_HOURS: `field operator value`, mono),
     text (serif, truncated to three lines with an expand control). When the version is **not approved** and the
     user is REVIEWER, SIGNER or ADMIN: an "Edit list" toggle switches the table to row editing (PrimeNG Table
     `editMode="row"` is not used; instead each cell becomes a form control in place so every row is editable at
     once): ref InputText, title InputText, text Textarea, type Select, numeric field Select / operator Select /
     value InputNumber shown only for numeric types; row actions move up, move down, delete; "Add requirement"
     appends an empty row and focuses its ref. "Save list" sends the whole list to `PUT
     /rule-versions/{id}/requirements` in the API's body shape; a 400 problem detail whose message names a ref or
     row is shown as an inline error under that row, otherwise as a Message above the table. "Discard" restores
     the server list. Order is the list order (FIX-1 makes the server keep it; until FIX-1 merges the server may
     reorder by ref and the screen must simply show what comes back).
     "Approve requirements" (primary, same roles, only when not approved and at least one requirement exists)
     opens a confirmation Dialog: "Approving locks this list. Assessments for <jurisdiction> will use these N
     requirements. The list can no longer be edited." with "Approve" and "Cancel"; on success toast "Requirements
     approved", status becomes *Approved*, the edit controls disappear. Approved lists show no edit controls at
     all; a 409 from the server (already approved) is shown as a Message and the view reloads.
   - **Rule text tab**: the stored rule text pages in serif inside the document frame (fetch pages through
     `GET /documents/{documentId}/pages/{n}` from WS-PERF once it merges; until then `GET /documents/{id}`; use
     whichever exists on `origin/main` when you start, and say which in the PR), page navigation, mono page
     numbers.
   - **Compare tab**: `GET /rule-versions/{id}/diff` (default against the superseded version; a Select offers
     any other approved version of the same jurisdiction and license type as `against`). Summary line
     "3 changed · 1 added · 0 removed · 8 unchanged" with icons. Table rows by status with a Tag (Added,
     Removed, Changed, Unchanged, and "ref changed" as a second small Tag); CHANGED rows show the word diff
     with inserted words in `ins` (underline, success colour) and deleted words in `del` (strike-through,
     danger colour), never colour alone; the numeric check before → after when `NUMERIC` changed. A
     "Rule text" expander shows `ruleTextDiff` the same way. If the version supersedes nothing and no
     `against` is chosen: one sentence "This is the first version for <jurisdiction>; choose a version to
     compare with."
5. **Keyboard and accessibility**: the version list is arrow-key navigable with `aria-selected`; the edit
   grid is a real form (labels via `aria-label` per cell naming the ref and column); Tab order left to
   right; the approve dialog returns focus to the button; axe clean in light and dark, at 1280 and 400 px
   (panes stack under 1024 px).
6. **Unit tests** (Vitest): the version status derivation (one pure function `ruleVersionStatus(view, all)`
   with every state), the grouping and ordering, the edit-list model (add, move, delete, dirty flag,
   restore), the problem-detail-to-row mapping.
7. **End-to-end tests** (Playwright, `frontend/e2e/rules.spec.ts`, slot 10, seeded Northfield data): a
   reviewer opens Rules, sees the seeded Colorado revision as *Needs approval*, edits one requirement's title,
   saves, reloads and sees it; approves it through the dialog and sees *Approved* and the audit entry; the
   Compare tab shows exactly one Changed row (CO-5, numeric and text) and the `ins`/`del` marks; a contributor
   creates a new version through the dialog and sees *Extracting* then (FakeLlmClient) *Needs approval*; axe on
   every state in both themes. Every e2e test keeps the WS-UI rule that no request leaves localhost.
8. No backend change is expected. If one is needed (for example a missing field in a view), add it with
   tests, a validation-table row if it is an endpoint, and call it out in the PR.
Done when: `LEXVAY_E2E_SLOT=10 make test` green; screenshots of the list, the edit grid, the approve dialog and the
compare tab in both themes under `docs/screens/`; `DECISIONS.md (F3)` and `PROGRESS.md (F3)` written.

## F4 — Evidence request screens (frontend; slot 4; Opus 5.5; after F1 merges; uses F4a)
Purpose: "Request evidence from program director" in Review currently ends in a decision nobody can act on in
the product. F4 closes the loop on screen: the contributor sees what was asked, opens the program's documents,
selects the passage, and the requirement returns to the reviewer with that evidence shown next to what the
system proposed. A person's evidence is held to the same rule as the model's: the quote must be on the stored
page (product rule 2), which the selection in `DocumentViewer` guarantees and the server re-verifies. The
system's proposed finding is never changed by an answer. Decisions D-260 to D-269 in `docs/decisions/F4.md`.
1. **Route and navigation**: `programs/:programId/requests` ("Evidence requests" in the rail, icon
   `phosphorTray`, after Review; badge = OPEN count, text "n open evidence requests"; visible to every role).
   Data from `GET /programs/{id}/evidence-requests?status=OPEN|ANSWERED|ALL`.
2. **Inbox**: a SelectButton for Open / Answered / All with counts; PrimeNG Table rows: jurisdiction code (mono),
   requirement ref (mono) and title, the request note (truncated to two lines, full text in the row detail),
   who asked and when ("Dana Whitfield, 26 Nov"), status Tag with icon (Open: `phosphorEnvelopeOpen`; Answered:
   `phosphorCheckCircle` with "awaiting review"), responses count, and the latest response summary (who, when,
   file, page). A stale request (follow-up from F4a: the mapping went stale after a change) is shown with a
   *Blocked* Tag and helper text "This requirement is waiting for a re-run after a change; answer it after the
   re-run" and no answer action; the inbox derives `blocked` from the mapping's `stale` flag, which the list
   endpoint must expose (backend: add `stale` to `EvidenceRequestView`, one column in the existing statement,
   with a test). Empty state: one sentence ("No open evidence requests. Reviewers create them from the Review
   screen.").
3. **Answer** (row action "Provide evidence", for CONTRIBUTOR, REVIEWER, SIGNER and ADMIN): opens a full-height
   PrimeNG Drawer (right, 70 % width, full width under 1024 px) with three parts: the requirement (ref, title,
   rule text in serif, the request note and who asked); a document picker (Select over the program's
   documents excluding rule text, grouped by course code, showing file name and kind; data from
   `GET /programs/{id}/documents` if it exists on main, otherwise the program's courses and their documents
   through existing endpoints; say which in the PR); and `DocumentViewer` with `selectable` true on the chosen
   document. Selecting text (or the keyboard range dialog) fills a "Selected passage" box in serif with the
   page number and character range in mono; a comment Textarea (optional, 4000 characters); "Submit evidence"
   posts `{ documentId, pageNumber, quote, comment }` to `POST /mappings/{id}/evidence-responses`. A 400 (quote
   not on page) is shown inline under the selected passage with the server's detail; 409s (stale, signed, no
   open request) are shown in a Message and the inbox reloads. On success: toast "Evidence sent to review",
   the drawer closes, the row moves to Answered.
4. **Review screen**: when a mapping has responses (`GET /mappings/{id}/evidence-responses`, fetched with the
   selected mapping, cached per mapping in the screen's workspace), the Evidence pane shows a second block under
   the system's evidence: "Evidence provided by <name>, <date>" (label with `phosphorUser` icon, never colour
   alone) with the quote shown in `DocumentViewer` highlight exactly like the system's quote, plus the comment.
   The system's proposed finding and its evidence stay first and unchanged. The decision note placeholder for
   an answered request reads "Note the evidence you relied on". The requirement list shows a small
   `phosphorChatCircleText` marker with text "evidence provided" on mappings with responses.
   **Decided during F4 (orchestrator):** `GET /programs/{id}/documents` is added in F4 (one statement, `current`
   flag from the course-version supersession, any role); the picker shows current documents by default with an
   "Include earlier catalog years" switch and disables documents without pages; `Mapping` gains `responseCount`
   (correlated count served by `evidence_response_mapping_idx`), measured with `make perf`.
5. **Accessibility and keyboard**: drawer traps focus and returns it to the row action; the inbox table is
   keyboard navigable; axe clean in both themes at 1280 and 400 px.
6. **Unit tests** (Vitest): inbox status derivation including blocked, the selection-to-request mapping, the
   review workspace merge of responses into the evidence view.
7. **End-to-end** (`frontend/e2e/evidence-requests.spec.ts`, slot 4, seeded data): a reviewer requests evidence
   on a Missing requirement with a note; a contributor signs in, sees it in the inbox with the badge, opens it,
   picks a syllabus, selects a real passage with the keyboard range dialog, submits; the reviewer sees the
   requirement back in "Needs review" with "Evidence provided by ..." and the highlighted quote, accepts it;
   the inbox shows nothing open. A tampered quote (submitted through the API in the test) is refused with the
   server's message. Axe on every state in both themes; no request leaves localhost.
Done when: `LEXVAY_E2E_SLOT=4 make test` green; screenshots of the inbox, the drawer with a selection and the review
pane with provided evidence in both themes under `docs/screens/`; `docs/decisions/F4.md` and `docs/progress/F4.md`.

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
`DECISIONS.md (D-140 to D-149)` (D-140 to D-149).

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
Decisions D-150 to D-159 (folded into `DECISIONS.md`).

## FIX-1 — Requirement order column (after WS-PERF merges; slot 5; migration `V012__requirement_ordinal.sql`)
Defect found in review of F3a: `RuleVersionRepository.requirements` orders by `created_at, ref`; rows of one
version share a timestamp, so the order is the text order of the ref ("CO-10" before "CO-2").
1. Migration `V012__requirement_ordinal.sql` (V006 stays unused): inside the migration, `ALTER TABLE requirement NO FORCE
   ROW LEVEL SECURITY` and disable the `requirement_locked` trigger, add `ordinal integer`, backfill with
   `row_number() OVER (PARTITION BY rule_version_id ORDER BY created_at, (substring(ref from '(\d+)$'))::int
   NULLS LAST, ref)`, set `NOT NULL`, add `UNIQUE (rule_version_id, ordinal)`, then re-enable the trigger and
   `FORCE ROW LEVEL SECURITY`. A migration test loads rows before the migration is applied is not possible
   with Flyway-on-start, so prove the backfill expression with a SQL-level test on a temporary table.
2. `RequirementDraft` lists carry their order: extraction and the replace endpoint assign `ordinal` = list
   position starting at 1. `Requirement` exposes `ordinal`. Every query that lists requirements or mappings
   orders by `r.ordinal` (search for `r.created_at, r.ref` and `created_at, ref`). The diff's "newer
   version's order" then follows the rule's own order.
3. Tests: a rule with twelve requirements lists CO-1 … CO-12 in order through the requirements endpoint,
   the mappings endpoint and the diff; replace keeps the submitted order; `Rule5` immutability unchanged.

## FIX-2 — Job entity id column and index (slot 5; migration `V013__job_entity_id.sql`)
Defect found in review of WS-PERF (D-115): `JobRepository.latest` filters by `payload->>:key`, which scans the
whole `job` table under row-level security because `jsonb ->>` is not leakproof and jobs are never deleted.
Every job payload today holds exactly one id (`documentId`, `ruleVersionId`, `assessmentId`, `changeEventId`,
`reportId`, and `ruleSourceId` from F12), so the id becomes a real column.
1. Migration: `ALTER TABLE job ADD COLUMN entity_id uuid`; backfill with the single value of the payload
   object (`(SELECT value::uuid FROM jsonb_each_text(payload) LIMIT 1)`, guarded by
   `jsonb_typeof(payload) = 'object'`); `SET NOT NULL`; `CREATE INDEX job_entity_idx ON job (tenant_id, type,
   entity_id, created_at DESC)`. Prove the backfill expression with a SQL-level test on a temporary table, as
   FIX-1 does. The job policies in V002 are unchanged; `entity_id = :id` is leakproof, so the planner may use
   the index under the policy. `IndexUsageTest` (WS-PERF) gains this index and the lookup query.
2. `JobRepository.enqueue(JobType type, UUID entityId, Map<String, ?> payload)` writes both columns; the five
   call sites pass the id they already put in the payload (payload stays, for future jobs that need more
   input). `latest(JobType, UUID entityId)` replaces `latest(JobType, String key, UUID value)`; no string key
   anywhere. `Job` exposes `entityId`.
3. Tests: the two lookups return the same job as before (`DocumentService` ingest status, rule version
   extraction status); `EXPLAIN` under the application role shows an index scan on `job_entity_idx`;
   `MigrationsApplyCleanlyTest` counts the new file; the WS-PERF latency table in `docs/PERF.md` gets the
   before and after rows for `GET /documents/{id}` and `GET /rule-versions`.
Decisions D-200 to D-209 in `docs/decisions/FIX-2.md`.

## F11 — OCR for scanned documents (slot 6; migration `V009__document_page_text_source.sql`)
Real syllabi and handbooks are often scans. Today a PDF without a text layer is rejected. OCR text is less
reliable than a text layer, so it is stored with its source and confidence and is treated cautiously.
1. **Seam** `OcrEngine` in `com.lexvay.document`: `boolean available()`, `Optional<OcrPage>
   recognize(BufferedImage page)` with `OcrPage(String text, double meanConfidence)` (0–100).
   `NoOcrEngine` (not available) and `TesseractCliOcrEngine`. Selected by `LEXVAY_OCR` = `off` (default) |
   `tesseract`; binary from `LEXVAY_OCR_TESSERACT_PATH` (default `tesseract`), language from
   `LEXVAY_OCR_LANGUAGE` (default `eng`).
2. **Tesseract call**: write the page image as PNG into a private temporary directory, run
   `tesseract <png> stdout -l <lang> --psm 3 tsv` with an argument array (never a shell), 60 s timeout,
   delete the files afterwards. Parse the TSV: words are rows with level 5 and conf ≥ 0; join words of the
   same (block, paragraph, line) with one space, lines with a line feed, paragraphs with a blank line;
   mean confidence is the average `conf` of those words. A page whose image exceeds 25 megapixels is
   rendered at a lower DPI so it stays under the cap.
3. **Ingest rule** per PDF page: if the text layer has at least 20 non-whitespace characters, keep it
   (`TEXT_LAYER`). Otherwise, when the engine is available, render the page at 300 DPI in grayscale with
   PDFBox and recognise it; accept the result when it has at least 20 non-whitespace characters (`OCR`).
   Otherwise the page text is empty. A document whose pages are all empty still fails with "Document has no
   extractable text"; append "It looks scanned; OCR is not enabled on this server" when no page had a text
   layer and the engine is unavailable. At most `lexvay.ocr.max-pages` (default 200) pages per document are
   sent to OCR; more is a permanent job failure with a clear message.
4. **Schema** (V009): `document_page.text_source text NOT NULL DEFAULT 'TEXT_LAYER' CHECK (text_source IN
   ('TEXT_LAYER','OCR'))`, `document_page.ocr_confidence numeric(5,2)`, and `CHECK ((text_source = 'OCR') =
   (ocr_confidence IS NOT NULL))`.
5. **Product rule 4**: evidence cited from an OCR page whose confidence is below
   `lexvay.matching.min-ocr-confidence` (default 80) cannot be `EVIDENCE_FOUND`; it is downgraded to
   `AMBIGUOUS` with the reason "the cited page was read by OCR with NN % confidence; check the original".
   `EvidenceMatcher` now has three downgrade rules (elective course, thin quote, low OCR confidence):
   express them as an ordered list of small rule objects applied in sequence, each appending its reason,
   instead of a third `if` block. `Passage` carries `textSource` and `ocrConfidence`.
6. **Read models**: page summaries in `GET /documents/{id}` and the evidence block of a mapping expose
   `textSource` and `ocrConfidence`. The `DOCUMENT_INGESTED` audit detail counts OCR pages.
7. **Tests**: TSV parser against a recorded fixture; ingest of an image-only PDF with a fake engine (pages
   stored as `OCR` with confidence); engine unavailable gives the hint message; page cap; quote
   verification works against OCR text exactly as against a text layer;
   `Rule4_LowOcrConfidenceForcesAmbiguousTest`; one test with the real binary guarded by
   `assumeTrue(engine.available())` that renders known text into an image-only PDF and finds its key words.
   Install Tesseract locally with Homebrew and in CI with the `tesseract-ocr` apt package so that test runs.
Decisions in `DECISIONS.md (D-160 to D-169)` (D-160 to D-169). If a `Dockerfile` exists on `main` when you finish,
add Tesseract to the runtime stage and set `LEXVAY_OCR=tesseract` there; otherwise say so in the report.

## F7 — Retrieval quality (slot 3; no schema change)
Today the shortlist is the top 8 whole pages by `ts_rank` over an OR of the requirement's words. Two
weaknesses on real documents: `ts_rank` has no notion of how common a word is in this program's corpus, so
boilerplate ("course", "students", "counseling") dominates; and whole pages are long, which costs tokens and
dilutes the model's attention. Everything below is measured before and after with the evaluation harness.
1. **Retrieval corpus** in `eval/retrieval/`: fictional, labeled like the seed. At least 12 additional
   syllabi of 3 to 5 pages each with realistic sections (description, outcomes, weekly schedule, assignments)
   and identical policy boilerplate on every syllabus (academic integrity, accommodations, grading scale),
   plus the 14 seed documents. `queries.yml`: at least 30 requirement-style queries, each with the expected
   file, page and a substring of the passage that carries the evidence; at least 6 of them marked
   `lexicalGap: true`, where the document uses different vocabulary from the requirement (for example
   "psychopathology" against "diagnosis of mental and emotional disorders"). Lexical-gap queries are reported
   separately and never count toward thresholds; they quantify what lexical search cannot do.
2. **Metrics** (new `RetrievalScorer`, pure like `EvalScorer`; printed by `make eval` in a second table and
   written to `docs/RETRIEVAL.md`): recall@8, recall@3 and mean reciprocal rank of the expected page, and
   excerpt coverage (the expected substring is inside the excerpt that would be sent to the model, among
   recalled queries). Report each for the baseline ranker and the new one.
3. **BM25 ranking in SQL** inside `PostgresPassageSearch` (extend the shared SQL template; the reviewer
   search keeps `ts_rank_cd`). Query lexemes come from PostgreSQL itself:
   `tsvector_to_array(to_tsvector('english', title))` with boost 2.0 and the same for the text with boost
   1.0 (a lexeme in both takes 2.0). Corpus = the program's non-rule pages. `N` = page count, `df(t)` = pages
   containing lexeme `t`, `len(p)` = `length(tsv)` of the page, `avglen` = its corpus mean, `tf(t,p)` = number
   of positions of `t` in the page's `tsv`. Score(p) = Σ over query lexemes present in p of
   `boost(t) · ln(1 + (N − df(t) + 0.5)/(df(t) + 0.5)) · tf·(k1+1) / (tf + k1·(1 − b + b·len(p)/avglen))` with
   `k1 = 1.2`, `b = 0.75`. Candidates are pages matching the OR of the lexemes, built as a `tsquery` from
   quoted lexemes inside SQL (never string-built from user text). Ties break by document creation time, page
   number, document id, as today. One statement per requirement; budget p95 ≤ 30 ms on a synthetic
   500-page program (add that to the perf-tagged tests). Keep the old ranker selectable
   (`lexvay.matching.ranker` = `bm25` default | `ts_rank`) so the harness can compare them.
4. **Excerpts** (new `PassageExcerpter` in `assessment.match`): a page of at most 2 500 characters is sent
   whole. A longer page is sent as: its first lines up to 300 characters (the header that names the course),
   then up to three fragments chosen by `ts_headline` for the same lexeme query (`MaxFragments=3,
   MaxWords=120, MinWords=60`, fragment delimiter U+0003, start and stop selectors empty), each located in
   the stored page text with `QuoteVerifier.find`, expanded to the enclosing line boundaries, merged when they
   touch, emitted in page order and separated by a line containing only `[...]`. If any fragment cannot be
   located, send the first 2 500 characters instead. `Passage` gains `excerpt` (what the model sees) while
   `text` stays the full page; the quote is still verified against the full stored page.
5. **Prompt** `match-v2`: same rules as `match-v1` plus: passages may be excerpts; `[...]` marks omitted text;
   a quote must be copied from within one excerpt and must not span `[...]`. The assessment records
   `match-v2`. Keep `match-v1` on disk for history.
6. **Thresholds** in `eval/thresholds.yml` (non-gap queries, BM25): recall@8 ≥ 0.95 and not below the
   baseline, recall@3 not below the baseline, excerpt coverage = 1.00. `make eval` fails otherwise. The
   existing pipeline metrics on the seed must stay exactly as they are (0 % false evidence found).
7. Tests: scorer unit tests; BM25 ordering on a tiny hand-computed corpus (assert the scores to 1e-6
   against values computed by hand in the test); excerpter cases (short page, long page, fragment not
   found, merging); the prompt contains the excerpt and not the full long page; quote from an excerpt
   verifies; quote spanning `[...]` is rejected by the verifier because it is not in the page.
Decisions in `DECISIONS.md (F7)` (D-170 to D-179).

## F12 — Rule source monitoring (backend only; slot 8; migration `V011__rule_source.sql`)
Purpose: change tracking today starts only when a person uploads a new rule text. Real boards publish rule
changes on a web page or PDF without notice; the product must notice them. The system fetches, detects a
textual change, creates an **unapproved** draft rule version and opens the existing change flow. It never
approves anything and never alters an approved version (product rules 1, 5, 6). Decisions D-180 to D-189 in
`DECISIONS.md (D-180 to D-189)`.
1. **Schema** (`V011__rule_source.sql`; RLS + FORCE + policy as in V002, so the schema tests pick it up):
   `rule_source` (`id`, `tenant_id`, `jurisdiction_id` → jurisdiction, `license_type`, `label`, `url`
   (http or https only, CHECK with a regex on the scheme), `check_interval_hours` int CHECK ≥ 1 AND ≤ 720,
   `active` boolean default true, `last_checked_at` timestamptz, `last_content_hash` text, `created_at`,
   `created_by`); updates restricted with `restrict_update_to_columns` to `active`, `last_checked_at`,
   `last_content_hash`. `rule_source_check` (append-only, `forbid_change()`): `id`, `tenant_id`,
   `rule_source_id`, `checked_at`, `outcome` CHECK IN ('UNCHANGED', 'CHANGED', 'DRAFT_EXISTS',
   'FETCH_FAILED'), `http_status` int, `content_hash` text, `detail` text, `document_id` → document (null
   unless CHANGED), `rule_version_id` → rule_version (null unless CHANGED). Index
   `(rule_source_id, checked_at DESC)`. The job payload carries `ruleSourceId`.
2. **Fetching seam** `com.lexvay.rule.source.HttpFetcher` with `Fetched fetch(URI url)` returning status,
   content type and bytes. `JdkHttpFetcher` (java.net.http, connect 10 s, read 30 s, follows at most 3
   redirects re-checking every hop, body cap 20 MB → `FetchRejected`), and an SSRF guard: before connecting,
   resolve the host and refuse loopback, link-local, private (RFC 1918), unique-local and multicast
   addresses, and any non-http(s) scheme, with a 400 at creation time and a `FETCH_FAILED` check at run
   time. `FakeHttpFetcher` in tests is scripted by URL. No test touches the network.
3. **Text and hash**: PDF, DOCX and TXT go through the existing `TextExtractor`. Add `FileType.HTML`
   (detected by content type `text/html` or a leading `<!doctype html`/`<html`) extracted with jsoup
   (pin the current release from Maven Central metadata; record it in D-002): drop `script`, `style`,
   `nav`, `header`, `footer`, keep block structure as line breaks, pages = one page per 4 000 characters
   split at paragraph boundaries, exactly like the TXT path does today. The content hash is SHA-256 of
   `QuoteVerifier.normalizeWhitespace` over the concatenated page text, so whitespace-only and markup-only
   edits are `UNCHANGED`.
4. **Job** `CHECK_RULE_SOURCE` (new `JobType`, handler in `rule.source`): fetch; on failure write a
   `FETCH_FAILED` check with the status and message and finish normally (the schedule retries later; this is
   not a `PermanentJobFailure`). Otherwise compute the hash; equal to `last_content_hash` → `UNCHANGED` and
   update `last_checked_at`. Different → store the bytes as a `document` of kind `RULE_TEXT` through
   `DocumentService` (the normal ingest path, so pages, text and audit are identical to an upload), then find
   the current approved version for the same jurisdiction and license type that nothing supersedes; if an
   unapproved version already supersedes it → `DRAFT_EXISTS` (detail names it), no new version. Otherwise
   create the version through `RuleVersionService.create` (label `<source label> fetched <yyyy-mm-dd>`,
   `sourceUrl` = the source url, `effectiveFrom` = the check date, `supersedesId` = the approved version or
   null when none exists) so extraction and the change event follow the existing path; write the `CHANGED`
   check with both ids and set `last_content_hash`. The first check of a new source is always `CHANGED`.
   `created_by` of the system's rows is null, exactly like other job-created rows.
5. **Scheduler** `RuleSourceScheduler` next to `JobPoller`, `@Scheduled(fixedDelayString =
   "${lexvay.rule-sources.poll-interval-ms:300000}")`, enabled only when `lexvay.rule-sources.enabled`
   is true (default false; `.env.example` documents `LEXVAY_RULE_SOURCES_ENABLED`). It runs under the
   worker flag like the poller and enqueues one `CHECK_RULE_SOURCE` job per due source in a single
   `INSERT ... SELECT` statement: active, `last_checked_at IS NULL OR last_checked_at < now() -
   check_interval_hours * interval '1 hour'`, and no QUEUED or RUNNING job for the same source.
6. **API** (`/api/v1`): `POST /rule-sources` (ADMIN, REVIEWER) body `{ jurisdictionCode, licenseType, label,
   url, checkIntervalHours }` → 201 and an enqueued first check; `GET /rule-sources` (any role) one statement
   with a lateral join to the latest check; `GET /rule-sources/{id}/checks?limit=` (any role);
   `POST /rule-sources/{id}/check` (ADMIN, REVIEWER) → 202 `{ jobId }` (409 when a check is already queued or
   running); `PUT /rule-sources/{id}/active` (ADMIN) body `{ active }`. Every mutating endpoint audited and
   added to `Rule9_AuditCoverageTest`.
7. **Seed**: `seed/manifest.yml` gains one fictional source per demo tenant pointing at a `file` URL? No:
   `JdkHttpFetcher` refuses non-http schemes. The seed registers one source per tenant with
   `https://example.invalid/rules/colorado-lpc.pdf` and no check (sources are inert until the scheduler is
   enabled); the demo stays offline.
8. **Tests**: fake-fetched UNCHANGED → no document, no version, `last_checked_at` moved; CHANGED → document,
   unapproved version superseding the approved one, EXTRACT job queued, change event OPEN; DRAFT_EXISTS when
   a draft already supersedes; FETCH_FAILED on 500 and on a body over the cap, job DONE not FAILED; whitespace
   and markup-only changes are UNCHANGED (HTML fixture); SSRF guard refuses `127.0.0.1`, `10.0.0.1`,
   `169.254.1.1`, `[::1]`, `ftp://`; scheduler enqueues due sources once (no duplicate while QUEUED); RLS
   isolation on both tables; audit coverage. Nothing in this item can approve a version (assert
   `requirements_approved_at IS NULL` after a CHANGED run).

## F13 — Work queue API (backend only; slot 9; after WS-PERF merges; no schema change unless EXPLAIN demands, then `V014__work_queue_indexes.sql`)
Purpose: a reviewer, signer, contributor or administrator should open the product and see what needs them,
across programs and states, without visiting each overview. One read endpoint, one SQL statement, under the
read budget. Decisions D-190 to D-199 in `DECISIONS.md (F13)`.
1. `GET /api/v1/work-queue` → `{ generatedAt, counts: { <kind>: n }, items: [...] }`. Each item:
   `{ kind, programId, programName, jurisdictionCode, count, oldestAt, ids }` where `ids` holds the handful of
   record ids the UI needs to navigate (assessment id, rule version id, change event ids), never more than
   20 per item. Kinds, visible per role:
   - `REQUIREMENTS_TO_REVIEW` (REVIEWER, SIGNER): per program × jurisdiction, current (D-066) mappings of the
     latest FINISHED assessment with no decision or a last event of `REOPENED`, excluding stale ones. The
     predicate is the one in `MappingRepository.countUndecided`; move it into a shared SQL constant in
     `MappingRepository` and use it from both places (no second copy).
   - `EVIDENCE_REQUESTS_OPEN` (CONTRIBUTOR, REVIEWER, SIGNER, ADMIN): mappings whose latest review event is
     `EVIDENCE_REQUESTED`, per program × jurisdiction; stale ones are counted separately in `ids.blockedCount`
     (follow-up from F4: they cannot be answered until the change is re-run).
   - `RULE_VERSIONS_TO_APPROVE` (REVIEWER, SIGNER): unapproved rule versions with at least one requirement,
     per jurisdiction (program fields null). `EXTRACTION_FAILED` (ADMIN, REVIEWER): rule versions whose
     EXTRACT_REQUIREMENTS job is FAILED and which have no requirements.
   - `CHANGES_OPEN` (REVIEWER, SIGNER, ADMIN): OPEN change events, per kind, with the affected programs.
   - `READY_TO_SIGN` (SIGNER): program × jurisdiction where the latest assessment is FINISHED, the undecided
     count is zero, and no SIGNED determination exists for that assessment and jurisdiction. This must agree
     exactly with what `POST /determinations` accepts; the test proves it by signing every READY_TO_SIGN item
     and asserting no 409, then asserting the item disappears.
   - `ASSESSMENTS_FAILED` (ADMIN): assessments in FAILED status, per program.
   Order: kinds in the order listed above, then `oldestAt` ascending.
2. One statement: CTEs per kind joined with `UNION ALL`, filtered by the caller's role in SQL (bind the role,
   do not branch in Java). Measure with the WS-PERF harness; budget p95 ≤ 40 ms on the seeded tenant. Add an
   index only if `EXPLAIN (ANALYZE, BUFFERS)` shows a sequential scan on `mapping`, `review_event` or `job`
   for this statement; if so, the migration is `V014__work_queue_indexes.sql` and the plan goes in
   `docs/PERF.md`.
3. Also `GET /api/v1/work-queue/count` → `{ total }` for the shell badge, same CTEs, `count(*)` only.
4. Tests: visibility per role (a CONTRIBUTOR never sees `READY_TO_SIGN`); counts move when a reviewer
   accepts, requests evidence, confirms a gap, when a contributor answers, when a change event opens and is
   re-run, when a determination is signed; `READY_TO_SIGN` parity with the signing endpoint as above; RLS
   (another tenant's items never appear); the perf-tagged latency test; `ForbiddenWordsTest` stays green (the
   kinds above contain no forbidden words). Read-only endpoint, so nothing to add to the audit coverage set.

## F8 — Determination register (slot 2; Opus 5.5; backend and frontend)
Purpose: an institution must be able to say, for each program, in which states it has determined the curriculum
meets the educational requirements for licensure, in which it has determined it does not, and in which it has made
no determination (34 CFR 668.43(a)(5)(v)). Today signed determinations are visible one program and state at a time.
The register is the tenant-wide record of every signed determination, exactly as signed, with the versions it
names, and a per-program view grouped into the three regulatory lists. It reports what people signed; it never
infers an outcome (product rule 1), and absence of a signature is shown as absence, not as an outcome. Decisions
D-270 to D-279 in `docs/decisions/F8.md`.
1. **Read API** `GET /api/v1/determinations/register?programId=&jurisdiction=&outcome=&current=true|false`
   (any role; `current` defaults to true = status `SIGNED`; false includes `REOPENED`): rows `{ determinationId,
   programId, programName, licenseType, catalogYear, jurisdictionCode, jurisdictionName, outcome, status,
   signedBy, signedByName, signedAt, ruleVersionId, ruleVersionLabel, ruleEffectiveFrom, versionsHash,
   assessmentId, courseVersionCount }`, ordered by program name, jurisdiction code, signed time descending. One
   statement, `@Transactional(readOnly = true)`, added to `EndpointLatencyTest` (40 ms p95) and
   `ReadPathConnectionsTest`, rows in `InputValidationTableTest` (bad UUID, bad outcome, bad jurisdiction pattern).
2. **Program disclosure view** `GET /api/v1/programs/{id}/determination-lists` → `{ program, generatedAt,
   determinedToMeet: [...], determinedNotToMeet: [...], noDeterminationMade: [...] }`. Each entry: jurisdiction
   code and name plus either the current signed determination's id, signer, date, rule version label and versions
   hash, or, in `noDeterminationMade` only, `reason`: `SIGNED_NO_DETERMINATION` (a signed NOT_DETERMINED outcome) or
   `NO_CURRENT_SIGNATURE` (a target jurisdiction of the program with no current SIGNED determination, including
   reopened ones; then `reopenedDeterminationId` when one exists). Every target jurisdiction of the program appears
   in exactly one list; jurisdictions that are not targets never appear. One statement. Test: the three lists
   partition the targets exactly, on seeded data and on a fixture with each case.
3. **CSV export** `GET /api/v1/determinations/register.csv` with the same filters: RFC 4180 (CRLF, quotes doubled,
   every field quoted), UTF-8 with a header row, columns in the order of point 1 with human labels, and
   spreadsheet-formula protection (a cell that starts with `=`, `+`, `-`, `@`, tab or carriage return is prefixed
   with `'`). `Content-Disposition: attachment; filename="lexvay-determination-register-<yyyy-mm-dd>.csv"`. Built in
   memory from the same query (the register is bounded by programs × jurisdictions); one helper `CsvWriter` in
   `common/` with unit tests (quoting, CRLF, formula prefix, non-ASCII). Exporting signed records is audited:
   `REGISTER_EXPORTED` with the filters and the row count (add the endpoint to `Rule9_AuditCoverageTest` even
   though it is a GET, and say why in D-27x).
4. **Screen** `/register` (top-level, inside the shell; rail entry "Determination register" with
   `phosphorBookBookmark`, shown above the program entries as a tenant-wide item; reachable without choosing a
   program). Two tabs (PrimeNG Tabs):
   - **Register**: filters (program Select, jurisdiction Select, outcome Select with the three outcome labels used
     in signing, "Include reopened" ToggleSwitch), a PrimeNG Table with sortable columns, outcome as Tag with icon
     and text, status Tag (Signed, Reopened with `phosphorArrowCounterClockwise`), versions hash in mono truncated
     to 12 with a copy button (`phosphorCopy`, accessible name "Copy versions hash", toast "Copied"), row click
     opens the program's Evidence report for that jurisdiction. "Download CSV" button (`phosphorDownloadSimple`)
     with the current filters.
   - **By program**: program Select, then three sections titled exactly "Determined to meet", "Determined not to
     meet", "No determination made", each a short table (jurisdiction, signer, date, rule version) with an empty
     state sentence; entries with `NO_CURRENT_SIGNATURE` read "No current signed determination" (and "Reopened on
     <date> after a change" when a reopened id is present), entries with `SIGNED_NO_DETERMINATION` read "Signed
     as no determination made". A short paragraph above the sections states that the lists show signed records
     only and that the institution's published disclosure is its own responsibility. "Print" button opens the
     browser print dialog.
   - **Print stylesheet**: `@media print` hides the shell, rail, filters and buttons; shows a header with tenant
     name, program, "Generated <date time>", and keeps the fictional-data notice for demo tenants; tables do not
     split rows across pages; black on white regardless of theme.
   The UI never uses the words "compliant" or "meets requirements" (`ForbiddenWordsTest`); outcome labels come
   from the existing `labels.ts`.
5. **Tests**: backend as listed; Vitest for the grouping view model and the CSV link builder; Playwright
   (`frontend/e2e/register.spec.ts`, slot 2): sign California in the seeded program, open the register, see the
   row, filter by outcome, download the CSV and assert its header and the California row, open By program and
   see California under the right list and every other target state under "No determination made" with the
   right reason, emulate print media and assert the shell is hidden; axe in both themes at 1280 and 400 px.
Done when: `LEXVAY_E2E_SLOT=2 make test` green, `make perf` green, screenshots of both tabs in both themes and of the
print view in `docs/screens/`, `docs/decisions/F8.md` and `docs/progress/F8.md` written.

## OPS-1 — Image build and smoke in CI (after F6 merges; no slot; Sonnet 5.5)
Purpose: F6 ships a Dockerfile and `scripts/smoke.sh`; nothing runs them on every change. Decisions D-240 to D-244
in `DECISIONS.md (OPS-1)`.
1. A third job `image` in `.github/workflows/ci.yml`, after `test` succeeds: build the image with Buildx
   (`docker/setup-buildx-action`, pinned by major tag and recorded with its version in the decision file) using
   the GitHub Actions cache (`cache-from`/`cache-to: type=gha`), tag `lexvay:ci-<sha>`, never push. Then run
   `scripts/smoke.sh` against that tag (the script takes the tag and the slot; slot 0 is free in CI). Fail the
   job on any smoke check failure. Report the image content size in the job summary (`$GITHUB_STEP_SUMMARY`).
2. The job runs on `pull_request` and on `push` to `main` with the same concurrency group as the others.
3. `scripts/smoke.sh` must not need Colima: the Docker socket comes from `DOCKER_HOST` if set, otherwise the
   default; the compose project name carries the slot like `scripts/lib/stack.sh` does (reuse that file).
4. Done when a pull request shows the `image` job green with the smoke output in its log, and
   `docs/OPERATIONS.md` names the job and the tag convention.

## F7c — Search terms proposed at extraction (after F7 and FIX-1 merge; slot 3; migration `V015__requirement_search_terms.sql`)
Purpose: F7 measured what lexical search cannot do: 2 of 6 lexical-gap queries are recalled, because the rule
says "diagnosis of mental and emotional disorders" and the syllabus says "psychopathology". The model already
reads the rule text at extraction; it can propose the vocabulary a syllabus would use, a person can correct it,
and retrieval can search for it. The model proposes terms; it never decides a finding (product rule 1), and a
term never produces evidence by itself: quotes are still verified against the page (rule 2). Decisions D-250 to
D-259 in `docs/decisions/F7c.md`.
1. **Schema**: `requirement.search_terms text[] NOT NULL DEFAULT '{}'` with a CHECK that the array has at
   most 12 elements and each is 1 to 40 characters (`array_length` and a `bool_and` over `unnest`, or a
   helper SQL function `search_terms_valid(text[])` IMMUTABLE). Requirement rows stay immutable after
   approval as today (the trigger is unchanged; terms are edited through the replace endpoint before
   approval, like every other field).
2. **Extraction**: new prompt `extract-v2.system.txt` next to `extract-v1` (kept on disk): same rules plus
   "for each requirement list up to 8 search terms: single words or two-word phrases that a university
   syllabus or degree plan would use for this subject, including synonyms and course-title vocabulary; never
   repeat words already in the requirement text; no state names, no numbers." The schema
   `requirement-extraction.schema.json` gains `search_terms` (array of strings, `maxItems` 8, `maxLength` 40,
   required); `RequirementListParser` trims, lower-cases, de-duplicates, drops terms that already occur in
   the requirement text after `to_tsvector`-style stemming is NOT available in Java, so compare lower-cased
   whole words only, and drops empty results; the replace endpoint accepts `search_terms` optionally
   (absent = empty). `seed/fake-llm.yml` extraction scripts gain plausible `search_terms` for every seeded
   requirement (fictional, hand-written, at least 3 per requirement; the Colorado revision keeps CO-5's terms
   identical so the diff still shows exactly one CHANGED row). `RequirementComparison` ignores
   `search_terms` for evaluation sameness (a terms-only edit is not a requirement change) but reports it as a
   new aspect `SEARCH_TERMS` for the diff view.
3. **Retrieval**: `PassageSearch.search` receives the terms with the title and text; in the BM25 statement the
   term lexemes come from `to_tsvector('english', array_to_string(:searchTerms, ' '))` with boost 1.0 and are
   unioned with the title and text lexemes (a lexeme present in several sources keeps the highest boost).
   The `ts_rank` ranker ORs them in the same way. The reviewer search is untouched.
4. **Evaluation**: `eval/retrieval/queries.yml` gains `searchTerms` per query (hand-written, fictional,
   written before looking at the expected passage, 3 to 8 per query; the file header says so). `make eval`
   reports a third column "BM25 + terms" for both the counted set and the lexical-gap set; thresholds: the
   counted-set recall@8 and recall@3 must not fall below "BM25" alone, and the lexical-gap recall@8 must be at
   least 4 of 6. `docs/RETRIEVAL.md` shows all three columns. The pipeline metrics on the seed must be unchanged
   (0 false EVIDENCE_FOUND).
5. **Screens**: the F3 requirement grid gets a "Search terms" cell (PrimeNG Chips or AutoComplete in multiple
   mode, max 12) shown and editable under the same rules as the other cells; the Review screen's "How it was
   checked" names the terms that matched when the citation came from a page found only through terms
   (`Passage.matchedVia`: TITLE, TEXT or TERMS, carried into the mapping rationale as a sentence). Frontend
   work is a separate small item if F3 has merged before this starts; otherwise it is folded into F3.
6. **Tests**: parser (dedupe, drop words in text, limits), schema validation rejects 9 terms, BM25 ordering with
   a term that only occurs in the page title, comparison ignores terms for carry-over but the diff reports
   `SEARCH_TERMS`, replace endpoint stores terms and rejects 13, extraction with the fake model stores the
   scripted terms, the lexical-gap threshold in `make eval`, `ForbiddenWordsTest` green.

## Follow-ups recorded from reviews
- F3b: in the edit grid the title field stays about 7 rem wide even when the row has room; give title the
  remaining width (`minmax(10rem, 1fr)`) while ref, type and check keep fixed widths. The initial bundle is 698.9 kB
  raw, 1 kB under the warning: any new component in the shared preset must move another Rules-only or
  screen-only token set out first (D-246 pattern).
- Merge round of 2026-11-26 (owner asked for every open PR to be merged): the orchestrator integrated #20, #18, #21,
  #17, #22 and #23 on one tree, ran the full suite (788 backend, 70 frontend unit, 13 end-to-end) and merged them
  in that order with the recorded conflict resolutions. Fixes made in that round: fetched HTML is served as plain
  text (`FileType.HTML`), `Passage` carries F7's excerpt and rank and F11's OCR fields together, one
  `documentPage` method in `ApiService`, `RetrievalFileTest` builds a `TextExtractor`, and the database
  healthcheck runs over TCP (first-time role creation could finish after a socket check passed).
- Bundle: the initial bundle is 703.7 kB raw after F3, over the 700 kB warning budget (error at 800 kB). Before the
  next screen lands, defer the rule workspace's edit grid and dialogs (`@defer`) and check what the Listbox,
  DatePicker and Accordion token sets add to the preset.
- F13 decisions to settle: `ASSESSMENTS_FAILED` should list only failures newer than the program's latest finished
  assessment; an open evidence request on a signed mapping stays in `EVIDENCE_REQUESTS_OPEN` although answering
  it returns 409 (hide it, or show it as blocked like stale ones); `AssessmentRepository.findingCounts` should use
  `MappingRepository.NEEDS_DECISION`; `MappingRepository.CURRENT` is quadratic in the mappings of one assessment.
- Worktrees for integration work must live under the repository (Colima shares only the home directory with its
  VM; a bind mount from `/private/tmp` is empty inside the container).
- TEST-1: the end-to-end backend runs FakeLlmClient without the extraction and match scripts that only the seed
  process loads, so a rule version entered in the browser always ends in "Extraction failed". Load
  `seed/fake-llm.yml` into the e2e backend (profile `e2e`, same loader as `SeedLoader`) and extend F3's e2e test
  so a contributor's new version reaches *Needs approval* through extraction.
- F1: check by hand that Chrome, Firefox and Safari render a PDF opened through "Open original" while the
  response carries the app's CSP (`object-src 'none'`); if a browser refuses, give `GET /documents/{id}/file` its own
  CSP (`default-src 'none'; frame-ancestors 'none'`) in `SecurityHeadersFilter`. The OCR note in the viewer is covered
  only by unit tests until the seed has a scanned document (add one to `seed/` with F7c or later).
- F12: a failed check waits a full interval (up to 30 days) before the scheduler retries; a shorter backoff
  needs a `next_check_at` column. No screen lists rule sources yet; add a Sources tab to the F3 rule workspace.
- F12 deployment note: the SSRF guard resolves the host before connecting and the JDK client resolves it
  again, so a DNS-rebinding window remains; production egress must also block private ranges (Terraform).
- WS-UI: the overview still finds the latest assessment through the audit log; switch it to the WS-PERF
  `GET /programs/{id}/overview` endpoint (do this in F1). Audit filtering is client-side over the newest 500
  entries; switch to the WS-PERF server filters (F5). Add a build check that the production `index.html` has no
  inline script (F6 or F1). PrimeNG's own dialog-close and select-chevron SVGs are not Phosphor.
- WS-PERF: `GET /documents/{id}` and the rule version views look up job status by scanning the `job` table under
  RLS because `jsonb ->>` is not leakproof and the payload expression indexes are ignored. FIX-2: add a generated
  `payload_entity_id uuid` column on `job` (from the payload's entity id), index it, and compare with `=`. Decided
  by the orchestrator when scheduled; spec to follow.
- F11: OCR of a 200-page scan runs inside the job's single transaction and a retry redoes every page. Follow-up:
  OCR per page in its own transaction (or a job per page) inside `JobRunner`.
- F11 spec change (decided during review): a PDF page's text layer is never discarded; OCR replaces it only when
  the layer is under 20 characters and OCR yields at least 20.
- WS-SEC: every new endpoint must add a row to `InputValidationTableTest` (its `everyEndpointHasARow` guard fails
  otherwise). The PDF page cap (500) is enforced at ingest, so an oversized PDF fails its job rather than the
  upload; a synchronous page count at upload is a possible follow-up. Form login stays enabled in the `oidc`
  profile; an SSO-only switch is a follow-up. Behind a load balancer set `LEXVAY_FORWARD_HEADERS=native`.
- WS-EVAL: the live thresholds in `eval/thresholds.yml` (recall 0.80, extraction 0.90, evidence agreement
  0.80) are unmeasured first values; set them from the first `make eval-live` run once a model key exists.
  Terraform still needs a remote state backend, WAF and flow logs before any real deployment, and the
  database roles must be bootstrapped with `db/initdb/01-roles.sh` after RDS is created.
- `scripts/eval.sh` repeats the slot and cleanup lines of `scripts/e2e.sh`; extract `scripts/lib/stack.sh`
  and source it from both (do this inside F7, which already touches the evaluation scripts).
- The old Review screen logs `TypeError ... reading 'id'` at `review.ts:82`; verify it is gone after WS-UI.
- Tests that need the seeded demo data currently use three different approaches (`SeedLoaderTest` loads the
  shared tenants; F3a copies the manifest under another tenant name; F9 ingests the PDFs through the upload
  API). After WS-EVAL makes `SeedLoader` load into a uniquely named tenant, give tests one shared fixture
  for "a fresh tenant with the demo program" and delete the other two approaches.
- F9 reported its end-to-end run on backend port 8084 although it was assigned slot 8; check that agents
  export `LEXVAY_E2E_SLOT` in the same shell as `make test`.
- F1 viewer: search headlines are computed excerpts and cannot be mapped to character offsets; opening a
  search hit shows the page and highlights the query words in the browser.
- F4 screens: an OPEN evidence request on a stale mapping cannot be answered (409). Add `stale` to the
  evidence request row and show such rows as blocked until the change is re-run.
- F3 screens: any authenticated role may read a diff; unapproved lists can be diffed (useful before approval).

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
