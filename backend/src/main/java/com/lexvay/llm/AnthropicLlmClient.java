package com.lexvay.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Claude API client (Anthropic Java SDK). The model id comes from LEXVAY_LLM_MODEL and the effort from
 * LEXVAY_LLM_EFFORT (D-033). The output JSON schema is passed as a structured-output format so the
 * response is constrained to it; the caller still validates. No sampling parameters are sent and
 * thinking is left at the model's default. Written against the SDK documentation on 2026-11-26.
 */
public class AnthropicLlmClient implements LlmClient {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final AnthropicClient client;
    private final String model;
    private final OutputConfig.Effort effort;

    public AnthropicLlmClient(AnthropicClient client, String model, String effort) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("LEXVAY_LLM_MODEL must be set");
        }
        this.client = client;
        this.model = model;
        this.effort = OutputConfig.Effort.of(effort.toLowerCase());
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long started = System.nanoTime();
        Message message = client.messages().create(buildParams(request));
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            throw new LlmRefusedException("The model declined this request"
                    + message.stopDetails().map(d -> ": " + d.category() + " " + d.explanation()).orElse(""));
        }
        String text = message.content().stream()
                .map(ContentBlock::text)
                .flatMap(java.util.Optional::stream)
                .map(block -> block.text())
                .collect(Collectors.joining());
        return new LlmResponse(text, message.model().asString(), (int) message.usage().inputTokens(),
                (int) message.usage().outputTokens(), durationMs);
    }

    MessageCreateParams buildParams(LlmRequest request) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(request.maxOutputTokens())
                .system(request.systemPrompt())
                .addUserMessage(request.userPrompt())
                .outputConfig(OutputConfig.builder()
                        .effort(effort)
                        .format(JsonOutputFormat.builder().schema(schemaOf(request.outputSchema())).build())
                        .build())
                .build();
    }

    /** The SDK carries free-form JSON as {@link JsonValue}; our schema is a Jackson 3 tree. */
    static JsonOutputFormat.Schema schemaOf(JsonNode schema) {
        @SuppressWarnings("unchecked")
        Map<String, Object> asMap = MAPPER.convertValue(schema, LinkedHashMap.class);
        JsonOutputFormat.Schema.Builder builder = JsonOutputFormat.Schema.builder();
        asMap.forEach((key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)));
        return builder.build();
    }

    @Override
    public String modelId() {
        return model;
    }

    @Override
    public boolean metered() {
        return true;
    }
}
