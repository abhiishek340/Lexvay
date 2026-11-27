# FIX-6 progress (OCR checkpoints per page)

Branch `fix-ocr-checkpoints`, slot 5, migration V017. Decisions D-436 to D-441 are in `docs/decisions/FIX-6.md`.

## Done
- V017 `document_ocr_page`: tenant policy, forced RLS, `forbid_change()` trigger, primary key as the only index.
- `OcrCheckpoints` (with `NONE`), `DocumentOcrCheckpoints` (`REQUIRES_NEW` per page, one query to load), two
  `DocumentRepository` statements, `OcrPage.NOTHING`.
- `TextExtractor.extract(FileType, byte[], OcrCheckpoints)`; the two-argument form delegates with `NONE`.
- `IngestDocumentHandler` passes the document's checkpoints and audits `ocrPagesReused`.
- Tests: checkpoint cases in `TextExtractorOcrTest`, `DocumentOcrCheckpointIngestTest`,
  `Rule5_OcrCheckpointImmutabilityTest`, `Rule7_OcrCheckpointIsolationTest`.
- `docs/OPERATIONS.md`: one sentence.

## Test runs
`LEXVAY_E2E_SLOT=5 make test`, exit 0: backend `Tests run: 1091, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`),
frontend `Test Files 29 passed (29)`, `Tests 312 passed (312)`, end-to-end `37 passed (4.0m)`. The first backend run had one
failure in the new ingest test: `JobRunner.runOne` claims the oldest queued job of any tenant, and jobs that other test
classes had left queued were claimed first. The test now drains the queue in `setUp`.
