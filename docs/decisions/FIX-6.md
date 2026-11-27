# FIX-6 decisions (OCR checkpoints per page)

**D-436 — One immutable table, no job per page.** `document_ocr_page` (V017) holds one row per page that was sent to
the OCR engine: `tenant_id`, `document_id`, `page_number`, the normalized `text`, `mean_confidence`, `render_dpi` and
`created_at`, primary key `(document_id, page_number)`. The primary key serves the only read (all checkpoints of one
document), so there is no other index. Row-level security is enabled and forced with the same `tenant_isolation`
policy as `document_page`; the application role gets its privileges from the default privileges of the role script, so
the migration has no `GRANT`. `forbid_change()` (V003) rejects UPDATE and DELETE (rule 5). Rows stay after the ingest
succeeds or fails for good: they record how a page's text was obtained. `JobRunner` and the job table are unchanged;
the retry that already exists is what resumes.

**D-437 — The checkpoint stores what the engine said, never a verdict.** Every page sent to the engine is saved, the
too-short ones and the ones with no words too (`text = ''`, `mean_confidence` NULL; `OcrPage.NOTHING` in code). Whether
the text is enough to count as read (`TextExtractor.hasEnoughText`) is decided in code from the stored text on every
extraction, so a checkpoint can never change an outcome and a later change of that threshold applies to old
checkpoints too. The confidence is saved after the same clamp and two-decimal rounding that `document_page` applies, so
a page read from a checkpoint carries exactly the confidence the first run gave it.

**D-438 — `OcrCheckpoints`, and `find` takes the render DPI.** The interface has `find` and `save` and the constant
`NONE`; `TextExtractor.extract(FileType, byte[])` delegates with `NONE`, so rule texts, rule sources and the existing
tests are unchanged. A page that needs OCR asks `find` first; on a hit it neither renders nor calls the engine; on a
miss it renders, recognises and saves before the next page starts. A checkpoint taken at another DPI than
`renderDpi(page)` (the DPI rule changed, D-164 family) is ignored and the page is recognised again; the insert is
`ON CONFLICT DO NOTHING`, so the first checkpoint of a page stays and this run uses its fresh result in memory. The
spec lists `find(int pageNumber)`, which cannot express that comparison, so the signature is
`find(int pageNumber, int renderDpi)` (see the report; this is the one deviation).

**D-439 — Commit per page with `PROPAGATION_REQUIRES_NEW`.** `DocumentOcrCheckpoints.forDocument(tenantId,
documentId)` loads every checkpoint of the document in one statement and returns a `ForDocument` that is used for one
ingest attempt. `save` runs `INSERT ... ON CONFLICT DO NOTHING` through a `TransactionTemplate` that always starts a new
transaction, so the row commits although the job's transaction rolls back later. The new transaction gets a new
connection from `TenantAwareDataSource`, which binds `lexvay.tenant_id` on checkout; nothing needs the worker flag
because the ingest job already runs as its tenant. This is proved, not assumed: a test saves inside a transaction marked
rollback-only and finds the row afterwards, and the isolation test shows tenant B can neither read nor write tenant A's
checkpoints through the same path.

**D-440 — Audit, no new action.** `DOCUMENT_INGESTED` gains `ocrPagesReused`, the number of pages read from a
checkpoint instead of the engine (`ForDocument.reused()`, counted in `find` when a checkpoint at the right DPI is
returned). It is a detail of an existing action, so `Rule9_AuditCoverageTest` and the frontend's `audit-sentence.spec.ts`
need no row. No endpoint changes. The handler reads the tenant before extraction because the checkpoints need it.

**D-441 — Tests.** `TextExtractorOcrTest` (where the fake-engine OCR tests of F11 already live) gains the checkpoint
cases with an in-memory `InMemoryOcrCheckpoints` that has the table's semantics: zero engine calls and equal pages on a
second extraction, a page saved before the next is read, a short checkpoint still falling back to the text layer, a page
with no words stored as nothing, and a DPI mismatch recognising again. `FakeOcrEngine.failOnCall(n)` throws once on the
n-th recognition. `DocumentOcrCheckpointIngestTest` runs the real upload and job path: the engine fails on page 3 of 4
on the first attempt only, pages 1 and 2 are committed after the rollback, the retry recognises pages 3 and 4 only, the
final `document_page` rows equal those of a clean run in another program, and the audit says `ocrPagesReused = 2`. It
also covers the rollback-only commit and the keep-first rule. `Rule5_OcrCheckpointImmutabilityTest` and
`Rule7_OcrCheckpointIsolationTest` cover rules 5 and 7; `Rule7_EveryTableHasPolicyTest` covers the table without a change.
