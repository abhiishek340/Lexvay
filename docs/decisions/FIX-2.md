# FIX-2 — Job entity id column and index (D-200 to D-209)

**D-200 — `job.entity_id uuid NOT NULL` holds the record a job works on.** Every job payload carried exactly one
id (`documentId`, `ruleVersionId`, `assessmentId`, `changeEventId`, `reportId`, and `ruleSourceId` from F12), and
the lookups compared that value with `payload->>'key'`. That comparison is not leakproof, and the policies of `job`
have an OR branch for the worker flag (V002, V011), so the planner could not use an expression index below them and
scanned the whole table, which only grows because jobs are never deleted (D-115). `entity_id = :id` is leakproof.
It is a stored column written by the enqueuer, not a generated column over the payload: a generated column would
need the key name per job type, which is the string key this item removes. Rejected: one column per record kind
(six nullable columns for one value).

**D-201 — Migration `V013__job_entity_id.sql`.** `NO FORCE ROW LEVEL SECURITY` on `job`, `ADD COLUMN entity_id uuid`,
backfill, `SET NOT NULL`, `FORCE ROW LEVEL SECURITY`, then the index; all in Flyway's transaction, so a failure
leaves neither the policy weakened nor the column half filled (the pattern of V012, D-231). The exemption is needed
because the backfill touches rows of every tenant and the owner is otherwise bound by `FORCE`. The backfill is
`(SELECT value::uuid FROM jsonb_each_text(payload) LIMIT 1)` for rows where `jsonb_typeof(payload) = 'object'`. A
job whose payload is not an object of one uuid value is left NULL and `SET NOT NULL` fails the migration loudly;
no such row can exist, because every enqueue path put one id in the payload object. The job triggers and policies
of V002, V003 and V011 are unchanged.

**D-202 — The index is `job_entity_idx (type, entity_id, created_at DESC)`, without `tenant_id` (orchestrator
decision, supersedes the key in the roadmap).** The roadmap specified `(tenant_id, type, entity_id, created_at
DESC)`. The implementation of FIX-2 measured it and the orchestrator accepted the change. Under the policy OR
branch (V002, V011) `tenant_id = current_tenant()` is a filter, not an index condition, so an index that leads with
`tenant_id` is walked with a skip scan over every tenant, and its cost grows with the number of tenants. Entity ids
are random uuids, so `tenant_id` adds no selectivity to the key. Measured on the `IndexUsageTest` database (about 200
tenants, 1304 pages of `job`), shared buffers of one ingest status lookup as the application role under row-level
security: 1304 for the sequential scan it replaces, 636 with the tenant-leading key, 21 to 25 with this key.
`IndexUsageTest` now records the buffers of each job lookup and fails above 100, so a key that walks every tenant
cannot return unnoticed. The tenant condition is still applied, by the policy, on the few heap rows the index
returns.

**D-203 — API.** `JobRepository.enqueue(JobType type, UUID entityId, Map<String, ?> payload)` writes both columns;
`latest(JobType, UUID entityId)` replaces `latest(JobType, String key, UUID value)`; `latestByEntity(JobType,
Collection<UUID>)` replaces `latestByPayloadValue` and keys its result by `Job.entityId()`, so it no longer selects an
extra payload column. `Job` gains `entityId` after `type`. There is no string payload key in any lookup, so the
helper that validated keys for use inside SQL text is deleted. `GET /jobs/{id}` (`JobView`) is unchanged.

**D-204 — The payload stays.** Handlers still read their input from it (`Map<String, Object> payload`), and a
future job may need more than one id. Call sites pass the id they already put in the payload: document upload,
rule version create, assessment run, change re-run, report generation, rule source check.

**D-205 — Every `payload->>` comparison in main code is replaced.** `JobRepository.latest`, `latestByEntity`,
`WorkQueueRepository.EXTRACTION_FAILED` (`x.entity_id = rv.id`) and F12's scheduler dedupe in
`RuleSourceRepository.ENQUEUE` (`j.entity_id = rs.id`; the statement also inserts `entity_id`, `rs.id`, beside the
unchanged `jsonb_build_object('ruleSourceId', rs.id)` payload). `grep -rn "payload *->>" backend/src` finds only the
migration comment. The job policies in V002 and V011 are untouched.

**D-206 — The scheduler's dedupe is served by `job_claim_idx`, not `job_entity_idx`.** `IndexUsageTest` runs
`EXPLAIN` of the exact `RuleSourceRepository.ENQUEUE` statement, as a tenant (manual check) and under the worker flag
without a tenant (`enqueueDue`, the scheduler). In both the planner reads the QUEUED and RUNNING jobs from
`job_claim_idx (status, created_at)` and filters the entity; those jobs are few, which is what the statement asks
for, and it never scans `job` sequentially. The roadmap expected `job_entity_idx` here. The test asserts what is
true and what matters (an index on `job`, no sequential scan of `job`) instead of forcing the planner's hand.

**D-207 — `IndexUsageTest`.** The two rejected payload candidates and their `EXPLAIN` rows are removed (the
statements no longer exist); `job_entity_idx` is a kept candidate for the ingest status, the extraction status and
the rule version list (`latestByEntity`); every statement fails the test on `Seq Scan on job`. `PerfSeed.addHistoryVolume`
adds finished jobs (20 re-ingests per document, 20 extractions per rule version, 30 checks per rule source), because
at the seed's few hundred jobs the planner correctly prefers a sequential scan even without row-level security
and the index could not be shown to be chosen. `ENQUEUE` and a new `DUE` predicate are public constants of
`RuleSourceRepository`, like `WorkQueueRepository.LIST`, so the test explains what the application runs. The
candidate for `requirement` named `requirement_rule_version_id_ref_key`, which V012 replaced with
`requirement_ordinal_key`; `make perf` had failed on `main` since FIX-1 and is repaired here (query `ORDER BY ordinal`).

**D-208 — The backfill is proven by running the migration's own statement.** `JobEntityIdBackfillTest` creates a
temporary table named `job` (it shadows the real one for that connection only, and is dropped in a `finally`
because the pool reuses connections), reads `V013__job_entity_id.sql` from the classpath, runs its `UPDATE`
unchanged and asserts: documentId, ruleVersionId and ruleSourceId payloads yield their value; an empty object, an
array and a string stay NULL and are not visited. `JobEntityLookupTest` covers the repository: newest job of a type
for a record, other types and records ignored, another tenant's job invisible, `latestByEntity` returning one job
per record and omitting records without one. `MigrationsApplyCleanlyTest` already counts migration files from the
classpath, so it counts V013 without change.

**D-209 — Measurements.** `docs/PERF.md` ("Job lookups (FIX-2)") has the before and after rows for
`GET /documents/{id}` and `GET /rule-versions` and the plans and buffer counts. At the seeded volume (under a
thousand jobs) the endpoints do not move beyond run-to-run noise; the change shows in the plans, which no longer scan
`job`, and in the buffer counts on the larger database.
