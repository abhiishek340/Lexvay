#!/usr/bin/env bash
# Evaluation harness: seeds a fresh tenant into an isolated database, runs the pipeline and scores it against
# eval/golden.yml (see docs/ROADMAP.md, WS-EVAL). Prints one metrics table; exits non-zero when a limit in
# eval/thresholds.yml is broken or a stored quote cannot be verified.
#
#   scripts/eval.sh          scripted model (FakeLlmClient): deterministic, no network, no key
#   scripts/eval.sh --live   real model, chosen by LEXVAY_LLM_PROVIDER:
#                              openai-compatible  a server you run (llama-server, Ollama, vLLM, LM Studio); needs
#                                                 LEXVAY_LLM_BASE_URL and LEXVAY_LLM_MODEL, costs nothing per call, and keeps
#                                                 the report in eval/runs/ (D-616 to D-618)
#                              anthropic (or unset)  the Claude API: only when ANTHROPIC_API_KEY is set, otherwise says so
#                                                 and exits 0; billed, so the spend gate applies (D-562)
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
  case "${LEXVAY_LLM_PROVIDER:-anthropic}" in
    openai-compatible)
      if [ -z "${LEXVAY_LLM_BASE_URL:-}" ] || [ -z "${LEXVAY_LLM_MODEL:-}" ]; then
        echo "eval-live: LEXVAY_LLM_PROVIDER=openai-compatible needs LEXVAY_LLM_BASE_URL and LEXVAY_LLM_MODEL (no default). Nothing was run." >&2
        exit 64
      fi
      # The local server needs no key from Anthropic, and a real one must never reach this run.
      unset ANTHROPIC_API_KEY
      ;;
    anthropic)
      if [ -z "${ANTHROPIC_API_KEY:-}" ]; then
        echo "eval-live: ANTHROPIC_API_KEY is not set, so no real model can be called. Nothing was run."
        exit 0
      fi
      if [ "${LEXVAY_LLM_PAID_APPROVED:-false}" != "true" ] || [ -z "${LEXVAY_LLM_RATES:-}" ]; then
        echo "eval-live: LEXVAY_LLM_PAID_APPROVED is not true or LEXVAY_LLM_RATES is empty; the spend gate will refuse every model call." >&2
      fi
      ;;
    *)
      echo "eval-live: LEXVAY_LLM_PROVIDER must be openai-compatible or anthropic, not ${LEXVAY_LLM_PROVIDER}. Nothing was run." >&2
      exit 64
      ;;
  esac
  export LEXVAY_LLM_PROVIDER="${LEXVAY_LLM_PROVIDER:-anthropic}"
else
  # A real key in the environment must never reach a scripted run.
  unset ANTHROPIC_API_KEY
  export LEXVAY_LLM_PROVIDER=fake
fi

source scripts/lib/stack.sh
stack_init eval
stack_start_database
stack_package_backend

echo "eval: running the pipeline (${LEXVAY_LLM_PROVIDER} model) and the retrieval measurement"
java -jar "$JAR" --spring.profiles.active=eval --lexvay.seed.dir=seed --lexvay.eval.dir="${LEXVAY_EVAL_DIR:-eval}" \
  --lexvay.eval.retrieval-report=docs/RETRIEVAL.md --logging.level.root=WARN --spring.main.banner-mode=off
