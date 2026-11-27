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

`requirement (rule_version_id)`: not created. The existing unique index `requirement_rule_version_id_ref_key`
serves it (planner: Bitmap Heap Scan on requirement; Bitmap Index Scan on requirement_rule_version_id_ref_key; Bitmap Index Scan on requirement_tenant_idx).

### Dropped (the planner never chose them with row-level security)

| Candidate (created only inside IndexUsageTest) | Query | Planner with row-level security | Planner without row-level security |
|---|---|---|---|
| `rejected_document_program_idx` | documents of a program (unique index tenant_id, program_id, sha256 serves it) | Index Scan using document_tenant_id_program_id_sha256_key on document | Bitmap Heap Scan on document; Bitmap Index Scan on rejected_document_program_idx |
| `rejected_change_event_status_created_idx` | open change events, newest first | Bitmap Heap Scan on change_event; Bitmap Index Scan on change_event_tenant_idx | Seq Scan on change_event |
| `rejected_job_document_id_idx` | ingest status of a document | Seq Scan on job | Index Scan using rejected_job_document_id_idx on job |
| `rejected_job_rule_version_id_idx` | extraction status of a rule version | Seq Scan on job | Index Scan using rejected_job_rule_version_id_idx on job |

The job indexes work without row-level security (right column) and are ignored with it. The `job` table's policy
has an OR branch for the worker flag, and `jsonb ->>` is not leakproof, so the planner may not use the condition as
an index condition below the policy. Recommendation, not implemented (schema change outside the spec): a generated
`uuid` column holding the payload id plus an index, compared with a leakproof `=`. Until then `GET /documents/{id}`
and the rule version views scan the job table (jobs are never deleted); at the seed size this costs nothing
measurable, at hundreds of thousands of jobs it will.

## Not covered

- All numbers are loopback. Production adds network latency per round trip, which favours the new design.
- The benchmark signs one jurisdiction; the seed's change events leave the others stale.
- `GET /reports/{id}` and `GET /jobs/{id}` are not in the benchmark (they need a report or job id and read one row).
