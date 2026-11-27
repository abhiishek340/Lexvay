# FIX-3 progress

- ASSESSMENTS_FAILED ignores failures older than the latest finished assessment (D-360).
- Evidence requests carry `signed`; the work queue counts signed and stale requests as blocked (D-361).
- `findingCounts` shares the decided predicate (D-362).
- `CURRENT` measured (D-363) and rewritten as a once-per-statement set (D-364): mappings of a 500-row assessment 15 to
  30 ms down to about 3 ms, work queue 280 ms down to 2 to 5 ms on the same tenant.
- `IndexUsageTest` no longer pins one index for the manual dedupe (D-365).
