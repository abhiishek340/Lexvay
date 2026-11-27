#!/usr/bin/env bash
# Runs the production image against its own PostgreSQL and proves the whole path a user takes: the app shell on a
# deep link, sign-in through the container, an API call, a problem detail with a request id, the usage endpoint,
# the metrics on the management port, structured logs, and a graceful stop.
#   scripts/smoke.sh               builds the image as lexvay:local first (what `make smoke` does)
#   scripts/smoke.sh <image-tag>   tests an image that already exists (CI passes lexvay:ci-<sha>); nothing is built
# The Docker daemon is the one the docker CLI already points at: DOCKER_HOST when set, otherwise the default.
# The compose project and the ports come from scripts/lib/stack.sh, so several checkouts can run at once:
# LEXVAY_E2E_SLOT (0-9) shifts them. Slot 0: PostgreSQL :5433, app :8081, management :9091 (slot n adds n to each).
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/lib/stack.sh
stack_init smoke
IMAGE="${1:-}"
export LEXVAY_IMAGE="${IMAGE:-lexvay:local}"
export LEXVAY_APP_PORT=$((8081 + SLOT))
export LEXVAY_APP_MANAGEMENT_PORT=$((9091 + SLOT))
APP="http://localhost:$LEXVAY_APP_PORT"
MGMT="http://localhost:$LEXVAY_APP_MANAGEMENT_PORT"
EMAIL="priya.raman@northfield.example"
PASSWORD="${LEXVAY_DEMO_PASSWORD:-lexvay-demo}"
COMPOSE="$COMPOSE --profile app"
WORK="$LEXVAY_FILESTORE_PATH"

fail() { echo "smoke: FAIL: $*" >&2; $COMPOSE logs --no-color app 2>&1 | tail -40 >&2; exit 1; }
expect() { # expect <description> <actual> <expected>
  [ "$2" = "$3" ] || fail "$1: expected '$3', got '$2'"
  echo "smoke: ok: $1"
}
contains() { # contains <description> <haystack> <needle>
  case "$2" in *"$3"*) echo "smoke: ok: $1" ;; *) fail "$1: '$3' not found in: ${2:0:300}" ;; esac
}
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

$COMPOSE down -v >/dev/null 2>&1 || true
if [ -n "$IMAGE" ]; then
  docker image inspect "$IMAGE" >/dev/null 2>&1 || { echo "smoke: image $IMAGE does not exist" >&2; exit 1; }
  echo "smoke: testing the existing image $IMAGE"
else
  echo "smoke: building the image (LEXVAY_PRIMEUI_LICENSE is passed through; empty when unset)"
  $COMPOSE build app >/dev/null
fi
SIZE=$(docker image inspect "$LEXVAY_IMAGE" --format '{{.Size}}')
echo "smoke: image size $((SIZE / 1024 / 1024)) MiB"

echo "smoke: starting the database and loading the demo tenants with the same image"
$COMPOSE up -d --wait db
SEED_OUT=$($COMPOSE run --rm --no-deps -v "$PWD/seed:/seed:ro" -e SPRING_PROFILES_ACTIVE=seed -e LEXVAY_SEED_DIR=/seed app 2>&1) \
  || { echo "$SEED_OUT" | tail -30; fail "seeding"; }
ASSESSMENT=$(echo "$SEED_OUT" | grep -o 'Northfield University loaded.*assessment [0-9a-f-]\{36\}' | grep -o '[0-9a-f-]\{36\}$')
PROGRAM=$(echo "$SEED_OUT" | grep -o 'Northfield University loaded (program [0-9a-f-]\{36\}' | grep -o '[0-9a-f-]\{36\}$')
[ -n "$ASSESSMENT" ] && [ -n "$PROGRAM" ] || fail "could not read the seeded ids from the seed output"

echo "smoke: starting the app (cold start until the container reports healthy)"
START=$(date +%s)
$COMPOSE up -d --wait app
echo "smoke: cold start to healthy: $(( $(date +%s) - START )) s (compose polls every 15 s, so the log line below is more exact)"
$COMPOSE logs --no-color app 2>&1 | grep -o 'Started LexvayApplication in [0-9.]* seconds' | head -1 || true

echo "smoke: container"
expect "runs as a non-root user" "$($COMPOSE exec -T app id -u)" "10001"
expect "root filesystem is read-only" "$(docker inspect "$($COMPOSE ps -q app)" --format '{{.HostConfig.ReadonlyRootfs}}')" "true"
LEFTOVERS=$(docker run --rm --entrypoint sh "$LEXVAY_IMAGE" -c 'find / \( -name "*.java" -o -name ".m2" -o -name ".npm" -o -name node_modules -o -name pom.xml -o -name "*Test*.class" \) -not -path "/proc/*" -not -path "/sys/*" 2>/dev/null | head -3')
expect "no source, test, Maven or npm files in the image" "$LEFTOVERS" ""

