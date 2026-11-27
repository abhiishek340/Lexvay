# Read-path performance

Budget (CLAUDE.md): p95 under 40 ms for every read endpoint on the seeded tenant. `make perf` enforces it and
runs the index check; `make backend-test` runs everything else. Decisions and rejected alternatives:
`DECISIONS.md (D-110 to D-119)` (D-110 to D-119).

## How it is measured

`backend/src/test/java/com/lexvay/perf/EndpointLatencyTest.java`, JUnit tag `perf`, excluded from the default
run. It starts the application on a random port against a PostgreSQL 18.7 Testcontainer, loads the fictional seed
with `SeedLoader`, signs in over real HTTP (session cookie, CSRF cookie and header, `Accept-Encoding: gzip` like a
browser), signs one jurisdiction through the API so that determination, review and change views have data, then for
each endpoint sends 50 warm-up requests and times 300 sequential ones. It prints p50, p95 and p99 and fails when any
p95 is over 40 ms. It runs the whole list twice: on the two seeded tenants, and again after loading 30 renamed
copies of both (62 tenants, planner statistics refreshed). "Connections per call" is the number of checkouts from
the connection pool, counted by wrapping the data source.

Each table below is the median of three runs of the same code (the run whose p95 values sum to the median of the
three sums). Other agents were building on this machine during every run, so absolute numbers are noisy by a
factor of two; the column "worst p95 of 3 runs after" shows the budget also holds in the noisiest run.
Results are in milliseconds on the loopback interface (no network latency, so compression and fewer round trips
show up less than they would in production).

Before = commit `91401aa` (benchmark added, application code unchanged). After = this branch.
The benchmark list grew between the two (new endpoints, and the ones merged from main: search, rule version
diff, evidence requests); rows marked "new" have no before.

## Result on the seeded tenants

| Endpoint | connections per call before | after | p50 ms before | after | p95 ms before | after | p99 ms before | after | worst p95 of 3 runs after | bytes on the wire before | after |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| GET /auth/me | 0.0 | 0.0 | 0.74 | 0.72 | 2.07 | 1.82 | 2.87 | 2.56 | 4.78 | 186 | 186 |
| GET /jurisdictions | 1.0 | 1.0 | 1.29 | 2.34 | 1.84 | 3.89 | 3.18 | 5.45 | 6.08 | 3911 | 1786 |
| GET /programs | 1.0 | 1.0 | 1.04 | 1.49 | 1.64 | 2.78 | 2.12 | 3.52 | 5.60 | 305 | 304 |
| GET /programs/{id} | 1.0 | 1.0 | 1.03 | 1.31 | 1.30 | 2.17 | 1.54 | 3.48 | 7.19 | 303 | 302 |
| GET /programs/{id}/courses | 3.0 | 1.0 | 2.37 | 1.96 | 3.65 | 5.06 | 5.12 | 7.19 | 7.60 | 4366 | 1056 |
| GET /programs/{id}/facts | 2.0 | 1.0 | 1.52 | 1.57 | 1.91 | 1.99 | 2.61 | 2.81 | 11.70 | 1390 | 593 |
| GET /programs/{id}/determinations | 3.0 | 1.0 | 2.10 | 1.51 | 2.58 | 1.79 | 3.86 | 2.15 | 9.58 | 1039 | 669 |
| GET /change-events | 5.0 | 1.0 | 4.12 | 1.25 | 6.38 | 2.67 | 7.80 | 3.27 | 8.76 | 934 | 934 |
| GET /rule-versions | 13.0 | 1.0 | 10.47 | 1.58 | 16.67 | 3.04 | 27.30 | 3.56 | 13.01 | 3439 | 963 |
| GET /rule-versions/{id}/requirements | 2.0 | 1.0 | 2.25 | 1.42 | 4.42 | 2.03 | 6.29 | 2.96 | 10.29 | 2761 | 923 |
| GET /assessments/{id} | 2.0 | 1.0 | 3.36 | 2.79 | 5.95 | 5.84 | 7.09 | 7.68 | 6.08 | 790 | 789 |
| GET /assessments/{id}/mappings?jurisdiction=CA | 2.0 | 1.0 | 2.92 | 2.05 | 5.36 | 4.81 | 7.44 | 11.18 | 6.95 | 9599 | 2779 |
| GET /assessments/{id}/mappings (all) | 2.0 | 1.0 | 3.97 | 6.03 | 6.18 | 11.62 | 7.69 | 19.23 | 11.62 | 45654 | 7576 |
| GET /documents/{id} | 3.0 | 1.0 | 2.45 | 4.00 | 4.41 | 5.35 | 5.06 | 6.52 | 7.32 | 492 | 493 |
| GET /audit?limit=100 | 1.0 | 1.0 | 1.21 | 1.60 | 2.51 | 3.79 | 3.87 | 5.09 | 6.42 | 30846 | 7821 |
| GET /audit?action=REVIEW_RECORDED&entityType=mapping (new) | n/a | 1.0 | n/a | 0.86 | n/a | 1.06 | n/a | 1.22 | 5.90 | n/a | 1002 |
| GET /programs/{id}/overview (new) | n/a | 1.0 | n/a | 3.45 | n/a | 3.88 | n/a | 4.25 | 8.71 | n/a | 1379 |
| GET /programs/{id}/assessments (new) | n/a | 1.0 | n/a | 1.05 | n/a | 1.30 | n/a | 1.58 | 3.49 | n/a | 344 |
| GET /documents/{id}/pages/1 (new) | n/a | 1.0 | n/a | 1.58 | n/a | 2.75 | n/a | 3.08 | 2.75 | n/a | 1178 |
| GET /mappings/{id}/reviews (new) | n/a | 1.0 | n/a | 1.35 | n/a | 2.92 | n/a | 4.70 | 2.92 | n/a | 202 |
| GET /mappings/{id}/evidence-responses (new) | n/a | 1.0 | n/a | 1.11 | n/a | 1.34 | n/a | 3.56 | 4.08 | n/a | 2 |
| GET /programs/{id}/evidence-requests?status=ALL (new) | n/a | 1.0 | n/a | 1.80 | n/a | 4.01 | n/a | 4.75 | 4.47 | n/a | 2 |
| GET /programs/{id}/search?q=ethics (new) | n/a | 1.0 | n/a | 3.49 | n/a | 5.33 | n/a | 5.89 | 5.36 | n/a | 1331 |
| GET /rule-versions/{id}/diff (new) | n/a | 1.0 | n/a | 4.37 | n/a | 9.08 | n/a | 12.11 | 9.08 | n/a | 1619 |
| Program screen today: program + determinations + change-events (3 requests) | 9.0 | 3.0 | 7.94 | 8.75 | 17.57 | 14.60 | 36.16 | 25.38 | 14.60 | 934 | 934 |

