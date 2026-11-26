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
| F1 Evidence viewer with in-context highlight | `feat-evidence-viewer` | 1 | Opus 5.5 | queued (after WS-UI, WS-PERF) |
| F2 Program setup workspace | `feat-setup-workspace` | 2 | Sonnet 5.5 | queued (after WS-UI) |
| F3 Rule workspace and requirement diff | `feat-rule-workspace` | 3 | Sonnet 5.5 | queued (after WS-UI) |
| F4 Evidence requests | `feat-evidence-requests` | 4 | Sonnet 5.5 | queued |
| F5 Administration and audit tools | `feat-admin-audit` | 5 | Sonnet 5.5 | queued |
| F6 Production packaging and observability | `ops-packaging` | 6 | Sonnet 5.5 | queued |
| F7 Matching quality | `match-quality` | 7 | Sonnet 5.5 | queued (after WS-EVAL) |

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

---

## Queued items (specs are written by the orchestrator when the item is started)
- **F1 Evidence viewer**: page text in the right pane with the quote highlighted in context, previous and
  next page, open original file; uses the document page endpoint.
- **F2 Program setup workspace**: create program, courses, upload documents with ingest status, record
  program facts by selecting the quote in the document.
- **F3 Rule workspace**: enter rule text, review and edit the extracted requirement list, approve; requirement
  level diff between rule versions (match by ref; added, removed, changed, unchanged; word-level diff).
- **F4 Evidence requests**: "Request evidence" creates a task for contributors; responding with a document
  re-runs matching for that requirement only.
- **F5 Administration and audit tools**: user management for admins, audit filters and CSV export.
- **F6 Production packaging and observability**: container image, structured logs with request ids,
  metrics, per-assessment cost meter.
- **F7 Matching quality**: sub-page passages with `ts_headline` context, ranking improvements measured by
  the evaluation harness, prompt caching for the system prompt.
