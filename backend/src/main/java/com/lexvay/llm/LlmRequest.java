package com.lexvay.llm;

import tools.jackson.databind.JsonNode;

/**
 * One model call. {@code scriptKey} identifies the call for {@link FakeLlmClient} scripts (for example
 * {@code EXTRACT:<rule version label>} or {@code MATCH:<requirement ref>}); the real client ignores it.
 */
public record LlmRequest(LlmPurpose purpose, String scriptKey, String systemPrompt, String userPrompt,
                         JsonNode outputSchema, int maxOutputTokens) {
}