## Result with 62 tenants loaded

| Endpoint | connections per call before | after | p50 ms before | after | p95 ms before | after | p99 ms before | after | worst p95 of 3 runs after | bytes on the wire before | after |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| GET /auth/me | 0.0 | 0.0 | 0.12 | 0.30 | 0.21 | 0.52 | 0.34 | 0.81 | 0.52 | 186 | 186 |
| GET /jurisdictions | 1.0 | 1.0 | 0.83 | 1.81 | 1.54 | 2.63 | 2.02 | 3.22 | 2.63 | 3911 | 1786 |
| GET /programs | 1.0 | 1.0 | 0.82 | 2.41 | 0.96 | 3.26 | 1.27 | 3.75 | 3.26 | 305 | 304 |
| GET /programs/{id} | 1.0 | 1.0 | 1.05 | 2.20 | 1.71 | 3.72 | 1.96 | 4.31 | 4.59 | 303 | 302 |
| GET /programs/{id}/courses | 3.0 | 1.0 | 3.70 | 2.50 | 5.67 | 7.21 | 7.09 | 11.09 | 7.21 | 4366 | 1056 |
| GET /programs/{id}/facts | 2.0 | 1.0 | 1.54 | 3.13 | 4.82 | 4.34 | 6.82 | 4.85 | 4.34 | 1390 | 593 |
| GET /programs/{id}/determinations | 3.0 | 1.0 | 4.18 | 2.61 | 5.61 | 3.93 | 6.59 | 4.60 | 3.93 | 1039 | 669 |
| GET /change-events | 5.0 | 1.0 | 3.26 | 2.94 | 3.85 | 4.69 | 5.04 | 5.92 | 4.69 | 934 | 934 |
| GET /rule-versions | 13.0 | 1.0 | 8.78 | 4.15 | 15.85 | 12.32 | 22.75 | 30.05 | 12.32 | 3439 | 963 |
| GET /rule-versions/{id}/requirements | 2.0 | 1.0 | 1.40 | 2.24 | 1.66 | 4.40 | 2.20 | 5.40 | 4.52 | 2761 | 923 |
| GET /assessments/{id} | 2.0 | 1.0 | 1.55 | 2.06 | 2.16 | 4.13 | 3.40 | 5.55 | 4.13 | 790 | 789 |
| GET /assessments/{id}/mappings?jurisdiction=CA | 2.0 | 1.0 | 2.10 | 2.08 | 2.50 | 3.95 | 3.02 | 4.78 | 4.11 | 9599 | 2779 |
| GET /assessments/{id}/mappings (all) | 2.0 | 1.0 | 2.39 | 7.16 | 3.34 | 12.29 | 3.97 | 19.92 | 12.29 | 45654 | 7576 |
| GET /documents/{id} | 3.0 | 1.0 | 2.40 | 3.14 | 4.00 | 5.35 | 4.97 | 6.92 | 5.35 | 492 | 493 |
| GET /audit?limit=100 | 1.0 | 1.0 | 1.03 | 1.62 | 2.14 | 4.66 | 3.78 | 10.58 | 4.66 | 30846 | 7821 |
| GET /audit?action=REVIEW_RECORDED&entityType=mapping (new) | n/a | 1.0 | n/a | 1.19 | n/a | 2.17 | n/a | 2.81 | 2.17 | n/a | 1002 |
| GET /programs/{id}/overview (new) | n/a | 1.0 | n/a | 3.92 | n/a | 8.10 | n/a | 12.27 | 8.10 | n/a | 1379 |
| GET /programs/{id}/assessments (new) | n/a | 1.0 | n/a | 1.53 | n/a | 2.26 | n/a | 3.87 | 3.54 | n/a | 344 |
| GET /documents/{id}/pages/1 (new) | n/a | 1.0 | n/a | 1.18 | n/a | 1.57 | n/a | 1.93 | 2.63 | n/a | 1178 |
| GET /mappings/{id}/reviews (new) | n/a | 1.0 | n/a | 1.36 | n/a | 4.21 | n/a | 5.85 | 4.21 | n/a | 202 |
| GET /mappings/{id}/evidence-responses (new) | n/a | 1.0 | n/a | 1.32 | n/a | 1.73 | n/a | 2.30 | 6.53 | n/a | 2 |
| GET /programs/{id}/evidence-requests?status=ALL (new) | n/a | 1.0 | n/a | 1.98 | n/a | 3.87 | n/a | 5.09 | 5.28 | n/a | 2 |
| GET /programs/{id}/search?q=ethics (new) | n/a | 1.0 | n/a | 2.05 | n/a | 2.92 | n/a | 3.96 | 4.56 | n/a | 1331 |
| GET /rule-versions/{id}/diff (new) | n/a | 1.0 | n/a | 3.54 | n/a | 9.50 | n/a | 15.13 | 9.50 | n/a | 1619 |
| Program screen today: program + determinations + change-events (3 requests) | 9.0 | 3.0 | 5.63 | 5.36 | 8.38 | 14.16 | 11.16 | 19.24 | 14.16 | 934 | 934 |

