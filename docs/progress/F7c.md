# F7c — Search terms proposed at extraction: progress and evidence

Branch `search-terms`, slot 3. Backend and evaluation, plus a frontend pass-through so the grid keeps the terms; the screen cell is UI2-4. Decisions D-250 to D-259 in `docs/decisions/F7c.md`.

## What was built
- **Migration `V014__requirement_search_terms.sql`**: `requirement.search_terms text[] NOT NULL DEFAULT '{}'` and `search_terms_valid(text[])`
  (at most 12 terms, each 1 to 40 characters after trimming) in a CHECK. No existing row is updated, so the lock trigger and `FORCE ROW LEVEL SECURITY`
  are left alone (D-250).
- **Extraction**: `extract-v2.system.txt` (v1 stays on disk, the job records `extract-v2`), `search_terms` required in the extraction schema
  (`maxItems` 8, `maxLength` 40). `SearchTerms.normalise` is the one normaliser (trim, lower-case, de-duplicate, drop empty terms, terms with a digit or a
  state name, and terms whose words are all in the title or text). The replace endpoint takes `search_terms` optionally; more than 12 is a 400 naming the row.
- **`RequirementComparison.Aspect.SEARCH_TERMS`** (does not affect evaluation): a terms-only edit carries decisions over on re-run, and the diff reports it.
- **Retrieval**: the one BM25 statement gets the terms' lexemes (boost 1.0) next to the title's (2.0) and the text's (1.0); `Passage.matchedVia`
  (`TITLE`, `TEXT`, `TERMS`) and `matchedTerms`; `EvidenceMatcher` adds "Found through the search terms: ..." when the cited page was found only through terms.
  Quotes are verified against the stored page exactly as before.
- **Seed and fixtures**: hand-written fictional terms for all 55 scripted seed requirements and the 4 Nevada fixture ones; the Colorado revision keeps them identical.
- **Evaluation**: `searchTerms` for all 35 queries, a third column "BM25 + terms", `minGapRecallAtDeep: 0.66` (4 of 6) in `eval/thresholds.yml`.

## Retrieval (`make eval`, `docs/RETRIEVAL.md`)
Fictional corpus of 27 documents; 29 counted queries and 6 lexical gaps.

| Counted queries | ts_rank (baseline) | BM25 | BM25 + terms |
|---|---|---|---|
| Recall@8 | 96.6 % (28 / 29) | 96.6 % (28 / 29) | 96.6 % (28 / 29) |
| Recall@3 | 86.2 % (25 / 29) | 89.7 % (26 / 29) | 93.1 % (27 / 29) |
| Mean reciprocal rank | 0.796 | 0.766 | 0.839 |
| Excerpt coverage | 100.0 % (28 / 28) | 100.0 % (28 / 28) | 100.0 % (28 / 28) |

| Lexical gaps | ts_rank (baseline) | BM25 | BM25 + terms |
|---|---|---|---|
| Recall@8 | 33.3 % (2 / 6) | 33.3 % (2 / 6) | 100.0 % (6 / 6) |
| Recall@3 | 16.7 % (1 / 6) | 16.7 % (1 / 6) | 100.0 % (6 / 6) |
| Mean reciprocal rank | 0.111 | 0.076 | 0.917 |

The limit (lexical-gap recall@8 at least 4 of 6) is met with margin on the first run; no term was changed after any run. The only edits to the term lists
after writing them removed terms the production normaliser would have dropped (all their words are in the requirement), so no result moved. The result is one
author's one pass over six queries, written with the expected passages' `contains` strings in the same file (D-258); it shows that the mechanism works when the
vocabulary is right, not how often a model's proposals will be. R13 is still outside the top eight in all three columns.
The pipeline on the seed is unchanged: 0 false EVIDENCE_FOUND (0 / 33), recall 33 / 33, quote verification 31 / 31, numeric accuracy 11 / 11,
extraction agreement 42 / 42, evidence agreement 39 / 39, 36 model calls.

