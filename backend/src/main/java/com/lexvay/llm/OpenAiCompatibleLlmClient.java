package com.lexvay.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * A server that speaks the OpenAI-compatible {@code /chat/completions} API: llama.cpp's {@code llama-server}, Ollama,
 * vLLM, LM Studio. The operator runs it, so a call has no per-call fee ({@link #metered()} is false) and no model is
 * built in: {@code LEXVAY_LLM_MODEL} must name the one the server serves (D-616).
 *
 * <p>The output schema is sent as {@code response_format} of type {@code json_schema}. A server that does not know it
 * answers HTTP 400 naming {@code response_format}; the call is then repeated once with the schema in the system message
 * instead. Either way the caller validates the answer against the schema, as it does for any provider (D-617). The base
 * URL's host must be one the operator listed in {@code LEXVAY_LLM_ALLOWED_HOSTS}, so a prompt never leaves the machine
 * by a typing mistake, and redirects are never followed.
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int ERROR_BODY_LIMIT = 300;

    private final HttpClient http;
    private final URI completions;
    private final String host;
    private final String model;
    private final String apiKey;
    private final Duration timeout;

    public OpenAiCompatibleLlmClient(String baseUrl, String model, String apiKey, Duration timeout, List<String> allowedHosts) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("LEXVAY_LLM_BASE_URL must be set for LEXVAY_LLM_PROVIDER=openai-compatible");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("LEXVAY_LLM_MODEL must be set for LEXVAY_LLM_PROVIDER=openai-compatible; it has no default");
        }
        URI base = URI.create(baseUrl.strip());
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("http") || scheme.equals("https")) || base.getHost() == null) {
            throw new IllegalArgumentException("LEXVAY_LLM_BASE_URL must be an http or https URL with a host: " + baseUrl);
        }
        this.host = base.getHost().replaceAll("^\\[|]$", "").toLowerCase(Locale.ROOT);
        if (allowedHosts.stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).noneMatch(host::equals)) {
            throw new IllegalArgumentException("LEXVAY_LLM_BASE_URL host '" + host + "' is not in LEXVAY_LLM_ALLOWED_HOSTS ("
                    + String.join(",", allowedHosts) + "); add it only when prompts may be sent there");
        }
        this.completions = URI.create(baseUrl.strip().replaceAll("/+$", "") + "/chat/completions");
        this.model = model.strip();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        log.info("LLM provider openai-compatible: model {} at host {}, no per-call fee, prompts are sent to that host", this.model, host);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long started = System.nanoTime();
        HttpResponse<String> answer = post(body(request, true));
        if (answer.statusCode() == 400 && answer.body().contains("response_format")) {
            answer = post(body(request, false));
        }
        if (answer.statusCode() / 100 != 2) {
            throw new IllegalStateException("The model server at " + host + " answered HTTP " + answer.statusCode() + ": "
                    + answer.body().substring(0, Math.min(answer.body().length(), ERROR_BODY_LIMIT)));
        }
        return response(request, MAPPER.readTree(answer.body()), (System.nanoTime() - started) / 1_000_000);
    }

    private LlmResponse response(LlmRequest request, JsonNode answer, long durationMs) {
        JsonNode message = answer.path("choices").path(0).path("message");
        if (!message.path("refusal").asString("").isBlank()) {
            throw new LlmRefusedException("The model declined this request: " + message.path("refusal").asString());
        }
        if (!message.path("content").isString()) {
            throw new IllegalStateException("The model server at " + host + " returned no message content");
        }
        String text = message.path("content").asString();
        JsonNode usage = answer.path("usage");
        boolean reported = usage.path("prompt_tokens").isNumber() && usage.path("completion_tokens").isNumber();
        int inputTokens = reported ? usage.path("prompt_tokens").asInt() : LlmResponse.estimateInputTokens(request);
        int outputTokens = reported ? usage.path("completion_tokens").asInt() : LlmResponse.estimateOutputTokens(text);
        return new LlmResponse(text, model, inputTokens, outputTokens, durationMs, !reported);
    }

    private ObjectNode body(LlmRequest request, boolean withResponseFormat) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", request.maxOutputTokens());
        var messages = body.putArray("messages");
        String system = withResponseFormat ? request.systemPrompt()
                : request.systemPrompt() + "\n\nAnswer with one JSON object that conforms to this JSON schema, and nothing else:\n"
                + request.outputSchema();
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", request.userPrompt());
        if (withResponseFormat) {
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_schema");
            ObjectNode jsonSchema = format.putObject("json_schema");
            jsonSchema.put("name", request.purpose().name().toLowerCase(Locale.ROOT));
            jsonSchema.set("schema", request.outputSchema());
            jsonSchema.put("strict", true);
        }
        return body;
    }

    private HttpResponse<String> post(ObjectNode body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(completions).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
        if (!apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new UncheckedIOException("The model server at " + host + " did not answer within " + timeout.toSeconds() + " s", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot reach the model server at " + host + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the model server at " + host, e);
        }
    }

    @Override
    public String modelId() {
        return model;
    }

    @Override
    public Optional<String> endpointHost() {
        return Optional.of(host);
    }
}
