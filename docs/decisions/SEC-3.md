# SEC-3 decisions (Tamper-evident audit trail)

**D-530 — The chain is built after the fact, and the queue is written by the audit statement itself.**
`AuditRepository.insert` is one statement, `WITH entry AS (INSERT INTO audit_log ... RETURNING id, tenant_id,
created_at) INSERT INTO audit_unsealed SELECT ... FROM entry`, so an entry and its place in the sealer's queue commit
together or not at all and no entry can escape sealing. Chaining inside the business transaction was rejected by the
spec (a per-tenant lock held until commit by every audited write would serialise the tenant and invite deadlocks) and
stays rejected. `Rule9_SingleAuditWritePathTest` reads `src/main` and fails if any other file contains
`INSERT INTO audit_log`, or if that statement stops queueing. V019 backfills `audit_unsealed` from every existing
`audit_log` row, with row-level security switched off on the two tables for the migration and forced again at its end
(the pattern of V016), so the sealer's first runs seal the history. `AuditSealBackfillTest` runs the migration's own
statement against shadow tables, because no test can load rows and then migrate. `audit_seal` has no `created_by`:
the spec lists its columns and a seal is made by the system.

**D-531 — The canonical form is a contract, written in `docs/OPERATIONS.md`, and checked against hashes computed
outside the code.** `AuditCanonical` joins the ten fields of the spec with U+001F and hashes the UTF-8 bytes with
`DocumentService.sha256`, the one SHA-256 of a byte array in the code base (no second digest helper). The time is
formatted with a fixed six-digit fraction, because `Instant.toString` drops trailing zero groups; PostgreSQL stores
microseconds, so the form loses nothing. `detail` is read as `jsonb::text` and never re-serialised in Java.
`AuditCanonicalTest` pins two hashes computed with Python's `hashlib`; `AuditSealerTest` recomputes a whole chain with
the SQL recipe in the operations guide, and `AuditExportApiTest` recomputes it from the exported CSV alone with
`AuditCanonical`. A field is not length-prefixed, so a value containing U+001F could in theory be ambiguous; actions
and record types are constants in the code and `detail` is JSON, where U+001F is always escaped.

**D-532 — Seal order is the order sealed.** An entry that commits late, with an earlier `created_at` than entries
already sealed, takes the next `seq`; the chain proves the order entries were sealed in, not the order they were
created in. Within one batch the order is `created_at, audit_id`. `AuditSealerTest.anEntryCommittedLateTakesTheNextNumber...`
inserts such an entry. The queue's own `created_at` (copied by the statement that writes the entry) is what the sealer
sorts on, so the read is an index range scan on `audit_unsealed (tenant_id, created_at, audit_id)` and not a sort of
the tenant's whole backlog.

**D-533 — `AuditSealer` seals, `AuditSealPoller` schedules; the lock is per tenant and per transaction.** The spec puts
`@Scheduled` on `AuditSealer`. A scheduled bean cannot be switched off in tests while its logic stays callable, so the
timer is a separate component, as `JobPoller` is to `JobRunner`: `AuditSealPoller` is `@ConditionalOnProperty
lexvay.audit.sealing-enabled` (default true, off in `AbstractDatabaseTest` and in the seed and eval profiles), runs
every `lexvay.audit.seal-interval-ms` (default 2000, `LEXVAY_AUDIT_SEAL_INTERVAL_MS`), begins no batch once the context
is closing, and carries its own `@EnableScheduling` so it works with job polling off. `AuditSealer.sealWhile` finds the
tenants with queued rows under the worker flag (V019 adds one policy, `worker_queue_select`, a SELECT on
`audit_unsealed`; D-013), then for each tenant runs batches of 500 in separate transactions as that tenant. A batch takes
`pg_try_advisory_xact_lock(hashtextextended('audit-seal:' || tenant, 0))` and skips the tenant when it is held, reads
the head, reads the queue, hashes in Java, inserts the seals in one `unnest` statement, and deletes the queue rows that
now have a seal in one `DELETE ... USING audit_seal` (no id lists). A tenant whose batch throws is logged with the
exception class only and left for the next run, so it cannot hold up the others. `AuditSealPollerTest` is the
shutdown test. The restart test seals one batch, builds a second `AuditSealer` and finishes the queue.

