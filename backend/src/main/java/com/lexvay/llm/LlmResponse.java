package com.lexvay.llm;

/**
 * Raw model output plus the facts needed for the audit trail and cost reporting. {@code tokensEstimated} is true when
 * the provider reported no usage for at least one of the counts and it was estimated from text length (see
 * {@link #estimateInputTokens} and {@link #estimateOutputTokens}); {@link LlmCallRecorder} stores that mark with the call.
 */
public record LlmResponse(String text, String model, Integer inputTokens, Integer outputTokens, long durationMs,
                          boolean tokensEstimated) {

    /** A response whose token counts were reported by the provider. */
    public LlmResponse(String text, String model, Integer inputTokens, Integer outputTokens, long durationMs) {
        this(text, model, inputTokens, outputTokens, durationMs, false);
    }

    /** A token is roughly four characters of the user prompt. The model never counts (rule 3). */
    public static int estimateInputTokens(LlmRequest request) {
        return request.userPrompt().length() / 4;
    }

    public static int estimateOutputTokens(String text) {
        return text.length() / 4;
    }
}
