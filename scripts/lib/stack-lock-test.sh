#!/usr/bin/env bash
# Tests the machine-wide stack semaphore in scripts/lib/stack.sh (make test-scripts). No Docker, no network: a fake
# `docker` first on PATH stands in for the compose calls, and LEXVAY_STACK_LOCK_DIR points at a throwaway directory so no real token
# is touched. Runs under bash 3.2 (macOS) and finishes in well under 20 seconds.
set -euo pipefail
cd "$(dirname "$0")/../.."
REPO=$(pwd)

WORK=$(mktemp -d)
export LEXVAY_STACK_LOCK_DIR="$WORK/lexvay-stack-tokens"
TOKENS="$LEXVAY_STACK_LOCK_DIR"
mkdir -p "$WORK/bin"
printf '#!/bin/sh\nexit 0\n' > "$WORK/bin/docker"
chmod +x "$WORK/bin/docker"
export PATH="$WORK/bin:$PATH"
export LEXVAY_STACK_WAIT_MINUTES=1
source scripts/lib/stack.sh

# A stack user: takes a token, announces it by creating <ready>, then idles until signalled.
cat > "$WORK/holder.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
cd "$REPO"
source scripts/lib/stack.sh
stack_init holder
touch "$1"
sleep 300 &
BACKEND_PID=$!
wait "$BACKEND_PID"
EOF
export REPO

PIDS=""
cleanup() {
  for pid in $PIDS; do kill "$pid" 2>/dev/null || true; done
  rm -rf "$WORK"
}
trap cleanup EXIT

FAILURES=0
check() { # check <description> <command...>
  local description="$1"; shift
  if "$@"; then echo "ok: $description"; else echo "FAIL: $description" >&2; FAILURES=$((FAILURES + 1)); fi
}

wait_for() { # wait_for <seconds> <command...>: polls until the command succeeds
  local deadline=$((SECONDS + $1)); shift
  until "$@"; do
    [ "$SECONDS" -lt "$deadline" ] || return 1
    sleep 0.2
  done
}

# start_holder <name> <slot>: launches a holder and sets HOLDER_PID. Its ready file is $WORK/<name>.ready.
start_holder() {
  LEXVAY_E2E_SLOT="$2" bash "$WORK/holder.sh" "$WORK/$1.ready" > "$WORK/$1.log" 2>&1 &
  HOLDER_PID=$!
  PIDS="$PIDS $HOLDER_PID"
}

ready() { [ -f "$WORK/$1.ready" ]; }
gone() { ! ps -p "$1" >/dev/null 2>&1; }
token_count() { find "$TOKENS" -mindepth 1 -maxdepth 1 -type d ! -name '.mutex' 2>/dev/null | wc -l | tr -d ' '; }
no_mutex() { [ ! -e "$TOKENS/.mutex" ]; }

stop_holder() { # stop_holder <pid>: TERM, then wait for it to finish its cleanup
  kill -TERM "$1"
  wait_for 10 gone "$1"
}

echo "== the mutex stays exclusive even when mkdir reports success for a directory that exists =="
# uutils mkdir, the default on Ubuntu 26.04 (the CI runner), does exactly that when it loses a race, so a mkdir lock admits
# two holders now and then. This mkdir does it every time, which makes that outcome certain instead of rare.
mkdir -p "$WORK/lossy-bin" "$TOKENS"
printf '#!/bin/sh\n/bin/mkdir "$@" 2>/dev/null && exit 0\nfor arg in "$@"; do last=$arg; done\n[ -d "$last" ]\n' > "$WORK/lossy-bin/mkdir"
chmod +x "$WORK/lossy-bin/mkdir"
stack_mutex_lock "$TOKENS"
( PATH="$WORK/lossy-bin:$PATH"; stack_mutex_lock "$TOKENS"; touch "$WORK/second.locked"; stack_mutex_unlock ) &
SECOND=$!
PIDS="$PIDS $SECOND"
sleep 0.5
check "a second contender waits while the mutex is held" test ! -f "$WORK/second.locked"
stack_mutex_unlock
wait "$SECOND"
check "and takes it once it is released" test -f "$WORK/second.locked"
check "the mutex is gone after both" no_mutex

