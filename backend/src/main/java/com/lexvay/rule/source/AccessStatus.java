package com.lexvay.rule.source;

/**
 * Whether a person has reviewed the source's terms of use and robots.txt and decided that it may be fetched. Only
 * PERMITTED sources are fetched on a schedule. UNREVIEWED sources are fetched only when a person asks for a check;
 * RESTRICTED and BLOCKED sources are never fetched, by anyone (RG-2, D-568).
 */
public enum AccessStatus {
    UNREVIEWED, PERMITTED, RESTRICTED, BLOCKED;

    /** True for the statuses under which no request may be made to the source at all. */
    public boolean neverFetched() {
        return this == RESTRICTED || this == BLOCKED;
    }
}
