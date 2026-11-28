# OPS-2 — Cap concurrent local test stacks (D-641 to D-643)

**D-641 — A machine-wide semaphore of directory tokens in `scripts/lib/stack.sh`.** `stack_init` takes one of
`LEXVAY_STACK_CONCURRENCY` tokens (default 2) before it creates anything, so `make e2e`, `make eval` and
`make smoke` share one cap. A token is the directory `<lock dir>/<n>` (n from 1 to the cap) holding `pid` and `slot`.
The lock dir is `LEXVAY_STACK_LOCK_DIR`, default `/tmp/lexvay-stack-tokens-$(id -u)`, deliberately independent of
`TMPDIR`: a sandboxed agent that sets its own `TMPDIR` still shares one cap with every other process of the same user. Only `mkdir` is used for locking, because macOS has no `flock`. Waiting
polls once a second,
prints "<name>: waiting for a free test stack (n of N in use: slots ...)" at once and every 30 s, and gives up with
exit 1 after `LEXVAY_STACK_WAIT_MINUTES` (default 60; 0 means a single attempt). `LEXVAY_STACK_CONCURRENCY=0` skips the
lock. CI runs one stack on a fresh runner, so the default of 2 never makes it wait and CI needs no setting. Both
settings must be whole numbers; anything else is refused before anything starts.

**D-642 — Scans and reclaims run under a short mutex; owners are judged by `ps -p`.** Reclaiming a stale token with a
bare `rm` then `mkdir` lets two waiters race: one can delete the token the other has just created. The scan, reclaim
and creation therefore happen while holding `<lock dir>/.mutex` (another `mkdir`, held for milliseconds).
A mutex older than a minute belongs to a process that died inside that window and is removed. A token is stale when
its pid file is missing or empty (the creator died between `mkdir` and writing it) or when `ps -p <pid>` finds no
process. `ps -p` is used instead of `kill -0` because `kill -0` also fails for a live process owned by another user,
which would wrongly reclaim that user's token. A reused pid keeps a dead owner's token alive; the wait limit bounds the
damage, and nothing is reclaimed that a live process might own.

**D-643 — Release on every exit path, and never touch a stack that was not started.** `stack_init` installs
`trap stack_cleanup EXIT` and turns INT and TERM into `exit 130` and `exit 143`, so the EXIT trap runs for them and
cleanup has one code path. `stack_cleanup` gives the token back last, after the compose project is down, so the next
run cannot start a database on the slot's port before this one has gone. A run signalled while still waiting has
started nothing, so it does not run `compose down -v` (another run on the same slot may own that project) and has no
token to release. Known limit: bash runs a trap only after the foreground command finishes, so a TERM sent to the
script alone (not to its process group, as Ctrl-C does) takes effect when Playwright or Maven returns; a holder killed
with KILL is reclaimed by the dead-pid rule instead.
