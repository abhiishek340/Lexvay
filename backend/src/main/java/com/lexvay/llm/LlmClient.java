package com.lexvay.llm;

import java.util.Optional;

/**
 * The only seam through which the product talks to a language model. Three implementations:
 * {@link FakeLlmClient} (deterministic, scripted; used by every automated test), {@link AnthropicLlmClient} (the Claude
 * API) and {@link OpenAiCompatibleLlmClient} (a server the operator runs, usually on the same machine). The model is
 * never asked to do arithmetic or to decide.
 */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);

    String modelId();

    /** True when the provider bills each call; {@link SpendGate} then decides whether the call may be made (D-562). */
    default boolean metered() {
        return false;
    }

    /** True when the answers are scripted, not produced by a model; never allowed for real data (D-561). */
    default boolean scripted() {
        return false;
    }

    /** The host calls are sent to when the operator chose it (LEXVAY_LLM_BASE_URL); empty for the scripted model and the Claude API. */
    default Optional<String> endpointHost() {
        return Optional.empty();
    }
}
