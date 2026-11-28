#!/usr/bin/env bash
# Tests scripts/eval-diff (make test-scripts): two small lab reports written here, no Docker, no network, needs jq.
set -euo pipefail
cd "$(dirname "$0")/../.."

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
FAILED=0

check() { # check <description> <expected exit> <command...>
  local description="$1" expected="$2" actual=0
  shift 2
  "$@" >"$WORK/out" 2>"$WORK/err" || actual=$?
  if [ "$actual" -ne "$expected" ]; then
    echo "FAIL: $description (exit $actual, wanted $expected)"; cat "$WORK/out" "$WORK/err"; FAILED=1
  else
    echo "ok: $description"
  fi
}

contains() { # contains <description> <file> <text>
  if grep -qF -- "$3" "$2"; then echo "ok: $1"; else echo "FAIL: $1 (missing: $3)"; cat "$2"; FAILED=1; fi
}

# report <file> <sha of the key> <answer to UT-2> <split> <severe numerator>
report() {
  cat > "$1" <<JSON
{"format":"lexvay-eval-lab-report/1","mode":"SCRIPTED","model":"fake-llm","date":"2026-11-28","split":"$4","keys":[{"id":"demo","sha256":"$2"}],
 "pipeline":{"metrics":{"severeFalseAssurance":{"numerator":$5,"denominator":2,"rate":0.$5,"low":0.0,"high":0.66}},
  "quoteVerification":{"numerator":2,"denominator":2,"rate":1.0},
  "rows":[{"key":"demo","requirement":"UT/UT-1","expected":"EVIDENCE_FOUND","answered":"EVIDENCE_FOUND","evidenceMatches":true,"severeFalseAssurance":false},
          {"key":"demo","requirement":"UT/UT-2","expected":"EVIDENCE_MISSING","answered":"$3","evidenceMatches":false,"severeFalseAssurance":false}]},
 "baseline":{"status":"BLOCKED","reason":"needs a model"}}
JSON
}

KEY=$(printf 'a%.0s' $(seq 64))
OTHER=$(printf 'b%.0s' $(seq 64))
report "$WORK/a.json" "$KEY" EVIDENCE_MISSING dev 0
report "$WORK/same.json" "$KEY" EVIDENCE_MISSING dev 0
report "$WORK/changed.json" "$KEY" EVIDENCE_FOUND dev 1
report "$WORK/otherkey.json" "$OTHER" EVIDENCE_MISSING dev 0
report "$WORK/holdout.json" "$KEY" EVIDENCE_MISSING holdout 0
echo '{"format":"something-else"}' > "$WORK/foreign.json"

check "identical reports show no difference" 0 scripts/eval-diff "$WORK/a.json" "$WORK/same.json"
contains "identical reports say nothing was answered differently" "$WORK/out" "no requirement was answered differently"

check "a changed metric and answer are reported with exit 1" 1 scripts/eval-diff "$WORK/a.json" "$WORK/changed.json"
contains "the metric is shown with both counts" "$WORK/out" "severeFalseAssurance: 0 / 2, 0 % -> 1 / 2, 10 %   CHANGED"
contains "the requirement is named with both answers" "$WORK/out" "demo UT/UT-2: answered EVIDENCE_MISSING -> EVIDENCE_FOUND"
contains "a baseline that is not measured is not compared" "$WORK/out" "Generic baseline: BLOCKED -> BLOCKED, not compared"

check "reports of different keys are not comparable" 64 scripts/eval-diff "$WORK/a.json" "$WORK/otherkey.json"
contains "the refusal says why" "$WORK/err" "different keys or different splits"
check "reports of different splits are not comparable" 64 scripts/eval-diff "$WORK/a.json" "$WORK/holdout.json"
check "a file that is not a lab report is refused" 64 scripts/eval-diff "$WORK/a.json" "$WORK/foreign.json"
check "a missing file is refused" 64 scripts/eval-diff "$WORK/a.json" "$WORK/missing.json"
check "one argument is a usage error" 64 scripts/eval-diff "$WORK/a.json"

[ "$FAILED" -eq 0 ] && echo "eval-diff-test: all passed"
exit "$FAILED"
