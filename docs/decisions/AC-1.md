# AC-1 decisions: academic pathway model (D-586 to D-595)

Work item AC-1 in `docs/ROADMAP.md`. Folded into `DECISIONS.md` by the orchestrator when the item merges.

**D-586 — A pathway is a set of independent groups per program and catalog year.** `pathway_group` holds one group
(`ALL_OF`, `CHOOSE_COUNT` or `CHOOSE_CREDITS`), `pathway_group_course` its member courses (the logical course, not a
version), `pathway_group_retirement` the retirement of a group. A group is never edited: all three tables carry
`forbid_change()` and forced row-level security, and a correction retires a group and adds another (rule 5). A graduation
path is any choice of courses that satisfies every group; groups are independent and a course may be a member of several.
The minimum of a group is null when it is not recorded; `min_count` and `min_credits` are `> 0`, so a group that asks
for nothing cannot be stored. Rejected: one `required` flag per course (cannot say "choose 2 of 3"); a free-form
expression language (cannot be checked, rule 3).

**D-587 — Credits belong to a course version and have a unit; there is no conversion.** `course_version.credits`
(`numeric(5,2)`, null = unknown) and `credit_unit` (`SEMESTER` default, `QUARTER`). A new value is a new version, so the
audited history of a course keeps what the catalog said in each year. A group's minimum is read in the unit of its members;
members in two units make every answer that needs their credits UNKNOWN. Conversion needs expert approval and is a later
item. A pathway member's credits are those of the version of the group's own catalog year, else the course's newest
version (`PathwayRepository.ACTIVE_GROUPS`), so a 2027-28 version does not rewrite the 2026-27 pathway.

**D-588 — The backfill assumes nothing.** V024 groups, per program and per catalog year of its course versions, the courses
with `required = true` as one `ALL_OF` "Required courses" group and the others as one `CHOOSE_COUNT` "Electives" group
with `min_count` NULL. How many electives a graduate takes is not in the old data, so the evaluator answers UNKNOWN for
them until a person records it. `PathwayBackfillTest` runs the migration's own statements. A course created later through
`POST /programs/{id}/courses` is in no group (decided in D-591) until a person adds it to one.

**D-589 — The evaluator reasons about one group at a time and never enumerates paths.** `PathwayCoverage` is pure
(unit-tested without a database). A set of courses S is on every valid path when every path contains at least one course of
S. A path that avoids S exists exactly when every group can be satisfied by its members outside S, so S is on every path as
soon as one group cannot: an `ALL_OF` group that holds a course of S; a `CHOOSE_COUNT` group whose other members number
fewer than its minimum; a `CHOOSE_CREDITS` group whose other members add up to fewer credits than its minimum. For one
course this is the rule of the spec; for a set it is what the coverage matrix needs (evidence in both electives of "choose 1
of 2" is on every path). The answer has three values. A minimum not recorded, a credit not recorded where it matters (credits
that are not recorded can only add, so a known sum that already reaches the minimum settles the question), two units, and a
course that no group places are UNKNOWN, never TRUE, and the reasons come back with the answer. A group that holds none of
the courses cannot force anything, so its unknown minimum does not matter for them. "On some path" asks whether every group
can be satisfied and a group holds a course of S. The fewest credits of a path add every `ALL_OF` member and the cheapest
selection per `CHOOSE` group (the smallest `min_count` credits; for `CHOOSE_CREDITS` the smallest subset sum that reaches the
minimum, exact, over hundredths). The minimum is UNKNOWN when any credit of a group is not recorded (a member without credits
could be the cheap one), when units are mixed, when no group exists, or when two groups hold the same course (a course taken
once counts once, and telling which group it counts for would mean enumerating paths).

