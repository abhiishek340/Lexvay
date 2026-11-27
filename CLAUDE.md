# Lexvay — rules for everyone (human or agent) working in this repository

Lexvay compares a university's curriculum against state professional-licensing requirements and produces
an evidence-backed record that qualified university staff review and sign. The software proposes; people
decide. This is a real product, not a demo: build depth, not breadth.

## Authority
1. Owner directives (latest first). On 2026-11-26 the owner put the project in "CTO mode": improve the
   product continuously, use a real UI library, add real features, keep latency low. These directives
   override the original build prompt on scope and stack.
2. The nine product rules below. Nobody may weaken them, including the orchestrator.
3. `DECISIONS.md`, then `docs/ROADMAP.md` (work items with exact specs), `docs/DESIGN.md` (UI), `docs/PLAN.md`.
If two sources conflict, the higher one wins. If a work item's spec is wrong or impossible, stop and report;
do not improvise a different design. Architecture, algorithms and product decisions belong to the
orchestrator; implement the spec exactly and report disagreement in your final message.

## Product rules that must never be broken (each has tests; see docs/RULES.md)
1. The system never determines. Findings are only `EVIDENCE_FOUND`, `EVIDENCE_MISSING`, `AMBIGUOUS`.
   Only a human confirms a gap. Only a `SIGNER` signs.
2. No evidence without a quote verified in code against stored page text.
3. Numbers (credits, hours, dates) are checked by the rules engine. The model never does arithmetic.
4. When unsure, `AMBIGUOUS`. A false `EVIDENCE_FOUND` is the worst possible error.
5. Nothing is overwritten. New versions, append-only events, database triggers.
6. A signed determination is locked and names its exact rule and course versions.
7. Tenants are isolated by PostgreSQL row-level security, fail closed.
8. No student data. No `student` table or column, ever.
9. Every action is audited: who, when, what, which record.
Also: never invent regulatory content (seed data is fictional and labeled), and the UI never says
"compliant" or "meets requirements" (`ForbiddenWordsTest`).

## Engineering bar
- Write code a staff engineer would sign: small cohesive types, explicit SQL, no dead code, no duplicated
  logic. Before adding a helper, search for an existing one and reuse or extend it.
- No stubs, `TODO`s, mocked production paths, fake success responses or sample-only features. If something
  cannot be finished properly, say so and stop.
- Tests ship with the code. Only `FakeLlmClient` in tests; no test touches the network or needs a key.
  Never claim a test passed without running it. `make test` must be green before you open a pull request.
- Latency is a feature: one transaction per read request, no N+1 queries, indexes for every hot path,
  budgets in `docs/PERF.md` (p95 under 40 ms for read endpoints on the seeded tenant).
- Pin exact dependency versions from the registry, never from memory; record them in `DECISIONS.md` D-002.
- No secrets in the repository. Configuration through `LEXVAY_*` environment variables and `.env.example`.
- Every mutating endpoint is audited and listed in `Rule9_AuditCoverageTest`.

## UI bar (details in docs/DESIGN.md)
- PrimeNG 22 components with the Lexvay preset. Do not hand-build a component PrimeNG provides.
- Phosphor icons through `@ng-icons`. No emoji, no Unicode glyphs as icons, no decorative gradients, no
  "AI look": no sparkle icons, no purple-to-pink anything, no card grids of marketing copy.
- IBM Plex Sans for UI, IBM Plex Serif for rule text and quoted evidence, IBM Plex Mono for ids and offsets.
- WCAG 2.1 AA, keyboard operable, visible focus, state by icon and text as well as colour, responsive to
  400 px, light and dark themes. The end-to-end suite runs axe on every screen: zero serious or critical.

## Git and pull requests
- One branch per work item, named in `docs/ROADMAP.md`. Commit in logical steps with plain, specific
  messages. **Never add `Co-Authored-By` trailers or any AI attribution to commits or pull requests.**
- Push your branch and open a pull request against `main` with `gh pr create` (account `abhiishek340`).
  Do not merge it: the orchestrator reviews, waits for CI and merges.
- Do not rewrite history on `main`. Do not touch another work item's files unless your spec says so; if
  you must, keep the change minimal and call it out in the pull request.
- No deployments and no cloud resources. Terraform is validated, never applied.

## Local environment (this machine)
- Node 26 through nvm: `export NVM_DIR="$HOME/.nvm"; . "$NVM_DIR/nvm.sh"; nvm use 26` before any npm command.
- Docker runs in Colima; the Makefile resolves the socket. If Docker is down: `colima start`.
- Commands: `make test` (backend + frontend unit + end-to-end), `make backend-test`, `make frontend-test`,
  `make e2e`, `make dev`, `make seed`, `make seed-pdfs`.
- Several checkouts run end-to-end tests at the same time. Use the slot assigned to your work item:
  `LEXVAY_E2E_SLOT=<n> make e2e` (slot n shifts PostgreSQL 5433+n, backend 8081+n, web 4300+n, management 9091+n).
  Slot 0 belongs to the orchestrator. Never use ports 5432, 8080 or 4200 in tests.
- In a fresh worktree run `cd frontend && npm ci` once before frontend commands.

## Stack (pins in DECISIONS.md D-002)
Java 21, Spring Boot 4.1.3, Spring JDBC (no ORM), PostgreSQL 18.7 with Flyway and RLS, job table with
`FOR UPDATE SKIP LOCKED`, PostgreSQL full-text search behind `PassageSearch`, `FileStore` (local | S3),
`LlmClient` (`FakeLlmClient` | `AnthropicLlmClient`), Angular 22 standalone with PrimeNG 22, Playwright + axe.

## Layout
`backend/` `frontend/` `seed/` `eval/` `infra/` `scripts/` `docs/` `docker-compose.yml` `Makefile`
`DECISIONS.md` `PROGRESS.md` `.env.example`
