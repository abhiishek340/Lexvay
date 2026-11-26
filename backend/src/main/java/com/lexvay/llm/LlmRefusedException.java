package com.lexvay.llm;

/** The model declined to answer (stop reason {@code refusal}). Not retried; the job fails with the reason. */
public class LlmRefusedException extends RuntimeException {
    public LlmRefusedException(String message) {
        super(message);
    }
}
