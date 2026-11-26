package com.lexvay.audit;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditEntry(UUID id, UUID userId, String action, String entityType, UUID entityId,
                         Map<String, Object> detail, OffsetDateTime createdAt) {
}
