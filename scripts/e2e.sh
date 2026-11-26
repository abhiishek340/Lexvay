#!/usr/bin/env bash
# Runs the Playwright end-to-end tests against an isolated, freshly seeded stack:
# PostgreSQL on :5433 (compose project lexvay-e2e), backend on :8081, Angular dev server on :4300.
# The development database and servers are left untouched.
set -euo pipefail
cd "$(dirname "$0")/.."
export LEXVAY_DB_PORT=5433
export LEXVAY_DB_URL=jdbc:postgresql://localhost:5433/lexvay
export LEXVAY_PORT=8081
export LEXVAY_FILESTORE_PATH="$(mktemp -d)"
export LEXVAY_LLM_PROVIDER=fake
export LEXVAY_DEMO_PASSWORD="${LEXVAY_DEMO_PASSWORD:-lexvay-demo}"
COMPOSE="docker compose -p lexvay-e2e -f docker-compose.yml"
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

echo "e2e: starting backend on :8081"
java -jar "$JAR" > "$LEXVAY_FILESTORE_PATH/backend.log" 2>&1 &
BACKEND_PID=$!
for i in $(seq 1 60); do
  if curl -sf "http://localhost:8081/actuator/health" >/dev/null; then break; fi
  sleep 1
  if [ "$i" = "60" ]; then echo "backend did not start"; tail -50 "$LEXVAY_FILESTORE_PATH/backend.log"; exit 1; fi
done

echo "e2e: running Playwright"
( cd frontend && npx playwright test )
