# SEC-3 progress (Tamper-evident audit trail)

Branch `sec-audit-seal`, slot 6, migration V019 (V018 is F14's, PR #47, not on main when this was built).
Decisions D-530 to D-537 are in `docs/decisions/SEC-3.md`.

## Done
- V019: `audit_seal` (unique `(tenant_id, seq)`, `forbid_change()` trigger, forced RLS), `audit_unsealed` (the queue;
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
- `make test` with `LEXVAY_E2E_SLOT=6` after merging origin/main: 1141 backend tests, 414 frontend unit tests, 55
  end-to-end tests, all passing. An earlier run of the merged tree failed `ManagementPortTest` (twice) and
  `MetricsTest` with a 404 from the management port; both pass alone and in the next full run, and nothing in this item
  touches them, so it is recorded as an environmental flake while other builds shared the machine.
- `make perf`: `EndpointLatencyTest` failed once on `GET /programs/{id}/facts` p95 46.9 ms (budget 40) while other
  builds were running, and passed on the rerun; `IndexUsageTest` and `AuditSealPerfTest` pass. Verifying 20 005
  sealed entries: 52 to 75 ms on a quiet machine, 139 to 221 ms on a busy one (budget 2 s).
- The Python recipe in the operations guide was run against a real export: intact, and "broken at entry 6" after one
  word of one entry was changed.

## Deviations from the spec
- Four export columns instead of two (D-535), and `AUDIT_VERIFIED` also records `firstBrokenSeq` when broken (D-536).
- `AuditSealer` is not itself scheduled; `AuditSealPoller` is (D-533), so tests can switch the timer off.
- If V018 lands after V019 on a development database that already applied V019, Flyway needs `outOfOrder`
  (`flyway.outOfOrder=true`) or a recreated database; nothing is deployed.
