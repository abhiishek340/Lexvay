# OPS-2 — Cap concurrent local test stacks

Branch `ops-stack-cap`, end-to-end slot 5. Decisions D-641 to D-643 in `docs/decisions/OPS-2.md`. Tooling only: no
application code, no migration.

## Done
- `scripts/lib/stack.sh`: `stack_init` acquires a directory token (`LEXVAY_STACK_CONCURRENCY`, default 2; 0 turns it off),
  waiting up to `LEXVAY_STACK_WAIT_MINUTES` (default 60) with a status line every 30 s; `stack_cleanup` releases it, and
  INT and TERM now run the cleanup. `scripts/e2e.sh`, `scripts/eval.sh` and `scripts/smoke.sh` are unchanged.
- `scripts/lib/stack-lock-test.sh` and `make test-scripts` (about 7 s, no Docker): the third holder waits and then takes a
  released token, a dead owner's token and a token without a pid are reclaimed, a TERM-killed holder releases, a wait
  that times out exits 1 naming the slots, concurrency 0 creates no token, bad limits are refused.
- CI runs `make test-scripts` before `make test`.
- Docs: CLAUDE.md "Local environment", README.

## Results
See the pull request for the command output.
