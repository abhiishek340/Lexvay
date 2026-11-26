#!/usr/bin/env bash
# Runs the Playwright end-to-end tests against an isolated, freshly seeded stack.
# Several checkouts can run at once: set LEXVAY_E2E_SLOT (0-9) to shift every port and the compose project.
#   slot 0: PostgreSQL :5433, backend :8081, Angular dev server :4300   (slot n adds n to each)
# The development database (:5432) and servers (:8080, :4200) are never touched.
set -euo pipefail
cd "$(dirname "$0")/.."
SLOT="${LEXVAY_E2E_SLOT:-0}"
DB_PORT=$((5433 + SLOT))
BACKEND_PORT=$((8081 + SLOT))
WEB_PORT=$((4300 + SLOT))
export LEXVAY_DB_PORT="$DB_PORT"
export LEXVAY_DB_URL="jdbc:postgresql://localhost:$DB_PORT/lexvay"
export LEXVAY_PORT="$BACKEND_PORT"
export LEXVAY_E2E_BACKEND_PORT="$BACKEND_PORT"
export LEXVAY_E2E_WEB_PORT="$WEB_PORT"
export LEXVAY_FILESTORE_PATH="$(mktemp -d)"
export LEXVAY_LLM_PROVIDER=fake
export LEXVAY_DEMO_PASSWORD="${LEXVAY_DEMO_PASSWORD:-lexvay-demo}"
COMPOSE="docker compose -p lexvay-e2e-$SLOT -f docker-compose.yml"
BACKEND_PID=""
cleanup() {
  if [ -n "$BACKEND_PID" ]; then kill "$BACKEND_PID" 2>/dev/null || true; fi
  $COMPOSE down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "e2e: starting isolated database"
$COMPOSE down -v >/dev/null 2>&1 || true
$COMPOSE up -d --wait

echo "e2e: packaging backend"
( cd backend && ./mvnw -q -B -DskipTests package )
JAR=$(ls backend/target/lexvay-backend-*.jar | grep -v original | head -1)

echo "e2e: seeding demo tenants"
java -jar "$JAR" --spring.profiles.active=seed --lexvay.seed.dir=seed

echo "e2e: starting backend on :$BACKEND_PORT"
java -jar "$JAR" > "$LEXVAY_FILESTORE_PATH/backend.log" 2>&1 &
BACKEND_PID=$!
for i in $(seq 1 60); do
  if curl -sf "http://localhost:$BACKEND_PORT/actuator/health" >/dev/null; then break; fi
  sleep 1
  if [ "$i" = "60" ]; then echo "backend did not start"; tail -50 "$LEXVAY_FILESTORE_PATH/backend.log"; exit 1; fi
done

echo "e2e: running Playwright"
( cd frontend && npx playwright test )
