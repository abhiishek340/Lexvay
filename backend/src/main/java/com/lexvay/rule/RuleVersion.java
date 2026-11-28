package com.lexvay.rule;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A rule version as {@code rule_version_current} reads it: {@code legal} is its current legal status and dates, the
 * newest correction or else the creation values. {@code observedAt} is when the text was acquired, null for an upload.
 */
public record RuleVersion(UUID id, UUID jurisdictionId, String jurisdictionCode, String licenseType, String label,
                          String sourceUrl, LegalPosition legal, OffsetDateTime observedAt, UUID supersedesId,
                          UUID documentId, UUID approvedBy, OffsetDateTime approvedAt, OffsetDateTime createdAt,
                          UUID createdBy, long requirementCount) {

    public boolean approved() {
        return approvedAt != null;
    }
}