echo "smoke: app shell"
expect "probe /readyz on the main port" "$(status "$APP/readyz")" "200"
SHELL_HTML=$(curl -s "$APP/")
contains "root serves the app shell" "$SHELL_HTML" "<app-root"
DEEP=$(curl -s -D "$WORK/deep.headers" "$APP/programs/$PROGRAM/review")
contains "a deep link serves the app shell" "$DEEP" "<app-root"
contains "the shell is revalidated" "$(tr -d '\r' < "$WORK/deep.headers" | tr 'A-Z' 'a-z')" "cache-control: no-cache"
ASSET=$(echo "$SHELL_HTML" | grep -o 'main-[A-Z0-9]*\.js' | head -1)
contains "hashed bundle is cached for a year" "$(curl -s -D - -o /dev/null "$APP/$ASSET" | tr -d '\r' | tr 'A-Z' 'a-z')" "immutable"
expect "a missing file is a 404" "$(status "$APP/missing.js")" "404"
expect "the API requires sign-in" "$(status "$APP/api/v1/programs")" "401"
expect "/actuator/prometheus is not served on the main port" "$(status "$APP/actuator/prometheus")" "401"

echo "smoke: sign in through the container"
curl -s -c "$WORK/jar" -o /dev/null "$APP/api/v1/auth/session"
XSRF=$(awk '$6 == "XSRF-TOKEN" {print $7}' "$WORK/jar")
LOGIN=$(curl -s -b "$WORK/jar" -c "$WORK/jar" -H "X-XSRF-TOKEN: $XSRF" -H "X-Request-Id: smoke-login-0001" \
  --data-urlencode "username=$EMAIL" --data-urlencode "password=$PASSWORD" -w '\n%{http_code}' "$APP/api/v1/auth/login")
expect "login succeeds" "${LOGIN##*$'\n'}" "200"
contains "login names the user's role" "$LOGIN" '"role":"REVIEWER"'
contains "the session is signed in" "$(curl -s -b "$WORK/jar" "$APP/api/v1/auth/session")" "$EMAIL"
contains "an API read works" "$(curl -s -b "$WORK/jar" "$APP/api/v1/programs")" "$PROGRAM"

echo "smoke: request id and problem details"
UNKNOWN=$(curl -s -b "$WORK/jar" -D "$WORK/unknown.headers" -H "X-Request-Id: smoke-unknown-0001" "$APP/api/v1/unknown")
contains "unknown API path is a problem detail" "$UNKNOWN" '"status":404'
contains "the problem detail carries the request id" "$UNKNOWN" '"requestId":"smoke-unknown-0001"'
contains "the header echoes the request id" "$(tr -d '\r' < "$WORK/unknown.headers")" "X-Request-Id: smoke-unknown-0001"

echo "smoke: usage of the seeded assessment"
USAGE=$(curl -s -b "$WORK/jar" "$APP/api/v1/assessments/$ASSESSMENT/usage")
echo "$USAGE"
contains "usage reports model calls" "$USAGE" '"purpose":"MATCH_EVIDENCE"'

echo "smoke: a new assessment on the container's unscripted fake model fails and is counted"
XSRF=$(awk '$6 == "XSRF-TOKEN" {print $7}' "$WORK/jar")
expect "starting an assessment is accepted" "$(status -b "$WORK/jar" -c "$WORK/jar" -X POST -H "X-XSRF-TOKEN: $XSRF" "$APP/api/v1/programs/$PROGRAM/assessments")" "202"
for _ in $(seq 1 30); do
  curl -s "$MGMT/actuator/prometheus" | grep -q 'lexvay_job_duration_seconds_count{.*outcome="failed"' && break
  sleep 1
done

echo "smoke: metrics on the management port"
expect "prometheus scrape" "$(status "$MGMT/actuator/prometheus")" "200"
SCRAPE=$(curl -s "$MGMT/actuator/prometheus")
echo "$SCRAPE" | grep '^lexvay' || true
contains "job outcomes are counted" "$SCRAPE" 'outcome="failed"'
contains "failed model calls are counted" "$SCRAPE" 'lexvay_llm_calls_total{model="fake-llm",outcome="error"'
contains "queue depth gauge" "$SCRAPE" "lexvay_job_queue_depth"
contains "JVM metrics" "$SCRAPE" "jvm_memory_used_bytes"
contains "full health report" "$(curl -s "$MGMT/actuator/health")" '"status":"UP"'
expect "the API is not served on the management port" "$(status "$MGMT/api/v1/programs")" "401"

echo "smoke: structured logs"
LOGS=$($COMPOSE logs --no-color app 2>&1)
contains "logs are ECS JSON" "$LOGS" '"ecs":{"version"'
contains "log lines carry the request id" "$LOGS" '"requestId":"smoke-'
contains "job log lines carry the job id" "$LOGS" '"jobId":"'
echo "$LOGS" | grep '"requestId":"smoke-' | head -1 | sed 's/^[^{]*//'
echo "$LOGS" | grep '"jobId":"' | head -1 | sed 's/^[^{]*//'

echo "smoke: graceful stop"
$COMPOSE stop -t 40 app >/dev/null
contains "shutdown completed" "$($COMPOSE logs --no-color app 2>&1)" "Graceful shutdown complete"
echo "smoke: PASSED"