### What changed the numbers

- Connections per call fall to exactly one for every read endpoint (`ReadPathConnectionsTest` asserts it). Each
  checkout before cost three round trips (`set_config`, the statement, `RESET` on return), so the rule version
  list (13 checkouts) made about 39 round trips and the change event list (5 checkouts) about 15; each now makes
  about three plus one per extra statement. The round trip counts are derived from the code, not measured.
- The N+1 removals show in the rule version list (13 to 1 connection, 8 ms to under 2 ms at p50), the change event
  list (5 to 1), determinations (3 to 1) and the program screen (9 connections for three requests before; the overview
  is one request and one connection).
- Single-statement reads (programs, audit, jurisdictions, auth) were one checkout before and are one now, with the
  same number of round trips. Their p50 and p95 differences in the tables are noise: with load averages around 5
  from other builds, the same endpoint on identical code varies by up to a factor of two between runs (compare the
  three before runs, `GET /documents/{id}` p50 1.9, 2.5 and 5.1 ms). Only the connection count, the multi-statement
  endpoints and the byte counts are reliable signals here. The budget has a large margin everywhere.
- Compression makes the 30 to 45 kB responses (mappings, audit) 4 to 6 times smaller on the wire (the last
  column). On loopback it only costs CPU: the 45 kB mappings response is about 1 to 2 ms slower at p50 after, in all
  three runs, and still far inside the budget. Over a real network the saved bytes outweigh that.

