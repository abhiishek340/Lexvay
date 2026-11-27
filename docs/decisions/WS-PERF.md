# WS-PERF decisions (D-110 to D-119)

Work item: read-path latency and read APIs (docs/ROADMAP.md, WS-PERF). Measurements are in docs/PERF.md.

## D-110 One connection per read request; the tenant is bound on checkout only

Every service method that only reads is `@Transactional(readOnly = true)`, so a request checks out one
connection, binds the tenant once, and runs all its statements and its commit on it. `TenantAwareDataSource`
no longer wraps the connection in a proxy and no longer issues `RESET lexvay.tenant_id` on return: checkout
always runs `set_config` (tenant present) or `RESET` (no tenant), so the value an idle pooled connection holds
cannot be used before the next checkout replaces it. Tenant isolation stays fail closed (product rule 7).
`Rule7_PooledConnectionRebindsTest` proves it on one physical connection (a pool of size one lends the same
backend to tenant A, to no tenant and to tenant B). `ReadPathConnectionsTest` asserts exactly one checkout per
read endpoint.

Reads that run a single statement and have no service method (audit list, jurisdictions, jobs) stay in
autocommit: one checkout and no extra COMMIT round trip.

Rejected:
- Remembering the last bound tenant per physical connection to skip `set_config` when it matches. It saves a
  round trip but makes isolation depend on state the application tracks about the connection; anything that
  changes the setting behind its back (a bug, `RESET ALL`, a future SQL function) would leak. Not worth it.
- `connectionInitSql` or a pool-level hook for the tenant. The tenant changes per request; the pool cannot know it.
- Keeping the close-time `RESET` as a second line of defence. It costs a statement on every return and defends
  nothing, because the checkout rebind is the only protection that matters.

## D-111 Program overview: contents and package

`GET /programs/{id}/overview` returns `{ program, assessment, determinations, openChangeEvents, undecidedTotal }`
in one read-only transaction. `assessment` is the latest FINISHED one (null before the first run).
`undecidedTotal` is the sum over jurisdictions of `total - decided` from that assessment, which is exactly the
set the signing gate counts (stale, undecided or reopened mappings), so it needs no extra query.
`openChangeEvents` is a list of the same views as `GET /change-events`, limited to OPEN events that touch this
program: a new version of one of its courses, or a new rule version for one of its target jurisdictions and its
license type. The spec did not say whether the list is program-scoped; an unscoped list would show another
program's course changes on this program's screen. The orchestrator should confirm this reading.

The service lives in a new package `overview` because it composes assessment, determination and change data and
nothing depends on it; putting it in `program` would create a package cycle (assessment already depends on program).

Rejected: a count instead of a list (the screen needs to link to the events); embedding mappings (too large).

## D-112 Document page caching

`GET /documents/{id}/pages/{n}` answers with `Cache-Control: private, max-age=31536000, immutable` and a strong
ETag `"<document sha256>-<page number>"`. Pages never change (the database rejects updates), so a year is safe.
`private` keeps shared caches and proxies out. `If-None-Match` is answered with 304 by Spring's
`HttpEntityMethodProcessor` from the ETag on the response entity; the 304 keeps ETag and Cache-Control. The header
is written as a literal string because the order of its directives is part of the specified contract. A 404 is
never marked cacheable.

Known edge: the browser cache is keyed by URL, not by user. A second user on the same browser profile who
requests the same document id would be served the first user's cached copy without a request, but only for a
document id the first user could read, and the second user cannot learn that id from the API unless they can read
it. Accepted; `Vary: Cookie` would remove it at the cost of losing the cache across logins.

Rejected: `public` (cross-tenant risk), `ShallowEtagHeaderFilter` (hashes the body, so it still reads and
serialises the page), a weak ETag (the content is byte-identical for a given document and page), `must-revalidate`
(defeats the point for an immutable resource).

## D-113 Review history

