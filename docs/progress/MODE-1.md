# MODE-1 progress: data mode and spend gate

Branch `mode-data-spend`, migration V022, slot 8. Decisions D-560 to D-565 are in `docs/decisions/MODE-1.md`.

## Plan (written before building)
1. V022: `tenant.data_mode`, an audit trigger for a change of mode, a spend-gate SELECT policy and index on `llm_call`.
2. Tenant code: `DataMode`, `Tenant`/`TenantRepository`, `TenantBootstrapService.createTenant(mode)`, session `demo` from the mode,
   `lexvay.demo-tenant-names` removed.
3. LIVE rules: gateway refuses the scripted model, the seed refuses LIVE tenants, a failing-closed approval hook.
4. Spend gate: `LlmClient.metered()` and `scripted()`, `LlmRates`, `TokenPrice`, `SpendGate`, `LlmUsageRepository.thisMonthExcluding`,
   `LlmRefusalAudit`, permanent job failure.
5. DEMO marker in the PDF report, register CSV and audit CSV.
6. Tests, OPERATIONS, `.env.example`, compose.

## Done
All of the plan. Changes outside the spec's list of files, each small:
- `JobRunner`: a refused model call fails the job at once.
- `RuleVersionService`: one constructor parameter and one call to `RealDataApprovalGuard`.
- `ReportService`, `RegisterService`/`RegisterCsv`, `AuditExportService`: the DEMO marker.
- `audit-sentence.ts` and its spec (the audit actions `LLM_CALL_REFUSED` and `TENANT_DATA_MODE_CHANGED`; the spec now also reads
  migrations for audit actions).
- `docker-compose.yml`: passes the three new variables with refusing defaults.
- e2e `register.spec.ts` and `records.spec.ts`: the CSVs of the seeded (DEMO) tenant start with the notice line.

## Deferred and notes
- Requirement approval in a LIVE tenant is refused until RG-1 records a dates basis (D-561); RG-1 replaces `RealDataApprovalGuard.datesBasis`.
- Evidence packages (F15) are not on this branch; F15 adds the DEMO marker (D-564).
- `infra/ecs.tf` sets provider `anthropic` and none of the new variables, so a deployment made from it refuses every paid call until
  the variables are added there. Nothing was applied (D-562).
- `AssessmentUsageService` and `EvalService` keep their own display price; folding them into `TokenPrice` is a follow-up (D-562).

## Test runs
All with `LEXVAY_E2E_SLOT=8` on 2026-11-27, tree equal to origin/main (7b04d82 plus the docs commit 4eb75fa) plus this branch.

- `make test`, first run: backend 1151 tests, 0 failures; frontend 42 files, 425 tests passed; end-to-end 55 passed, 7 skipped, 1 failed
  (`report-check.spec.ts:58`, a focus assertion on a dialog heading; the screen is untouched by this item).
- `make test`, second run: backend 1151 tests, 0 failures; frontend 425 passed; end-to-end 3 failed of a different set
  (`people.spec.ts:230`, `rules.spec.ts:167` and `rules.spec.ts:232`, which depends on the one before it), all timing of
  screen state (a prefilled field, a keyboard step) on a machine running several slots at once.
- `make e2e` alone, third run: 58 passed, 7 skipped (the screenshot tests), exit 0, the same count as main.

The end-to-end failures differed between runs and passed in the third, and none of them involves data mode, exports or the
model gate; I treated them as the load-dependent flakiness the intake mentions and did not change those specs.
