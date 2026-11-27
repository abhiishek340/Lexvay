# FIX-2 — Job entity id column and index

Branch `fix-job-entity-index`, migration `V013__job_entity_id.sql`, decisions D-200 to D-209 in
`docs/decisions/FIX-2.md`, measurements in `docs/PERF.md` ("Job lookups (FIX-2)").

## Done
- `job.entity_id uuid NOT NULL`, backfilled from the single value of the payload object, `job_entity_idx
  (tenant_id, type, entity_id, created_at DESC)`; `FORCE ROW LEVEL SECURITY` lifted for the backfill only, in the
  migration's transaction.
- `JobRepository.enqueue(type, entityId, payload)`, `latest(type, entityId)`, `latestByEntity(type, ids)`;
  `Job.entityId`. Six call sites pass the id they already put in the payload.
- Every `payload->>` comparison in main code replaced by `entity_id`: job lookups, the work queue's failed extraction
  test, the rule source scheduler's dedupe. Policies of V002 and V011 unchanged.
- Tests: `JobEntityIdBackfillTest` (the migration's own UPDATE on a temporary table), `JobEntityLookupTest`,
  `IndexUsageTest` (`job_entity_idx` for the three lookups; the dedupe and scheduler statements; fails on any
  `Seq Scan on job`).
- Also: `IndexUsageTest` named `requirement_rule_version_id_ref_key`, which V012 replaced, so `make perf` was red on
  `main`; the candidate now orders by `ordinal` and expects `requirement_ordinal_key`.
  `RuleSourceSchedulerLoopTest` registers and deactivates its inactive source in one transaction (the running
  scheduler could queue it in between) and counts jobs by `entity_id`.

## Evidence (2026-11-26, Docker via Colima, after merging `origin/main`)
`make backend-test`:

    Tests run: 792, Failures: 0, Errors: 0, Skipped: 0
    BUILD SUCCESS

`make perf` (EndpointLatencyTest, then IndexUsageTest):

    Tests run: 1, Failures: 0, Errors: 0, Skipped: 0   (EndpointLatencyTest)
    BUILD SUCCESS
    Tests run: 1, Failures: 0, Errors: 0, Skipped: 0   (IndexUsageTest)
    BUILD SUCCESS

`LEXVAY_E2E_SLOT=5 make e2e`:

    1 skipped
    13 passed (56.5s)

`RuleSourceSchedulerLoopTest` run ten times in a row: 10 of 10 green.

Before and after for `GET /documents/{id}` and `GET /rule-versions`: `docs/PERF.md`. At the seeded volume the rows
do not move beyond noise; the plans change from sequential scans of `job` to index scans.

## Disagreement
The roadmap's index key leads with `tenant_id`. Under the job policies' OR branch that column is a filter, so the
index is skip-scanned over all tenants: 636 buffers per lookup on the perf database against 21 to 25 for
`(type, entity_id, created_at DESC)` and 1304 for the old sequential scan (D-202). Implemented as specified; the
recommendation is to change the key before V013 is first deployed. The roadmap also expected the scheduler's dedupe to
use `job_entity_idx`; the planner uses `job_claim_idx` (queued jobs are few) and never scans `job` (D-206).
