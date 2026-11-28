package com.lexvay.rule.source;

import java.util.List;
import java.util.UUID;

/**
 * How a source is governed (RG-2): what it is, where it may be fetched from, what its text must contain, whether a
 * person permitted fetching it, and who looks after it. {@code accessNote} records the terms and robots findings; who
 * wrote it and when is in the audit trail.
 */
public record SourceGovernance(AuthorityClass authorityClass, List<String> allowedHosts, List<String> allowedPathPrefixes,
                               List<String> expectedIdentifiers, AccessStatus accessStatus, String accessNote,
                               UUID ownerUserId) {

    /** What a source starts with: its own host, class OTHER, access not reviewed. */
    public static SourceGovernance initial(String host) {
        return new SourceGovernance(AuthorityClass.OTHER, List.of(host), List.of(), List.of(), AccessStatus.UNREVIEWED, null, null);
    }

    public FetchScope scope() {
        return new FetchScope(allowedHosts, allowedPathPrefixes);
    }
}
