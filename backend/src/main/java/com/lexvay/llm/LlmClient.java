package com.lexvay.llm;

/**
 * The only seam through which the product talks to a language model. Two implementations:
 * {@link FakeLlmClient} (deterministic, scripted; used by every automated test) and
 * {@link AnthropicLlmClient} (the Claude API). The model is never asked to do arithmetic or to decide.
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
}
