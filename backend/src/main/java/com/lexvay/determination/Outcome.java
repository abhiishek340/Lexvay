package com.lexvay.determination;

/**
 * The three outcomes a signer may record (D-004; 34 CFR 668.43(a)(5)(v) and (c)(1)). The labels are the words the
 * signing dialog uses (frontend {@code OUTCOME_LABEL}); backend text that shows an outcome (PDF report, CSV export)
 * uses them too, so the forbidden phrase never appears.
 */
public enum Outcome {
    MEETS("Determined to meet"),
    DOES_NOT_MEET("Determined not to meet"),
    NOT_DETERMINED("No determination made");

    private final String label;

    Outcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
