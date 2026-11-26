package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.lexvay.rule.RequirementListParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** No network: checks only that the request sent to the SDK carries the configured model, effort, prompts and schema. */
class AnthropicLlmClientTest {

    JsonMapper json = JsonMapper.builder().build();
    JsonSchemaValidator validator = new JsonSchemaValidator(json, RequirementListParser.SCHEMA);
    AnthropicLlmClient client = new AnthropicLlmClient(mock(AnthropicClient.class), "claude-sonnet-5-5", "medium");

    @Test
    void requestCarriesModelEffortPromptsAndSchema() {
        var request = new LlmRequest(LlmPurpose.EXTRACT_REQUIREMENTS, "EXTRACT:x", "system text", "user text",
                validator.schema(), 4321);
        MessageCreateParams params = client.buildParams(request);

        assertThat(params.model().asString()).isEqualTo("claude-sonnet-5-5");
        assertThat(params.maxTokens()).isEqualTo(4321L);
        assertThat(params.system()).isPresent();
        assertThat(params.system().get().toString()).contains("system text");
        assertThat(params.messages()).hasSize(1);
        assertThat(params.messages().get(0).content().toString()).contains("user text");
        assertThat(params.outputConfig()).isPresent();
        assertThat(params.outputConfig().get().effort()).hasValueSatisfying(e -> assertThat(e.toString()).isEqualTo("medium"));
        var format = params.outputConfig().get().format();
        assertThat(format).isPresent();
        assertThat(format.get().schema()._additionalProperties().keySet()).contains("type", "properties", "required", "additionalProperties");
        assertThat(client.modelId()).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void modelIsRequired() {
        assertThatThrownBy(() -> new AnthropicLlmClient(mock(AnthropicClient.class), "", "medium"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
