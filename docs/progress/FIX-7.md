# FIX-7 — One "latest finished assessment" ordering

Branch `fix-latest-finished`, decisions D-450 to D-452 in `docs/decisions/FIX-7.md`. Backend only, no migration.

- `MappingRepository.latestAssessmentsUsing` now runs `LATEST_ASSESSMENTS_USING`: `program` joined laterally to
  `AssessmentRepository.LATEST_FINISHED`, filtered by an `EXISTS` over the program's latest assessment's mappings of the
  rule version (D-450). `grep -rn "finished_at DESC" backend/src/main/java` shows only `LATEST_FINISHED`.
- `LatestAssessmentsUsingParityTest`: old statement as a literal against the new method, equal as sets for every rule version
  of the seeded tenant, with three extra programs (newest finished lacks the version; newest assessment FAILED; tied finish
  time) and explicit expectations (D-451).
- `IndexUsageTest` explains the statement on the perf seed (100 tenant copies, application role): `program_tenant_idx`,
  then per program `assessment_program_status_finished_idx`, then `mapping_assessment_jurisdiction_idx` (500 rows of
  the large assessment) and `requirement_pkey`. No sequential scan on `mapping` or `assessment`.
  Execution 1.05 ms, 1 551 shared buffers, planning 0.6 ms (`target/perf/latest-assessments-using-plan.txt`) (D-452).
- `make perf`: `EndpointLatencyTest` and `IndexUsageTest` green (slot 6).
- ROADMAP: the FIX-5 and F10a follow-up lines about this ordering are removed.
