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
Final tree: SEC-3 merged with origin/main after OPS-2 (migrations V018, V020, V022, then this item's V023; F15's shared
`AuditCsv` carries the seal columns, so the evidence package's `audit.csv` has them too, checked in `PackageApiTest`).
`LEXVAY_E2E_SLOT=6 make test` and `make perf`, one run each, both exit 0:
- Backend 1231 tests, 0 failures. Frontend unit 425 passed. End-to-end 56 passed (the stage waited for a free test
  stack under the OPS-2 cap, as expected).
- `make perf`: `EndpointLatencyTest`, `IndexUsageTest` and `AuditSealPerfTest` pass. Verifying 20 005 sealed entries took
  71 to 89 ms (median 76 ms, budget 2 s); sealing them took 0.5 s. The queue read uses `audit_unsealed_tenant_idx` with
  entries fetched by `audit_log_pkey`; the dequeue finds queue rows by `audit_unsealed_pkey`.
- Earlier runs on this branch, made while other builds loaded the machine (load average 14 to 23) before OPS-2, lost
  single timing-sensitive end-to-end tests and endpoint p95 values; none touched the audit trail and all passed on the
  quiet reruns above.
- The Python recipe in the operations guide was run against a real export: intact, and "broken at entry 6" after one
  word of one entry was changed.

## Deviations from the spec
- Four export columns instead of two (D-535), and `AUDIT_VERIFIED` also records `firstBrokenSeq` when broken (D-536).
- `AuditSealer` is not itself scheduled; `AuditSealPoller` is (D-533), so tests can switch the timer off.
- The migration was V019 while it waited and is V023 now: V020 (F15) and V022 (MODE-1) merged first, and numbers follow merge
  order.
