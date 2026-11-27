# FIX-1 — Requirement order column

Branch `fix-requirement-ordinal`, migration `V012__requirement_ordinal.sql`, decisions D-230 to D-238 in
`docs/decisions/FIX-1.md`.

## Done
- `requirement.ordinal` (backfilled, `NOT NULL`, `UNIQUE (rule_version_id, ordinal)`); migration lifts and restores
  `FORCE ROW LEVEL SECURITY` and the `requirement_locked` trigger around the backfill.
- Extraction and the replace endpoint store list position from 1 (`RuleVersionRepository.insertRequirements`).
- `Requirement.ordinal`; requirement and mapping listings order by `ordinal`; the diff follows it.
- Tests: `RequirementOrderApiTest` (twelve requirements through requirements, mappings and diff; replace keeps
  submitted order), `RequirementOrdinalBackfillTest` (the migration's own UPDATE on a temporary table).

## Evidence
`make backend-test` (2026-11-26, Docker via Colima):

    Tests run: 492, Failures: 0, Errors: 0, Skipped: 0
    BUILD SUCCESS

`LEXVAY_E2E_SLOT=5 make e2e`:

    1 skipped
    6 passed (23.2s)

(the skipped test is `screens.spec.ts`, the screenshot spec.)