echo "== two tokens taken: the third waits, then gets one when a holder exits =="
export LEXVAY_STACK_CONCURRENCY=2
start_holder a 1; A=$HOLDER_PID
start_holder b 2; B=$HOLDER_PID
check "first holder gets a token" wait_for 10 ready a
check "second holder gets a token" wait_for 10 ready b
start_holder c 3; C=$HOLDER_PID
sleep 2
check "third holder does not start while both tokens are held" test ! -f "$WORK/c.ready"
check "third holder says it is waiting and names the slots" grep -q 'holder: waiting for a free test stack (2 of 2 in use: slots [12], [12])' "$WORK/c.log"
stop_holder "$A"
check "third holder gets the token the first one released" wait_for 10 ready c
check "exactly two tokens are held again" test "$(token_count)" = 2
stop_holder "$B"
stop_holder "$C"
check "every token is released and no mutex is left" test "$(token_count)" = 0
check "the mutex is gone" no_mutex

echo "== a killed holder releases its token =="
export LEXVAY_STACK_CONCURRENCY=1
rm -f "$WORK"/*.ready
start_holder d 4; D=$HOLDER_PID
wait_for 10 ready d
check "the holder owns the only token" test "$(cat "$TOKENS/1/pid")" = "$D"
stop_holder "$D"
check "TERM removed the token" test "$(token_count)" = 0

echo "== a stale token with a dead pid is reclaimed =="
sleep 0 & DEAD=$!
wait "$DEAD"
mkdir -p "$TOKENS/1"
echo "$DEAD" > "$TOKENS/1/pid"
echo 9 > "$TOKENS/1/slot"
rm -f "$WORK"/*.ready
start_holder e 5; E=$HOLDER_PID
check "a holder gets the reclaimed token" wait_for 10 ready e
check "the token now names the new owner" test "$(cat "$TOKENS/1/pid")" = "$E"
check "and its slot" test "$(cat "$TOKENS/1/slot")" = 5
stop_holder "$E"

echo "== a token directory without a pid file is reclaimed =="
mkdir -p "$TOKENS/1"
rm -f "$WORK"/*.ready
start_holder f 6; F=$HOLDER_PID
check "a holder gets the incomplete token" wait_for 10 ready f
stop_holder "$F"

echo "== waiting gives up after LEXVAY_STACK_WAIT_MINUTES =="
start_holder g 7; G=$HOLDER_PID
wait_for 10 ready g
set +e
LEXVAY_STACK_WAIT_MINUTES=0 bash -c 'cd "$REPO"; source scripts/lib/stack.sh; stack_init giveup' > "$WORK/giveup.log" 2>&1
STATUS=$?
set -e
check "a run that cannot get a token exits 1" test "$STATUS" = 1
check "with a clear error naming the slots in use" grep -q 'giveup: no free test stack after 0 minutes (1 of 1 in use: slots 7)' "$WORK/giveup.log"
check "and the holder keeps its token" test "$(cat "$TOKENS/1/pid")" = "$G"
stop_holder "$G"

echo "== concurrency 0 skips the lock =="
rm -rf "$TOKENS"
export LEXVAY_STACK_CONCURRENCY=0
start_holder i 8; I=$HOLDER_PID
start_holder j 9; J=$HOLDER_PID
start_holder k 0; K=$HOLDER_PID
check "any number of stacks start at once" wait_for 10 ready k
check "all three are running" test -f "$WORK/i.ready" -a -f "$WORK/j.ready" -a -f "$WORK/k.ready"
check "no token directory is created for them" test ! -e "$TOKENS"
stop_holder "$I"; stop_holder "$J"; stop_holder "$K"

echo "== bad limits are refused =="
set +e
LEXVAY_STACK_CONCURRENCY=two bash -c 'cd "$REPO"; source scripts/lib/stack.sh; stack_init bad' > "$WORK/bad.log" 2>&1
STATUS=$?
set -e
check "a non-numeric LEXVAY_STACK_CONCURRENCY exits 1 with a message" bash -c "[ $STATUS = 1 ] && grep -q 'must be a whole number' '$WORK/bad.log'"

if [ "$FAILURES" -gt 0 ]; then echo "stack-lock-test: $FAILURES check(s) failed" >&2; exit 1; fi
echo "stack-lock-test: all checks passed"
