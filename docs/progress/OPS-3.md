# OPS-3 progress: performance budget on CI

Branch `ops-perf-ci` from `origin/main` at `b9753c1`. Decisions: `docs/decisions/OPS-3.md` (D-644 to D-646). Evidence class:
fixtures, on a GitHub-hosted runner (`ubuntu-latest`).

## Done
- `perf` job in `.github/workflows/ci.yml`: push to `main`, nightly 07:00 UTC, `workflow_dispatch`, and pull requests that
  change the code, the migrations, the perf tests or `docs/PERF.md`; 45 minute timeout; job summary and `perf-reports`
  artifact.
- Composite action `.github/actions/setup-backend` for the Java and Maven cache steps, used by `test` and `perf`.
- `make perf` runs both tests and fails if either does. `docs/PERF.md` names CI as the arbiter.

## First real run (pull request 56, run 46119041008, job `perf`)
- The `perf` job passed. `make perf` took 3 minutes 8 seconds in total: `EndpointLatencyTest` 79 s, `IndexUsageTest` 78 s at
  the default 100 copies. So the default seed size fits the 45 minute timeout many times over and no CI size was needed.
- Every endpoint was under the 40 ms budget on both tables. Examples, p95 in ms (seed only, then 62 tenants):
  `GET /jurisdictions` 4.48 and 1.15, `GET /rule-versions` 2.47 and 1.81, `GET /programs/{id}/overview` 3.08 and 2.78.

## Remaining unknowns
- The `test` job of the same run failed in `make test-scripts` (`stack-lock-test.sh`: "exactly two tokens are held again"),
  and `main` at `b9753c1` failed the same way. That is OPS-2's script test, not this change; it passed on `main` at
  `f0f3106`, so it looks timing dependent on a runner.
- RG-1 (pull request 55) was not merged when this ran, so `GET /rule-versions` with its changes is not measured here yet.
