# OPS-2b — Make the stack mutex race-free on Ubuntu 26.04 (D-647)

**D-647 — The mutex in `scripts/lib/stack.sh` is a file created with the shell's own `O_EXCL` open, not a `mkdir`.**
D-641 and D-642 used `mkdir` as the lock because macOS has no `flock`. The CI runner image is now Ubuntu 26.04, whose
`mkdir` is uutils coreutils 0.10.0, and that `mkdir` is not atomic: it first checks that the path is free, then calls
`mkdirat`, and when `mkdirat` fails with `EEXIST` because another process created the directory in between, it looks
at the path again, finds a directory and exits 0. Two processes calling `mkdir` on the same new path at the same moment
can therefore both succeed. `strace` of six racing calls shows the losing one doing `statx = ENOENT`,
`mkdirat = EEXIST`, `statx = directory`, then exit 0; 16 racing calls produced two winners in about one round in ten
(GNU `mkdir` on the same image, `gnumkdir`, produced none in 40 rounds).

Effect in `scripts/lib/stack-lock-test.sh`: holders a and b start together and both took the mutex, both scanned an empty
token directory, both went for token 1 (the second `mkdir "$dir"` failed with "File exists", but `stack_try_token` runs
as the condition of an `until`, which switches `set -e` off, so nothing stopped it) and both wrote their pid and slot
into it, the later write winning. Token 2 was
never created, so the third holder found one live token and one free slot and started at once: "third holder does not
start while both tokens are held", "says it is waiting" and "exactly two tokens are held again" failed. The same flaw
would let two real runs share a token, so the cap of D-641 was not enforced either. It passed on macOS and on
Ubuntu 24.04 (GNU coreutils), and on 26.04 only when the race was not lost (1 failure in 6 runs).

The mutex is now the file `<lock dir>/.mutex`, taken with `( set -C; : > "$STACK_MUTEX" )`. With `noclobber` the shell
itself opens the file with `O_CREAT|O_EXCL`, one system call that exactly one contender wins, on every kernel and with
whatever coreutils are installed; bash 3.2 on macOS does the same. The stale-mutex rule is unchanged (older than a
minute, removed with `rm -f`). Tokens stay directories holding `pid` and `slot`; they are created only while the
mutex is held, so their `mkdir` never races. Nothing else in the design changes, and D-642's description "another
`mkdir`" is replaced by this decision.

Test: a new first section puts a `mkdir` that reports success for an existing directory (what uutils does when it loses
the race, made certain instead of rare) first on `PATH` for the second contender, holds the mutex in the test shell and
checks that the second contender stays blocked until the mutex is released. It fails on the old code on every run and
passes on the new. No retry and no longer sleep was added to the existing scenarios: the failure was in the lock, not in
the test's timing.

Rejected: `flock` (absent on macOS); another coreutils command as the lock (the cause is trusting an external command for
atomicity, and the shell's own redirection is the one primitive that needs none); making
the test retry or wait longer (the failure is a shared token, not slowness); pinning the CI runner to `ubuntu-24.04`
(hides a real defect that also affects 26.04 developer machines).
