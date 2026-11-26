package com.lexvay.llm;

/**
 * The only seam through which the product talks to a language model. Two implementations:
 * {@link FakeLlmClient} (deterministic, scripted; used by every automated test) and
 * {@link AnthropicLlmClient} (the Claude API). The model is never asked to do arithmetic or to decide.
 */
public interface LlmClient {

    LlmResponse complete(LlmRequest request);

    String modelId();
}
