# MODE-1 decisions (D-560 to D-565)

Work item MODE-1, "Data mode and spend gate" (docs/ROADMAP.md). Folded into DECISIONS.md by the orchestrator when it
merges.

**D-560 — The data mode is a column of the tenant, set by the code that creates the tenant, changed only in the
database.** V022 adds `tenant.data_mode text NOT NULL CHECK (data_mode IN ('DEMO', 'LIVE'))` with default `LIVE`, so a
tenant created without saying otherwise is the cautious one. `TenantBootstrapService.createTenant` takes the mode as a
required argument: the seed loader and the evaluation corpus pass `DEMO`, the test helper `TestTenants` creates `DEMO`
tenants (the tests answer with the scripted model, which only `DEMO` may use) and can create `LIVE` ones. The column is
added with the `DEMO` default and then the default is switched to `LIVE`, which marks the tenants that exist today
(the seed tenants and tests' tenants; nothing is deployed, D-453) without an `UPDATE` that row-level security would
not let a migration run across tenants. `lexvay.demo-tenant-names` (D-064) is removed and `/auth/session.demo` is
`data_mode = 'DEMO'`. No API changes the mode. A change is the most consequential administrative act, so a trigger
(`tenant_data_mode_audited`) writes `TENANT_DATA_MODE_CHANGED` with the old and new mode and the database role, which
keeps product rule 9 true for an act made at a SQL prompt; the procedure is in docs/OPERATIONS.md. The audit sentence
spec now also reads migrations for audit actions, so the trigger's action cannot go without a sentence.

**D-561 — A `LIVE` tenant never meets the scripted model, the seed or a fictional rule.**
- `LlmClient` gains `scripted()` and `metered()` (both default false; `FakeLlmClient` is scripted, `AnthropicLlmClient`
  is metered), so the gateway decides from what the client is, not from a provider name. `LlmGateway.complete` refuses
  a scripted client for a `LIVE` tenant with "No model is configured for real data". The tenant's mode is read only
  when the client is scripted, so a call to a real model costs no extra query.
- `SeedLoader` refuses a `LIVE` tenant that holds a seed admin email, even though it would otherwise skip it quietly:
  an unexpected `LIVE` tenant under a seed address is a mistake someone should see.
- `RealDataApprovalGuard` is called by `approveRequirements`. A `DEMO` tenant approves as before. In a `LIVE` tenant the
  version needs a recorded dates basis that does not contain "Fictional demo data" (case-insensitive). RG-1 (V021,
  `dates_basis`) has not merged, so nothing records a basis and the guard's one private method, `datesBasis`, finds
  none: every approval in a `LIVE` tenant is refused with a 409. That is the failing-closed hook the spec asks for;
  RG-1 replaces the body of that one method with a read of the newest `rule_version_status` row and the guard's tests
  then also cover a real basis.
- Reports (the header block of the PDF), the register CSV and the audit CSV of a `DEMO` tenant carry "Fictional demo
  data" (D-564).

**D-562 — The spend gate decides before anything is sent, from configuration and from recorded cost.** `SpendGate`
applies to a client whose `metered()` is true. It refuses unless `LEXVAY_LLM_PAID_APPROVED` is `true` (default
`false`), the model has a price in `LEXVAY_LLM_RATES`, and recorded cost this UTC month plus this call's largest cost is
at most `LEXVAY_LLM_MONTHLY_BUDGET_USD` (default `0`; equal to the budget is allowed, and a budget of zero refuses every
call because a call always costs something).
- The price table is `model=inputPerMtok:outputPerMtok` entries in one variable (`LlmRates`). Spring's map binding
  from environment variables mangles model ids, and the variable is easy to read in `.env`. There are no built-in
  prices: a price in the code goes stale without anyone noticing, so the default table is empty and every cost is
  unknown. Prices must be positive, an entry that cannot be read stops the start, and a model that appears in this
  month's recorded calls without a price makes the month's cost unknown, so the call is refused (a model whose
  recorded calls carry no tokens, such as fixture rows, costs nothing whatever its price).
- A call's largest cost is `maxOutputTokens` at the output price plus one input token per UTF-8 byte of the system
  prompt, the user prompt and the output schema at the input price, rounded up to a millionth of a dollar. A token is
  never shorter than a byte, so the input figure is an upper bound; the model never counts anything (rule 3).
- "Recorded" is `LlmUsageRepository.thisMonthExcluding`: the sum of stored input and output tokens per model since the
  start of the UTC month, leaving out the scripted model, whose calls are free. The budget belongs to the deployment's
  one provider account, so it adds up every tenant. A second SELECT policy on `llm_call` (V022) is honoured only under
  the transaction-local flag `lexvay.spend_gate`, the same pattern as the job worker's flag (D-013). The query runs in
  its own transaction so the flag does not outlive it inside a job's transaction, and it reads totals only, never the
  stored input or output. `llm_call_created_idx` serves it.
- What the gate cannot see: a call that failed at the provider is not stored, and several instances can pass the budget
  together by the calls in flight. The budget is a guard rail; OPERATIONS says to set a spending limit with the
  provider as well.
- `TokenPrice` holds one price and its cost formula. `AssessmentUsageService` and `EvalService` keep their own
  single-model display price and formula (their rounding differs); they were left alone to keep this change out of
  other files, and folding them into `TokenPrice` is a safe follow-up.

**D-563 — A refused call is an audited, permanent failure that carries no content.** `LlmCallBlockedException` extends
`LlmRefusedException` with a closed `Reason` (`NO_MODEL_FOR_REAL_DATA`, `PAID_CALLS_NOT_APPROVED`, `COST_UNKNOWN`,
`OVER_BUDGET`). The gateway counts it as `refused` in the metrics, records `LLM_CALL_REFUSED` (reason, message, purpose
and model; entity type `llm_call`) in its own transaction through `LlmRefusalAudit`, so the entry survives the job's
rollback, and logs one warning line with the reason, purpose and model. `JobRunner` treats it like
`PermanentJobFailure`: the job fails on the first attempt (retrying cannot change the answer until someone changes
the configuration), with the message as its error and the usual `JOB_FAILED` entry. A refusal by the model itself
(`stop_reason: refusal`) is unchanged. `eval-live` goes through the same gate.

**D-564 — A `DEMO` tenant's outputs say so in their header.** The PDF report adds a first header line "Data: Fictional
demo data". The register CSV and the audit CSV start with one record holding that text, above the column labels. A
`LIVE` tenant's files are as before. Package files (F15) are not on this branch: F15 must add the same marker with
`DataMode.DEMO_NOTICE` when it merges, and a test beside its own.

**D-565 — One startup line states the money posture.** `SpendGate` logs once when the application starts, for every
provider including `fake`: the provider, whether paid calls are approved, the monthly budget and the models that have a
price. It never logs a key or a price list value beyond the model names.
