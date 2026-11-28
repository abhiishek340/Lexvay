# Lexvay

Lexvay compares a university program's curriculum documents against a state's professional-licensing
rule text and produces an evidence-backed record that qualified university staff review and sign.

The software proposes three findings only: evidence found, evidence missing, ambiguous. Every proposed
piece of evidence is a verbatim quote verified against the stored document. Numbers are checked by
code, not by a language model. A named person confirms gaps and a named signer signs each state-level
determination, which is then locked to the exact rule and course versions it used.

**Status:** planning complete (2026-11-26); nothing built yet. Start with `docs/PLAN.md`.

All rule text and curriculum in this repository is fictional seed data for a prototype. Nothing here is
licensing guidance.

## Documents

| File | Purpose |
|---|---|
| `docs/ANALYSIS.md` | What the sources say, where they disagree, how Lexvay resolves it |
| `docs/PLAN.md` | Architecture, versions, cross-cutting designs, the ten milestones |
| `docs/RULES.md` | The nine product rules and the tests that enforce each |
| `docs/WORKFLOW.md` | How every milestone is planned, built, verified, recorded and committed |
| `DECISIONS.md` | Every choice the specification did not make |
| `PROGRESS.md` | Milestone plans and real results |
| `CLAUDE.md` | Rules for anyone (human or agent) working in this repository |

## Running

Prerequisites: Java 21, Node 26 (`frontend/.nvmrc`), a Docker engine with Compose v2 (Docker Desktop,
OrbStack or Colima). Maven is not needed; `backend/mvnw` downloads Maven 3.10.2 on first use.

```
cp .env.example .env   # local defaults, edit if needed
docker compose up -d   # PostgreSQL 18.7 with the lexvay_owner and lexvay_app roles
make dev               # backend on :8080, frontend on :4200 (proxies /api to the backend)
make test              # backend (Testcontainers), frontend unit tests
make seed              # demo tenant (from milestone 6)
make eval              # evaluation harness (from milestone 9)
```

## Demo tenant

`make seed` loads **Northfield University** (fictional) with the M.S. Clinical Mental Health Counseling
program, 13 documents, five "Demo rule set" rule versions (42 requirements) and a finished assessment, plus
a second tenant used only by isolation tests. Sign in at http://localhost:4200 with the password from
`LEXVAY_DEMO_PASSWORD` (default `lexvay-demo`):

| User | Role |
|---|---|
| dana.whitfield@northfield.example | Signer |
| priya.raman@northfield.example | Reviewer |
| jordan.ellis@northfield.example | Contributor |
| admin@northfield.example | Admin |

`make e2e` runs the Playwright end-to-end tests (with axe accessibility checks) against an isolated,
freshly seeded stack on ports 5433, 8081 and 4300. `make seed-pdfs` regenerates the seed PDFs from
`seed/docs`. At most two such stacks (`make e2e`, `make eval`, `make smoke`) run on one machine at a time, so
concurrent checkouts do not starve each other: the rest wait for a token. `LEXVAY_STACK_CONCURRENCY` sets the cap
(`0` turns it off) and `LEXVAY_STACK_WAIT_MINUTES` how long to wait; `make test-scripts` tests the mechanism.

Backend tests start their own PostgreSQL container. The Makefile resolves the Docker socket from the
active Docker context, so Colima and OrbStack work without setting `DOCKER_HOST`.