- The rate-limit and security-headers filters merged from WS-SEC run on every request in the final benchmark
  run (the tables above were measured before that merge; a full `make perf` after it passes, worst p95 15 ms
  under a load average of 6 to 7). `GET /auth/me` touches no database, so it shows the filters' cost: p50 0.27 and
  0.88 ms on the two passes, against 0.12 to 0.74 ms in the earlier runs. The difference is inside the run-to-run
  noise; the filters cost well under 1 ms. `ReadPathConnectionsTest` still sees exactly one checkout per read.

## One connection per request

`@Transactional(readOnly = true)` on the read service methods, no close-time `RESET` in `TenantAwareDataSource`,
and the proxy that issued it removed. Proof that tenant isolation stays fail closed:
`Rule7_PooledConnectionRebindsTest` (one physical connection lent to tenant A, nobody, tenant B, nobody).
All `Rule7_*` tests are unchanged and green.

## Indexes

`backend/src/main/resources/db/migration/V008__read_path_indexes.sql`. Verified by
`backend/src/test/java/com/lexvay/perf/IndexUsageTest.java`: it loads the seed plus 100 renamed copies of both
tenants (about 200 tenants), adds synthetic history rows (20 extra assessments per
program and, for each assessment, two determinations per rule version, inserted by the container's superuser because the seed never
produces them), runs `ANALYZE`, then runs `EXPLAIN` of each query as the application role under row-level security.
It fails if the planner does not use a kept index, or if it starts using a rejected one.

### Kept (9 of the 14 candidates; the planner chose each)

| Index | Query it serves | Planner uses it (application role, row-level security) |
|---|---|---|
| `mapping_assessment_jurisdiction_idx` | mappings of one jurisdiction in an assessment (list, counts) | yes |
| `mapping_requirement_idx` | mark mappings stale when a rule version is superseded | yes |
| `mapping_evidence_span_idx` | mark mappings stale when a course version is superseded | yes |
| `determination_program_jurisdiction_status_idx` | is there a signed determination for the jurisdiction | yes |
| `determination_rule_version_idx` | reopen determinations of a superseded rule version | yes |
| `document_course_version_idx` | documents of a course version | yes |
| `evidence_span_document_idx` | spans cited from the documents of a course version | yes |
| `audit_log_entity_idx` | audit history of one record | yes |
| `assessment_program_status_finished_idx` | latest finished assessment of a program | yes |

`requirement (rule_version_id)`: not created. The existing unique index `requirement_ordinal_key` (V012,
`(rule_version_id, ordinal)`) serves it (planner: Bitmap Heap Scan on requirement; Bitmap Index Scan on
requirement_ordinal_key; Bitmap Index Scan on requirement_tenant_idx). Before V012 it was
`requirement_rule_version_id_ref_key`; `IndexUsageTest` still named that index and had failed on `main` since FIX-1.

### Dropped (the planner never chose them with row-level security)

| Candidate (created only inside IndexUsageTest) | Query | Planner with row-level security | Planner without row-level security |
|---|---|---|---|
| `rejected_document_program_idx` | documents of a program (unique index tenant_id, program_id, sha256 serves it) | Index Scan using document_tenant_id_program_id_sha256_key on document | Bitmap Heap Scan on document; Bitmap Index Scan on rejected_document_program_idx |
| `rejected_change_event_status_created_idx` | open change events, newest first | Bitmap Heap Scan on change_event; Bitmap Index Scan on change_event_tenant_idx | Seq Scan on change_event |

The two job candidates that stood here (an expression index on `payload->>'documentId'` and one on
`payload->>'ruleVersionId'`) are gone: FIX-2 replaced the payload comparison with a column, see the next section.

## Job lookups (FIX-2)

`V013__job_entity_id.sql` adds `job.entity_id uuid NOT NULL` (the one record a job works on) and
`job_entity_idx (type, entity_id, created_at DESC)` (D-202). `JobRepository.latest`, `latestByEntity`, the
work queue's failed extraction test and the rule source scheduler's "already queued" test compare `entity_id = ...`,
a leakproof comparison, instead of `payload->>'key'`. `IndexUsageTest` runs `EXPLAIN` as the application role under
row-level security on about 200 tenants, with synthetic finished jobs (20 re-ingests per document, 20 extractions per
rule version, 30 checks per rule source, as the seed never produces them) and fails on a sequential scan of `job`:

