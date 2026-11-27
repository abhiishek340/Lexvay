# Lexvay — Roadmap and work items

Owned by the orchestrator (CTO). Agents implement one work item each, exactly as specified, on the named
branch, and open a pull request. Status values: `queued`, `in progress`, `in review`, `merged`.

## Owner decisions needed
- **PrimeUI license key (D-080) — now the single biggest visual defect.** The design v2 work cannot hide it.
  **PrimeUI license key (D-080).** PrimeNG 22 is distributed under the PrimeUI License and verifies a key offline.
  Without one every page shows an "Invalid PrimeUI License" badge. The Community License is free for individuals
  and organisations under USD 1 M revenue with fewer than 5 developers (renewed yearly); register at
  https://primeui.dev, claim the key, and pass it at build time: `ng build --define
  "LEXVAY_PRIMEUI_LICENSE='<key>'"` (never commit it). Alternative if declined: Angular 21 with PrimeNG 21 (MIT),
  a framework downgrade the orchestrator does not recommend.

## Design system v3 "Ledger" (owner directive 2026-11-27: "not AI slop", professional, human craft)
The owner reviewed v2 on the running app and rejected it: yellow and orange accents, icon-heavy pills and cards, a
palette and card art that look generated. `docs/DESIGN.md` is rewritten as v3 and is the contract. Every screen
moves to it in four items. UI3-0 goes first. UI3-1, UI3-2 and UI3-3 start when UI3-0 merges and run in parallel.
F12b and any later screen are built in v3.

### UI3-0 — Foundation, shell, sign-in and overview (slot 1; Opus 5.5; D-480 to D-499)
1. **Tokens and preset v3:**
   - The colour table, radius, elevation, spacing and type scale exactly as in DESIGN.md. Remove every v2 token
     that has no v3 equivalent (`ambiguous`, `accent-tint`, `seal`, e1/e2/e3, `--lx-spring`, `--lx-t-hero`, ...).
   - Restyle all PrimeNG component tokens the app uses (button, inputtext, select, multiselect, dialog, popover,
     menu, tabs, datatable, toggleswitch, selectbutton, tooltip, toast, accordion, autocomplete, fileupload,
     metergroup, confirmdialog) to v3. Keep the per-chunk token-set pattern.
   - `tokens.spec.ts` proves AA over the new table.
2. **Source scan:** a unit test that fails on:
   - any colour literal (hex, `rgb(`, `hsl(`, named colours) in `frontend/src` outside `theme/`;
   - the retired names (`ProgressRing`, `StateMap`, `lx-stat`, `count-up`, `p-tag`, `p-chip`, `border-radius: 999`,
     `9999px`, `backdrop-filter`, `duotone`).
3. **Shared components:**
   - New: `ui/status-mark.ts` (the mark table), `ui/meter.ts`, `ui/figure.ts`, `ui/label.ts` and `ui/help.ts`
     (one popover per screen).
   - Redraw `ui/seal.ts` as line art.
   - Delete `ui/state-map/`, `progress-ring.ts`, `stat.ts`, `count-up.ts`, `status-icon.ts` and `info-tip.ts` with
     their tests, after moving every caller. Moving callers is part of this item: every screen must compile and
     pass its tests with the replacements. Use the plain v3 equivalent (Meter, a mark plus word, Label or plain
     text, Help), not a redesign of that screen. The full screen passes are UI3-1 to UI3-3.
   - `DecisionBar`: no blur, no key chips.
4. **Shell:** top bar, rail, bottom tab bar and More menu exactly as DESIGN.md says. The "Demo data" pill becomes meta
   text; count pills become `tnum` meta text; the active nav item uses the left rule. Programmatic focus on the page
   title draws no ring.
5. **Sign in:** one centred column as DESIGN.md says; the map story and headline panel go.
6. **Program overview:** the title row, Figure row, States table and Needs you list as DESIGN.md says. This is the
   reference screen for the others, so its spacing must be exact: 40 px page padding, 40 px between sections with
   hairlines, 24 px from title row to figures, table rows 40 px.
7. **Motion:** replace the v2 motion tokens and keyframes with the v3 ones. Delete the staggered entrances, ring
   sweeps, seal stamp and sign-in story. The route transition becomes the 180 ms fade with a 4 px rise. Keep FIX-8's
   `data-transition="none"` mechanism if it has merged.
8. **Tests and evidence:**
   - Unit tests for StatusMark (every status maps to shape and word), Meter (segments to scale, text) and the source
     scan.
   - Update e2e selectors that depended on removed components; axe on every screen in both themes.
   - Screenshots `ui3-0-*` of sign-in, the overview, the shell with the rail expanded and collapsed, and the 400 px
     tab bar, all in both themes. Also retake one screenshot of every other screen as it looks after the caller
     moves, so the orchestrator sees the starting point for UI3-1 to UI3-3.
   - The initial bundle must shrink; report before and after.

### UI3-1 — Review and Coverage in v3 (slot 2; Opus 5.5; after UI3-0; D-500 to D-509)
Review as DESIGN.md describes:
- three hairline columns;
- the state switcher as an underlined tab row with "3 of 8" text;
- the filter as a text tab row with counts;
- the requirement column with the rule text set on a left rule;
- Finding as a section;
- the decision footer.

Then the signing dialog: the three outcomes as a radio list with one line each, and the line-art Seal preview.
Coverage: the matrix on `surface` with the v3 marks, flags as text with marks, the filter as a text tab row.
Screenshots `ui3-1-*`.

### UI3-2 — Changes, Audit trail, Evidence report, Register and People in v3 (slot 3; Opus 5.5; after UI3-0 and F14; D-510 to D-519)
- Changes and Audit trail: plain day-grouped lists of one-sentence rows.
- Evidence report: the 816 px sheet, with actions in the title row; F14's check dialog restyled.
- Determination register and People: tables under a title row and meta line.
- The setup-password page in the sign-in column style.

Screenshots `ui3-2-*`.

### UI3-3 — Rules, Requests and Program setup in v3 (slot 4; Opus 5.5; after UI3-0 and UI2-4; D-520 to D-529)
- Rules: version list and detail with hairlines; the diff in Document type with `found` and `missing` underlines
  (no tinted blocks).
- Evidence requests: a table, with the answer drawer as an overlay sheet.
- Program setup: the numbered text step list on the left and plain forms on the right.

Screenshots `ui3-3-*`.

## Design system v2, superseded by v3 (owner directive 2026-11-26: "world best UI", clean, few words, animation)
The contract is `docs/DESIGN.md` v2. Built in five items. UI2-0 lands first; UI2-1 to UI2-4 run in parallel on top of
it. Every item is Opus 5.5, every item's PR carries screenshots of every state it touches in both themes at 1440 and
400 px, and the orchestrator reviews those screenshots before merge. Word budget, motion rules and the forbidden list
in DESIGN.md are part of every spec below.

### UI2-0 — Foundation, shell and sign-in (slot 1; decisions D-301 to D-319)
1. **Fonts**: replace IBM Plex Sans and Serif with `@fontsource-variable/inter` and `@fontsource-variable/source-serif-4`
   (exact versions from the registry, at least 14 days old, recorded in D-002); keep IBM Plex Mono. Font-face with
   `font-display: swap` and metric-matched fallbacks (`size-adjust`, `ascent-override`) so swapping causes no layout
   shift. Remove the Plex Sans and Serif packages.
2. **Preset v2**: rewrite `lexvay-preset.ts` to the token table, elevation, radius and type scale of DESIGN.md v2,
   keeping the `light-dark()` mechanism and `--p-lx-*` names (rename tokens in one pass across all screens; no old name
   survives). PrimeNG components restyled through the preset and pass-through so Button, Select, Table, Tabs, Dialog,
   Drawer, Popover, Tag, Message, Toast, InputText, Password, Textarea, Listbox, Stepper and FileUpload look like
   Lexvay, not Aura. A Vitest contrast test computes WCAG ratios for every text/background pair in the token table in
   both themes and fails under 4.5 (3.0 for large text and icons).
3. **Motion**: `styles.css` motion tokens; utilities `lxEnter` (applies `animate.enter` with a stagger index),
   `lxCountUp` directive (Web Animations, final value under reduced motion), and the router configured with
   `withViewTransitions()` plus the route transition of DESIGN.md (skip the transition when reduced motion is set or the
   browser lacks the API). One `MotionPreferences` service that every component reads.
4. **Shared components** in `frontend/src/app/ui/`: `StateMap` (with the 51-tile layout constant, roving tabindex,
   popover, three sizes, wave entrance; unit tests for layout completeness and keyboard order), `ProgressRing`, `Stat`,
   `InfoTip`, `StatusIcon` (mapping table from finding and decision, unit-tested), `Seal`, `CommandPalette` (pure
   fuzzy matcher with tests; sources: routes, states of the current program, requirements of the latest assessment,
   role-allowed actions; Ctrl+K / Cmd+K; recent items in `localStorage` per user).
5. **Shell v2**: top bar, rail and avatar menu exactly as DESIGN.md "Shell"; the fictional-data strip becomes the "Demo
   data" pill; the work queue button uses `GET /work-queue/count` (badge) and `GET /work-queue` (popover, at most 8
   items, each linking to its screen). Keyboard shortcuts dialog lists Ctrl+K.
6. **Sign in v2** as DESIGN.md "Screens"; the demo account chips appear only when `/session` reports `demo: true`.
7. Every existing screen must still work and pass axe after the token rename; screen layouts are not redesigned in
   this item (UI2-1 to UI2-4 do that), only made to use the new tokens and components where a v1 element is removed
   (the theme control, the notice strip).
8. Tests: unit tests above; e2e: sign-in story stops on typing and is absent under reduced motion (emulate media),
   command palette opens with the shortcut and jumps to Review for a state, work queue popover lists items, theme from
   the avatar menu persists, axe in both themes at 1440 and 400 px on sign-in and overview.
Done when `LEXVAY_E2E_SLOT=1 make test` is green, the initial bundle stays under the warning budget (fonts are not in
the JS bundle), and the PR has screenshots of sign-in, the shell, the command palette and the work queue popover.

### UI2-1 — Program overview and determination register (slot 2; after UI2-0; D-320 to D-329)
Overview exactly as DESIGN.md: Display title with pills, hero panel with StateMap `hero` and three Stats, "Needs you"
from the work queue (program-scoped), state cards with segmented rings and one stage-dependent action; the shared
element from a tile to Review's state header (`view-transition-name`). Register: StateMap `compact` coloured by
current outcome above the table, table restyled, By program view as three clean lists with Seals; print view
unchanged in content. Remove every explanatory paragraph into InfoTips. Tests: e2e for the map's keyboard navigation
and popover action, the shared-element navigation (assert URL and heading; the transition itself is visual), axe.

### UI2-2 — Review and signing (slot 1 after UI2-0 merges; D-330 to D-339)
Review exactly as DESIGN.md: state pills with mini rings, header progress ring, list rail with StatusIcon, requirement
hero card, DocumentViewer on the right, DecisionBar with in-place gap note, decision motion sequence, "Sign <state>"
header action, signing dialog with outcome cards and the Seal stamp moment. Keep every keyboard shortcut and the live
region. Tests: existing review e2e updated to the new accessible names only where they change, decision sequence under
reduced motion (no waiting on animation), axe.

Also in UI2-2: `DocumentViewer` reflows PDF line breaks for reading. A newline whose next character is a lower-case
letter, a digit or an opening parenthesis, and whose previous character is not sentence-final punctuation, renders as
a space; other newlines stay. Display-only and one character for one character, so stored offsets, the highlight and
selection offsets are unchanged (one pure function `softWrap(text)` with Vitest tests proving equal length and that
selection offsets map back to the stored text exactly).

### UI2-3 — Changes, audit trail and evidence report (slot 7; after UI2-0; D-340 to D-349)
Changes as a timeline of cards; audit as a day-grouped timeline with avatar initials and one-sentence entries (the
sentence built by one pure, tested function per action); evidence report as a sheet with the Seal or Draft stamp and a
floating "Download PDF" pill. Tests: e2e for re-run from a timeline card, audit filter chips, report download; axe.

### UI2-4 — Rules, evidence requests and program setup in v2 (slot 4; after UI2-0 and F4; D-350 to D-359)
Restyle the Rules workspace and F4's evidence request screens to v2 (word budget, InfoTips, cards, motion; requests
with `signed: true` from FIX-3 show as blocked like stale ones), and build
F2 (program setup) directly in v2 as specified in the F2 section with the v2 components (Stepper restyled, StateMap
`mini` for choosing target states). Tests as in F2 plus axe on every Rules and Requests state.

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
| FIX-2 Job entity id column and index | `fix-job-entity-index` | 5 | Sonnet 5.5 | merged (PR #27) |
| F1 Evidence viewer with in-context highlight | `feat-evidence-viewer` | 1 | Opus 5.5 | merged (PR #21) |
| F2 Program setup workspace | `feat-setup-workspace` | 3 | Opus 5.5 | folded into UI2-4 (built directly in v2) |
| F3 Rule workspace screens (uses F3a) | `feat-rule-workspace` | 10 | Opus 5.5 | merged (PR #22); review fixes in F3b |
| F4 Evidence request screens (uses F4a) | `feat-evidence-requests` | 4 | Opus 5.5 | merged (PR #28) |
| F5a User administration and audit export API | `feat-admin-api` | 5 | Sonnet 5.5 | merged (PR #32) |
| F5b Administration screens (v2) | `ui2-admin` | 2 | Opus 5.5 | merged (#44) |
| F7 Retrieval quality (BM25 ranking, excerpts, retrieval metrics) | `retrieval-quality` | 3 | Sonnet 5.5 | merged (PR #17) |
| F3b Rule workspace review fixes (edit layout, error text, unsaved-changes guard, viewer reuse) | `feat-rule-workspace-fixes` | 10 | Opus 5.5 | merged (PR #24) |
| TEST-1 Scripted fake model in the end-to-end backend | `test-scripted-e2e-model` | 11 | Sonnet 5.5 | merged (PR #25) |
| PERF-FE-1 Icons and theme tokens per chunk | `perf-frontend-chunks` | 6 | Sonnet 5.5 | merged (PR #29); remaining cuts in UI2-0 |
| UI2-0 Design v2 foundation, shell and sign-in | `ui2-foundation` | 1 | Opus 5.5 | merged (PR #34) |
| UI2-1 Overview and register in v2 | `ui2-overview-register` | 2 | Opus 5.5 | merged (PR #39) |
| UI2-2 Review and signing in v2 | `ui2-review` | 1 | Opus 5.5 | merged (#37) |
| UI2-3 Changes, audit and report in v2 | `ui2-changes-audit-report` | 7 | Opus 5.5 | merged (PR #38) |
| UI2-4 Rules, requests and setup in v2 (includes F2) | `ui2-rules-requests-setup` | 4 | Opus 5.5 | in progress |
| FIX-3 Work queue and evidence request follow-ups | `fix-queue-followups` | 9 | Sonnet 5.5 | merged (PR #31) |
| FIX-4 Rule source backoff, live search terms, PDF page cap at upload | `fix-backoff-terms-pages` | 7 | Sonnet 5.5 | merged (PR #33) |
| SEC-2 Argon2id passwords and audit list index | `sec-argon2-audit-index` | 5 | Sonnet 5.5 | merged (PR #36) |
| FIX-5 Logout audit actor, work queue predicate | `fix-logout-actor` | 11 | Sonnet 5.5 | merged (PR #40) |
| F7b Answer stability and prompt caching | `match-stability` | — | Sonnet 5.5 | queued (needs a live model key to measure) |
| F7c Search terms proposed at extraction | `search-terms` | 3 | Sonnet 5.5 | merged (PR #30); screen cell in UI2-4 |
| OPS-1 Image build and smoke in CI | `ops-ci-image` | — | Sonnet 5.5 | merged (PR #20) |
| F8 Determination register | `feat-determination-register` | 2 | Opus 5.5 | merged (PR #26) |
| F10a Coverage matrix API | `feat-coverage-api` | 8 | Sonnet 5.5 | merged (PR #35) |
| F10b Coverage matrix screen (v2) | `ui2-coverage` | 7 | Opus 5.5 | merged (#41) |
| FIX-6 OCR checkpoints per page | `fix-ocr-checkpoints` | 5 | Sonnet 5.5 | merged (#42) |
| F12b Rule sources in the Rules workspace (v3) | `ui3-rule-sources` | 5 | Opus 5.5 | queued (after UI3-3) |
| FIX-7 One latest-finished ordering | `fix-latest-finished` | 6 | Sonnet 5.5 | merged (#45) |
| F14 Check a report file | `feat-report-check` | 3 | Opus 5.5 | in progress |
| INFRA-2 Remote state, WAF, flow logs, egress | `infra-hardening` | — | Sonnet 5.5 | in progress |
| FIX-8 No console errors | `fix-console-errors` | 7 | Sonnet 5.5 | in progress |
| UI3-0 Design v3 foundation, shell, sign-in, overview | `ui3-foundation` | 1 | Opus 5.5 | in progress |
| UI3-1 Review and Coverage in v3 | `ui3-review-coverage` | 2 | Opus 5.5 | queued (after UI3-0) |
| UI3-2 Changes, audit, report, register, people in v3 | `ui3-records` | 3 | Opus 5.5 | queued (after UI3-0, F14) |
| UI3-3 Rules, requests, setup in v3 | `ui3-rules-requests-setup` | 4 | Opus 5.5 | queued (after UI3-0, UI2-4) |
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

## F2 — Program setup workspace (slot 3; Opus 5.5; frontend with small backend additions; after F4 merges)
Purpose: today a program can only be created by the seed or by hand through the API. A university's contributor
must be able to set one up in the product: the program and its target states, its courses and their catalog-year
versions, the syllabi and other documents, the program facts the rules engine needs (each fact backed by a quote
selected on its page, product rule 2), a readiness check, and the first assessment with visible progress. The same
workspace stays the place to add a course version or a document later. Decisions D-285 to D-294 in
`docs/decisions/F2.md`.
1. **Routes**: `/programs/new` (CONTRIBUTOR, ADMIN; "New program" button on the Programs home screen and an entry
   in the program switcher's footer) and `programs/:programId/setup` ("Program setup" in the rail, icon
   `phosphorWrench`, after Program overview; visible to every role, editable for CONTRIBUTOR and ADMIN, read-only
   otherwise). Both render the same `SetupWorkspace` with a PrimeNG Stepper: Details → States → Courses → Documents
   → Program facts → Readiness. Steps are freely navigable once the program exists; on `/programs/new` only Details
   is enabled until the program is created, then the URL becomes `programs/:id/setup` (replace, no extra history
   entry). Each step shows a short status in its header (for example "6 courses", "2 documents still being read",
   "1 fact missing").
2. **Details and States** (create only): name, license type (Select over the license types the tenant's rule
   versions use, plus free text), catalog year, and target states (MultiSelect of jurisdictions with code and name,
   at least one). Submit calls `POST /programs`. After creation both steps are read-only with one sentence: "Name,
   license type, catalog year and states are fixed once the program exists. Create a new program for a different
   combination." (Changing targets later touches signed determinations; it is a separate item, not part of F2.)
3. **Courses**: table of courses (code mono, title, current catalog year, required or elective Tag) from
   `GET /programs/{id}/courses`; "Add course" Dialog (code, title, catalog year, required ToggleSwitch) calls
   `POST /programs/{id}/courses` then `POST /courses/{id}/versions`; row action "New catalog year" opens a Dialog
   (catalog year, required) that posts a version with `supersedesId` = the current version and explains in one line
   that the change opens a change event for every assessment that cited the old version. Server validation errors
   appear under the fields.
4. **Documents**: grouped like F4's picker (course, then "Program documents"), from `GET /programs/{id}/documents`
   (added by F4). Upload with PrimeNG FileUpload in custom mode (no auto upload of hidden fields): per file a kind
   Select (SYLLABUS defaults when uploading into a course group; DEGREE_PLAN, HANDBOOK, ACCREDITATION for program
   documents) and the course version for syllabi; `POST /programs/{id}/documents` per file, sequentially, with a row
   per file showing Uploading → Reading → Ready (n pages) / Failed (server message). Reading status comes from
   `GET /documents/{id}` (its ingest status) polled every 2 s while any file is Reading and the tab is visible; a
   duplicate (409 or the server's duplicate message) is shown as "Already uploaded" with a link to the existing
   document. Each document row opens the F1 `DocumentViewer` in a Drawer (read-only). Accepted types and the size
   limit come from the server's messages, not a client copy; the input's `accept` lists `.pdf,.docx,.txt`.
5. **Program facts**: one row per `NumericField` the approved rules of the program's states use (TOTAL_CREDITS,
   PRACTICUM_HOURS, INTERNSHIP_HOURS; derive the list from `GET /rule-versions` requirements of approved, current
   versions for the program's states and license type, computed in one pure function with a Vitest test), each
   showing the current fact (value, document, page, quote) from `GET /programs/{id}/facts` or "Not recorded".
   "Record" opens a Drawer: InputNumber for the value, document picker, `DocumentViewer` with `selectable` true; the
   selected passage must contain the number as written (the server verifies the quote; the screen also shows a
   warning, not a block, when the selected text does not contain the digits of the value). Submit
   `POST /programs/{id}/facts`. Facts are immutable records: recording again adds a newer fact; the row shows the
   newest and a "History" disclosure lists earlier ones.
6. **Readiness**: a checklist computed in one pure function (`programReadiness(program, courses, documents, facts,
   ruleVersions)`, Vitest-tested) with one line per check, each with an icon and text, never colour alone:
   every target state has an approved, current rule version for the license type (list the states that do not,
   linking to Rules); every course's current version has at least one ready document; no document is still being
   read; every numeric field used by those rules has a recorded fact. Blocking checks disable "Run assessment";
   the missing-fact check is a warning (the rules engine then reports those numeric requirements as missing
   evidence, which is correct behaviour, and the line says so). "Run assessment" (CONTRIBUTOR, ADMIN, REVIEWER)
   calls `POST /programs/{id}/assessments` and shows progress from `GET /assessments/{id}` polled every 2 s
   (status, and requirements processed out of total if the view carries them; otherwise status only) until
   FINISHED or FAILED, then a toast and a link to Review.
7. **Backend additions** (only if missing on main when you start; each with tests, a row in
   `InputValidationTableTest`, one connection per read): none are expected beyond F4's documents endpoint. If the
   assessment view has no progress count, do not add one in F2; show status only and record it as a follow-up.
8. **Bundle**: the setup workspace is a lazy route chunk; its PrimeNG components that are not already in the shared
   preset (Stepper, FileUpload, MultiSelect, ToggleSwitch, Drawer if F4 did not add it) register their token sets
   with the F3b pattern (`updatePreset` on first load, D-246). The initial bundle must stay under 700 kB raw; report
   before and after.
9. **Tests**: Vitest for readiness, the numeric-field derivation and the upload queue model; Playwright
   (`frontend/e2e/setup.spec.ts`, slot 3) creating a program "Demo M.A. School Counseling (fictional)" with two
   states that have approved rules in the seed, adding two courses, uploading two fictional syllabi from
   `frontend/e2e/fixtures/` (PDF and DOCX, labelled fictional) and waiting for Ready, recording TOTAL_CREDITS with a
   keyboard-selected quote, seeing readiness turn green, running the assessment and reaching Review; a read-only
   pass as a REVIEWER (no edit controls); axe in both themes at 1280 and 400 px; no request leaves localhost.
Done when: `LEXVAY_E2E_SLOT=3 make test` green; screenshots of every step in both themes in `docs/screens/`;
`docs/decisions/F2.md` and `docs/progress/F2.md` written.

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

## F5a — User administration and audit export API (backend only; slot 5; migration `V015__user_administration.sql`)
Purpose: a tenant's administrator must add colleagues, change their role and remove their access without the seed or
the database, and must be able to hand an auditor the audit trail as a file. Users are never deleted (product rule 9:
every action names who did it). The screens come in v2 later (F5b). Decisions D-370 to D-379 in
`docs/decisions/F5a.md`.
1. **Schema (V015)**: `app_user` gains `active boolean NOT NULL DEFAULT true` and `security_stamp integer NOT NULL
   DEFAULT 0` (updates to `app_user` stay ordinary; there is no immutability trigger on it today; keep it that way).
   New table `user_setup_token` (`id`, `tenant_id`, `user_id` → app_user, `token_hash` text unique (SHA-256 hex),
   `expires_at`, `used_at`, `created_at`, `created_by`), RLS + FORCE + tenant policy as in V002, immutable except
   `used_at` (`restrict_update_to_columns('used_at')`), plus a SELECT and UPDATE policy honouring the existing
   transaction-local `lexvay.auth_lookup` flag so the anonymous setup call can find its token, exactly like
   `app_user`'s `auth_lookup` policy.
2. **Create**: `POST /api/v1/users` (ADMIN) `{ email, displayName, role }` → 201 `{ user, setupCode, expiresAt }`. The
   code is 32 random bytes from `SecureRandom`, base64url, returned once and stored only as its SHA-256; valid 72 hours.
   Email is lower-cased and unique across tenants (409 "An account with this email exists"). Audited `USER_CREATED`
   (email, role; never the code). `POST /api/v1/users/{id}/setup-code` (ADMIN) issues a new code and marks every
   unused earlier code of that user used (audited `USER_SETUP_CODE_ISSUED`).
3. **Set password**: `POST /api/v1/auth/setup` (anonymous, CSRF as for login, counted against the login rate-limit
   budget) `{ code, password }`. Look the token up under `auth_lookup` in one statement; unknown, used or expired →
   400 "This setup code is not valid" (same message for all three). Password 12 to 128 characters and not equal to
   the email (400 naming the rule). Then, under the token's tenant: hash with the application's existing
   `PasswordEncoder`, set `password_hash`, increment `security_stamp`, mark the token used, audit `PASSWORD_SET`
   with the user as actor. Does not sign the user in; the client goes to the sign-in screen.
4. **Role and access**: `PUT /api/v1/users/{id}/role` `{ role }` and `PUT /api/v1/users/{id}/active` `{ active }`
   (ADMIN). An administrator cannot change their own role or deactivate themselves (409), and no change may leave
   the tenant without an active ADMIN (409 "At least one active administrator is required"), checked in the same
   transaction with `SELECT ... FOR UPDATE` on the tenant's active admins so two concurrent changes cannot both
   pass. Each change increments `security_stamp` and is audited (`USER_ROLE_CHANGED` with old and new role,
   `USER_DEACTIVATED`, `USER_REACTIVATED`). `findForLogin` ignores inactive users (the login error stays the generic
   "Email or password is incorrect"); OIDC login refuses them with the existing 403.
5. **Session revocation**: the session principal `LexvayUser` carries the stamp it was issued with. A
   `UserStampCheck` in the existing `TenantContextFilter` path compares it with the current `(active, role,
   security_stamp)` from a per-instance Caffeine cache (TTL 15 s, max 10 000 users, evicted locally on every change
   made by this instance). On mismatch or inactive: invalidate the session and answer 401 problem detail "Your access
   changed. Sign in again." A change made on another instance takes effect within 15 s (record in D-37x and
   `docs/OPERATIONS.md`). The cache lookup is one statement and at most one extra connection checkout per user per
   15 s; `ReadPathConnectionsTest` warms the cache first and still asserts one checkout per read.
6. **Reads**: `GET /api/v1/users` gains `active`, `createdAt` and `passwordSet` (boolean) for ADMIN callers; other
   roles keep today's fields. Ordered by display name.
7. **Audit export**: `GET /api/v1/audit.csv` (ADMIN) with the existing audit filters (`action`, `entityType`,
   `entityId`, `userId`, `from`, `to`), streamed: one read-only transaction, `JdbcClient` query with fetch size 500
   streamed row by row through F8's `CsvWriter` into the response writer (never the whole log in memory), columns
   time (ISO 8601 UTC), actor (display name, or "System"), action, record type, record id, details (the JSON as
   text), `Content-Disposition: attachment; filename="lexvay-audit-<yyyy-mm-dd>.csv"`. Audited `AUDIT_EXPORTED`
   with the filters and the row count after the stream completes. Test with 20 000 audit rows that heap growth during
   the export stays under 32 MB (measure with `MemoryMXBean` before and after, after a GC).
8. **Tests**: every rule above, including concurrency of the last-admin rule (two threads), expired and reused codes,
   timing-safe comparison of the code hash (`MessageDigest.isEqual`), session revocation over real HTTP (sign in,
   deactivate from an admin session, next request 401), RLS isolation of `user_setup_token`, immutability, rate limit on
   `/auth/setup`, rows in `InputValidationTableTest` for every new endpoint, `Rule9_AuditCoverageTest` for every
   mutating one and the export, `ForbiddenWordsTest` green.
Done when `make backend-test`, `make perf` and `LEXVAY_E2E_SLOT=5 make e2e` are green.

## F10a — Coverage matrix API (backend only; slot 8; no migration expected)
Purpose: a program director needs to see, for one state, which course carries the evidence for which requirement, and
where a requirement rests on a single elective course that not every student takes. The matrix reports what the
evidence shows and what people decided; it never concludes anything (product rule 1). Screen in v2 later (F10b).
Decisions D-390 to D-399 in `docs/decisions/F10a.md`.
1. `GET /api/v1/programs/{id}/coverage?jurisdiction=XX` (any role; 400 when the jurisdiction is not a target of the
   program) over the program's latest finished assessment and current mappings (`MappingRepository.current(...)`,
   FIX-3). Response: `{ program, jurisdiction, assessmentId, ruleVersion: { id, label }, courses: [ { courseId,
   courseVersionId, code, title, catalogYear, required } ], requirements: [ { mappingId, ref, title, type, finding,
   decision, cells: [ { courseVersionId, source, quoteCount } ], programLevel: [ { documentId, filename, kind,
   source } ], risk } ] }`. Courses are the program's current course versions, ordered required first, then by code.
   Requirements follow `ordinal`.
2. **Cells**: a course version appears in a requirement's `cells` when evidence for that mapping cites a document of
   that course version. `source` is `SYSTEM` (the mapping's own verified evidence span) or `PERSON` (an evidence
   response, F4a); both can be present. Evidence from documents with no course version (degree plan, handbook)
   goes to `programLevel`. Numeric requirements evaluated by the rules engine carry no cells and `risk` = `NONE`.
3. **Risk** (computed in SQL, one value per requirement): `NO_EVIDENCE` when there is no cell and no program-level
   evidence; `ELECTIVE_ONLY` when every cell is an elective course (`required = false`) and there is no
   program-level evidence; `SINGLE_COURSE` when exactly one required course carries all the evidence and there is
   no program-level evidence; `NONE` otherwise. These are descriptive flags with fixed names; the API and later
   screen describe them as "rests on an elective", "rests on one course", "no evidence cited", never as a judgement.
4. One SQL statement, one read-only transaction; added to `EndpointLatencyTest` (40 ms p95) and
   `ReadPathConnectionsTest`; rows in `InputValidationTableTest` (bad UUID, bad jurisdiction pattern, non-target
   jurisdiction); `IndexUsageTest` candidate if `EXPLAIN` shows a sequential scan on `mapping`, `evidence_span`,
   `evidence_response` or `document`.
5. Tests on the seeded program: California's matrix lists every requirement in order with the expected cells (assert
   the COUN 605 cell for CA-2), the seeded elective-only requirement is `ELECTIVE_ONLY`, a numeric requirement has no
   cells, an evidence response from F4 adds a `PERSON` cell, a superseded rule version's mappings do not appear
   (change-tracking fixture), tenant isolation 404, and `ForbiddenWordsTest` green.
Done when `make backend-test`, `make perf` and `LEXVAY_E2E_SLOT=8 make e2e` are green.

## F10b — Coverage matrix screen (frontend, v2; slot 7; Opus 5.5; uses F10a)
Purpose: show a program director, for one state, which course carries the evidence for each requirement and where a
requirement rests on an elective, on one course, on outdated evidence or on nothing, at a glance and without words.
Decisions D-416 to D-425 in `docs/decisions/F10b.md`.
1. **Route and navigation**: `programs/:programId/coverage` ("Coverage" in the rail, icon `phosphorGridFour`, after
   Review); state pills like Review's (full names, mini rings of the share of requirements with risk `NONE`); the
   selected state in the URL (`?jurisdiction=CA`); one request per state to `GET /programs/{id}/coverage`.
2. **Matrix**: rows are requirements in `ordinal` order (ref mono, title), columns are the program's current courses
   (code mono, rotated 0°, with a small "required" or "elective" marker under the code), plus a last column "Program"
   for program-level documents. A cell is a 20 px dot: filled accent for `SYSTEM` evidence, ringed accent for
   `PERSON` evidence, both shown as a dot with a ring; an outdated cell from `outdatedCells` sits under its
   `supersededBy` column as a dashed ring with a small clock icon. Hovering or focusing a cell opens a popover: course
   code and title, source, quote count, and an action "Open in Review" that deep-links to the requirement
   (`review?jurisdiction=XX&ref=REF`, UI2-0's query parameters). Rules-engine requirements show "Checked by the rules
   engine" in the row instead of cells.
3. **Risk column** (sticky right): one chip per row with icon and text: "Rests on an elective" (ambiguous colour),
   "Rests on one course" (neutral with outline), "Outdated evidence" (ambiguous with clock), "No evidence cited"
   (missing colour), nothing for `NONE`. Above the matrix, filter chips by risk with counts; clicking one dims rows that
   do not match (opacity .35, 200 ms) rather than hiding them, so the matrix keeps its shape.
4. **Motion**: the matrix enters column by column (stagger 20 ms, at most 12) under the motion table; dots scale in
   from .6; reduced motion shows everything at once.
5. **Accessibility**: the matrix is a real `table` with row and column headers; each cell has an accessible name
   ("COUN 605, evidence from the system, 1 quote") and is reachable with the keyboard (roving tabindex across cells,
   arrow keys); the popover opens on Enter or focus and closes on Escape; colour is never the only carrier (dot vs ring
   vs dashed ring, chip icons and text). Under 900 px the matrix scrolls horizontally inside its own container with the
   requirement column sticky left and the risk column sticky right; the page never scrolls sideways.
6. **Word budget**: no paragraph; one InfoTip next to the title explains the dots and the risk chips.
7. **Bundle**: lazy route chunk; icons and tokens per chunk (PERF-FE-1 pattern); the initial bundle stays under the
   680 kB warning.
8. **Tests**: Vitest for the view model (column order, cell lookup including outdated cells, risk filter counts);
   e2e (`frontend/e2e/coverage.spec.ts`): California shows CA-2's COUN 605 dot, CA-6 is "Rests on an elective",
   Utah's UT-7 is "Outdated evidence" with a dashed cell under COUN 625, keyboard navigation across cells, the
   popover's "Open in Review" lands on the requirement, the risk filter dims rows, axe in both themes at 1440 and 400 px.
Done when `make frontend-test` and `LEXVAY_E2E_SLOT=7 make test` are green with screenshots `f10b-*` in both themes at 1440
and 400 px.

## F5b — Administration screens (frontend, v2; slot 2; Opus 5.5; uses F5a, SEC-2)
Purpose: an administrator manages colleagues in the product: add, change role, remove access, reissue a setup code.
A new colleague sets their own password from the code. Sessions that lost access end cleanly. Decisions D-426 to
D-435 in `docs/decisions/F5b.md`.
1. **People** (`/people`, tenant-wide rail entry under Determination register, icon `phosphorUsers`, ADMIN only; other
   roles never see the entry and the route guard sends them to the overview): a table of users (avatar initials, name,
   email, role chip, status chip Active / Deactivated / Setup pending when `passwordSet` is false, created date),
   search by name or email, filter chips by role and status. Row actions in a menu: Change role, Deactivate or
   Reactivate, New setup code. An administrator's own row has no role or deactivate action (the API refuses them; the
   screen does not offer them).
2. **Add person** (Dialog): name, email, role (Select with one-line role descriptions in an InfoTip). On success the
   dialog turns into a one-time code view: the setup link (`<origin>/setup#code=…`, fragment so no server log sees it (D-435)) in mono with a Copy button and
   the expiry ("valid for 72 hours, until 30 Nov 15:04"), a clear sentence that the code is shown once, and Done. The
   code is never stored in the browser (no storage, no logs; cleared from component state on close). "New setup code"
   shows the same view.
3. **Change role / deactivate** (ConfirmDialog with the consequence in one sentence: "Their current session ends.");
   the 409 messages from the API (last administrator, self) appear inline in the dialog.
4. **Set password** (`/setup#code=…`, anonymous, uses the sign-in layout from UI2-0 without the map story): two
   password fields with show/hide, live rules (12 to 128 characters, not the email) shown as a checklist with icons,
   a strength meter based on length and character variety only (no dictionary library), Submit to
   `POST /auth/setup`; the generic invalid-code message for 400s; on success go to sign-in with a toast "Password set.
   Sign in." The page strips the code from the address bar with `history.replaceState` right after reading it.
5. **Session and busy handling** (shared, in `core/`): a 401 with the problem detail "Your access changed. Sign in
   again." (F5a session revocation) signs the user out locally, routes to sign-in and shows that sentence; a 503 with
   `Retry-After` on sign-in or setup (SEC-2's hashing gate) shows "Sign-in is busy. Try again in a moment." and
   disables the button for the `Retry-After` seconds with a visible countdown.
6. **Tests**: Vitest for the password rule model and strength meter, the role/status filters, and the 401/503 handling;
   e2e (`frontend/e2e/people.spec.ts`): the admin adds a Contributor, copies the code (read it from the dialog), signs
   out, sets the password through the setup page, signs in as the new person; the admin changes the role and the
   new person's next request lands on sign-in with the access-changed sentence; deactivate and reactivate; an
   administrator cannot act on their own row; a reviewer never sees People; axe in both themes at 1440 and 400 px.
Done when `make frontend-test` and `LEXVAY_E2E_SLOT=2 make test` are green with screenshots `f5b-*` in both themes at 1440 and
400 px.

## FIX-4 — Rule source backoff, live search terms, PDF page cap at upload (backend; slot 7; migration `V016__rule_source_schedule.sql`)
Three follow-ups from the F12, F7c and WS-SEC reviews. Decisions D-380 to D-389 in `docs/decisions/FIX-4.md`.
1. **Rule source schedule.** `rule_source` gains `next_check_at timestamptz` and `consecutive_failures integer NOT
   NULL DEFAULT 0` (backfill `next_check_at = coalesce(last_checked_at + check_interval_hours * interval '1 hour',
   now())`, then `SET NOT NULL`); recreate the update restriction so these two columns are updatable alongside
   `active`, `last_checked_at` and `last_content_hash`. The scheduler's due predicate becomes `active AND
   next_check_at <= now()` with a partial index `ON rule_source (next_check_at) WHERE active`. After a check:
   UNCHANGED, CHANGED and DRAFT_EXISTS reset `consecutive_failures` to 0 and set `next_check_at = now() + interval`;
   FETCH_FAILED increments it and sets `next_check_at = now() + least(interval, 15 minutes × 2^(failures − 1))`. A
   manual check does not change the schedule except through its outcome. `GET /rule-sources` exposes `nextCheckAt`
   and `consecutiveFailures`. Tests: backoff sequence 15, 30, 60 minutes capped at the interval; reset on success;
   scheduler picks only due sources; `IndexUsageTest` covers the due query.
2. **Live evaluation with search terms.** `eval/golden.yml` gains hand-written fictional `search_terms` for every
   golden requirement (header says so), and the replace path used by `make eval-live` carries them, so live
   retrieval is measured with terms. The live report adds, per requirement, the overlap between the model's proposed
   terms and the golden terms (Jaccard of the normalised sets) and its mean. `make eval` (scripted) is unchanged except
   that the golden file now validates the new field (`RetrievalFileTest`-style check).
3. **PDF page cap at upload.** `DocumentService.upload` counts the pages of a PDF before storing it (PDFBox, temp-file
   memory setting so a 25 MB file does not sit in heap twice) and answers 400 "PDF has N pages; the limit is 500"
   without storing anything; ingest keeps its own check as a second line. Test: a 501-page PDF is refused at upload,
   nothing stored, no job queued, the message names the count.
Done when `make backend-test`, `make perf`, `make eval` and `LEXVAY_E2E_SLOT=7 make e2e` are green.

## FIX-3 — Work queue and evidence request follow-ups (backend only; slot 9; no migration expected)
Settles the open points recorded in the F13 and F4 reviews. Decisions D-360 to D-369 in `docs/decisions/FIX-3.md`.
1. **`ASSESSMENTS_FAILED`** lists a program's FAILED assessments created after its latest FINISHED assessment (or all
   failures when none finished). A failure followed by a successful run disappears from the queue. Test both.
2. **Signed requests.** An open evidence request whose mapping's assessment and jurisdiction already have a SIGNED
   determination cannot be answered (409 today). Extract the "signed for this assessment and jurisdiction" predicate
   that `DeterminationRepository.hasSigned` uses into one SQL constant and use it in three places: `hasSigned`,
   `EvidenceRequestView` (new `signed boolean`, one column in the existing statement) and the work queue, where
   `EVIDENCE_REQUESTS_OPEN` counts signed requests in `blockedCount` with stale ones and does not count them as
   answerable items (rename nothing; document the meaning in D-36x). The status stays OPEN in the API. The inbox shows
   them as blocked in UI2-4 (frontend is out of scope here). Tests: after signing, the request is `signed: true`,
   the work queue no longer lists it as answerable, and answering still returns 409.
3. **One "decided" predicate.** `AssessmentRepository.findingCounts` uses `MappingRepository.LATEST_EVENT` and
   `NEEDS_DECISION` instead of its own copy; the result is identical (assert with the existing overview tests plus one
   that compares counts before and after on the seeded tenant).
4. **`MappingRepository.CURRENT`.** Measure before changing: on the perf database, add one assessment with 500
   mappings and a superseded rule version, run `EXPLAIN (ANALYZE, BUFFERS)` of `GET /assessments/{id}/mappings`'s
   statement as the application role, and record the plan in `docs/PERF.md`. Rewrite the filter only if that
   statement exceeds 5 ms or 2 000 buffers; if you rewrite, the result set must be identical (a test compares
   both forms over the change-tracking fixtures).
Done when `make backend-test`, `make perf` and `LEXVAY_E2E_SLOT=9 make e2e` are green.

## FIX-2 — Job entity id column and index (slot 5; migration `V013__job_entity_id.sql`)
Defect found in review of WS-PERF (D-115): `JobRepository.latest` filters by `payload->>:key`, which scans the
whole `job` table under row-level security because `jsonb ->>` is not leakproof and jobs are never deleted.
Every job payload today holds exactly one id (`documentId`, `ruleVersionId`, `assessmentId`, `changeEventId`,
`reportId`, and `ruleSourceId` from F12), so the id becomes a real column.
1. Migration: `ALTER TABLE job ADD COLUMN entity_id uuid`; backfill with the single value of the payload
   object (`(SELECT value::uuid FROM jsonb_each_text(payload) LIMIT 1)`, guarded by
   `jsonb_typeof(payload) = 'object'`); `SET NOT NULL`; `CREATE INDEX job_entity_idx ON job (type, entity_id,
   created_at DESC)` (key decided in review, D-202). Prove the backfill expression with a SQL-level test on a temporary table, as
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
Decisions D-200 to D-209 in `DECISIONS.md (FIX-2)`.

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

## PERF-FE-1 — Icons and theme tokens per chunk (frontend; slot 6; Sonnet 5.5; after F8 merges)
Purpose: the initial bundle sits at the 700 kB raw warning after every screen lands, because `provideIcons(LEXVAY_ICONS)`
in `app.config.ts` registers every Phosphor icon of every screen in the initial chunk, and the shared preset carries
token sets for components only some screens render. Shaving each screen by hand does not hold; this makes each lazy
chunk carry its own icons and tokens. Decisions D-295 to D-299 in `docs/decisions/PERF-FE-1.md`.
1. **Icons**: `app.config.ts` provides only the icons the shell, sign-in and the home screen render (`SHELL_ICONS` in
   `theme/icons.ts`). Every routed screen provides its own set with `provideIcons` in the route's `providers` (one
   `<SCREEN>_ICONS` constant per screen file, next to the screen), and shared components (`DocumentViewer`, the
   diff and clamp helpers) provide theirs in the component's `viewProviders`. `LEXVAY_ICONS` is deleted. A Vitest
   test renders each routed screen's template with only its own providers and fails if an `ng-icon` name is not
   registered (walk the compiled templates or the rendered DOM for `ng-icon[name]`; no runtime warnings allowed).
2. **Theme tokens**: the shared preset keeps only the token sets of components used by the shell or by two or more
   screens' first paint (Button, Tag, Message, Toast, Skeleton, Menu, Select, Tabs, Badge, Tooltip, InputText,
   Password, DataTable); every other component's token set moves to the chunk of the screen that uses it, registered
   once on first load through one shared helper `registerComponentTokens(sets)` (generalise F3b's `rules-theme.ts`
   and F8's register equivalent into it; delete their duplicates).
3. **Budget**: lower the initial budget to 650 kB warning and 750 kB error (raw) once the initial bundle is under
   640 kB; report the before and after sizes (raw and estimated transfer) and the size of each lazy chunk in
   `docs/PERF.md` under a new "Frontend bundle" section with the method.
4. **No visual change**: regenerate every screenshot with `LEXVAY_SCREENSHOTS=1` and confirm in the PR that they are
   unchanged apart from timestamps; axe stays clean in both themes.
Done when: `make frontend-test` and `LEXVAY_E2E_SLOT=6 make test` green; budget lowered; `docs/PERF.md` updated.

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

## F7c — Search terms proposed at extraction (slot 3; migration `V014__requirement_search_terms.sql`; screens in UI2-4)
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

## FIX-6 — OCR checkpoints per page (backend only; slot 5; Sonnet 5.5; migration `V017__document_ocr_page.sql`)
Problem (F11 follow-up): `IngestDocumentHandler` OCRs every scanned page inside the job's single transaction. A
failure on page 150 of a 200-page scan rolls everything back, and the retry runs Tesseract on all 200 pages again
(minutes of CPU each time, three attempts). Decided design: each page's OCR result is checkpointed in its own
committed transaction, and a retry reuses those checkpoints. No job-per-page, no change to `JobRunner`.

1. **Table** `document_ocr_page` (V017): `tenant_id uuid NOT NULL REFERENCES tenant`, `document_id uuid NOT NULL
   REFERENCES document`, `page_number int NOT NULL CHECK (page_number > 0)`, `text text NOT NULL` (normalized OCR
   output, possibly empty), `mean_confidence numeric(5,2)` (NULL when the engine returned nothing), `render_dpi int
   NOT NULL CHECK (render_dpi > 0)`, `created_at timestamptz NOT NULL DEFAULT now()`, `PRIMARY KEY (document_id,
   page_number)`. RLS enabled and forced, with the same tenant policy and grants as `document_page`. It is
   immutable: `forbid_change()` trigger on UPDATE and DELETE (V003 pattern). No other index: the PK serves the
   one read. Rows stay after ingest; they record how a page's text was obtained (rule 5).
2. **What is stored:** the raw normalized OCR result of every page that was sent to the engine, including pages
   whose result is too short to count as read. The "enough text" decision (`TextExtractor.hasEnoughText`) is
   made in code every time, from the stored text, so a checkpoint never changes the outcome.
3. **`OcrCheckpoints`** (interface, package `document`): `Optional<OcrPage> find(int pageNumber)` and
   `void save(int pageNumber, OcrPage page, int renderDpi)`, with `OcrCheckpoints.NONE` (finds nothing, saves
   nothing). `TextExtractor.extract(FileType, byte[], OcrCheckpoints)` is the full form. `extract(FileType,
   byte[])` delegates with `NONE` and stays for rule texts, rule sources and tests. In `pdfPages`, a page that
   needs OCR first asks `find`. On a hit it uses the stored text and confidence, without rendering or calling
   the engine. On a miss it renders, recognizes and calls `save` before moving to the next page. When the engine
   returns `Optional.empty()`, save `text = ''` with a NULL confidence. A hit whose `render_dpi` differs from
   `renderDpi(page)` is ignored and the page is OCR'd again (the DPI rule changed). The insert is still
   `ON CONFLICT DO NOTHING`, and the in-memory result uses the new OCR.
4. **`DocumentOcrCheckpoints`** (`@Component`, backed by `DocumentRepository`):
   `OcrCheckpoints forDocument(UUID tenantId, UUID documentId)`. It loads every checkpoint of the document in one
   query when created. `save` runs its INSERT ... ON CONFLICT DO NOTHING through a `TransactionTemplate` with
   `PROPAGATION_REQUIRES_NEW`, so the row commits even when the job's transaction later rolls back.
   `TenantAwareDataSource` sets `lexvay.tenant_id` on the new connection; prove it with a test, do not assume it.
   It needs no worker flag: the ingest job already runs as its tenant.
5. **`IngestDocumentHandler`** passes `forDocument(...)`. The `DOCUMENT_INGESTED` audit details gain
   `ocrPagesReused` (count of checkpoint hits). This is not a new action, so `audit-sentence.spec.ts` needs no row.
   No endpoint changes.
6. **Tests** (no network; Tesseract only where the existing OCR test already uses it):
   - `TextExtractorTest`: a counting fake `OcrEngine` plus in-memory checkpoints. A second extract performs zero
     engine calls and returns pages equal to the first, with the same text, offsets, source and confidence. A
     short-text checkpoint still falls back to the text layer. A DPI mismatch triggers a fresh OCR.
   - Integration, existing Testcontainers harness: the fake engine throws on page 3 of a 4-page scanned PDF on the
     first attempt only.
     - After the failed attempt, rows for pages 1 and 2 exist, committed despite the rollback.
     - The retry calls the engine for pages 3 and 4 only.
     - The final `document_page` rows equal those of a clean single run.
     - The audit shows `ocrPagesReused = 2`.
   - Rule 7: tenant B cannot see tenant A's checkpoints, and `Rule7_EveryTableHasPolicyTest` covers the table.
   - Rule 5: UPDATE and DELETE are rejected.
7. **Docs:** decisions D-436 to D-441 in `docs/decisions/FIX-6.md`, progress in `docs/progress/FIX-6.md`,
   `docs/OPERATIONS.md` (one sentence: checkpoints make OCR retries resume). Do not edit DECISIONS.md, PROGRESS.md
   or the ROADMAP board; the orchestrator folds them.

## F12b — Rule sources in the Rules workspace (frontend, v2; slot 1; Opus 5.5; after UI2-4 merges; D-442 to D-449)
F12 built `/api/v1/rule-sources`: list, create, checks history, check now, pause and resume. No screen shows any of
it yet, so the only way to see why a draft rule version appeared is the audit trail. Build it as a **Sources** tab
of the Rules workspace, after UI2-4 has restyled that workspace. No backend change; if one turns out to be needed,
stop and report.

1. **List** (`GET /rule-sources`): one row per source, grouped by state code in the same order as the target-state
   pills, with inactive sources at the end of their group.
   - Each row shows:
     - the label;
     - the host of the URL in IBM Plex Mono (the full URL in a tooltip and as a link that opens a new tab with
       `rel="noopener noreferrer"`);
     - the latest outcome as StatusIcon plus text;
     - "Checked 3 h ago" (relative time with an absolute tooltip);
     - "Next in 21 h", or "Paused" when inactive.
   - Outcome words:
     - `UNCHANGED`: "No change".
     - `CHANGED`: "Changed, draft created", with a link to that rule version in the workspace.
     - `DRAFT_EXISTS`: "Changed, a draft already waits", with a link to the waiting draft.
     - `FETCH_FAILED`: "Could not fetch", plus "N in a row" when `consecutiveFailures > 1`. The InfoTip holds the
       HTTP status and `detail`.
     - No check yet: "Not checked yet".
   - The words "compliant" and "meets requirements" never appear; `ForbiddenWordsTest` scans the new templates.
2. **History:** expanding a row (PrimeNG Accordion, or a disclosure button with `aria-expanded`) loads
   `GET /rule-sources/{id}/checks?limit=20` once per expansion and shows a vertical timeline. Reuse UI2-3's
   changes timeline markup. If it is screen-local, extract it to `ui/` first and keep Changes unchanged
   (screenshots identical). Show `contentHash` shortened to 12 characters in Plex Mono with a copy button.
3. **Actions:**
   - **Check now** (ADMIN and REVIEWER): `POST /rule-sources/{id}/check`. The row shows a running state, and the
     screen polls `GET /rule-sources` every 2 s until that source's `lastCheckedAt` changes, for up to 60 s. On
     timeout the row says "Still checking" and offers to refresh. One poller for the whole screen, cancelled on
     destroy. No N+1: the list endpoint already returns everything.
   - **Pause / Resume** (ADMIN only): PrimeNG ToggleSwitch, optimistic, reverted with an inline error on failure.
   - Roles without permission do not see the controls; they are not merely disabled.
4. **Add source** (ADMIN and REVIEWER): a PrimeNG Dialog with these fields:
   - state, a Select of the program's target states;
   - license type;
   - label;
   - URL, validated `^https?://\S+$` on the client as on the server;
   - interval, a SelectButton with Daily (24), Weekly (168) and Monthly (720).
   A 400 maps field errors inline. On success, the new row enters with `animate.enter` and the dialog closes,
   returning focus to the Add button.
5. **Empty state:** a Phosphor duotone `globe` at 32 px, one line ("No sources monitored for this state") and the
   Add button. Word budget as in DESIGN.md: everything explanatory goes behind an InfoTip.
6. **Bundle:** the tab is its own lazy chunk with its own icons and token sets (PERF-FE-1 pattern). The initial
   bundle must not grow by more than 0.5 kB.
7. **Tests:**
   - Unit tests for outcome words, grouping and order, poll stop conditions and role gating.
   - End-to-end, on the seeded stack:
     - Add a source with an unreachable URL (`https://example.invalid/rules`). Check now. The row ends at
       "Could not fetch". The SSRF guard or the fetch failing are both acceptable; assert only on the visible
       outcome.
     - Pause it.
     - Open its history.
     - The audit trail shows the three sentences.
   - axe on every state, both themes, and 400 px.
   - Screenshots `f12b-*` in both themes at 1440 and 400 px.

## FIX-7 — One "latest finished assessment" ordering (backend only; slot 6; Sonnet 5.5; no migration; D-450 to D-452)
`MappingRepository.latestAssessmentsUsing` (called by `RerunChangeHandler`) is the last place with its own copy of the
"latest finished" ordering, in the correlated subquery aliased `a2`. Every other caller composes
`AssessmentRepository.LATEST_FINISHED` (D-411). Decided form, the same shape as the work queue's `latest` CTE:

```sql
SELECT la.id AS assessment_id, p.id AS program_id, la.created_by
FROM program p
JOIN LATERAL (SELECT a.id, a.created_by FROM assessment a WHERE a.program_id = p.id AND <LATEST_FINISHED>) la ON true
WHERE EXISTS (SELECT 1 FROM mapping m JOIN requirement r ON r.id = m.requirement_id
              WHERE m.assessment_id = la.id AND r.rule_version_id = :rv)
```

1. Replace the statement with this, built with `.formatted(AssessmentRepository.LATEST_FINISHED)`. Keep the
   method name, the signature and the result keys.
2. **Parity test:** on the seeded demo tenant (use the seed fixture that `WorkQueueSeedParityTest` uses),
   compare the old query (kept inside the test as a literal) with the new method for every rule version in
   the seed. Do the same for a second program whose newest finished assessment does not use the version, and
   for a program whose newest assessment is FAILED but whose older finished one uses the version. The results
   must be equal as sets.
3. **Index usage:** add the statement to `IndexUsageTest` with the same expectations as the work queue's
   `latest` (no sequential scan on `mapping` or `assessment` on the perf seed).
4. **Cleanup:** `grep -rn "finished_at DESC" backend/src/main` must then show only `LATEST_FINISHED` itself.
   Record that in D-450.
5. Remove the FIX-5 and F10a lines about this from "Follow-ups recorded from reviews" in `docs/ROADMAP.md`;
   that is the only ROADMAP edit. Decisions go in `docs/decisions/FIX-7.md`, progress in `docs/progress/FIX-7.md`.

## F14 — Check a report file (backend and frontend; slot 3; Opus 5.5; migration `V018__report_content_hash.sql`; D-453 to D-462)
Signed evidence reports leave Lexvay as PDFs and travel by email to deans, boards and auditors. Staff need to answer
"is this the file Lexvay produced, unchanged, and does its determination still stand?" without trusting the sender.
Decided design: hash the PDF bytes when the report is generated, and look a dropped file up by that hash. The file
is never stored.

**Backend**
1. **V018:**
   - `ALTER TABLE report ADD COLUMN content_sha256 text CHECK (content_sha256 ~ '^[0-9a-f]{64}$')`.
   - `CREATE INDEX report_content_sha_idx ON report (tenant_id, content_sha256) WHERE content_sha256 IS NOT NULL`.
   - `GenerateReportHandler` computes SHA-256 over the exact bytes it stores and sets the column in the same UPDATE
     as `markReady`; `markReady` gains the parameter.
   - Reports generated before V018 keep NULL. Nothing is deployed, and the seed regenerates its reports, so there
     is no backfill. Record that in D-453.
2. **`POST /api/v1/reports/check`:**
   - Multipart, one part `file`, any authenticated role.
   - Maximum 25 MB, enforced by the existing upload limit configuration; do not add a second limit.
   - The body must start with `%PDF-`, otherwise 400 "Not a PDF file".
   - Hash with a streaming `MessageDigest` and never hold the file twice in memory.
   - Look up within the tenant through RLS: `SELECT ... FROM report r JOIN determination d ... WHERE r.content_sha256
     = :h`. One statement, served by the new index; prove it in `IndexUsageTest`.
   - Response `ReportCheck(boolean match, String sha256, Match matchDetails)`. `Match` holds `reportId`,
     `generatedAt`, `determinationId`, `programName`, `jurisdictionCode`, `ruleVersionLabel`, `signedByName`,
     `signedAt`, `determinationStatus` (`SIGNED` | `REOPENED`), `reopenedAt` (null unless reopened) and
     `versionsHash`.
   - Several reports cannot share a hash in practice. If one ever does, return the newest and log a warning.
   - Audited as `REPORT_CHECKED`, with details `{match, sha256}` and entity the report when matched, else null.
   - Add it to `Rule9_AuditCoverageTest` and to `InputValidationTableTest`.
   - Add the sentence to `frontend/.../audit-sentence.ts` and its spec: "{actor} checked a report file: it matches"
     or "…: no match".
3. **Tests:**
   - Generate a report, download it and check it: a match with every field right.
   - One byte changed: no match.
   - A PDF from another tenant's report: no match (rule 7).
   - A reopened determination: the match says REOPENED with its time.
   - Not a PDF: 400. Oversized: 413.
   - The audit entry for each case.

**Frontend (Evidence report screen, design v2)**
4. A secondary button "Check a file" in the report screen's header. It opens a PrimeNG Dialog with a drop zone
   (PrimeNG FileUpload in custom mode, restyled): drag a PDF or choose one. Show a ProgressRing while the file
   uploads and hashes.
5. **Results:**
   - **Match, determination signed:** the Seal in `preview` mode, a headline "Matches the report generated
     {date}", and three facts:
     - state and program;
     - "Signed by {name}, {date}";
     - rule version label.
     A link opens that determination's report. The SHA-256 is in Plex Mono, shortened to 16 characters, with a copy
     button.
   - **Match, determination reopened:** a warning StatusIcon and "Matches, but this determination was reopened on
     {date}". There is no Seal; a reopened record is not presented as standing.
   - **No match:** a neutral icon, "No report in this workspace has exactly this content", and an InfoTip explaining
     that any change to the file, even re-saving it, changes its fingerprint.
   - Wording: never "compliant", "meets requirements", "valid" or "authentic". The page states facts about the file.
6. **Tests and checks:**
   - Unit tests for the three result views and the size and type errors.
   - End-to-end: download a seeded report, check it (match), check a modified copy (no match), and see the audit
     sentence. Run axe on each state in both themes and at 400 px.
   - Screenshots `f14-*` at 1440 and 400 px in both themes.
   - The initial bundle must not grow by more than 0.5 kB; the dialog is deferred.

## INFRA-2 — Remote state, WAF, flow logs and private-range egress (Terraform only; no slot; Sonnet 5.5; D-463 to D-470)
These are the WS-EVAL and F12 deployment follow-ups. Validated with `make infra-validate`, never planned against an account or applied.
Pin the provider versions already in `versions.tf`. Any new provider feature must exist in `hashicorp/aws` 6.74.0;
check the registry docs for that version, not memory.

1. **Remote state:**
   - Add `backend "s3" {}` (partial configuration) to `versions.tf`.
   - Add `infra/backend.hcl.example` with `bucket`, `key`, `region`, `encrypt = true`, `kms_key_id` and
     `use_lockfile = true` (native S3 locking, so no DynamoDB table). Leave every value a placeholder.
   - Add `backend.hcl` to `.gitignore`.
   - `make infra-validate` keeps `init -backend=false`.
   - `docs/OPERATIONS.md`: how to create the state bucket (versioning, SSE-KMS, public access block, TLS-only
     bucket policy) as a one-time bootstrap outside this stack, and run `init -backend-config=backend.hcl`.
2. **WAF in front of CloudFront:**
   - Add a provider alias `aws.us_east_1`, since CLOUDFRONT-scope web ACLs live there.
   - New `waf.tf` with `aws_wafv2_web_acl` (`scope = "CLOUDFRONT"`, default allow) and these rules in order:
     1. `AWSManagedRulesAmazonIpReputationList`.
     2. `AWSManagedRulesKnownBadInputsRuleSet`.
     3. `AWSManagedRulesCommonRuleSet`, with `SizeRestrictions_BODY` overridden to `count`. Uploads are up to
        25 MB and the application enforces that limit.
     4. A rate-based rule blocking an IP after `var.waf_login_rate_limit` (default 300) requests in 5 minutes to the
        path `/api/v1/auth/` (starts-with match). It complements the application's Argon2 gate; it does not replace
        it.
   - Attach the ACL through `web_acl_id` on the distribution.
   - Logging: `aws_wafv2_web_acl_logging_configuration` to a us-east-1 CloudWatch log group named
     `aws-waf-logs-${local.name}`. Redact the `authorization` and `cookie` headers; retention
     `var.log_retention_days`.
3. **VPC flow logs:**
   - `aws_flow_log` on the VPC, `traffic_type = "ALL"`, to a CloudWatch log group encrypted with
     `aws_kms_key.data`, retention `var.log_retention_days`.
   - A delivery IAM role whose trust is limited to `vpc-flow-logs.amazonaws.com` with `aws:SourceAccount`.
   - Extend the KMS key policy so CloudWatch Logs may use the key for that log group only (encryption context
     condition).
4. **Egress from the private subnets** (the DNS-rebinding window left by F12's SSRF guard):
   - Add an `aws_network_acl` on the private subnets.
   - **Outbound rules:**
     - 100: allow all to the VPC CIDR.
     - 110–140: deny all to `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` and `100.64.0.0/10`.
     - 200: allow all to `0.0.0.0/0`.
   - **Inbound rules:**
     - 100: allow all from the VPC CIDR.
     - 200: allow TCP 1024–65535 from `0.0.0.0/0`, for return traffic, since NACLs are stateless.
   - In a comment and in OPERATIONS.md, state that link-local addresses (`169.254.0.0/16`, including the ECS
     credentials endpoint the task needs) never pass a NACL. The application's `SsrfGuard` remains the control for
     those.
5. **Tests**, in `infra/tests/` with mocked providers and `command = plan` (the existing pattern):
   - The distribution's `web_acl_id` is the ACL's.
   - The ACL holds the four rules in that order, with the body-size override.
   - The flow log targets an encrypted log group.
   - The NACL has the VPC allow first, the four denies, then the default allow, and is associated with every
     private subnet.
   - Variable validation for `waf_login_rate_limit` (100 to 20000).
6. **Docs:** remove the WS-EVAL Terraform line and the F12 deployment note from "Follow-ups recorded from
   reviews". Decisions go in `docs/decisions/INFRA-2.md`, progress in `docs/progress/INFRA-2.md`.

## FIX-8 — No console errors (frontend; slot 7; Sonnet 5.5; D-471 to D-474)
Signing in logs `AbortError: Transition was skipped` twice in the browser console. `withViewTransitions` in
`app.config.ts` calls `skipTransition()` for reduced motion and same-screen changes, and a quick redirect starts a
new transition. The rejected `ready`/`finished` promises reach `provideBrowserGlobalErrorListeners`. The end-to-end
suite did not notice, because it only guards against unknown icons.

1. **`app.config.ts`:** in `onViewTransitionCreated`, attach handlers to `transition.ready`, `transition.finished`
   and `transition.updateCallbackDone`. They ignore a `DOMException` named `AbortError` and rethrow anything else.
   Put this in one small function (`ignoreSkippedTransition`) with a unit test covering both cases.
2. **`consoleGuard`:** add an automatic fixture in `e2e/support.ts`, beside `iconGuard`. It collects every
   `console` message of type `error` and every `pageerror`, and fails the test if any were recorded.
   - Tests that deliberately produce an error (an expected 4xx or 5xx, which Chromium logs as "Failed to load
     resource") declare it with `allowConsoleErrors(page, /pattern/)`. The pattern must be specific to that
     response's URL or status. No global allowlist.
3. **Run the whole e2e suite.** Fix every real error it finds in application code. If a fix belongs to another
   item's screen, keep it minimal and list it in the PR.
4. **Record it:** decisions in `docs/decisions/FIX-8.md`, progress in `docs/progress/FIX-8.md`. List every
   `allowConsoleErrors` added, with the reason for each.

## Follow-ups recorded from reviews
- FIX-5 (rule 9): `LOGOUT` is audited after the session is cleared, so its audit entry has no actor. Capture the
  user before the logout handler clears the context and record `LOGOUT` with that user as actor and `entity_id`;
  `Rule9_AuditCoverageTest` asserts the actor. UI2-3's `audit-sentence.spec.ts` now fails for any new audited action
  without a sentence and label, so backend items that add an audited action also touch that frontend file.
- F5a security: any ADMIN can issue a setup code for any user, including a SIGNER, and so could sign in as them; the
  actions are audited (`USER_SETUP_CODE_ISSUED`, `PASSWORD_SET`). Without email delivery this is inherent to
  password accounts. Production deployments should use OIDC for SIGNER and ADMIN accounts; `docs/OPERATIONS.md`
  must say so in its "Users and sessions" section. SEC-2 (next): move the password encoder to Argon2id behind the
  delegating encoder (existing bcrypt hashes keep verifying and are upgraded on the next successful login), lift
  the 72-byte limit to 128 characters, and add `audit_log (tenant_id, created_at DESC)` if `IndexUsageTest` shows the
  audit list and export sorting without it.
- F7c: `make eval-live` replaces the model's requirement list with the golden list through the replace path, which
  carries no search terms, so live retrieval is measured without them. Add hand-written terms to `eval/golden.yml`
  (fictional, labelled) so the live run measures BM25 + terms, and report the model's own proposed terms against them.
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
  needs a `next_check_at` column. The Sources tab is scheduled as F12b.
- WS-UI: the overview still finds the latest assessment through the audit log; switch it to the WS-PERF
  `GET /programs/{id}/overview` endpoint (do this in F1). Audit filtering is client-side over the newest 500
  entries; switch to the WS-PERF server filters (F5). Add a build check that the production `index.html` has no
  inline script (F6 or F1). PrimeNG's own dialog-close and select-chevron SVGs are not Phosphor.
- WS-PERF: `GET /documents/{id}` and the rule version views look up job status by scanning the `job` table under
  RLS because `jsonb ->>` is not leakproof and the payload expression indexes are ignored. FIX-2: add a generated
  `payload_entity_id uuid` column on `job` (from the payload's entity id), index it, and compare with `=`. Decided
  by the orchestrator when scheduled; spec to follow.
- F11: OCR of a 200-page scan runs inside the job's single transaction and a retry redoes every page. Scheduled as
  FIX-6 (per-page checkpoints committed in their own transactions).
- F11 spec change (decided during review): a PDF page's text layer is never discarded; OCR replaces it only when
  the layer is under 20 characters and OCR yields at least 20.
- WS-SEC: every new endpoint must add a row to `InputValidationTableTest` (its `everyEndpointHasARow` guard fails
  otherwise). The PDF page cap (500) is enforced at ingest, so an oversized PDF fails its job rather than the
  upload; a synchronous page count at upload is a possible follow-up. Form login stays enabled in the `oidc`
  profile; an SSO-only switch is a follow-up. Behind a load balancer set `LEXVAY_FORWARD_HEADERS=native`.
- WS-EVAL: the live thresholds in `eval/thresholds.yml` (recall 0.80, extraction 0.90, evidence agreement
  0.80) are unmeasured first values; set them from the first `make eval-live` run once a model key exists.
  After RDS is created, the database roles must be bootstrapped with `db/initdb/01-roles.sh`.
- `scripts/eval.sh` repeats the slot and cleanup lines of `scripts/e2e.sh`; extract `scripts/lib/stack.sh`
  and source it from both (do this inside F7, which already touches the evaluation scripts).
- The old Review screen logs `TypeError ... reading 'id'` at `review.ts:82`; verify it is gone after WS-UI.
- Seeded test data: `SeedCopy.northfield` became the shared fixture (five seed tests use it); `SeedLoaderTest` tests
  the loader itself and F9's search tests ingest through the upload API on purpose. Settled, no item needed.
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
