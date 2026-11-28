package com.lexvay.llm;

public enum LlmPurpose {
    EXTRACT_REQUIREMENTS, MATCH_EVIDENCE,
    /** The evaluation lab's generic-model baseline (EV-2): never part of an assessment's own pipeline. */
    EVAL_BASELINE
}
