# FIX-7 decisions

#### D-450 `latestAssessmentsUsing` composes `AssessmentRepository.LATEST_FINISHED`

`MappingRepository.latestAssessmentsUsing` (the re-run handler's target list) was the last statement with its own copy of
"the program's latest finished assessment" (the correlated subquery aliased `a2`). It now reads

```sql
SELECT la.id AS assessment_id, p.id AS program_id, la.created_by
FROM program p
JOIN LATERAL (SELECT a.id, a.created_by FROM assessment a WHERE a.program_id = p.id AND <LATEST_FINISHED>) la ON true
WHERE EXISTS (SELECT 1 FROM mapping m JOIN requirement r ON r.id = m.requirement_id
              WHERE m.assessment_id = la.id AND r.rule_version_id = :rv)
```

the same shape as the work queue's `latest` CTE (D-411). D-411 said the constant did not fit this statement because it was
written for a correlated subquery over `a2`; the lateral form removes that obstacle, so no second constant is needed. The
method name, signature and result keys (`assessment_id`, `program_id`, `created_by`) are unchanged. The text is a public
constant, `MappingRepository.LATEST_ASSESSMENTS_USING`, so the index check explains exactly what runs (as
`BY_ASSESSMENT` does). The result has one row per program by construction, so the `DISTINCT` is gone.

Cleanup check: `grep -rn "finished_at DESC" backend/src/main` shows only `AssessmentRepository.LATEST_FINISHED` (the index
definition in migration V008 is not a query). The two follow-up lines about this (FIX-5 and F10a) are removed from the
ROADMAP.

#### D-451 Parity test: old statement as a literal, compared as sets

`LatestAssessmentsUsingParityTest` loads the seeded Northfield tenant (`SeedCopy`, as `WorkQueueSeedParityTest` does) and
keeps the old statement as a literal. For every rule version of the seed it asserts that the old statement and the new
method return equal sets. It adds three programs whose history separates the readings: the newest finished assessment does
not use the version (the older one does), the newest assessment is FAILED and finished later than the older finished one
that does use the version, and two finished assessments share a finish time (creation time decides). It then asserts the
expected assessments by name, so the parity cannot pass because both sides are empty. Fixtures are plain inserts under the
tenant's row-level security; nothing is mocked.

#### D-452 Index usage: same expectation as the work queue's `latest`

`IndexUsageTest` runs `EXPLAIN (ANALYZE, BUFFERS)` of `MappingRepository.LATEST_ASSESSMENTS_USING` on the perf seed (100
tenant copies, as the application role) for the first seeded rule version, writes the plan to
`target/perf/latest-assessments-using-plan.txt`, and fails on a sequential scan of `mapping` or `assessment` that was
executed. The plan and timings are in `docs/progress/FIX-7.md`.