| Statement | Before (payload) | After (`entity_id`) |
|---|---|---|
| ingest status of a document (`GET /documents/{id}`) | Seq Scan on job | Index Scan using job_entity_idx on job |
| extraction status of a rule version (`GET /rule-versions/{id}`) | Seq Scan on job | Index Scan using job_entity_idx on job |
| extraction status of every rule version (`GET /rule-versions`) | Seq Scan on job | Index Scan using job_entity_idx on job |
| a check of a source already queued, scheduler (all tenants) | not measured (payload comparison) | Index Scan using job_claim_idx on job j (see below) |
| a check of one source already queued, manual (`rs.id = ...`) | not measured (payload comparison) | Index Scan using rule_source_pkey on rule_source rs; Index Scan using job_entity_idx on job j |

The "before" plans are from the WS-PERF run recorded above (the payload expression index was never chosen under
row-level security) and, for the lookups, from running the old statement against the new database: a sequential scan
of the 1304 pages of `job`. The scheduler's dedupe (every due source, so no single entity to look up) uses `job_claim_idx (status, created_at)`:
it only looks for QUEUED or RUNNING jobs, which are few, so the planner reads those by status and filters the entity.
The manual check names one source and the planner reads that source's jobs through `job_entity_idx` (the statement
was listed here with `job_claim_idx`; the plan has been different since FIX-2 and `IndexUsageTest` now accepts either
index for it). Neither reads the whole table, which is what the test asserts.

Latency of the two endpoints, `make perf`, one run each (not a median of three; the machine was shared with other
builds, so differences of a factor of two are noise). The seeded database holds under a thousand jobs, so a
sequential scan costs nothing measurable there and none of these rows is expected to move:

| Endpoint | Pass | p50 ms before | after | p95 ms before | after | p99 ms before | after | connections per call before | after |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| GET /documents/{id} | seed (2 tenants) | 1.87 | 1.53 | 2.65 | 2.41 | 3.37 | 3.72 | 1.0 | 1.0 |
| GET /documents/{id} | 62 tenants | 3.75 | 1.38 | 6.84 | 2.01 | 13.15 | 2.77 | 1.0 | 1.0 |
| GET /rule-versions | seed (2 tenants) | 1.81 | 1.30 | 3.78 | 1.49 | 4.37 | 1.59 | 1.0 | 1.0 |
| GET /rule-versions | 62 tenants | 4.22 | 1.24 | 10.41 | 1.59 | 22.51 | 2.39 | 1.0 | 1.0 |

Read the table with the noise in mind: the same code measured the 62-tenant document endpoint at p50 3.75 ms in one
run and 6.02 ms in the next, so the improvement at 62 tenants is partly a quieter machine (the after run is the last
of three; the earlier two, with the previous index key, gave p95 11.18 and 4.60 ms). Every p95 is far inside the
40 ms budget before and after. What changed is the work per request as the table grows. `IndexUsageTest` records
the shared buffers of `EXPLAIN (ANALYZE, BUFFERS)` of each job lookup, as the application role under row-level
security on its database, and fails above 100:

| Access path | Ingest status of a document | Extraction status of a rule version | Rule version list (`latestByEntity`) |
|---|---:|---:|---:|
| sequential scan of `job` (the old payload statement) | 1304 | not measured | not measured |
| `(tenant_id, type, entity_id, created_at DESC)` (roadmap key, first implementation) | 636 | not measured | not measured |
| `job_entity_idx (type, entity_id, created_at DESC)` (V013, final) | 24 | 24 | 28 |

The first key was chosen by the planner and removed the sequential scan, but only halved the work: the policies of
`job` have an OR branch for the worker flag, so `tenant_id = current_tenant()` is a filter, not an index condition,
and PostgreSQL 18 walked the index with a skip scan over every tenant. Without `tenant_id` the lookup is one descent
(D-202). The final key's plan is a Bitmap Heap Scan over `job_entity_idx` for each lookup, with no sequential scan.

## Work queue (F13)

`GET /work-queue` and `GET /work-queue/count` (D-190 to D-199, `DECISIONS.md (F13)`): one statement and one
connection each, signer role (it sees five of the seven kinds). One `make perf` run (not a median of three),
milliseconds on loopback:

