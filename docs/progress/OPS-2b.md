# OPS-2b — Stack mutex race on Ubuntu 26.04

Branch `ops-stack-lock-fix`. Decision D-647 in `docs/decisions/OPS-2b.md`. Tooling only: no application code, no migration.

## Cause
The CI runner image is Ubuntu 26.04, whose `mkdir` is uutils coreutils 0.10.0. When two processes `mkdir` the same new
path together, the loser's `mkdirat` fails with `EEXIST`, uutils looks at the path again, sees a directory and exits 0.
The `mkdir`-based mutex of OPS-2 then admitted two holders, which shared one token, so the third holder found a free slot
at once. Reproduced in `ubuntu:26.04` (1 failure in 6 runs of the unchanged test); `ubuntu:24.04` and macOS never fail.

## Done
- `scripts/lib/stack.sh`: the mutex is the file `<lock dir>/.mutex`, created by the shell's `O_EXCL` open (`set -C`),
  removed with `rm -f`; the stale-mutex rule is unchanged. Header comment updated.
- `scripts/lib/stack-lock-test.sh`: a new first section runs the mutex against a `mkdir` that reports success for an
  existing directory and checks that a second contender stays blocked; it fails on the old code every time and passes
  now. `no_mutex` checks for a file instead of a directory.

## Results
See the pull request for the command output.
