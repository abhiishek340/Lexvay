# WS-EVAL progress (milestone 9 evidence)

Branch `eval-and-infra`, slot 3, run 2026-11-26 on macOS arm64 (Colima), after merging `origin/main`.
Decisions: `docs/decisions/WS-EVAL.md` (D-100 to D-109).

## `LEXVAY_E2E_SLOT=3 make eval` (scripted model), exit 0

```
Lexvay evaluation (fictional seed data)
  model   fake-llm (scripted; token counts are estimates)
  tenant  Northfield University (evaluation 6718a7ca)
  golden  42 requirements

Metric                                        Result      Count   Limit        Status
False EVIDENCE_FOUND rate                      0.0 %     0 / 33   <= 0.0 %     ok
Recall on golden EVIDENCE_FOUND              100.0 %    33 / 33   >= 100.0 %   ok
Quote verification                           100.0 %    31 / 31   = 100.0 %    ok
Numeric accuracy (rules engine)              100.0 %    11 / 11   >= 100.0 %   ok
Requirement extraction agreement             100.0 %    42 / 42   >= 100.0 %   ok
Evidence agreement (document, quote)         100.0 %    39 / 39   >= 100.0 %   ok

Model calls                                36
Input tokens                               50,781
Output tokens                              4,672
Cost per assessment (USD)                  n/a (LEXVAY_LLM_PRICE_*_PER_MTOK not set)
Wall time per assessment                   0.4 s

RESULT: pass
```

## A wrong golden entry fails the run

In a copy of `eval/` (the committed file was not touched) UT-2 was changed from EVIDENCE_FOUND to
EVIDENCE_MISSING and the real process was run with `LEXVAY_EVAL_DIR` pointing at the copy. The jar exited 1
(make reports `Error 1`):

```
False EVIDENCE_FOUND rate                      3.0 %     1 / 33   <= 0.0 %     FAIL
Disagreements with the golden set
  UT/UT-2    false EVIDENCE_FOUND   expected EVIDENCE_MISSING, got EVIDENCE_FOUND
RESULT: FAIL
  - false EVIDENCE_FOUND rate 3.0 % exceeds 0.0 %
make: *** [eval] Error 1
```

The same is proved in `EvalServiceTest` (`aWrongGoldenEntryFailsTheRunSoTheProcessWouldExitNonZero`, and a recall
case) and, for every metric, in `EvalScorerTest`.

`make eval-live` without `ANTHROPIC_API_KEY`: "eval-live: ANTHROPIC_API_KEY is not set, so no real model can be
called. Nothing was run." exit 0. It was not run against the real model.

## `make infra-validate`, exit 0

Terraform v1.17.2 on darwin_arm64, `hashicorp/aws` 6.74.0, `hashicorp/random` 3.9.1.

```
terraform -chdir=infra fmt -check -recursive        (no output, exit 0)
terraform -chdir=infra init -backend=false -input=false   Terraform has been successfully initialized!
terraform -chdir=infra validate                     Success! The configuration is valid.
terraform -chdir=infra test
  run "defaults_are_private_encrypted_and_versioned"... pass
  run "one_nat_gateway_per_zone_when_asked"... pass
  run "rejects_unknown_environment"... pass
  run "rejects_the_latest_image_tag"... pass
  run "rejects_a_certificate_that_is_not_acm"... pass
  run "rejects_a_database_that_is_not_postgresql_18"... pass
  run "rejects_a_storage_limit_below_the_initial_size"... pass
  run "rejects_a_vpc_too_small_to_split"... pass
Success! 8 passed, 0 failed.
```

`terraform test` uses mocked providers (no credentials, nothing created). `plan` and `apply` were never run.

## `LEXVAY_E2E_SLOT=3 make test`, exit 0

```
[INFO] Tests run: 237, Failures: 0, Errors: 0, Skipped: 0
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
 Test Files  3 passed (3)
      Tests  8 passed (8)
  3 passed (13.4s)
```

New tests: `EvalScorerTest` (9), `GoldenFileTest` (3), `EvalServiceTest` (3), `EvalModelTest` (1),
`FakeLlmClientTest.replaceScriptDropsEarlierResponsesForTheKey`. `SeedLoaderTest` and `RuleVersionDiffSeedTest` now pass
`ScriptedFakeModel`.

## Not done or not verified

- No run against the real model: no API key. The live thresholds for recall, extraction and evidence agreement are
  first guesses (D-104).
- Terraform was never planned or applied. RDS parameter group family `postgres18` and the CloudFront/ALB wiring are
  validated for schema only. Creating the `lexvay_owner` and `lexvay_app` roles on the new database is a manual step
  (D-108).
- The Playwright run logs a `TypeError: Cannot read properties of null (reading 'id')` from
  `frontend/src/app/screens/review/review.ts:82` during the review test; the test passes. Not touched (frontend).
