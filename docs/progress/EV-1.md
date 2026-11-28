## EV-1 — Local inference adapter and labelled evaluation runs

Branch `ev-local-inference`, no migration, slot 8. Decisions D-616 to D-620 are in `docs/decisions/EV-1.md`.

## Plan (written before building)
1. `OpenAiCompatibleLlmClient` (JDK `HttpClient`), `LlmConfig` provider `openai-compatible`, settings in `application.yml`.
2. Structured output with the 400 fallback; usage recorded or estimated and marked; the mark stored with the call.
3. Evaluation labels: `SCRIPTED`, `LOCAL_LIVE`, `PAID_LIVE`; header with mode, model, host and date; `thresholds.yml` keys;
   a local run's report kept under `eval/runs/`; `make eval-live` for either provider.
4. Tests against a loopback `HttpServer`; no model, no network.
5. OPERATIONS "Local inference", `.env.example`, compose.

## Done
All of the plan. Files outside the spec's list, each small:
- `FakeLlmClient`, `LlmResponse`, `LlmCallRecorder`, `LlmClient`: the shared token estimate, its mark and `endpointHost()`.
- `EvalRepository`, `EvalService`, `EvalRunner`, `Thresholds`, `GoldenFileTest`, `TermOverlapTest`: the new mode names and header fields.
- `docker-compose.yml`: passes the new variables; the model default is empty.
- `.gitignore`: `eval/runs/`.

After review (D-620): `LlmCallRecorder` stores `metered` in the request and takes the `LlmClient`; `SpendGate` sums metered calls
only (`LlmUsageRepository.meteredThisMonth`, renamed from `thisMonthExcluding`; `SpendGateTest` and `SpendGateDatabaseTest` follow);
every local request sends `temperature: 0` and `seed` when `LEXVAY_LLM_SEED` is set.

## Tests
See the PR for the commands and results.
