### EV-1 — Local inference adapter and labelled evaluation runs (D-616 to D-620)

Work item EV-1, "Local inference adapter and labelled evaluation runs" (docs/ROADMAP.md). Folded into DECISIONS.md by
the orchestrator when it merges. No migration, no new dependency, no model downloaded or run: the tests answer from an
in-process `com.sun.net.httpserver.HttpServer` on the loopback interface.

**D-616 — A local model is one more `LlmClient`: `openai-compatible`, with no model built in and no host assumed.**
`OpenAiCompatibleLlmClient` uses the JDK `HttpClient` against `<base url>/chat/completions`, the API that llama.cpp's
`llama-server`, Ollama, vLLM and LM Studio all expose.
- Settings: `LEXVAY_LLM_BASE_URL` (required), `LEXVAY_LLM_MODEL` (required, **no default**), `LEXVAY_LLM_API_KEY`
  (optional, sent as a bearer token only when set, never logged), `LEXVAY_LLM_TIMEOUT_SECONDS` (default 120, applies to
  the connection and to the whole request) and `LEXVAY_LLM_ALLOWED_HOSTS` (default `localhost,127.0.0.1,::1`).
- The base URL follows the OpenAI client convention: it ends before `/chat/completions` and so includes `/v1`
  (`http://localhost:8089/v1`, `http://localhost:11434/v1`). Appending a fixed `/v1/chat/completions` would break the
  address people copy from a server's own documentation.
- A base URL whose host is not in the allowed list fails startup with a message that names the host and the variable.
  The comparison is on the whole host, case-insensitive, with the brackets of an IPv6 literal removed, so `evil-localhost`
  never matches `localhost`. A remote server needs an explicit entry, and the one startup line (`LLM provider
  openai-compatible: model M at host H ...`) names the host. Redirects are never followed, so a server cannot send a
  prompt on to a host that was not listed. The client speaks HTTP/1.1, which every such server does.
- `metered()` is false (no per-call fee, so `SpendGate` is not consulted) and `scripted()` is false (a `LIVE` tenant is
  served). `LlmGateway` is unchanged: it already decides from those two methods, and a test sends a `LIVE` tenant's call
  through the real client and checks that neither the tenant's mode nor the gate is read.
- The model id the client reports is the configured one, not the `model` field of the answer, which some servers fill
  with a file path. `application.yml` no longer defaults `lexvay.llm.model`; `LlmConfig` supplies
  `claude-sonnet-5-5` for `anthropic` only when it is empty, so a local run can never silently use a Claude model name.
  `docker-compose.yml` passes the new variables and its model default is now empty for the same reason.
- A model that does not answer within the timeout, a refusal in the answer (`message.refusal`, the same
  `LlmRefusedException` as the Claude API's refusal) and an answer without message content fail the call; the job
  retries as for any provider failure.

**D-617 — Structured output is requested, never trusted; token counts are measured or marked as estimates.**
- The schema goes in `response_format: {type: "json_schema", json_schema: {name, schema, strict: true}}`. If the server
  answers HTTP 400 and the body names `response_format`, the call is repeated once without it and with the schema written
  into the system message. A 400 that does not name it, or any other status, fails the call at once; a second 400 fails it.
  The answer text is returned as it came. Validation stays where it is for every provider, in the callers
  (`EvidenceMatcher`, `ExtractRequirementsHandler`) through `JsonSchemaValidator`, so an invalid answer is the same
  failure as for the Claude API and quote verification (rule 2) is unchanged. `JsonSchemaValidator` loads its schema from
  the classpath, so validating inside the client would have needed a second validator.
- `usage.prompt_tokens` and `usage.completion_tokens` are recorded when both are present. When either is absent both are
  estimated by `LlmResponse.estimateInputTokens` and `estimateOutputTokens` (a quarter of the characters of the user
  prompt and of the answer, the formula `FakeLlmClient` always used, now in one place) and the response carries
  `tokensEstimated`. Mixing one reported count with one estimate would produce a figure that is neither.
- The mark is stored with the call. `llm_call` has no column for it and EV-1 has no migration, so `LlmCallRecorder`
  adds `"tokensEstimated": true` to the stored `request` document when the response says so (it is absent otherwise).
  `FakeLlmClient` always estimates, so its calls carry the mark too; `EvalRepository.modelUse` reads it back and the
  report says when any count is an estimate. If a column is wanted later, the recorder is the one place to change.

**D-618 — An evaluation report says what produced it.** `EvalReport.Mode` is `SCRIPTED`, `LOCAL_LIVE` and `PAID_LIVE`,
chosen from what the client is (`scripted()`, then `metered()`), not from its class. The header carries the mode with a
phrase (a `LOCAL_LIVE` model "has not been vetted by Lexvay, so the results describe it alone"), the model, the host
the model was called at (`LlmClient.endpointHost()`, empty for the scripted model and the Claude API, printed as `n/a`)
and the date from the injected `Clock`. `eval/thresholds.yml` keeps two sets of limits, now named `scripted` and `live`
(the former `fake`); `live` serves both live modes and stays "unmeasured first values", with the values it had, until a
measured run sets them. A run in `LOCAL_LIVE` also writes its report to `eval/runs/<date>-<model slug>.txt`
(lower case, runs of other characters become one dash), which is gitignored; a run on the same day with the same model
replaces that day's file. The report is written whether or not a limit was broken.

**D-619 — `make eval-live` follows `LEXVAY_LLM_PROVIDER`.** `openai-compatible` needs `LEXVAY_LLM_BASE_URL` and
`LEXVAY_LLM_MODEL` and exits 64 without them (the operator chose it, so a gap is a mistake); it unsets
`ANTHROPIC_API_KEY` so a real key cannot reach the run. `anthropic` or unset behaves as before (no key: says so, exit 0;
spend-gate warning). Any other value exits 64. `make eval` is unchanged and still unsets the key and forces `fake`.

**D-620 — Limits of this item, stated so nobody finds them by surprise.**
- The spend gate prices every non-scripted model that has tokens in this month's `llm_call` rows and refuses a billed call
  when one has no price (D-562). A local model's rows have tokens and no price, so using a local model and a billed
  provider against one database in one month makes the gate refuse the billed calls. That is the safe direction and is
  documented in OPERATIONS; excluding unbilled models from the sum needs a column or a rate of zero, which D-562 forbids,
  so it is left for the orchestrator.
- No sampling parameters are sent, as for the Claude API, so a local run uses the server's defaults and its results can
  vary between runs.
- Nothing is downloaded or run by this item, and no model is named in code, tests, configuration or documentation as
  recommended.
