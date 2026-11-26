package com.lexvay.llm;

/** Raw model output plus the facts needed for the audit trail and cost reporting. */
public record LlmResponse(String text, String model, Integer inputTokens, Integer outputTokens, long durationMs) {
}
