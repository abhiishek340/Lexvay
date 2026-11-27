package com.lexvay.audit;

import com.lexvay.common.WhereClause;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Optional narrowing of the audit list and of its CSV export; a null field does not filter. {@code from} and
 * {@code to} are ISO-8601 instants, both inclusive. Bound straight from the query string, so the two endpoints cannot
 * disagree about what a filter means.
 */
public record AuditFilter(String action, String entityType, UUID entityId, UUID userId,
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

    public static final AuditFilter NONE = new AuditFilter(null, null, null, null, null, null);

    /**
     * Only the filters that are set become predicates (D-116). Columns carry the table name because the export joins
     * {@code app_user}, which has a {@code created_at} of its own.
     */
    WhereClause where() {
        return new WhereClause()
                .andIfPresent("audit_log.action = :action", "action", action)
                .andIfPresent("audit_log.entity_type = :entityType", "entityType", entityType)
                .andIfPresent("audit_log.entity_id = :entityId", "entityId", entityId)
                .andIfPresent("audit_log.user_id = :userId", "userId", userId)
                .andIfPresent("audit_log.created_at >= :from", "from", from)
                .andIfPresent("audit_log.created_at <= :to", "to", to);
    }

    /** The filters as the audit entry of an export records them; absent filters are left out. */
    Map<String, Object> auditDetail() {
        Map<String, Object> detail = new LinkedHashMap<>();
        putIfPresent(detail, "action", action);
        putIfPresent(detail, "entityType", entityType);
        putIfPresent(detail, "entityId", entityId);
        putIfPresent(detail, "userId", userId);
        putIfPresent(detail, "from", from);
        putIfPresent(detail, "to", to);
        return detail;
    }

    private static void putIfPresent(Map<String, Object> detail, String key, Object value) {
        if (value != null) {
            detail.put(key, value.toString());
        }
    }
}
