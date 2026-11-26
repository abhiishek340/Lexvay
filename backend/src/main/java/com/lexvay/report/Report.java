package com.lexvay.report;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Report(UUID id, UUID determinationId, String storageKey, ReportStatus status, OffsetDateTime createdAt, UUID createdBy) {
}
