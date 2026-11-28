package com.lexvay.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Which provider the configuration builds, and what it refuses to start with. No model is called. */
@ExtendWith(OutputCaptureExtension.class)
class LlmConfigTest {

    ApplicationContextRunner runner(String... properties) {
        return new ApplicationContextRunner().withUserConfiguration(LlmConfig.class).withPropertyValues(properties);
    }

    @Test
    void theOpenAiCompatibleProviderBuildsTheLocalClientAndTheStartupLineNamesTheHostAndModel(CapturedOutput output) {
        runner("lexvay.llm.provider=openai-compatible", "lexvay.llm.model=local-model",
                "lexvay.llm.base-url=http://localhost:8089/v1").run(context -> {
                    assertThat(context).hasNotFailed();
                    LlmClient client = context.getBean(LlmClient.class);
                    assertThat(client).isInstanceOf(OpenAiCompatibleLlmClient.class);
                    assertThat(client.modelId()).isEqualTo("local-model");
                    assertThat(client.metered()).isFalse();
                    assertThat(client.scripted()).isFalse();
                });
        assertThat(output.getAll()).containsOnlyOnce("model local-model at host localhost");
    }

    @Test
    void aHostOutsideTheDefaultAllowedListFailsStartupNamingHostAndVariable() {
        runner("lexvay.llm.provider=openai-compatible", "lexvay.llm.model=m", "lexvay.llm.base-url=https://models.example.org/v1")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("models.example.org").hasMessageContaining("LEXVAY_LLM_ALLOWED_HOSTS"));
    }

    @Test
    void aRemoteHostNeedsAnExplicitEntryAndThenStartsAndIsNamedInTheStartupLine(CapturedOutput output) {
        runner("lexvay.llm.provider=openai-compatible", "lexvay.llm.model=m", "lexvay.llm.base-url=https://models.example.org/v1",
                "lexvay.llm.allowed-hosts=localhost, models.example.org").run(context -> assertThat(context).hasNotFailed());
        assertThat(output.getAll()).containsOnlyOnce("at host models.example.org");
    }

    @Test
    void theModelHasNoDefaultForALocalServer() {
        runner("lexvay.llm.provider=openai-compatible", "lexvay.llm.base-url=http://localhost:8089/v1")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("LEXVAY_LLM_MODEL must be set"));
    }

    @Test
    void theBaseUrlIsRequiredForALocalServer() {
        runner("lexvay.llm.provider=openai-compatible", "lexvay.llm.model=m")
                .run(context -> assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("LEXVAY_LLM_BASE_URL must be set"));
    }

    @Test
    void theClaudeApiClientKeepsItsDefaultModelWhenNoneIsSet() {
        runner("lexvay.llm.provider=anthropic").run(context -> assertThat(context.getBean(LlmClient.class).modelId())
                .isEqualTo(LlmConfig.ANTHROPIC_DEFAULT_MODEL));
    }

    @Test
    void theScriptedModelIsTheDefaultProvider() {
        runner().run(context -> assertThat(context.getBean(LlmClient.class)).isInstanceOf(FakeLlmClient.class));
    }
}
