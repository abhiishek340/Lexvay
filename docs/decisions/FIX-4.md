### FIX-4 — Rule source backoff, live search terms, PDF page cap at upload (D-380 to D-389)

**D-380 — Migration `V016__rule_source_schedule.sql`: a source is due when `next_check_at` has passed.** `rule_source` gains
`next_check_at timestamptz NOT NULL` and `consecutive_failures integer NOT NULL DEFAULT 0 CHECK (>= 0)`. Existing rows are backfilled with
`coalesce(last_checked_at + check_interval_hours * interval '1 hour', now())` (a never-checked source is due at once), then `SET NOT NULL`
and `DEFAULT now()`, so a new source is due at once without `RuleSourceRepository.insert` naming the column. The backfill updates rows of
every tenant and the V011 trigger `rule_source_monitoring_only` would refuse it, so, as V013 does, the migration lifts `FORCE ROW LEVEL
SECURITY` and drops that trigger for its duration and restores both in the same transaction; the recreated trigger allows `active`,
`last_checked_at`, `last_content_hash`, `next_check_at` and `consecutive_failures` (the URL, label, license type and interval stay immutable,
`Rule5_RuleSourceImmutabilityTest`). The scheduler's predicate (`RuleSourceRepository.DUE`) is `rs.active AND rs.next_check_at <= now()`,
with the partial index `rule_source_due_idx ON rule_source (next_check_at) WHERE active`. The V011 policies are unchanged. The backfill
statement is tested from the migration file itself against a temporary table (`RuleSourceScheduleBackfillTest`, the V013 technique). The
migration is V016 because V015 is reserved for F5a; Flyway accepts the gap (V006 is also unused).

**D-381 — The schedule is set in the statement that records the check (`RuleSourceRepository.markChecked(id, hash, outcome)`).** One
`UPDATE` computes both columns from the stored row: after UNCHANGED, CHANGED and DRAFT_EXISTS (the page was read), `consecutive_failures = 0`
and `next_check_at = now() + interval`; after FETCH_FAILED, `consecutive_failures + 1` and `next_check_at = now() + least(interval, 15
minutes × 2^(failures − 1))`, which in the statement is `2^consecutive_failures` of the row before the update. The delays are 15, 30, 60,
120 minutes and so on, never longer than the source's own interval (a one hour interval retries after 15, 30, 60, 60). The exponent is capped
at 12 inside `power(2, ...)`: 15 minutes × 4096 is 1 024 hours, more than the longest interval the table allows (720 hours), so the cap
changes no result and keeps a source that has failed for years from overflowing a `double` (`aVeryLongRunOfFailuresStillWaitsTheIntervalAndDoesNotOverflow`
sets 5 000 failures). The base is `RuleSourceRepository.RETRY_BASE_MINUTES = 15`. Computing from the row, not in Java, means two checks of one
source cannot race a read-modify-write of the counter. `now()` is the transaction time, so `last_checked_at` and `next_check_at` differ by exactly
the delay, which the tests assert in minutes. A manual check (`POST /rule-sources/{id}/check`) changes nothing when it is queued; when it
runs, its outcome sets the schedule like any other check, so a manual success on a failing source clears the failures and a manual failure
extends the backoff. DRAFT_EXISTS counts as a success: the page was fetched and read; the draft waiting for review is a person's task, not a
fault of the source. This settles D-189 (3): a failed check no longer waits a full interval.

**D-382 — API.** `GET /rule-sources` adds `nextCheckAt` (never null) and `consecutiveFailures` to each source (`RuleSourceView`); nothing else
changes and no frontend file is touched. A deactivated source keeps its schedule columns; the scheduler skips it because of `rs.active`, and
re-activating it makes it due when `next_check_at` has passed.

**D-383 — Golden search terms.** `eval/golden.yml` entries gain `searchTerms` (3 to 8 terms) for all 42 golden requirements; the header says they
are fictional and written from the requirement text alone, to be changed only when a requirement changes, never to raise a number. The key is
camelCase like the file's other keys (`quoteContains`) and `eval/retrieval/queries.yml`; the roadmap writes `search_terms`, which is the name in
the replace endpoint's JSON, where `Golden.requirementList` does use it. `YamlFile` rejects unknown fields, so a misspelt key fails at once.
`GoldenFileTest` runs every list through `SearchTerms.normalise` (title and text of the entry) and asserts it comes back unchanged, so the golden
terms are exactly what extraction would store: nothing a person later sees dropped. Honest limit: the terms use the vocabulary any counseling
syllabus uses and overlap the scripted terms of `seed/fake-llm.yml` (both are natural vocabulary for the same subjects, `confidentiality`,
`site supervisor`, `group dynamics`); the file was written from the requirement texts and not copied from the seed, but its author had read the
seed. The scripted `make eval` does not use them: its model's terms are the seed's, so comparing the two would measure the seed against itself.

