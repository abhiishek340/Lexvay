package com.lexvay.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Optional narrowing of the audit list; a null field does not filter. {@code from} and {@code to} are both inclusive. */
public record AuditFilter(String action, String entityType, UUID entityId, UUID userId, OffsetDateTime from,
                          OffsetDateTime to) {

    public static final AuditFilter NONE = new AuditFilter(null, null, null, null, null, null);
}
