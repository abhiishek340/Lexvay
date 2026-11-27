package com.lexvay.rule.source;

/** What one check of a rule source found. None of these is a determination about a program. */
public enum CheckOutcome {
    /** The text is the same as at the last check. */
    UNCHANGED,
    /** The text changed: a document and an unapproved draft rule version were created. */
    CHANGED,
    /** The text changed, but an unapproved draft already waits for review, so none was created. */
    DRAFT_EXISTS,
    /** The page could not be fetched or read. */
    FETCH_FAILED
}
