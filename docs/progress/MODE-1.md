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
- `infra/ecs.tf` now passes `LEXVAY_LLM_PAID_APPROVED=false`, a budget of 0 and empty rates, from variables with those defaults, so a
  deployment is visibly fail-closed. Terraform is not installed on this machine, so `make infra-validate` was not run; CI's infra job validates it.
- `LEXVAY_LLM_PRICE_*_PER_MTOK` and the Terraform variables behind them are removed; `LEXVAY_LLM_RATES` is the one price table (D-562).

## After review (merge with F15, one price table, Terraform)
- Merged origin/main (F14, F15 with V020 and the shared `AuditCsv`, the setup URL fix). The two conflicts (audit export service and
  its test) keep both sides: the demo notice row, then `AuditCsv.header`.
- Evidence package: `README.txt` of a DEMO tenant starts with "Fictional demo data."; `manifest.json` has `"dataMode"` (`DEMO` or
  `LIVE`); a LIVE README is unchanged; still deterministic (`PackageApiTest`).
- `LlmRates`/`TokenPrice` are the only price table and cost formula; `AssessmentUsageService` and `EvalService` use them and the
  `LEXVAY_LLM_PRICE_*` settings and Terraform variables are removed (D-562).
- `infra/ecs.tf` and `variables.tf`: `LEXVAY_LLM_PAID_APPROVED=false`, `LEXVAY_LLM_MONTHLY_BUDGET_USD=0`, empty `LEXVAY_LLM_RATES`.
  Terraform is not installed here, so `make infra-validate` was not run; CI's infra job validates it.

## Test runs after the merge
- `LEXVAY_E2E_SLOT=8 make test`: backend 1182 tests, 0 failures; frontend 425 passed; end-to-end 44 passed, 2 failed
  (`evidence-requests.spec.ts:14` and `:170`, an Enter key on a focused toggle not expanding the row), 13 did not run.
- `make e2e` alone: 58 passed, 7 skipped, exit 0.

## Test runs before the merge
All with `LEXVAY_E2E_SLOT=8` on 2026-11-27, tree equal to origin/main (7b04d82 plus the docs commit 4eb75fa) plus this branch.

- `make test`, first run: backend 1151 tests, 0 failures; frontend 42 files, 425 tests passed; end-to-end 55 passed, 7 skipped, 1 failed
  (`report-check.spec.ts:58`, a focus assertion on a dialog heading; the screen is untouched by this item).
- `make test`, second run: backend 1151 tests, 0 failures; frontend 425 passed; end-to-end 3 failed of a different set
  (`people.spec.ts:230`, `rules.spec.ts:167` and `rules.spec.ts:232`, which depends on the one before it), all timing of
  screen state (a prefilled field, a keyboard step) on a machine running several slots at once.
- `make e2e` alone, third run: 58 passed, 7 skipped (the screenshot tests), exit 0, the same count as main.

The end-to-end failures differed between runs and passed in the third, and none of them involves data mode, exports or the
model gate; I treated them as the load-dependent flakiness the intake mentions and did not change those specs.
