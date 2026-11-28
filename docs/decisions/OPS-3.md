# OPS-3 decisions: performance budget on CI (D-644 to D-646)

Why: the p95 budget (CLAUDE.md, `docs/PERF.md`) can only be measured on a quiet machine. On the shared developer
machine, with a load average of 16 to 44, even `GET /jurisdictions` breached it, so `make perf` there proves nothing.

## D-644 One toolchain action, and the path filter is a `git diff`
The Java and Maven-cache steps of the `test` job moved unchanged into the composite action
`.github/actions/setup-backend`, which `test` and `perf` both use (no copy of the steps). `dorny/paths-filter` is not a
pinned action in this repository, so the filter is a `git diff --name-only origin/<base>...HEAD` step in the `perf`
job (checkout with full history). A pull request that changes none of `backend/src/main/**` (which includes the
migrations), `backend/src/test/java/com/lexvay/perf/**` or `docs/PERF.md` skips the measuring steps and the job still
ends green, which is what lets it be a required check by convention.

## D-645 `make perf` reports both tests, and the summary only publishes
`make perf` used to stop at the first failing test, so a latency failure hid the index report. The recipe now runs
`EndpointLatencyTest` and `IndexUsageTest` and fails if either did. The job summary prints `target/perf/latency.md` (p95
per endpoint, the budget in the heading), `target/perf/index-usage.md` (the planner's choice per index and the plan of
every index it ignored) and the failure messages from the surefire reports; nothing is recomputed. The plan files are
kept as the `perf-reports` artifact for 14 days.

## D-646 Triggers, timeout and size
`ci.yml` gains `schedule` (07:00 UTC) and `workflow_dispatch`; the `test`, `infra` and `image` jobs run only for pushes
and pull requests, so a nightly run is just the perf job. The `perf` job has `timeout-minutes: 45`. The seed size is the
default of the tests (30 renamed copies for the latency test, 100 for the index test), which is the documented size in
`docs/PERF.md`; the first real run decides whether it fits (recorded in `docs/progress/OPS-3.md`). Nothing in the GitHub
settings changes: the PR job is a required check by convention. Pushes to `main` report and are never cancelled by a
newer push, because the concurrency group of those runs is the commit.
