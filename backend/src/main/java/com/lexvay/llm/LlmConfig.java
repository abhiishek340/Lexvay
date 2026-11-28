package com.lexvay.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

    /** The prices the spend gate multiplies tokens by; empty unless LEXVAY_LLM_RATES is set (D-562). */
    @Bean
    LlmRates llmRates(@Value("${lexvay.llm.rates:}") String spec) {
        return LlmRates.parse(spec);
    }

    @Bean
    LlmClient llmClient(@Value("${lexvay.llm.provider:fake}") String provider,
                        @Value("${lexvay.llm.model:claude-sonnet-5-5}") String model,
                        @Value("${lexvay.llm.effort:medium}") String effort) {
        return switch (provider) {
            case "fake" -> new FakeLlmClient();
            case "anthropic" -> new AnthropicLlmClient(AnthropicOkHttpClient.fromEnv(), model, effort);
            default -> throw new IllegalArgumentException("Unknown LEXVAY_LLM_PROVIDER value: " + provider);
        };
    }
}
