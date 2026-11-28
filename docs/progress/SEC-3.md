# SEC-3 progress (Tamper-evident audit trail)

Branch `sec-audit-seal`, slot 6, migration V023 (written as V019, renumbered before merge because V020 and V022 reached main first).
Decisions D-530 to D-537 are in `docs/decisions/SEC-3.md`.

## Done
- V023: `audit_seal` (unique `(tenant_id, seq)`, `forbid_change()` trigger, forced RLS), `audit_unsealed` (the queue;
  forced RLS plus the worker-flag read policy `worker_queue_select`; index `(tenant_id, created_at, audit_id)`),
  backfill of every existing audit entry into the queue.
- Write path: `AuditRepository.insert` is one `WITH ... INSERT` statement that also queues the entry;
  `Rule9_SingleAuditWritePathTest` greps `src/main` for any other `INSERT INTO audit_log`.
- `AuditCanonical` (canonical form, hash through `DocumentService.sha256`), `AuditChainCheck` (pure walk rules),
  `AuditSealRepository` (all sealer and verification SQL), `AuditSealer` (batches of 500, per-tenant advisory lock),
  `AuditSealPoller` (the timer, `lexvay.audit.sealing-enabled`, `LEXVAY_AUDIT_SEAL_INTERVAL_MS`, stops with the app).
- `GET /api/v1/audit/verify` (ADMIN), `AuditVerificationService`, `AuditVerification`; audited as `AUDIT_VERIFIED`
  (row in `Rule9_AuditCoverageTest`, `InputValidationTableTest`, and the sentence and label in `audit-sentence.ts`
  with its spec).
- Export: `Seq`, `Row hash`, `Entry ID`, `Actor ID` columns (empty seq and hash while pending); end-to-end header
  assertion updated.
- `docs/OPERATIONS.md`: "Audit trail integrity" (what is proved and what is not, canonical form, a Python check of an
  export, the SQL check against a restored database) and the new setting. `docs/PERF.md`: the seal section.
- `make perf` runs `AuditSealPerfTest`.

## Checks
Final tree: SEC-3 merged with origin/main at the F15 merge (V018, V020, V022, V023; F15's shared `AuditCsv` now carries the seal
columns, so the evidence package's `audit.csv` has them too, checked in `PackageApiTest`).
- Backend 1182 tests, frontend unit 421: all passing in one `make test` run. End-to-end: 58 passed in a separate
  `make e2e` run; the two full `make test` runs before it each lost one or two end-to-end tests to timing (a focus
  assertion in `report-check.spec.ts`, a keyboard test in `people.spec.ts`, a 90 s timeout in `review-and-sign.spec.ts`,
  `setup.spec.ts:218`), never the same one twice and none touching the audit trail, while the machine's load average was
  14 to 23 from other builds. An earlier merged-tree run also lost `ManagementPortTest` and `MetricsTest` once (404 from
  the management port; both pass alone and on rerun).
- `make perf`: `IndexUsageTest` and `AuditSealPerfTest` pass. `EndpointLatencyTest` did not pass on this machine in the
  final runs: its p95 values were 40 to 51 ms for endpoints this item does not touch (`/jurisdictions`, `/programs`,
  `/programs/{id}`) at a load average of 14 to 23; the same test passed on this branch at 52 to 75 ms verification
  times and a quiet machine before the F15 merge (and failed once on another unrelated endpoint, 46.9 ms, under load).
  It needs a rerun on a quiet machine.
- Verifying 20 005 sealed entries: 52 to 75 ms on a quiet machine, 139 to 221 ms busy, 433 to 989 ms at load average 20
  (budget 2 s). Sealing them: 0.5 s quiet, 5 s at that load.
- The Python recipe in the operations guide was run against a real export: intact, and "broken at entry 6" after one
  word of one entry was changed.

## Deviations from the spec
- Four export columns instead of two (D-535), and `AUDIT_VERIFIED` also records `firstBrokenSeq` when broken (D-536).
- `AuditSealer` is not itself scheduled; `AuditSealPoller` is (D-533), so tests can switch the timer off.
- The migration was V019 while it waited and is V023 now: V020 (F15) and V022 (MODE-1) merged first, and numbers follow merge
  order.
