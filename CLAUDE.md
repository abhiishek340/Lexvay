# Lexvay — rules for working in this repository

Lexvay compares a university's curriculum against state professional-licensing requirements and
produces an evidence-backed record that qualified university staff review and sign. The software
proposes; people decide. Read `docs/ANALYSIS.md`, `docs/PLAN.md`, `docs/RULES.md` and
`docs/WORKFLOW.md` before changing anything.

## Order of authority
1. The specification (the "Licentraz" build prompt; the product is now named Lexvay).
2. `DECISIONS.md`. 3. `docs/PLAN.md`. 4. The "LicenseGraph" architecture design (context only).
If the spec conflicts with itself, stop and ask. Do not guess silently.

## First principles, in order, for every piece of work
Question the requirement → delete what no acceptance check needs → simplify (one process, one
database, plain code) → keep `make test` fast and local → automate only what already works by hand.

## Product rules that must never be broken (each has a test; see docs/RULES.md)
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

## Working rules
- One milestone at a time (`PROGRESS.md`). Plan first, then build with tests, then paste real results.
- Never start a milestone on a red build. Never claim a test passed without running it.
- Only `FakeLlmClient` in tests. No test touches the network or needs a key.
- No stubs, `TODO`s, mocked production paths or fake success responses. If blocked, say so and stop.
- Pin exact versions from the registry, never from memory; record them in `DECISIONS.md` D-002.
- Never invent regulatory content. All rule text and curriculum here is fictional and labeled so.
- No secrets in the repo. `LEXVAY_*` env vars, documented in `.env.example`.
- Commit at the end of each milestone. Do not push, deploy or create cloud resources.
- If a command fails twice for the same reason, stop and report what was tried.
- UI never says "compliant" or "meets requirements". State is shown by text and shape, not colour alone.

## Stack (pinned in DECISIONS.md D-002)
Java 21, Spring Boot 4.1.3, Spring JDBC (no ORM), PostgreSQL 18.7 with Flyway and RLS, job table with
`FOR UPDATE SKIP LOCKED`, PostgreSQL full-text search behind `PassageSearch`, `FileStore` (local | S3),
`LlmClient` (`FakeLlmClient` | `AnthropicLlmClient`), Angular 22 standalone + CDK only, Playwright +
axe, Terraform (validate only).

## Layout
`backend/` `frontend/` `eval/` `seed/` `infra/` `docs/` `docker-compose.yml` `Makefile`
`README.md` `DECISIONS.md` `PROGRESS.md` `.env.example`

## Commands
`docker compose up -d` · `make dev` · `make test` · `make seed` · `make eval` · `make eval-live`
