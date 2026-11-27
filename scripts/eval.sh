#!/usr/bin/env bash
# Evaluation harness: seeds a fresh tenant into an isolated database, runs the pipeline and scores it against
# eval/golden.yml (see docs/ROADMAP.md, WS-EVAL). Prints one metrics table; exits non-zero when a limit in
# eval/thresholds.yml is broken or a stored quote cannot be verified.
#
#   scripts/eval.sh          scripted model (FakeLlmClient): deterministic, no network, no key
#   scripts/eval.sh --live   real model: only when ANTHROPIC_API_KEY is set, otherwise says so and exits 0
#
# The stack comes from scripts/lib/stack.sh, shared with scripts/e2e.sh: LEXVAY_E2E_SLOT (0-9) shifts the PostgreSQL
# port (5433 + slot) and the compose project is lexvay-eval-<slot>, which cannot collide with lexvay-e2e-<slot>.
# The development database (:5432) is never touched. LEXVAY_EVAL_DIR overrides the directory holding golden.yml,
# thresholds.yml and retrieval/. The retrieval measurement also rewrites docs/RETRIEVAL.md.
set -euo pipefail
cd "$(dirname "$0")/.."

MODE=fake
case "${1:-}" in
  "") ;;
  --live) MODE=live ;;
  *) echo "usage: scripts/eval.sh [--live]" >&2; exit 64 ;;
esac

if [ "$MODE" = live ]; then
  if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
    echo "eval-live: ANTHROPIC_API_KEY is not set, so no real model can be called. Nothing was run."
    exit 0
  fi
  if [ -z "${LEXVAY_LLM_PRICE_INPUT_PER_MTOK:-}" ] || [ -z "${LEXVAY_LLM_PRICE_OUTPUT_PER_MTOK:-}" ]; then
    echo "eval-live: LEXVAY_LLM_PRICE_INPUT_PER_MTOK or LEXVAY_LLM_PRICE_OUTPUT_PER_MTOK is not set; the cost line will read 0." >&2
  fi
  export LEXVAY_LLM_PROVIDER=anthropic
else
  # A real key in the environment must never reach a scripted run.
  unset ANTHROPIC_API_KEY
  export LEXVAY_LLM_PROVIDER=fake
fi

source scripts/lib/stack.sh
stack_init eval
stack_start_database
stack_package_backend

echo "eval: running the pipeline ($MODE model) and the retrieval measurement"
java -jar "$JAR" --spring.profiles.active=eval --lexvay.seed.dir=seed --lexvay.eval.dir="${LEXVAY_EVAL_DIR:-eval}" \
  --lexvay.eval.retrieval-report=docs/RETRIEVAL.md --logging.level.root=WARN --spring.main.banner-mode=off
