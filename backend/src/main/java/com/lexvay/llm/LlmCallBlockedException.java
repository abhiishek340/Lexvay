package com.lexvay.llm;

/**
 * A call the product refused to make, before anything was sent (D-563). Retrying cannot change the answer until
 * someone changes the configuration or the tenant's mode, so the job fails at once. The message names the reason and
 * never carries any of the request.
 */
public class LlmCallBlockedException extends LlmRefusedException {

    public enum Reason {
        /** A real-data tenant has no model: only the scripted one is configured. */
        NO_MODEL_FOR_REAL_DATA,
        /** The provider bills per call and LEXVAY_LLM_PAID_APPROVED is not true. */
        PAID_CALLS_NOT_APPROVED,
        /** No configured price for the model, so the cost of the call and of the month so far is unknown. */
        COST_UNKNOWN,
        /** The month's recorded cost plus this call's largest possible cost passes the monthly budget. */
        OVER_BUDGET
    }

    private final Reason reason;

    public LlmCallBlockedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
