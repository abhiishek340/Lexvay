### EV-2 — Evaluation lab (D-621 to D-630)

Work item EV-2, "Evaluation lab" (docs/ROADMAP.md). Folded into DECISIONS.md by the orchestrator when it merges. One migration
(V028, widening the `llm_call.purpose` CHECK, D-626), no new dependency, no model downloaded or run, no paid service called: the tests answer from `FakeLlmClient` and Mockito, and
no test touches the network. The demo key is built from the fictional seed only and says so.

**D-621 — The lab sits beside `make eval`, on the same runner and service.** `make eval` is unchanged. `make eval-lab` and
`make eval-holdout` are `scripts/eval.sh --lab` and `--holdout` (flags combine with `--live`; `--baseline` needs a lab flag;
`LAB_FLAGS` passes them from make). `EvalRunner` gets a lab branch selected by `lexvay.eval.lab` (`dev`, `holdout`), and
`EvalService` gives `LabService` what it already had: `assessed(seedDir, approved, use)` seeds a fresh isolated tenant, lets the
pipeline assess it and calls `use` with that tenant current; `run` is now a caller of it. So the pipeline under test is the one
`make eval` runs, in the same modes (`mode()` is the one place that decides `SCRIPTED`, `LOCAL_LIVE` or `PAID_LIVE`). A key
implements `RequirementList` like the golden set: in a live mode its approved requirements replace the model's extraction
through the reviewer's replace-requirements path, so matching is scored requirement by requirement; the scripted model keeps the
seed's own extraction. Search terms are optional in a key (they are retrieval vocabulary, not a label), and the demo key carries
the golden set's. A lab run does not run the retrieval measurement and does not rewrite docs/RETRIEVAL.md.

