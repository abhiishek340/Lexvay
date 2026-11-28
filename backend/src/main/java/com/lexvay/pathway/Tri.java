package com.lexvay.pathway;

/**
 * An answer that may not be known. UNKNOWN is never read as TRUE: a missing minimum or credit leaves a question open
 * (product rule 4).
 */
public enum Tri {
    TRUE, FALSE, UNKNOWN;

    /** FALSE when either is; otherwise UNKNOWN when either is; otherwise TRUE. */
    public Tri and(Tri other) {
        if (this == FALSE || other == FALSE) {
            return FALSE;
        }
        return this == UNKNOWN || other == UNKNOWN ? UNKNOWN : TRUE;
    }
}
