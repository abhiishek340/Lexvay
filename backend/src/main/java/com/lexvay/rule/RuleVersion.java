package com.lexvay.rule;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RuleVersion(UUID id, UUID jurisdictionId, String jurisdictionCode, String licenseType, String label,
                          String sourceUrl, LocalDate effectiveFrom, UUID supersedesId, UUID documentId,
                          UUID approvedBy, OffsetDateTime approvedAt, OffsetDateTime createdAt, UUID createdBy, long requirementCount) {

    public boolean approved() {
        return approvedAt != null;
    }
}