| Pass | Endpoint | p50 | p95 | p99 | connections per call | wire bytes |
|---|---|---:|---:|---:|---:|---:|
| 2 seeded tenants | GET /work-queue | 1.70 | 3.05 | 5.09 | 1.0 | 997 |
| 2 seeded tenants | GET /work-queue/count | 1.56 | 2.73 | 3.82 | 1.0 | 12 |
| 62 tenants | GET /work-queue | 3.47 | 5.80 | 8.07 | 1.0 | 997 |
| 62 tenants | GET /work-queue/count | 3.90 | 15.36 | 24.94 | 1.0 | 12 |

The 15 ms p95 of the count on the 62-tenant pass is one noisy run (its p50 is 3.9 ms); the budget is 40 ms.
A second full run after the last merge of main (rule source endpoints in the list), same machine: seed
`/work-queue` p50 4.07, p95 14.15, p99 25.32 and count p95 4.22; 62 tenants `/work-queue` p95 3.74 and count
p95 3.77. Variation between runs is machine noise; every p95 is far inside the budget.

Plan: `IndexUsageTest` runs `EXPLAIN (ANALYZE, BUFFERS)` of the exact statement as the application role under
row-level security on about 200 tenants with synthetic review history, for each role, and fails on a sequential scan
of `mapping`, `review_event` or `job` (plans in `backend/target/perf/work-queue-plan.txt`). Execution time
1.7 to 2.0 ms per role, planning 1.2 to 2.2 ms. Mappings use `mapping_assessment_jurisdiction_idx` and
`mapping_tenant_idx`, review events `review_event_mapping_idx`, the latest finished assessment
`assessment_program_status_finished_idx`, failed extraction jobs the `job_claim_idx` status condition, signed
determinations `determination_program_jurisdiction_status_idx`. The only sequential scan is the 51-row `jurisdiction`
table. No index is needed, so there is no `V014__work_queue_indexes.sql`. A first run before the history volume
existed showed a sequential scan of `review_event`: the table was empty and the planner ignores indexes on empty
tables, which is why `PerfSeed.addHistoryVolume` now adds review events. The cost of `MappingRepository.CURRENT` (D-199) was measured and removed in FIX-3, next section.

## Frontend bundle (PERF-FE-1)

Method: `cd frontend && npx ng build --stats-json --verbose` (production configuration, Angular 22.3.0); sizes are
raw bytes and Angular's estimated transfer (gzip). The breakdown groups the esbuild metafile (`browser-stats.json`)
by package for the `main` chunk. "Before" is `origin/main` at 611d0a0 (F8 merged), "after" is this branch.

| | before raw | after raw | before transfer | after transfer |
|---|---:|---:|---:|---:|
| Initial total | 703.12 kB | 695.03 kB | 169.43 kB | 168.22 kB |
| main.js | 695.67 kB | 687.58 kB | 167.84 kB | 166.62 kB |
| styles.css | 7.45 kB | 7.45 kB | 1.60 kB | 1.60 kB |

Named lazy chunks (raw / transfer, kB). A chunk now carries its screen's route file, token sets and the shared
modules only it uses; the screens that use a module together keep sharing the unnamed chunks.

| Chunk | before | after |
|---|---:|---:|
| rules | 209.98 / 45.83 | 211.28 / 46.30 |
| review | 53.47 / 14.08 | 55.69 / 14.65 |
| register | 30.25 / 8.04 | 30.66 / 8.16 |
| changes | 14.62 / 4.83 | 15.22 / 5.04 |
| report | 14.03 / 4.30 | 14.23 / 4.39 |
| overview | 12.40 / 3.96 | 12.70 / 4.08 |
| login, audit, home | 29.69, 7.38, 2.69 | 29.69, 7.39, 2.69 |

