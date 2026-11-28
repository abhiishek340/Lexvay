# Shared by scripts/smoke.sh, scripts/e2e.sh and scripts/eval.sh: an isolated, slot-numbered database stack that is always torn down.
# Source it from the repository root, then call stack_init <name> ("smoke", "e2e" or "eval") once and the other functions in order.
#
# LEXVAY_E2E_SLOT (0-9) shifts every port and the compose project, so several checkouts can run at once. The
# development database (:5432) is never touched. Callers may set BACKEND_PID; it is killed on exit.
#
# Machine-wide cap: too many stacks at once overload the machine and make timing-sensitive tests fail, so stack_init
# first takes one of LEXVAY_STACK_CONCURRENCY tokens (default 2; 0 turns the cap off) and waits up to
# LEXVAY_STACK_WAIT_MINUTES (default 60) for one. A token is a directory ${LEXVAY_STACK_LOCK_DIR:-/tmp/lexvay-stack-tokens-$(id -u)}/<n>
# holding its owner's `pid` and `slot`; a token whose pid is gone is reclaimed. Scans happen while holding the
# short-lived file <that directory>/.mutex, created by the shell itself with O_EXCL (noclobber). macOS has no flock, and
# mkdir is not a safe lock everywhere: uutils mkdir, the default on Ubuntu 26.04, reports success when it loses a race.
# stack_cleanup gives the token back on every exit path, including INT and TERM.

STACK_NOTICE_SECONDS=30

# Exits the calling script unless the two limits are whole numbers.
stack_check_limits() {
  case "${LEXVAY_STACK_CONCURRENCY:-2}" in ''|*[!0-9]*)
    echo "$STACK_NAME: LEXVAY_STACK_CONCURRENCY must be a whole number (0 turns the cap off)" >&2; exit 1;;
  esac
  case "${LEXVAY_STACK_WAIT_MINUTES:-60}" in ''|*[!0-9]*)
    echo "$STACK_NAME: LEXVAY_STACK_WAIT_MINUTES must be a whole number" >&2; exit 1;;
  esac
}

stack_pid_alive() { ps -p "$1" >/dev/null 2>&1; }

# Serialises token scans, so two waiters never reclaim the same stale token or both create one. It is held for
# milliseconds; a mutex older than a minute belongs to a process that died inside the critical section. The mutex is a
# file created by the shell's own O_EXCL open (noclobber): exactly one contender wins and no external command decides it.
stack_mutex_lock() {
  STACK_MUTEX="$1/.mutex"
  until ( set -C; : > "$STACK_MUTEX" ) 2>/dev/null; do
    if find "$STACK_MUTEX" -maxdepth 0 -mmin +1 2>/dev/null | grep -q .; then rm -f "$STACK_MUTEX"; fi
    sleep 0.05
  done
}

stack_mutex_unlock() {
  if [ -n "$STACK_MUTEX" ]; then rm -f "$STACK_MUTEX"; STACK_MUTEX=""; fi
}

# One attempt to take a token under <root>. Returns 0 and sets STACK_TOKEN_DIR on success. Returns 1 and sets
# STACK_TOKENS_USED and STACK_TOKENS_SLOTS (comma-separated) when all <max> tokens belong to live processes.
stack_try_token() {
  local root="$1" max="$2" n dir pid
  STACK_TOKENS_USED=0
  STACK_TOKENS_SLOTS=""
  stack_mutex_lock "$root"
  for n in $(seq 1 "$max"); do
    dir="$root/$n"
    if [ -d "$dir" ]; then
      pid=$(cat "$dir/pid" 2>/dev/null || true)
      if [ -n "$pid" ] && stack_pid_alive "$pid"; then
        STACK_TOKENS_USED=$((STACK_TOKENS_USED + 1))
        STACK_TOKENS_SLOTS="${STACK_TOKENS_SLOTS:+$STACK_TOKENS_SLOTS, }$(cat "$dir/slot" 2>/dev/null || echo '?')"
        continue
      fi
      rm -rf "$dir"
    fi
    mkdir "$dir"
    echo "$$" > "$dir/pid"
    echo "$SLOT" > "$dir/slot"
    STACK_TOKEN_DIR="$dir"
    stack_mutex_unlock
    return 0
  done
  stack_mutex_unlock
  return 1
}

