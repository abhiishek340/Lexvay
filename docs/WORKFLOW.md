# Lexvay — Build workflow

This is how every milestone is worked. It applies the specification's five first principles in order:
question, delete, simplify, speed up the loop, automate last.

## Order of authority

1. The specification (the "Licentraz" build prompt, renamed Lexvay).
2. `DECISIONS.md` — where the spec was silent or ambiguous and a choice was recorded.
3. `docs/PLAN.md` — how the milestones are built.
4. The architecture design ("LicenseGraph") — context only.

If any two disagree, the higher one wins. If the spec disagrees with itself, stop and ask; do not
guess silently.

## The milestone loop

```
plan  →  build with tests  →  verify  →  record  →  commit  →  next
```

### 1. Plan (before any code)
- Re-read the spec sections the milestone touches and the matching section of `docs/PLAN.md`.
- Write the milestone plan in `PROGRESS.md`: scope, the tests that will be written, the acceptance
  checks that will be run, decisions expected, anything already known to be at risk.
- Apply "question" and "delete": list what is *not* going to be built and why.

### 2. Build
- Tests are written with the code, in the same change. A feature without a test is not done.
- Only `FakeLlmClient` in automated tests. No test may need a network or an API key.
- No stubs, `TODO` markers, mocked production paths or fake success responses. If something cannot be
  finished, write that in `PROGRESS.md` and stop.
- Pin exact versions; never from memory. Check the registry when adding a dependency and record the
  pin in `DECISIONS.md` (D-002 table).
- Never invent regulatory content. Seed rule text is fictional and says so.
- No secrets in the repo. New configuration goes through `LEXVAY_*` environment variables and
  `.env.example`.

### 3. Verify
- `make test` must be fully green. Paste the real tail of the output into `PROGRESS.md`.
- Run every acceptance check in the milestone row of the spec by hand and record the real result
  (command and output, or the exact UI steps and what was seen).
- Coverage gate: `assessment`, `review`, `determination`, `change` at 85% line coverage or more,
  enforced by the build from milestone 4 onward.
- Update the Status column of `docs/RULES.md` for any rule the milestone makes enforced.

### 4. Record
- `PROGRESS.md`: what was built, test output, acceptance evidence, what was deferred and why.
- `DECISIONS.md`: every choice the spec did not make, with the alternative that was rejected.
- Anything in the spec that looks wrong goes in `DECISIONS.md` under "Spec concerns" and in the
  milestone report to the owner.

### 5. Commit
- One commit per milestone at minimum: `M<n>: <what>`. Do not push, deploy or create cloud resources.
- Never start the next milestone on a red build.

## Stop conditions (stop, write down what was tried, ask)

- A command fails twice for the same reason.
- The spec conflicts with itself or cannot be satisfied.
- A requirement cannot be met without a stub or a fake success path.
- Any action that would send tenant data, keys or personal data to an external service.

## Definition of done for a milestone

- [ ] Plan written in `PROGRESS.md` before coding.
- [ ] Every acceptance check in the spec's milestone row passes, with evidence pasted.
- [ ] `make test` green; output pasted.
- [ ] Product-rule tests for this milestone exist and pass (`docs/RULES.md`).
- [ ] New decisions recorded in `DECISIONS.md`.
- [ ] No TODOs, stubs or skipped tests in the diff.
- [ ] Committed.

## Conventions

- Product name: Lexvay. Env prefix `LEXVAY_`. Java root package `com.lexvay`. Repo root is this folder.
- Modules are packages under `com.lexvay`: `tenant`, `program`, `document`, `rule`, `assessment`,
  `review`, `determination`, `change`, `report`, `audit`, `auth`, `llm`, plus infrastructure packages
  `job` and `common` (D-019).
- Tests for product rules are named `Rule<N>_<What>Test` so the traceability matrix in `docs/RULES.md`
  can be checked by eye.
- API: REST, JSON, `/api/v1`, RFC 9457 problem details, `202 Accepted` with a job id for long work.
- UI wording: plain, direct; never "compliant" or "meets requirements"; findings shown with text and
  shape, never colour alone.
- Dates in docs are absolute (YYYY-MM-DD).
