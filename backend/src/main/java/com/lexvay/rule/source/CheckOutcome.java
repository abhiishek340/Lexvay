package com.lexvay.rule.source;

import java.util.Set;

/** What one check of a rule source found. None of these is a determination about a program. */
public enum CheckOutcome {
    /** The text is the same as at the last check. */
    UNCHANGED,
    /** The server answered 304 to the conditional request: nothing was downloaded and nothing is stored (RG-2). */
    NOT_MODIFIED,
    /** The text changed: a document and an unapproved draft rule version were created. */
    CHANGED,
    /** The text changed while an unapproved draft waits: its document is stored, linked to that draft, behind it in line. */
    CHANGED_QUEUED,
    /** Recorded before V025, when a change seen while a draft waited was not stored. No longer written. */
    DRAFT_EXISTS,
    /** The host's robots.txt forbids the page for the monitor; nothing was fetched (RG-2). */
    BLOCKED_BY_ROBOTS,
    /** What came back is not the rule text, for example a challenge page or a text without the expected identifiers (RG-2). */
    SUSPECT_CONTENT,
    /** The page could not be fetched or read. */
    FETCH_FAILED;

    /** The outcomes of a check that reached the source and read what it holds, which is what keeps a source fresh. */
    public static final Set<CheckOutcome> SUCCESSES = Set.of(UNCHANGED, NOT_MODIFIED, CHANGED, CHANGED_QUEUED);
}
