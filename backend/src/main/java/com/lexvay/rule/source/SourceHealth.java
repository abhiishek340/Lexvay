package com.lexvay.rule.source;

import java.time.OffsetDateTime;

/**
 * How well the monitor is keeping an eye on a source, computed when it is read and never stored (RG-2, D-573). It is
 * about the monitoring and says nothing about the law: a source in any state but HEALTHY means the rules it covers may
 * be out of date, not that they changed. When several apply the first of BLOCKED, UNREVIEWED, SUSPECT, FAILING, STALE
 * is shown.
 */
public enum SourceHealth {
    /** The source was read recently and nothing is wrong with how. */
    HEALTHY,
    /** It has not been read successfully within twice its check interval. */
    STALE,
    /** Three or more checks in a row failed to fetch it. */
    FAILING,
    /** Its access status forbids fetching it, or its robots.txt does. */
    BLOCKED,
    /** The last check got something that is not the rule text. */
    SUSPECT,
    /** Nobody has reviewed whether it may be fetched, so it is not fetched on a schedule. */
    UNREVIEWED;

    /** How many consecutive failed fetches make a source FAILING. */
    static final int FAILING_AFTER = 3;

    /**
     * What the health of one source is computed from. {@code latestOutcome} is null before the first check;
     * {@code lastSuccessAt} is null until a check reads the source.
     */
    public record Facts(AccessStatus accessStatus, CheckOutcome latestOutcome, int consecutiveFailures,
                        OffsetDateTime lastSuccessAt, OffsetDateTime createdAt, int checkIntervalHours) {
    }

    public static SourceHealth of(Facts facts, OffsetDateTime now) {
        if (facts.accessStatus().neverFetched() || facts.latestOutcome() == CheckOutcome.BLOCKED_BY_ROBOTS) {
            return BLOCKED;
        }
        if (facts.accessStatus() == AccessStatus.UNREVIEWED) {
            return UNREVIEWED;
        }
        if (facts.latestOutcome() == CheckOutcome.SUSPECT_CONTENT) {
            return SUSPECT;
        }
        if (facts.consecutiveFailures() >= FAILING_AFTER) {
            return FAILING;
        }
        OffsetDateTime lastRead = facts.lastSuccessAt() != null ? facts.lastSuccessAt() : facts.createdAt();
        return lastRead.plusHours(2L * facts.checkIntervalHours()).isBefore(now) ? STALE : HEALTHY;
    }
}