**D-622 — The answer key.** `eval/keys/<key-id>.yml`, validated first by `eval/keys/schema.json` (draft 2020-12, unknown fields
refused, every error names its path) and then by `AnswerKey.problems()` for what a schema cannot say: a requirement twice;
numeric type and numeric rule disagreeing; `EVIDENCE_FOUND` with no span, or `EVIDENCE_MISSING` / `NOT_APPLICABLE` with one; a
span in a document that is not in the source bundle or with an impossible range; an adjudication whose final label differs from
the requirement's, whose labellers did not disagree, or that names a labeller who is not in the provenance. A key that fails
either check is refused whole, never repaired. Provenance holds `fictional`, the date, the labellers (an `id`, a `role`, no
names, and `independent`) and the source bundle (program and catalog year, each rule version by jurisdiction, label and
effective date, each document by file name and SHA-256). Each requirement carries its approved text and type so that a label is
tied to the exact words it was made against. Spans are `{document SHA-256, page, charStart, charEnd}` in the stored page text,
which is what the pipeline's own spans use. The finding set adds `NOT_APPLICABLE`, which a person may give and the system never
can (rule 1: the system's findings are three). `eval/keys/demo-northfield.yml` has `fictional: true`, one labeller who is the
engineer who wrote the seed (`independent: false`, said in the file and in every report), 42 requirements and 39 spans. The spans
are the full sentences of the seed PDFs' extracted page text around the golden set's `quoteContains`, computed once with the
production extractor and checked in as data; the key's findings and documents come from `golden.yml`, not from
`seed/fake-llm.yml`, so the scripted pipeline reproducing it is a regression check and not an accuracy claim. Severity is CRITICAL
for numeric, accreditation, ethics and state-specific requirements except telehealth training, STANDARD otherwise.

**D-623 — Metric definitions.** All are counts out of counts (`Ratio`), pure in `LabScorer`, and each is also computed per state,
requirement type and severity (and per key when a run has several keys, which are pooled).
- *Severe false assurance* (headline): `EVIDENCE_FOUND` where the key says `EVIDENCE_MISSING` or `NOT_APPLICABLE`, or whose cited
  quote is from a course that is not on every valid graduation path while the requirement is `CRITICAL`. "On every path" is
  `PathwayCoverage.onEveryPath` (AC-1) over the program's active groups, the same question the pipeline's own downgrade rule asks:
  FALSE and UNKNOWN (the pathway does not say) both count, because rule 4 reads whatever is unrecorded against the quote; a document of
  no course (a degree plan, a handbook) is not judged. The baseline's quotes are judged the same way, so the two are compared on one
  definition. (The first version used `course_version.required` as a proxy, before AC-1 existed.) The denominator is
  every requirement of the key, not the answers the system gave, so it does not shrink when the system abstains. A key `AMBIGUOUS`
  answered `EVIDENCE_FOUND` is a wrong finding and counts in accuracy, not as severe, as the spec defines it.
- *Finding accuracy* leaves out requirements the key calls `NOT_APPLICABLE` (no system finding can be right); *abstention* is
  `AMBIGUOUS` over the requirements the key decides; *numeric accuracy* is the finding equal to the key over numeric requirements
  (`NUMERIC`, `SUPERVISED_HOURS`). That the rules engine produced the answer (rule 3) is still checked by `make eval`; here the
  definition is the same for the pipeline and the baseline so they compare.
- *Evidence precision and recall*: a cited span matches an accepted one when they share a document, a page and at least 50 % of
  the shorter span's characters. Using the shorter span means a short quote inside an accepted sentence matches, which is how a
  careful quote behaves; the cost is that a quote that contains the whole accepted span matches too, which the thin-quote rule
  (rule 4) and the verifier's single-page limit bound but do not remove. A requirement with no mapping counts as wrong in every
  denominator it belongs to and is never an assurance.
- Requirements the assessment has that the key does not label are listed in the report and not scored.

**D-624 — Wilson interval and limits.** `Ratio.wilson95()` is the Wilson score interval with z = 1.959963984540054, clamped to
exactly 0 at 0 successes and 1 at n (rounding must not put the rate outside its own interval); hand-computed fixtures: 8 of 10 is
[49.0 %, 94.3 %], 0 of 33 is [0 %, 10.4 %]. `eval/lab-thresholds.yml` has one limit per metric, a rate or `unset`. An `unset`
limit is printed and never passes or fails. Only severe false assurance is set (0.0), because rule 4 makes any occurrence
unacceptable; nothing else has been measured against independent labels, so nothing else is pre-agreed. A limit applies to the point
estimate and the report prints the interval next to it, so "0 of 42, passes" is read as "up to 8.4 % is still possible".
A set limit with nothing to measure is a breach ("cannot be measured"), like the `make eval` limits.

**D-625 — Splits, the lock and the guard.** `AnswerKeys` is the only reader. Each key declares `split: dev` or `holdout`;
`eval/holdout.lock` has one `<sha256>  <key file>` line per holdout key (the format of `shasum -a 256`, `#` comments). A dev
run reads the one `split` field of a holdout key and nothing more, runs no key named in the lock whatever it declares, and prints
nothing of a holdout key. `make eval-holdout` needs `CONFIRM_HOLDOUT=1` (refused with exit 64 by the script before the stack
starts) and `AnswerKeys.holdout()` refuses, naming each reason, when a holdout key is missing from the lock, its hash differs, a
lock line names a file that is missing or is not a holdout key, a line is malformed or a file is named twice. Changing the lock
is a deliberate commit of its own (said in the file, the refusal message and OPERATIONS). The repository ships no holdout key and
an empty lock: the only key is the fictional demo, so a split of it would be no holdout. A holdout run with no locked key says so
and exits 0 (nothing to measure, nothing refused); a dev run with no key is an error (exit 2), so `make eval-lab` cannot be green
by finding nothing.

**D-626 — The generic baseline.** `GenericBaseline`: per requirement, one call with the full text of the program's documents
(`document_page` rows of non-rule-text documents, in file-name and page order, until the next page would pass
`LEXVAY_EVAL_BASELINE_TOKEN_BUDGET`, default 24 000 tokens at a quarter of the characters, which holds the whole seed) and the
requirement's title and text, under one system prompt (`llm/eval-baseline-v1.system.txt`) that defines the three findings and
asks for the file name, page and a verbatim quote. The prompt carries none of the pipeline's rules (no elective rule, no
"when unsure choose AMBIGUOUS", no instruction about numbers). Output is validated against `llm/eval-baseline.schema.json`; an
answer outside it, or a model that declines, is an unusable answer (counted, and shown), never a finding. The quote is checked by
`QuoteVerifier` against the stored page the model was shown; a verified quote becomes the cited span (with the document's SHA-256
and whether its course is on every graduation path, D-623), an unverified one locates nothing and counts against the baseline's quote verification, and
the finding it claimed is scored as claimed. Calls go through `LlmGateway`, so the spend gate applies, and are recorded by
`LlmCallRecorder` with prompt version `eval-baseline-v1` so spend accounting stays true: the stored `metered` flag is what the gate
adds up, as for every call. They are stored under their own purpose, `EVAL_BASELINE`, because a record that called them
`MATCH_EVIDENCE` would misstate what the call was. Migration V028 widens the `llm_call.purpose` CHECK with it (V028: V026 is AC-1's
and V027 is RG-2's, both merged first, so it merges after them).
`EvalRepository.modelUse(boolean)` separates the two uses by purpose, so the pipeline's cost line is not inflated by the
baseline's calls and the baseline has its own. In
`SCRIPTED` mode the baseline is reported as `BLOCKED: needs a model` and no call is made; a `LlmCallBlockedException` (spend gate,
no price, over budget) blocks only the baseline, with the gate's message, and the pipeline's report stands.

**D-627 — Reports.** `eval/reports/<date>-<mode>-<split>.md` and `.json` (mode lower-cased, `_` as `-`), gitignored, written by
`EvalRunner` and printed. The Markdown's first line is `# Lexvay evaluation lab: fictional, not evidence of accuracy` when any
key is fictional and `# Lexvay evaluation lab` otherwise. The header lists mode (with EV-1's phrase), model, host, date, split,
each key (id, file hash prefix, fictional or real, date, labellers and how many are independent, requirement count, program,
bundle size) and "Accuracy claim supported", which lists every reason it is not: fictional key, fewer than two independent
labellers, scripted answers, dev split. Then the headline with its interval, one table (pipeline, and the baseline beside it, or
`BLOCKED: ...`, or a line saying it was not asked for), breakdowns for each system, model use, disagreements with the key and the
result. The JSON (`lexvay-eval-lab-report/1`) carries the same numbers with numerator, denominator, rate and interval, the
breakdowns, the usage and, per requirement, the expected and answered finding, whether the evidence matched and whether it was a
severe false assurance. `scripts/eval-diff a.json b.json` (jq; exit 0 identical, 1 different, 64 not comparable) compares two
reports and refuses two that scored different keys (by file hash) or different splits, since a metric over other questions says
nothing about a change; mode and model may differ, which is the usual reason to compare. Its test, `scripts/lib/eval-diff-test.sh`,
runs in `make test-scripts`.

**D-628 — Sources must be the seed.** The lab can only load what `SeedLoader` loads, and the seed is fictional by construction
(it refuses a manifest that is not). Before assessing, `LabService` checks that the key's bundle is satisfied by the seed: the
program name and catalog year, each rule version's jurisdiction, label and effective date, each document's SHA-256 against the
seed PDFs. A key for other sources is refused with `BLOCKED: answer key X was written for sources this checkout does not hold`,
naming what is missing, and exit 2. Loading a real program and its rule text into an evaluation tenant needs the rights review
INTAKE-2026-11-27 lists as open and a loader that is not this item; nothing here weakens "university content is never committed".

**D-629 — Changes to shared code, each small and covered.** `RequirementList` (new interface; `Golden` and `AnswerKey` implement
it, `EvalModel` takes it instead of `Golden`); `EvalService` (`assessed`, `demoTenant`, `mode`, `usage` and `pipelineUsage`
package-visible); `EvalScorer.verifiedQuotes` (the quote check, extracted from `score`); `EvalRepository.pages()` and
`modelUse(boolean)` (which also sums `duration_ms`); `EvalReport.Mode.description()`; `JsonSchemaValidator` can be built from a
schema already read and can validate a parsed tree; `YamlFile.readTree`; `Ratio.wilson95()` and `interval()`. `.gitignore` gets
`eval/reports/`; `.env.example` and `application.yml` get the baseline token budget.

**D-630 — Honest limits.** (1) The measured-baseline path of `LabService` is tested with the scripted model answering one script per
question while a Mockito spy makes `EvalService.mode()` say `LOCAL_LIVE` once (the assessment itself still runs the scripted path);
no local model was run, so no real model's baseline has ever been measured and the report of one has never been seen by this item's
author. (2) Adjudication is tested on synthetic keys; the demo key has one labeller and no disagreement to adjudicate, because
inventing a second labeller would fabricate provenance. (3) No metric but severe false assurance has a limit, and none can have
one before an independent key exists. (4) `NOT_APPLICABLE` is excluded from finding accuracy rather than scored, because the system
has no way to say it; the rule it is held to is severe false assurance. (5) "Not on every path" is only as good as the pathway recorded for the program: an unrecorded pathway
makes every course-borne quote count against a CRITICAL requirement (UNKNOWN), by design, so a program with no recorded groups
shows a severe false assurance for each such quote. After AC-1 changed the seed's degree plan, the demo key's hash for that
document was updated in the same commit (the plan's changed text is after every span the key accepts, so the offsets stand). (6) `make eval-lab` is not in CI: the backend
tests run the same dev run on the demo key (`LabServiceTest`) and `make eval-lab` is part of this item's acceptance runs. (7) A
whole-page quote overlaps an accepted span fully under D-623's definition; the lab does not detect over-long quotes.