After merging F4 (the evidence request screens, PR #28) the initial bundle is 698.75 kB raw / 168.84 kB transfer
(`requests-routes` 12.59 kB / 4.27 kB); the table above and the breakdown below are from before that merge.

The budget is unchanged at 700 kB warning and 800 kB error: the initial bundle is not under 640 kB (the condition for
650 / 750). The token sets of seven components left the shared preset (Aura sets in `main`: 55.2 kB to 52.0 kB) and
the global registry shrank from every icon of every screen to the 17 of `SHELL_ICONS`, but `main` still holds 52 Phosphor icons (26.9 kB; the 17
shell icons alone are 7.5 kB), because `@ng-icons/phosphor-icons/regular` is one module: the bundler puts it in the
chunk all its importers share, with every export any chunk uses. Per-chunk icon constants name what a chunk shows, but
the SVG strings come from `main` for as long as the shell imports that module.

What the 687.6 kB `main` holds (raw, grouped by package):

| Package | kB | Package | kB |
|---|---:|---|---:|
| @angular/core | 221.5 | @primeuix/utils | 29.8 |
| primeng | 110.9 | rxjs | 28.5 |
| @angular/router | 108.4 | @ng-icons/phosphor-icons | 26.9 |
| @primeuix/themes (Aura) | 52.0 | @primeui/license-manager | 22.6 |
| @angular/common | 49.1 | application code (shell 17.0, preset 8.0, rest 14) | about 39 |
| @angular/forms | 46.2 | @angular/platform-browser | 16.9 |
| @primeuix/styles | 44.4 | @ng-icons/core, @primeicons, tslib | 13.4 |
| @primeuix/styled | 30.2 | | |

Next cuts, with what each is worth (estimates from the same metafile; the first was measured on a scratch build):

1. The shell's own copy of its 17 icons (about 7.5 kB of SVG strings, generated and checked against the package by a
   test), so the package module is imported only by lazy chunks: initial 676.5 kB (minus 18.5 kB).
2. Move the remaining Aura token sets that are not first paint of the shell (DataTable 4.9, Message 5.3, Toast 4.8,
   Select 3.2, Tabs 1.7, InputText 1.6, Menu 1.5, Password 0.8 kB) into the chunks that render them: about minus 20 kB.
   The spec keeps these in the preset because two or more screens render them on first paint.
3. `@primeui/license-manager` (22.6 kB) is imported statically by `primeng/config`; it cannot leave without patching
   PrimeNG or an upstream change, and the only other large shell-only item, `@primeuix/styles/button` (23.9 kB, all
   button variants), is PrimeNG's.
4. 640 kB needs cuts 1 and 2 and one of the items in 3, or a decision to split the shell (the program switcher, theme
   switcher and navigation) behind `@defer`.

### UI2-0: cuts 1 and 2 taken, with the v2 shell

