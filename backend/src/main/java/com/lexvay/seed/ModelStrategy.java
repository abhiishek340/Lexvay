package com.lexvay.seed;

import java.util.Map;
import java.util.UUID;

/**
 * What the seed loader does around the two model-backed stages. Scripting {@link com.lexvay.llm.FakeLlmClient}
 * is one strategy ({@link ScriptedFakeModel}); a real model needs none ({@link #NONE}); the evaluation
 * harness adds its own to observe extraction and to substitute the golden requirement list.
 */
public interface ModelStrategy {

    /** No preparation: the model answers by itself. */
    ModelStrategy NONE = new ModelStrategy() {
    };

    /** Before a rule version is created, so extraction for {@code ruleSetLabel} can be prepared. */
    default void beforeExtraction(String ruleSetLabel) {
    }

    /** After extraction stored its unapproved list, before a reviewer approves it. Runs as that reviewer. */
    default void afterExtraction(String jurisdiction, UUID ruleVersionId) {
    }

    /** After every rule version is approved and before the assessment starts; documents are keyed by file name. */
    default void beforeMatching(Map<String, UUID> documentByFile) {
    }
}
