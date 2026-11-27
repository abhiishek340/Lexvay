# Shared by scripts/e2e.sh and scripts/eval.sh: an isolated, slot-numbered database stack that is always torn down.
# Source it from the repository root, then call stack_init <name> ("e2e" or "eval") once and the other functions in order.
#
# LEXVAY_E2E_SLOT (0-9) shifts every port and the compose project, so several checkouts can run at once. The
# development database (:5432) is never touched. Callers may set BACKEND_PID; it is killed on exit.

stack_init() {
  STACK_NAME="$1"
  SLOT="${LEXVAY_E2E_SLOT:-0}"
  DB_PORT=$((5433 + SLOT))
  export LEXVAY_DB_PORT="$DB_PORT"
  export LEXVAY_DB_URL="jdbc:postgresql://localhost:$DB_PORT/lexvay"
  export LEXVAY_FILESTORE_PATH="$(mktemp -d)"
  COMPOSE="docker compose -p lexvay-$STACK_NAME-$SLOT -f docker-compose.yml"
  BACKEND_PID=""
  trap stack_cleanup EXIT
}

stack_cleanup() {
  if [ -n "$BACKEND_PID" ]; then kill "$BACKEND_PID" 2>/dev/null || true; fi
  $COMPOSE down -v >/dev/null 2>&1 || true
  rm -rf "$LEXVAY_FILESTORE_PATH"
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
