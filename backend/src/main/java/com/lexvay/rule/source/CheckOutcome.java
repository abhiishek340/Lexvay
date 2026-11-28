package com.lexvay.rule.source;

/** What one check of a rule source found. None of these is a determination about a program. */
public enum CheckOutcome {
    /** The text is the same as at the last check. */
    UNCHANGED,
    /** The text changed: a document and an unapproved draft rule version were created. */
    CHANGED,
    /** The text changed while an unapproved draft waits: its document is stored, linked to that draft, behind it in line. */
    CHANGED_QUEUED,
    /** Recorded before V021, when a change seen while a draft waited was not stored. No longer written. */
    DRAFT_EXISTS,
    /** The page could not be fetched or read. */
    FETCH_FAILED
}
