## FIX-4 — Rule source backoff, live search terms, PDF page cap at upload

Branch `fix-backoff-terms-pages`, migration `V016__rule_source_schedule.sql` (V015 is reserved for F5a and was not on `main` when the PR was opened),
decisions D-380 to D-389 in `docs/decisions/FIX-4.md`. Backend and evaluation only; nothing under `frontend/src`.

### What changed
- **Rule source schedule (D-380 to D-382).** `rule_source.next_check_at` and `consecutive_failures`; the scheduler's due predicate is
  `active AND next_check_at <= now()` with the partial index `rule_source_due_idx`. `RuleSourceRepository.markChecked(id, hash, outcome)` sets both in
  one statement: a check that read the page waits the interval and clears the failures, a failed fetch waits `least(interval, 15 minutes x 2^(failures - 1))`.
  `GET /rule-sources` shows `nextCheckAt` and `consecutiveFailures`. The update restriction (V003 function) is recreated with five allowed columns.
- **Live evaluation with search terms (D-383, D-384).** `eval/golden.yml` has `searchTerms` for all 42 requirements; the replace path of `make eval-live`
  carries them; the live report prints the mean Jaccard overlap between the model's terms and the golden terms and one line per requirement
  (`TermOverlap`). `make eval` is unchanged except that the golden file validates the new field.
- **PDF page cap at upload (D-385).** `ContentLimits.requirePdfPages(byte[])` (PDFBox, temp-file stream cache) before anything is stored; ingest keeps its check.
- **`IndexUsageTest` (D-386).** Covers the due query. Before the merge of FIX-3 the manual-check candidate named `job_claim_idx` while the planner chooses
  `job_entity_idx`, which failed the first `make perf` run on this branch; FIX-3 had already fixed it (`Candidate.eitherOf`), so the merge took its version.

### Verification
`make backend-test` (exit 0):
```
[INFO] Tests run: 900, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
`make perf` (exit 0, run after merging `origin/main`):
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 143.5 s -- in com.lexvay.perf.EndpointLatencyTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 162.0 s -- in com.lexvay.perf.IndexUsageTest
[INFO] BUILD SUCCESS
| `rule_source_due_idx` | which active rule sources are due (scheduler, worker flag, all tenants; V016) | yes | Index Scan using rule_source_due_idx on rule_source rs; Index Scan using job_claim_idx on job j | yes | |
```
`GET /rule-sources` p95 3.83 ms, `GET /rule-sources/{id}/checks` p95 4.10 ms.

`LEXVAY_E2E_SLOT=7 make eval` (exit 0):
```
Metric                                        Result      Count   Limit        Status
False EVIDENCE_FOUND rate                      0.0 %     0 / 33   <= 0.0 %     ok
Recall on golden EVIDENCE_FOUND              100.0 %    33 / 33   >= 100.0 %   ok
Quote verification                           100.0 %    31 / 31   = 100.0 %    ok
Numeric accuracy (rules engine)              100.0 %    11 / 11   >= 100.0 %   ok
Requirement extraction agreement             100.0 %    42 / 42   >= 100.0 %   ok
Evidence agreement (document, quote)         100.0 %    39 / 39   >= 100.0 %   ok
RESULT: pass
RETRIEVAL: pass
```
`LEXVAY_E2E_SLOT=7 make e2e` (exit 0):
```
  1 skipped
  18 passed (1.6m)
```

### Not run
`make eval-live` (no `ANTHROPIC_API_KEY` here). The live report's text and the overlap arithmetic are covered by `TermOverlapTest`, and `EvalModelTest`
proves the model's terms are remembered and the golden terms are stored through the replace path.

### Tests added or changed
`RuleSourceScheduleTest`, `RuleSourceScheduleBackfillTest`, `RuleSourceEnqueueTest`, `Rule5_RuleSourceImmutabilityTest`, `RuleSourceApiTest`, `IndexUsageTest`,
`ContentLimitsTest`, `DocumentUploadTest` (501 pages now a 400 at upload), `GoldenFileTest`, `TermOverlapTest`, `EvalModelTest`, `EvalServiceTest`, `EvalScorerTest`.

### For the orchestrator
The roadmap says `search_terms` for the golden file; the key is `searchTerms` like the file's other keys (D-383). If F5a (V015) merges after this item, a database
that already applied V016 fails Flyway validation on V015 (D-389).