## Review fixes (after PR review)
- **No data loss on save**: the grid passes `search_terms` through (`RequirementView.searchTerms`, `RequirementDraft.search_terms`, `RequirementListEditor` keeps each row's terms).
  `make frontend-test`: 110 passed (17 files), including the new editor tests.
- **Eval profile** sets `management.server.port: -1`; `make eval` ran with a development backend holding :8090 and passed.
- `LEXVAY_E2E_SLOT=3 make e2e`: 18 passed, 1 skipped. Re-run after merging `origin/main` again: `make backend-test` 882 tests, 0 failures, BUILD SUCCESS.

## Tails

`LEXVAY_E2E_SLOT=3 make eval` (the eval profile now has `management.server.port: -1`, so it runs beside a development backend on :8090; D-259), exit 0:
```
Metric                                        Result      Count   Limit        Status
False EVIDENCE_FOUND rate                      0.0 %     0 / 33   <= 0.0 %     ok
Recall on golden EVIDENCE_FOUND              100.0 %    33 / 33   >= 100.0 %   ok
Quote verification                           100.0 %    31 / 31   = 100.0 %    ok
Numeric accuracy (rules engine)              100.0 %    11 / 11   >= 100.0 %   ok
Requirement extraction agreement             100.0 %    42 / 42   >= 100.0 %   ok
Evidence agreement (document, quote)         100.0 %    39 / 39   >= 100.0 %   ok
RESULT: pass

Metric                         ts_rank (baseline)                 BM25         BM25 + terms   Limit                          Status
Recall@8                          96.6 %  28 / 29      96.6 %  28 / 29      96.6 %  28 / 29   >= 95.0 %, not below baseline  ok
Recall@3                          86.2 %  25 / 29      89.7 %  26 / 29      93.1 %  27 / 29   not below baseline             ok
Mean reciprocal rank                        0.796                0.766                0.839   -                              -
Excerpt coverage                 100.0 %  28 / 28     100.0 %  28 / 28     100.0 %  28 / 28   = 100.0 %                      ok

Lexical gaps: recall@8 2 / 6 with ts_rank, 2 / 6 with BM25, 6 / 6 with BM25 + terms (limit >= 66.0 %, ok); recall@3 1 / 6, 1 / 6 and 6 / 6
RETRIEVAL: pass
```

`make backend-test` after merging `origin/main` (F4 included), exit 0:
```
[INFO] Tests run: 882, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```
`ForbiddenWordsTest` is in that run.

BM25 statement with six terms per requirement (`PassageSearchLatencyTest`, 500 pages, budget p95 30 ms), run apart because `make perf` does not include it:
```
BM25 shortlist over 500 pages, 200 requests: p50=12 ms, p95=17 ms, max=21 ms
```
F7 measured p50 9 ms and p95 15 ms without terms (D-173).

`make perf`: `EndpointLatencyTest` passed (BUILD SUCCESS; every read endpoint p95 under 17 ms on the seed, under 9 ms with 62 tenants; one connection per
call). `IndexUsageTest`, the second half of the target, fails:
```
[indexes of V008 and V013 the planner did not choose: drop them]
Expecting empty but was: ["job_claim_idx"]
```
This item adds no job query or index. F4's progress note records the same test failing on `main` for another index (`requirement_rule_version_id_ref_key`), so the test
is failing before this branch; not touched here.

`LEXVAY_E2E_SLOT=3 make e2e`, exit 0:
```
  18 passed (1.5m)
  1 skipped
```
(The skipped test is the screenshot spec, which only runs on request.)

## Tests added or changed
`SearchTermsTest`, `RequirementListParserTest` (dedupe, words already in the text, state and digit, 12 and 13, 40 and 41, shapes, 8 against 12),
`JsonSchemaValidatorTest` (nine terms, long term, missing member), `RequirementComparisonTest`, `RequirementSearchTermsConstraintTest` (the CHECK),
`Rule5_ApprovedRequirementsRejectEditsTest` (terms are locked), `PassageSearchTermsTest` (BM25 by hand with a term only on a heading line, labels, stemming,
matched terms, quotes in terms, ts_rank), `SearchTermsMatchingTest` (the rationale sentence, rule 2), `RuleVersionApiTest` (the scripted terms are stored; replace stores,
normalises, rejects 13 naming the row and a 41-character term, locks after approval), `RuleVersionChangeTest` (carry-over and diff), `SeedSearchTermsTest`,
`RetrievalFileTest` and `RetrievalScorerTest` (limits), `RetrievalServiceTest` (the lexical-gap limit with and without terms). Fixtures that build extraction output by hand now
supply `search_terms`.
