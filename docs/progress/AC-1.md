# AC-1 progress: academic pathway model

Branch `ac-pathway`, migration V026, decisions D-586 to D-595 in `docs/decisions/AC-1.md`. Folded into `PROGRESS.md` by the
orchestrator when the item merges.

## Built

- **V026**: `course_version.credits` / `credit_unit`; `pathway_group`, `pathway_group_course`, `pathway_group_retirement`
  with forced row-level security and `forbid_change()`; a backfill that groups required courses and electives per program
  and catalog year and assumes no minimum; `course_version_course_idx` and `pathway_group_program_idx`.
- **`PathwayCoverage`** (pure, three-valued): is a set of courses on every valid path, on some valid path, and the fewest
  credits of a path. Never enumerates paths. Truth table in `PathwayCoverageTest` (30 cases: unknowns, `CHOOSE_CREDITS`,
  mixed units, empty groups, overlapping groups, unplaced courses).
- **API**: `GET /programs/{id}/pathway?catalogYear=` (one statement), `POST /programs/{id}/pathway-groups`,
  `POST /pathway-groups/{id}/retire`; optional `credits` / `creditUnit` on course and course-version creation. Audited as
  `PATHWAY_GROUP_CREATED` and `PATHWAY_GROUP_RETIRED`; listed in `Rule9_AuditCoverageTest`, `InputValidationTableTest`,
  `audit-sentence.ts` and its spec.
- **Assessment**: `DowngradeRule.notOnEveryPath` replaces `notRequiredCourse`; `RulesEngine` compares a total of credits
  with the pathway minimum (AMBIGUOUS when both are known and differ). The pathway is read once per run.
- **Coverage**: `ELECTIVE_ONLY` now means a path avoids every cited course; new `PATHS_UNKNOWN`; still one statement.
- **Courses join groups**: creating a course or a course version puts it in "Required courses" or "Electives" of its catalog
  year, making the group when it is missing (D-588).
- **Seed**: credits that add up to 60, COUN 645 and the capstone courses, "Electives: choose 2 of 3" (Northfield) and
  "Electives: choose 1 of 2" (Westbrook).
- **Frontend**: read-only "Graduation paths" block on the Courses step, the `PATHS_UNKNOWN` flag, audit sentences.

## Tests added or changed

`PathwayCoverageTest`, `PathwayApiTest`, `PathwayAssessmentTest`, `PathwayBackfillTest`, `Rule5_PathwayImmutabilityTest`,
`Rule7_PathwayIsolationTest`, `RulesEngineTest`, `CoverageApiTest` (three flag cases), `Rule4_*`, `EvalServiceTest` and
`GoldenFileTest` (credit hours), `SeedLoaderTest`, `RegisterSeedTest`, `CoverageSeedTest`, perf scenarios and a pathway
plan check in `IndexUsageTest`, frontend `setup-model.spec.ts`, `coverage-view.spec.ts`, `audit-sentence.spec.ts`.

## Golden set

No change to the credit-hours entries. The seed's fictional data was made consistent instead (D-590): the degree plan lists a
capstone sequence, and the course credits add up to the program fact of 60 on the cheapest path.

## Results

See the pull request for the final tails of `make test`, `make eval` and `make perf`.

## Left for later items

The pathway editor (UI3-3), naming pathway groups in a signed determination (rule 6), unit conversion (needs expert
approval).
