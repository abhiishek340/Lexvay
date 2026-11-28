package com.lexvay.rule.source;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A source as listed, with its latest check (null before the first one), its schedule (when it is next due and how many
 * checks in a row failed to fetch the page), how it is governed, and its health: computed for this response from the
 * checks and never stored, and separate from the legal status of any rule (RG-2, D-573).
 */
public record RuleSourceView(UUID id, String jurisdiction, String licenseType, String label, String url,
                             int checkIntervalHours, boolean active, OffsetDateTime lastCheckedAt,
                             OffsetDateTime lastSuccessAt, OffsetDateTime nextCheckAt, int consecutiveFailures,
                             OffsetDateTime createdAt, SourceGovernance governance, SourceHealth health, LatestCheck latestCheck) {

    public record LatestCheck(OffsetDateTime checkedAt, CheckOutcome outcome, Integer httpStatus, String detail,
                              UUID documentId, UUID ruleVersionId) {
    }
}
