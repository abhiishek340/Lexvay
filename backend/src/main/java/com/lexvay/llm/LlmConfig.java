package com.lexvay.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    /** The model the Claude API client uses when LEXVAY_LLM_MODEL is empty. A local server has no default (D-616). */
    static final String ANTHROPIC_DEFAULT_MODEL = "claude-sonnet-5-5";

    /** The prices the spend gate multiplies tokens by; empty unless LEXVAY_LLM_RATES is set (D-562). */
    @Bean
    LlmRates llmRates(@Value("${lexvay.llm.rates:}") String spec) {
        return LlmRates.parse(spec);
    }

    @Bean
    LlmClient llmClient(@Value("${lexvay.llm.provider:fake}") String provider,
                        @Value("${lexvay.llm.model:}") String model,
                        @Value("${lexvay.llm.effort:medium}") String effort,
                        @Value("${lexvay.llm.base-url:}") String baseUrl,
                        @Value("${lexvay.llm.api-key:}") String apiKey,
                        @Value("${lexvay.llm.timeout-seconds:120}") long timeoutSeconds,
                        @Value("${lexvay.llm.allowed-hosts:localhost,127.0.0.1,::1}") String allowedHosts,
                        @Value("${lexvay.llm.seed:}") String seed) {
        return switch (provider) {
            case "fake" -> new FakeLlmClient();
            case "anthropic" -> new AnthropicLlmClient(AnthropicOkHttpClient.fromEnv(),
                    model.isBlank() ? ANTHROPIC_DEFAULT_MODEL : model, effort);
            case "openai-compatible" -> new OpenAiCompatibleLlmClient(baseUrl, model, apiKey, Duration.ofSeconds(timeoutSeconds),
                    List.of(allowedHosts.split(",")), seed.isBlank() ? null : Long.valueOf(seed.strip()));
            default -> throw new IllegalArgumentException("Unknown LEXVAY_LLM_PROVIDER value: " + provider);
        };
    }
}
