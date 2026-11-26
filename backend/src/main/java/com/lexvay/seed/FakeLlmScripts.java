package com.lexvay.seed;

import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Shape of seed/fake-llm.yml: extraction output per rule label, match output per requirement ref. */
public record FakeLlmScripts(Map<String, JsonNode> extraction, Map<String, MatchScript> match) {

    public record MatchScript(String finding, String document, Integer page, String quote, String rationale) {
    }
}
