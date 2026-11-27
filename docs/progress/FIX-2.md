# FIX-2 — Job entity id column and index

Branch `fix-job-entity-index`, migration `V013__job_entity_id.sql`, decisions D-200 to D-209 in
`docs/decisions/FIX-2.md`, measurements in `docs/PERF.md` ("Job lookups (FIX-2)").

## Done
- `job.entity_id uuid NOT NULL`, backfilled from the single value of the payload object, `job_entity_idx
  (type, entity_id, created_at DESC)`; `FORCE ROW LEVEL SECURITY` lifted for the backfill only, in the
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
    13 passed (1.2m)

`RuleSourceSchedulerLoopTest` run ten times in a row: 10 of 10 green.

Before and after for `GET /documents/{id}` and `GET /rule-versions`: `docs/PERF.md`. At the seeded volume the rows
do not move beyond noise; the plans change from sequential scans of `job` to index scans.

## Index key (orchestrator decision, D-202)
The roadmap's key led with `tenant_id`. Measured on the perf database, one lookup touched 636 shared buffers with it
(1304 for the old sequential scan) because the policies' OR branch makes `tenant_id` a filter and the index is
skip-scanned over every tenant. V013 now creates `(type, entity_id, created_at DESC)`: 24 buffers for the ingest status
and the extraction status, 28 for the rule version list. `IndexUsageTest` records these and fails above 100.
The scheduler's dedupe is served by `job_claim_idx` (queued jobs are few), never a sequential scan of `job` (D-206).