**D-534 — Verification walks the stored seals, stops at the first that does not hold, and says what it cannot see.**
`AuditVerificationService.verify` is one read-write transaction (it also writes the `AUDIT_VERIFIED` entry): pages of
1000 seals joined to their entries by keyset on `(tenant_id, seq)`, each checked by `AuditChainCheck` (pure, tested
without a database) in this order: the next number (a gap names the number that is missing), the link to the previous
`row_hash` (genesis for the first), and the recomputed hash. `sealed` is the number of seal rows, `pending` the
queue's length, `lastSealedAt` and `headHash` those of the newest seal. A seal removed from the end of the chain leaves
no gap and cannot be seen from the database alone; `docs/OPERATIONS.md` says to keep `headHash` and `sealed` outside
the database administrator's reach, and every `AUDIT_VERIFIED` entry carries them. Deleting a sealed `audit_log` row is
refused by the foreign key even with its trigger disabled; deleting a seal row with its trigger disabled reports the gap
(tests as the container's superuser). Over 20 000 sealed entries the walk took 52 to 75 ms on a quiet machine and 139 to 221 ms on a busy one (D-537, budget 2 s).

**D-535 — The export gains four columns, not two.** The spec adds `seq` and `row_hash`. An exported trail cannot be
recomputed from those alone: the hash covers the entry id and the actor's id, and the export shows the actor's display
name. So the columns are `Seq`, `Row hash`, `Entry ID` and `Actor ID`, after `Details` (existing columns keep their
positions), written by `AuditCsv`, which the F15 evidence package's `audit.csv` shares, so both carry them. A pending entry has an empty `Seq` and `Row hash`. The tenant id is not a column (one export is one
tenant) and is passed to the check; the previous hash is the previous row's, so the recipe needs an export without
filters. The Python recipe in `docs/OPERATIONS.md` was run against a real export, intact and with one word changed.
`AuditExportApiTest` fails if the file stops being enough to recompute every hash. The end-to-end header assertion in
`records.spec.ts` follows. Called out in the pull request as a spec deviation.

**D-536 — `AUDIT_VERIFIED` records the break, and the sentence never claims more than the entry says.** The spec's
detail is `{intact, sealed, headHash}`; a broken result also records `firstBrokenSeq`, because the audit screen's
sentence names the entry ("broken at entry N") and the entry list is the only place that sentence can read it from.
`headHash` is left out when nothing is sealed. The sentence says "intact" only for `intact: true`; an entry with no
usable detail reads "verified the audit trail" with no verdict. The action is audited as a read that hands out a
verdict (listed in `Rule9_AuditCoverageTest.AUDITED_READ_ENDPOINTS`, with rows in `InputValidationTableTest`), and is
ADMIN only like the export.

**D-537 — What the tests and the budget say.** Tests: `AuditCanonicalTest`, `AuditChainCheckTest`,
`AuditSealPollerTest` (no database); `AuditSealerTest` (contiguous chain, SQL recipe, restart, 1 200 entries over three
batches, eight writer threads against two competing sealers, late entry, every tamper), `AuditVerificationApiTest`
(ADMIN only, 401 and 403, the audit entry, pending, broken, another tenant), `AuditSealBackfillTest`,
`Rule5_AuditSealImmutabilityTest`, `Rule7_AuditSealIsolationTest`, `Rule9_SingleAuditWritePathTest`. Tests that edit or
delete behind the application connect as the container's superuser, disable the trigger for one statement and enable it
again in a `finally`. `AuditSealPerfTest` (`make perf`, tag `perf`) queues 20 000 entries of the perf seed's audit
volume, seals them with the real sealer, times five verifications after a warm-up (budget 2 s) and fails unless the
queue read uses `audit_unsealed_tenant_idx` with entries fetched by `audit_log_pkey`, and the dequeue finds its rows by
`audit_unsealed_pkey`; plans are printed in the run.
