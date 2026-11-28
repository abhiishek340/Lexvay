package com.lexvay.rule;

/**
 * Where a rule version stands in law. A person records it with a citation; nothing derives it from a fetch (D-551).
 * Only a version that is final can be approved for assessment, and only one in force, or final and already
 * effective on the assessment date, is selected (D-553, D-556).
 */
public enum LegalStatus {
    PROPOSED, FINAL_NOT_EFFECTIVE, IN_FORCE, SUPERSEDED, REPEALED, WITHDRAWN, UNKNOWN;

    /** Whether requirements of a version with this status may be approved for assessment. */
    public boolean approvable() {
        return this == IN_FORCE || this == FINAL_NOT_EFFECTIVE;
    }
}