`GET /mappings/{id}/reviews` returns every review event of the mapping oldest first, including REOPENED events
that the system recorded (user id and display name null). An unknown id, or another tenant's, is 404 (row-level
security hides the mapping). No role restriction: like every GET it is open to any authenticated user of the tenant.

## D-114 N+1 removal

Done in SQL, not in Java: determinations aggregate course version ids with an `ARRAY(SELECT ...)` in the main
query; change event views are one statement with LEFT JOINs and correlated subqueries for "has a document" and the
reopened determination ids (it also serves the overview and the re-run precondition); the rule version list takes
requirement counts from a subquery and the latest extraction jobs from one `DISTINCT ON` statement. The benchmark
exposed the rule version list (13 connections per call before) beyond the two repositories the spec named.

Rejected: an in-process cache (a stale-tenant risk and not needed once queries are set-based); batch loading with
`IN` lists per child table (more round trips than one joined statement).

## D-115 Indexes

V008 creates nine indexes, each chosen by the planner for its query on a database of about 200 tenants
(`IndexUsageTest`, evidence in docs/PERF.md). Four of the fourteen candidates in the spec are not created:
- `document (program_id)`: covered by the unique index `(tenant_id, program_id, sha256)`, because every query
  also carries the row-level security condition on `tenant_id`.
- `requirement (rule_version_id)`: covered by the unique index `(rule_version_id, ref)`.
- `change_event (status, created_at DESC)`: the planner prefers the tenant index; a tenant has few events.
- `job ((payload->>'documentId'))` and `job ((payload->>'ruleVersionId'))`: usable without row-level security
  but never chosen with it. The job table's policy has an OR branch for the worker flag, and `jsonb ->>` is not a
  leakproof function, so the planner may not use it as an index condition below the policy.

`IndexUsageTest` creates the rejected candidates inside its own database and asserts the planner still ignores
them, so a future Postgres or policy change that makes one useful fails the test and says so.

Finding for the orchestrator, not implemented because it is a schema change outside the spec: the ingest and
extraction status lookups (`GET /documents/{id}`, rule version views) scan the whole job table under row-level
security, and jobs are never deleted. A generated column `payload_ref uuid` (the payload id, compared with a
leakproof `=`) with an index would make them index lookups.

## D-116 Audit filters

`GET /audit` takes optional `action`, `entityType`, `entityId`, `userId`, `from`, `to` (ISO-8601 instants, both
ends inclusive). The SQL text contains only the predicates that were given, so each combination gets its own
plan. The response shape is unchanged. Rejected: `(:x IS NULL OR col = :x)`, which forces a generic plan that
cannot use an index on the filtered column.

## D-117 Response compression

`server.compression` is on for `application/json` and `application/problem+json` from 1 kB. `spring.jackson`
settings are untouched. `CompressionTest` checks over real HTTP that a large response is gzipped and a small one
is not. On loopback compression costs CPU and saves no time; it pays on a real network.

## D-118 Benchmark method

`EndpointLatencyTest` sends `Accept-Encoding: gzip` like a browser, so the "after" numbers include compression
CPU on the 30 to 45 kB responses. It signs one jurisdiction through the API first so the determination, review and
change views have data (the seed's course and rule changes leave the other jurisdictions stale, which blocks
signing). It runs twice: on the two seeded tenants, then after loading 30 renamed copies of both (62 tenants).
Connections per call are counted by wrapping the data source. Each result in docs/PERF.md is the median of three
runs, chosen by the sum of its p95 values; other agents were building on the same machine, so absolute numbers
vary and the comparison between before and after is what matters.

## D-119 Job payload key inside the SQL text

`JobRepository.latest` used a bind parameter for the payload key (`payload->>:key`), which the planner cannot
match to an expression index. The key is now part of the SQL text, restricted to letters, and the same helper
builds the set-based variant. Rejected: one method per key (more API for no benefit).
