package com.lexvay.rule.source;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A source as listed, with its latest check (null before the first one). */
public record RuleSourceView(UUID id, String jurisdiction, String licenseType, String label, String url,
                             int checkIntervalHours, boolean active, OffsetDateTime lastCheckedAt,
                             OffsetDateTime createdAt, LatestCheck latestCheck) {

    public record LatestCheck(OffsetDateTime checkedAt, CheckOutcome outcome, Integer httpStatus, String detail,
                              UUID documentId, UUID ruleVersionId) {
    }
}
