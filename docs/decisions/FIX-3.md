# FIX-3 — Work queue and evidence request follow-ups (D-360 to D-369)

#### D-360 `ASSESSMENTS_FAILED` lists failures after the latest finished assessment

A FAILED assessment is listed when its program has no FINISHED assessment, or when it was created after the creation
time of the program's latest FINISHED one (the `latest` CTE, D-195, now also exposes that `created_at`). A failure
followed by a successful run leaves the queue; a failure after a success appears again. Created time, not finished
time, because a failed run that started before the successful one was superseded by it, whichever ended first.
Tests: two failures without a success are both listed; a later success clears both; a failure after that success is
the only one listed (`WorkQueueApiTest`).

#### D-361 One "signed for this assessment and jurisdiction" predicate

`DeterminationRepository.signedFor(assessmentExpr, jurisdictionExpr)` returns the SQL `EXISTS` over SIGNED
determinations. It is a method, not a constant, because its users name the columns differently (bind parameters in
`hasSigned`, `m.` columns in the evidence request statement and in the work queue's `current_mappings`). The text of
the predicate exists once.
- `hasSigned` is `SELECT <predicate>` (a boolean, no `count`).
- `EvidenceRequestView.signed` is one more column of the existing statement; the status stays OPEN.
- Work queue: `current_mappings` gets `request_blocked` = latest event is an evidence request AND (stale OR signed).
  The `AND` runs the determination lookup only for mappings with an open request. `EVIDENCE_REQUESTS_OPEN` counts
  `request_blocked` mappings in `ids.blockedCount` and only the others as answerable items; a group with no answerable
  request is not listed. A signed determination covers every mapping of its assessment and jurisdiction, and a group is
  exactly that, so a signed group is never listed: `blockedCount` from signing alone is never visible, it only keeps
  the definition in one place.
- Answering still returns 409 (`EvidenceRequestService.respond`, unchanged).

#### D-362 One "decided" predicate

`AssessmentRepository.findingCounts` joins `MappingRepository.LATEST_EVENT` and counts `NOT UNDECIDED` as decided
(`NOT stale AND NOT NEEDS_DECISION`, De Morgan of the signing gate). `FindingCountsParityTest` compares it with the
previous statement (kept in the test) on the seeded tenant as seeded and after every kind of review history and a
stale mapping.

#### D-363 Measurement of `MappingRepository.CURRENT`

On the WS-PERF database (about 200 tenants, `IndexUsageTest`) one tenant got rule version v1 with 500 requirements, its
successor v2 with 500 more, an assessment with 500 mappings on v1 (v2 has no mappings in it: the historical case after
a rule change) and one with 500 on v1 and 500 on v2 (after the re-run, the 500 on v1 hidden). `EXPLAIN (ANALYZE,
BUFFERS)` of `GET /assessments/{id}/mappings` (`MappingRepository.BY_ASSESSMENT`) as the application role: 15.5 ms and
5 192 buffers before the re-run, 29.6 ms and 4 747 buffers after it (limits: 5 ms or 2 000 buffers). The plan is a
nested loop anti join with 250 000 and 500 500 rows removed by the join filter: quadratic, as D-199 predicted. The work
queue statement of that tenant took 265 to 285 ms (budget 40 ms). The filter was rewritten.

#### D-364 `MappingRepository.current(assessments)`

`CURRENT` (a correlated `NOT EXISTS` evaluated per mapping) is replaced by `current(String assessments)`:
`AND (m.assessment_id, r.rule_version_id) NOT IN (SELECT DISTINCT nm.assessment_id, nv.supersedes_id FROM mapping nm
JOIN requirement nr ... JOIN rule_version nv ... WHERE nv.supersedes_id IS NOT NULL AND nm.assessment_id <assessments>)`.
The subquery does not depend on the row, so PostgreSQL hashes it once per statement. `assessments` bounds the
assessments the statement reads: `= :a` for one, `IN (SELECT assessment_id FROM latest)` for the work queue. The result
set is identical (a mapping is hidden when a successor of its rule version has mappings in its assessment);
`RuleVersionChangeTest` compares the previous form (`LegacyCurrentFilter`, test only) with both new forms before the
change, after the successor was created and after the re-run, and the READY_TO_SIGN parity test (D-193) compares the
queue's form with the signing gate's. Result: the same statement takes 3.2 ms (3 530 buffers) and 3.5 ms (3 527
buffers); the work queue 2.0 to 5.2 ms. 3 500 buffers is above 2 000 but is per-row work (review events 1 000, evidence
spans 900, the set 1 500), linear in the rows; `IndexUsageTest` fails above 4 000.
Rejected: a per-mapping `EXISTS` with index probes (500 probes per mapping when the successor has no mappings: worse);
a `mapping.rule_version_id` column with an index (a migration for a read cost that is already linear); planner settings.

#### D-365 The manual "already queued?" check may use either job index

Since FIX-2 the planner may choose `job_entity_idx` for the rule source's manual check (one entity) or `job_claim_idx`
(queued jobs), depending on sampled statistics; both avoid a sequential scan of `job`. `IndexUsageTest` accepts either
for that statement (`Candidate.eitherOf`) and still fails on `Seq Scan on job`. `job_claim_idx` (V001) stays pinned for
the scheduler statement. Every V008 and V013 index must still be chosen by some statement.

#### D-366 Perf fixture

`PerfSeed.addLargeAssessments` builds the data of D-363; the plans are written to `target/perf/mappings-plan.txt`.