Same method, branch `ui2-foundation` after merging PERF-FE-1. Cut 1: `SHELL_ICONS` is a generated copy of the shell's
13 icons (`scripts/shell-icons.mjs`, checked against the package by `theme/icons.spec.ts`), so `main` no longer imports
`@ng-icons/phosphor-icons`. Cut 2: the shared preset keeps the Button, Badge, Popover and Tooltip sets (PrimeNG Tooltip, for the rail and the program pill, is 17.6 kB of `main`); every other set (with
Lexvay's overrides, one `theme/*-tokens.ts` file per component) is registered by the chunk that renders it. The v2
shell also defers its program switcher, command palette, work queue popover, avatar menu and toast (each a chunk with
its own icons and token sets).

| | PERF-FE-1 | UI2-0 |
|---|---:|---:|
| Initial total, raw | 695.03 kB | 606.77 kB |
| Initial total, estimated transfer | 168.22 kB | 155.87 kB |
| main.js, raw | 687.58 kB | 597.55 kB |
| styles.css, raw | 7.45 kB | 9.22 kB |

New lazy chunks (raw / transfer): login 51.0 / 13.4 kB, command palette 50.4 / 12.8 kB, toast 51.0 / 10.4 kB (both
mostly PrimeNG's Toast, Dialog and Menu code, loaded when the page is idle). The fonts are separate media files (Inter
latin 48 kB, Source Serif 4 latin 51 kB, each requested only when a glyph in its range is on the page). The budget is now
680 kB warning and 760 kB error.

## Rule source schedule (FIX-4)

`V016__rule_source_schedule.sql` makes "due" a stored time, `next_check_at`, with the partial index
`rule_source_due_idx ON rule_source (next_check_at) WHERE active` (D-380). `IndexUsageTest` plans the scheduler's
statement under the worker flag across all tenants, on a database where every seeded source is joined by 200 synthetic
sources that are not due yet (`PerfSeed.addHistoryVolume`), and requires the index in the plan:

```
Index Scan using rule_source_due_idx on rule_source rs; Index Scan using job_claim_idx on job j
```

Read endpoints are unchanged: `GET /rule-sources` p95 1.4 ms and `GET /rule-sources/{id}/checks` p95 3.5 ms in the same run
(two more columns in the existing statement).

## Current-mapping filter (FIX-3)

`GET /assessments/{id}/mappings` and every statement that hides superseded mappings (D-066) ran a correlated
`NOT EXISTS` per mapping, quadratic in the mappings of one assessment (D-199). `IndexUsageTest` now adds one tenant
with a rule version v1 (500 requirements), its successor v2 (500) and two assessments (D-363): 500 mappings on v1 with
no mappings on v2 (the historical case), and 500 on v1 plus 500 on v2 (after a re-run; the first 500 are hidden).
`EXPLAIN (ANALYZE, BUFFERS)` as the application role under row-level security on about 200 tenants, one run each,
warm (third execution):

| Statement | Before: execution, buffers | After: execution, buffers | Plan before | Plan after |
|---|---|---|---|---|
| mappings, before the re-run (500 rows) | 15.5 ms, 5 192 | 3.2 ms, 3 530 | Nested Loop Anti Join, 250 000 rows removed by join filter | hashed SubPlan, one pass over the assessment |
| mappings, after the re-run (500 shown, 500 hidden) | 29.6 ms, 4 747 | 3.5 ms, 3 527 | Nested Loop Anti Join, 500 500 rows removed by join filter | same |
| work queue, signer, same tenant | 275 ms | 5.2 ms | one anti join per mapping of the latest assessment | one hashed set over the latest assessments |

The limits that decide a rewrite were 5 ms and 2 000 buffers; both were exceeded, so the filter was rewritten
(D-363, D-364). After it the time is under 5 ms; the buffers stay above 2 000 because of per-row work (review event
lookup 1 000, evidence span 900) plus one pass over the assessment's mappings to build the set; they grow linearly with
the rows. `IndexUsageTest` fails above 4 000. Plans: `backend/target/perf/mappings-plan.txt` and
`work-queue-plan.txt` (the work queue plan of the other roles: 2.0 to 3.3 ms).

## Audit list and export (SEC-2)

`IndexUsageTest` adds about 80 000 audit entries to the perf database (300 for each of about 200 tenants, 20 000 more for
the tenant that runs the statements, inserted oldest first) and runs `EXPLAIN (ANALYZE, BUFFERS)` of `AuditRepository.listSql`
and `exportSql` as the application role under row-level security, warm (third execution). The index
`audit_log_newest_idx (tenant_id, created_at DESC)` has existed since V001, so no index was added (D-403).

| Statement | Execution | Buffers | Plan |
|---|---:|---:|---|
| list, 100 newest, no filter | 0.06 ms | 11 | Index Scan `audit_log_newest_idx`, Incremental Sort on `id` |
| list, one action | 0.09 ms | 56 | same, with a filter on the action |
| list, last 30 days | 0.06 ms | 11 | same, range in the index condition |
| export, no filter (20 372 rows) | 8.6 ms | 1 690 | Bitmap Heap Scan on `audit_log_tenant_idx`, hash join to `app_user`, in-memory Sort (3 MB) |
| export, one action (4 060 rows) | 3.2 ms | 1 690 | same |
| export, last 30 days (3 526 rows) | 1.6 ms | 327 | Bitmap Heap Scan on `audit_log_newest_idx`, Sort (0.5 MB) |

The export reads every matching row and sorts them before the first one is sent, because it reads a quarter of the table;
a `(tenant_id, created_at DESC, id DESC)` index tried on a throwaway PostgreSQL 18.7 with the same data did not change the
plan. The test fails when a list scans `audit_log` sequentially or sorts every matching row, and when an export scans it
sequentially. Plans: `backend/target/perf/audit-plans.txt`.

## Coverage matrix (F10a)

`GET /programs/{id}/coverage?jurisdiction=CA` (D-390 to D-397, `docs/decisions/F10a.md`): one statement, one row, one
connection. One `make perf` run, milliseconds on loopback:

| Pass | Endpoint | p50 | p95 | p99 | connections per call | wire bytes |
|---|---|---:|---:|---:|---:|---:|
| 2 seeded tenants | GET /programs/{id}/coverage?jurisdiction=CA | 3.15 | 4.91 | 5.88 | 1.0 | 1666 |
| 62 tenants | GET /programs/{id}/coverage?jurisdiction=CA | 2.64 | 4.15 | 5.28 | 1.0 | 1666 |

Plan (`IndexUsageTest`, `backend/target/perf/coverage-plan.txt`): 0.31 ms execution, no executed
sequential scan of `mapping`, `evidence_span`, `evidence_response` or `document`.

## Not covered

- All numbers are loopback. Production adds network latency per round trip, which favours the new design.
- The benchmark signs one jurisdiction; the seed's change events leave the others stale.
- `GET /reports/{id}` and `GET /jobs/{id}` are not in the benchmark (they need a report or job id and read one row).