**D-590 — Seed and golden set.** Northfield's courses get credits (3 each, the clinical sequence COUN 660 six), a third
elective COUN 645 (no syllabus, the "additional elective approved by the program director" of the degree plan) so that
"Electives: choose 2 of 3" is a real choice, and two groups in `seed/manifest.yml` (`pathwayGroups`). Westbrook, which had no
courses, gets COUN 500 and COUN 510 and "Electives: choose 1 of 2"; the seed loader returns before documents for a tenant
without rule versions. The golden set changes only where the new rule changes a proposal:
UT-1, CO-1, AZ-1, CA-1 and TX-1 (credit hours) go from `EVIDENCE_FOUND` to `AMBIGUOUS`, see D-592. No other finding changes:
the required courses are on every path, and the three electives stay ambiguous (the path "choose 2 of 3" avoids any one).
No threshold was loosened. Note for the owner: the degree plan document says the clinical sequence is 9 credits and the
courses it lists add up to far fewer than 60; the spec's 6 credits for COUN 660 and the seeded courses give a pathway minimum of 36.
Either way the program total of 60 and the pathway differ, and the cross-check says so.

**D-591 — The assessment asks the pathway, and a program without one is unknown, not fine.** `DowngradeRule.notRequiredCourse`
is replaced by `notOnEveryPath`: FALSE gives "the cited course COUN 650 is not on every graduation path", UNKNOWN gives
"the program's graduation paths are not fully known (Electives: minimum not recorded)", both AMBIGUOUS. A page of a
program-level document names no course and is not judged. The pathway is read once per assessment run (`MappingProducer.Basis`
with the program facts), not per requirement. The course's `required` flag is still stored and still shown in the prompt as
"(required)" or "(elective)" (prompt version unchanged), but no code decides on it. A course that is in no group makes its
quote AMBIGUOUS ("COUN 650 is not placed in a graduation group"): the setup screen shows the pathway read only for now and
UI3-3 designs the editor, so until then a contributor adds groups through the API. Test fixtures that are not about the
pathway use `AssessmentFixtures.course`, which keeps the pathway as V024's backfill would leave it.

**D-592 — The credit cross-check is the rules engine's arithmetic.** For a `TOTAL_CREDITS` requirement with a recorded
program fact, `RulesEngine` compares the fact with the pathway minimum (`PathwayCoverage.minimumCredits`). If the minimum is
known and differs, the finding is `AMBIGUOUS` whatever the comparison said, with the calculation, both numbers, the fact's
source (document and page) and the groups the minimum comes from in the rationale. An unknown minimum leaves the comparison
alone. The fact has no unit; the minimum's unit is named in the sentence. No model is involved (rule 3).

**D-593 — Coverage reads the pathway in the statement it already makes.** `ELECTIVE_ONLY` meant "no cited course is required".
It now means "some graduation path avoids every cited course"; the new `PATHS_UNKNOWN` ("Graduation paths not fully known")
means the pathway does not say. `CoverageRepository.MATRIX` embeds `PathwayRepository.ACTIVE_GROUPS` as one more common table
expression and returns the groups as a column; the risk of a row that survives the SQL tests is decided in Java by
`PathwayRisk` from the same `PathwayCoverage`, so the request keeps one statement and one transaction (D-110). `SINGLE_COURSE`
is unchanged and is decided after the pathway says TRUE.

**D-594 — A group's citation is an evidence span.** The spec lists `document_id`, `char_start` and `char_end` on the group.
Offsets are relative to a page (D-041), so those three columns cannot be verified against stored text without a page number.
The group therefore holds `evidence_span_id`, the same verified span a program fact holds (document, page, offsets, exact
quote), made through `ProgramCitationService` and `QuoteVerifier` (rule 2). Nothing is duplicated, and a citation that does
not verify is a 400 that stores nothing.

**D-595 — API and what is left out.** `GET /programs/{id}/pathway?catalogYear=` is one statement (the program's own year
without the parameter) and returns groups, members with credits and unit and whether each course alone is on every path and
on some path, the fewest credits with its unit, and the unknowns. `POST /programs/{id}/pathway-groups` and
`POST /pathway-groups/{id}/retire` are for contributors and administrators and answer with the pathway of the group's year;
they are audited as `PATHWAY_GROUP_CREATED` and `PATHWAY_GROUP_RETIRED` and listed in `Rule9_AuditCoverageTest`,
`InputValidationTableTest` and `audit-sentence.ts`. There is no `PUT`. `POST` of a course and of a course version take
optional `credits` and `creditUnit`. A group needs at least one and at most 100 courses, all of the program; a `minCount`
above the number of courses is refused. Not done, on purpose: determinations (rule 6) do not yet name the pathway groups
they were assessed against, and the setup screen shows the pathway read only; both belong to later items.
