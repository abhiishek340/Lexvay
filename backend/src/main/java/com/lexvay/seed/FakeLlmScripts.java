package com.lexvay.seed;

import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Shape of a fake-llm.yml file: extraction output per rule label, match output per requirement ref. A file may
 * hold only one of the two sections.
 */
public record FakeLlmScripts(Map<String, JsonNode> extraction, Map<String, MatchScript> match) {

    public FakeLlmScripts {
        extraction = extraction == null ? Map.of() : extraction;
        match = match == null ? Map.of() : match;
    }

    public record MatchScript(String finding, String document, Integer page, String quote, String rationale) {
    }

    /** This file's scripts with {@code later}'s added; a key present in both takes the later one. */
    public FakeLlmScripts overriddenBy(FakeLlmScripts later) {
        Map<String, JsonNode> mergedExtraction = new HashMap<>(extraction);
        mergedExtraction.putAll(later.extraction);
        Map<String, MatchScript> mergedMatch = new HashMap<>(match);
        mergedMatch.putAll(later.match);
        return new FakeLlmScripts(mergedExtraction, mergedMatch);
    }
}