**D-384 — The live report: overlap of the model's terms with the golden terms.** `EvalModel` already replaces the model's extraction by the
golden list in live mode; it now also remembers, before replacing, the stored `searchTerms` of every requirement the model proposed
(`proposedTerms()`), and the golden list it substitutes carries the golden terms (`search_terms` in the replace body), so retrieval and matching in
the live run use hand-written terms and the evaluation of matching is not confounded by the model's vocabulary. `TermOverlap.measure` gives, per
golden requirement, the Jaccard index of the two sets of normalised terms (shared over all distinct; a requirement the model did not extract
proposes none and scores 0; both empty is 1, which the golden file never produces), and the mean. `EvalReport` prints the mean after the
metrics table and one line per requirement (percent and the model's terms) in live mode only; `termOverlap` is null for the scripted model.
There is no threshold: a different vocabulary is not an error (terms only widen the shortlist, D-256), and a limit would invite tuning the
prompt to the golden file. Rejected: scoring the model's terms by retrieval recall (a second live evaluation per run, and `make eval-live`
already measures what retrieval finds with the golden terms).

**D-385 — The PDF page cap is checked at upload.** `ContentLimits.requirePdfPages(byte[])` loads the PDF with PDFBox's temp-file-only stream
cache (`IOUtils.createTempFileOnlyStreamCache`), reads `getNumberOfPages()` and closes it; `DocumentService.upload` calls it for a PDF next to
the DOCX check, before the duplicate check, the file store and the job. An over-long file is the usual 400 problem detail "PDF has 501 pages; the
limit is 500", nothing stored, no document row, no `DOCUMENT_UPLOADED` audit row and no ingest job. The upload's bytes are already one array in
the heap (`MultipartFile.getBytes`); PDFBox reads from that array without copying it, and the stream cache that would otherwise hold decoded
streams in memory is on disk, so a 25 MB PDF does not sit in the heap twice. A file PDFBox cannot open at all is left to ingest, which reports
"File could not be parsed as a PDF" (as before, and like the DOCX check). Ingest keeps its own check (`TextExtractor.pdfPages`), because rule text
fetched by a rule source reaches the extractor without passing through upload; the same `requirePdfPages(int)` and message serve both. The old
test that expected a 202 and a FAILED ingest for 501 pages now expects 400 at upload; `ContentLimitsTest` keeps the extractor's own refusal.
Cost: a PDF is opened twice (structure only at upload, then fully at ingest).

**D-386 — `IndexUsageTest` covers the due query.** The scheduler's statement (`ENQUEUE` with `DUE`, as the application runs it) is planned under the
worker flag with all tenants, and `rule_source_due_idx` must be in the plan. The seeded database has one source per tenant, all due, which is too
few for the planner to prefer an index, so `PerfSeed.addHistoryVolume` adds 200 synthetic sources per seeded source (not due for 1 to 200 hours),
the situation of a long-running installation where a few sources are due among many. The test's kept-index assertion now names V016.

**D-387 — Tests.** `RuleSourceScheduleTest` (new source due at once; 15, 30, 60, 120, 120 minutes with a two hour interval and failures 1 to 5;
the hash is not moved by a failure; reset after CHANGED, UNCHANGED and DRAFT_EXISTS; 5 000 failures do not overflow; queueing a manual check
leaves the schedule alone and its outcome sets it; the API fields), `RuleSourceEnqueueTest` (the due predicate now reads `next_check_at`, plus a
failed source queued again only when its backoff has passed), `RuleSourceScheduleBackfillTest`, `Rule5_RuleSourceImmutabilityTest` (the two new
columns are updatable, the message lists five), `RuleSourceApiTest` (the two fields), `IndexUsageTest`, `ContentLimitsTest` and
`DocumentUploadTest` (501 pages refused at upload with nothing stored, 500 accepted), `GoldenFileTest` (terms 3 to 8 and normaliser-stable, the
replace list carries them), `TermOverlapTest` (Jaccard, per-requirement and mean, live and scripted report), `EvalModelTest` (the model's terms
are remembered and the golden terms are stored).

**D-388 — Not done, on purpose.** No frontend change (the sources screen does not exist yet; the two new fields are in the API for it). No
setting for the base delay: 15 minutes is a constant beside its explanation, and the interval is already per source. `make eval-live` could not be
run here (no `ANTHROPIC_API_KEY`), so the live report's text is proven by `TermOverlapTest` and `EvalModelTest`, not by a live run.

**D-389 — Open points for the orchestrator.** (1) The YAML key is `searchTerms`, not `search_terms` (D-383). (2) V016 with V015 absent from `main`
at the time of writing; if F5a is not merged when this one is, nothing in this branch needs renumbering (Flyway runs V016 on a database that has no V015). The
risk is the other order: if F5a merges after this item, a database that already applied V016 fails Flyway's validation on V015 (out-of-order is
not enabled in `application.yml`). Merge F5a first when it is close, or renumber one of them.
