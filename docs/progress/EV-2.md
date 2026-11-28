## EV-2 — Evaluation lab

Branch `ev-lab`, migration V028 (D-626), slot 8. Decisions D-621 to D-630 are in `docs/decisions/EV-2.md`.

## Plan (written before building)
1. Answer key format: `eval/keys/schema.json`, `AnswerKey`, `AnswerKeys` (schema check, consistency check, splits, lock).
2. `eval/keys/demo-northfield.yml` (fictional, one non-independent labeller, 42 requirements) built from the seed and the golden set.
3. Pure metrics (`LabScorer`, `Ratio.wilson95`), limits (`eval/lab-thresholds.yml`, `unset` allowed), report (Markdown first line
   fictional, JSON for `scripts/eval-diff`).
4. `LabService` on `EvalService` (no parallel harness); `EvalRunner` lab branch; `scripts/eval.sh --lab/--holdout/--baseline`;
   `make eval-lab`, `make eval-holdout`.
5. `GenericBaseline` (same model, same documents, one generic prompt), BLOCKED in scripted mode.
6. Tests; OPERATIONS "Evaluation lab".

## Done
All of the plan. Shared code touched (D-629): `EvalService`, `EvalRunner`, `EvalModel`, `EvalScorer`, `EvalRepository`, `Golden`,
`EvalReport`, `Ratio`, `JsonSchemaValidator`, `YamlFile`, `Makefile` (two targets, `test-scripts`), `scripts/eval.sh`,
`.env.example`, `application.yml`, `.gitignore`.

## Not done, on purpose (D-630)
No held-out key exists, so `eval/holdout.lock` is empty. No real model was run, so no baseline of a real model has been measured.
No limit but severe false assurance is set.

## Tests
See the PR for the commands and results.
