#!/usr/bin/env bash
# Runs the Playwright end-to-end tests against an isolated, freshly seeded stack.
# Several checkouts can run at once: set LEXVAY_E2E_SLOT (0-9) to shift every port and the compose project.
#   slot 0: PostgreSQL :5433, backend :8081, Angular dev server :4300   (slot n adds n to each)
#   the backend's management port is :9091 plus the slot (8091+ would collide with slot 10+ backends)
# The development database (:5432) and servers (:8080, :4200) are never touched.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/stack.sh
stack_init e2e
BACKEND_PORT=$((8081 + SLOT))
WEB_PORT=$((4300 + SLOT))
export LEXVAY_PORT="$BACKEND_PORT"
export LEXVAY_MANAGEMENT_PORT=$((9091 + SLOT))
export LEXVAY_E2E_BACKEND_PORT="$BACKEND_PORT"
export LEXVAY_E2E_WEB_PORT="$WEB_PORT"
export LEXVAY_LLM_PROVIDER=fake
# Every browser test signs in from 127.0.0.1; the production login budget (10 per minute) would trip them.
export LEXVAY_RATELIMIT_LOGIN_PER_MINUTE=1000
export LEXVAY_DEMO_PASSWORD="${LEXVAY_DEMO_PASSWORD:-lexvay-demo}"

stack_start_database
stack_package_backend

echo "e2e: seeding demo tenants"
java -jar "$JAR" --spring.profiles.active=seed --lexvay.seed.dir=seed

echo "e2e: starting backend on :$BACKEND_PORT"
java -jar "$JAR" > "$LEXVAY_FILESTORE_PATH/backend.log" 2>&1 &
BACKEND_PID=$!
for i in $(seq 1 60); do
  if curl -sf "http://localhost:$BACKEND_PORT/readyz" >/dev/null; then break; fi
  sleep 1
  if [ "$i" = "60" ]; then echo "backend did not start"; tail -50 "$LEXVAY_FILESTORE_PATH/backend.log"; exit 1; fi
done

echo "e2e: running Playwright"
( cd frontend && npx playwright test )