# Blocks until a token is held (STACK_TOKEN_DIR is set), or exits the script after LEXVAY_STACK_WAIT_MINUTES.
stack_acquire_token() {
  local max="${LEXVAY_STACK_CONCURRENCY:-2}" wait_minutes="${LEXVAY_STACK_WAIT_MINUTES:-60}"
  local root="${LEXVAY_STACK_LOCK_DIR:-/tmp/lexvay-stack-tokens-$(id -u)}"
  [ "$max" -gt 0 ] || return 0
  local deadline=$((SECONDS + wait_minutes * 60)) next_notice=$SECONDS
  mkdir -p "$root"
  until stack_try_token "$root" "$max"; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "$STACK_NAME: no free test stack after $wait_minutes minutes ($STACK_TOKENS_USED of $max in use: slots $STACK_TOKENS_SLOTS); raise LEXVAY_STACK_WAIT_MINUTES or LEXVAY_STACK_CONCURRENCY" >&2
      exit 1
    fi
    if [ "$SECONDS" -ge "$next_notice" ]; then
      echo "$STACK_NAME: waiting for a free test stack ($STACK_TOKENS_USED of $max in use: slots $STACK_TOKENS_SLOTS)"
      next_notice=$((SECONDS + STACK_NOTICE_SECONDS))
    fi
    sleep 1
  done
}

stack_init() {
  STACK_NAME="$1"
  SLOT="${LEXVAY_E2E_SLOT:-0}"
  STACK_TOKEN_DIR=""
  STACK_MUTEX=""
  STACK_STARTED=""
  BACKEND_PID=""
  LEXVAY_FILESTORE_PATH=""
  trap stack_cleanup EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  stack_check_limits
  stack_acquire_token
  STACK_STARTED=1
  DB_PORT=$((5433 + SLOT))
  export LEXVAY_DB_PORT="$DB_PORT"
  export LEXVAY_DB_URL="jdbc:postgresql://localhost:$DB_PORT/lexvay"
  export LEXVAY_FILESTORE_PATH="$(mktemp -d)"
  COMPOSE="docker compose -p lexvay-$STACK_NAME-$SLOT -f docker-compose.yml"
}

# Safe on every path. A signal while still waiting for a token has started nothing, so the compose project (which
# another run on the same slot may own) is left alone then.
stack_cleanup() {
  if [ -n "$BACKEND_PID" ]; then kill "$BACKEND_PID" 2>/dev/null || true; fi
  if [ -n "$STACK_STARTED" ]; then $COMPOSE down -v >/dev/null 2>&1 || true; fi
  if [ -n "$LEXVAY_FILESTORE_PATH" ]; then rm -rf "$LEXVAY_FILESTORE_PATH"; fi
  stack_mutex_unlock
  if [ -n "$STACK_TOKEN_DIR" ]; then rm -rf "$STACK_TOKEN_DIR"; STACK_TOKEN_DIR=""; fi
}

stack_start_database() {
  echo "$STACK_NAME: starting isolated database (project lexvay-$STACK_NAME-$SLOT, port $DB_PORT)"
  $COMPOSE down -v >/dev/null 2>&1 || true
  $COMPOSE up -d --wait
}

# Builds the backend and sets JAR to the runnable jar.
stack_package_backend() {
  echo "$STACK_NAME: packaging backend"
  ( cd backend && ./mvnw -q -B -DskipTests package )
  JAR=$(ls backend/target/lexvay-backend-*.jar | grep -v original | head -1)
}
