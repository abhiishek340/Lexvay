package com.lexvay.llm;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {

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
