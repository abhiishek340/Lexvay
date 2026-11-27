package com.lexvay.audit;

import com.lexvay.common.WhereClause;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AuditRepository {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    /** One line of the CSV export; the actor is the display name, or "System" for work no person started. */
    public record ExportRow(Instant time, String actor, String action, String entityType, UUID entityId, String detail) {
    }

    static final int EXPORT_FETCH_SIZE = 500;

    private static final String EXPORT_SELECT = "SELECT audit_log.created_at, coalesce(app_user.display_name, 'System') AS actor, "
            + "audit_log.action, audit_log.entity_type, audit_log.entity_id, audit_log.detail::text AS detail "
            + "FROM audit_log LEFT JOIN app_user ON app_user.id = audit_log.user_id";

    private static final String SELECT =
            "SELECT id, user_id, action, entity_type, entity_id, detail::text AS detail, created_at FROM audit_log";

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public AuditRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(UUID tenantId, UUID userId, String action, String entityType, UUID entityId,
                       Map<String, ?> detail) {
        jdbc.sql("INSERT INTO audit_log (tenant_id, user_id, action, entity_type, entity_id, detail, created_by) "
                        + "VALUES (:tenantId, :userId, :action, :entityType, :entityId, :detail, :userId)")
                .param("tenantId", tenantId)
                .param("userId", userId)
                .param("action", action)
                .param("entityType", entityType)
                .param("entityId", entityId)
                .param("detail", json.writeValueAsString(detail), Types.OTHER)
                .update();
    }

    /** Newest first; only the filters that are set become predicates (WhereClause, D-116). */
    public List<AuditEntry> newestFirst(AuditFilter filter, int limit, int offset) {
        WhereClause where = filter.where();
        return where.bind(jdbc.sql(SELECT + where.sql() + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset"))
                .param("limit", limit).param("offset", offset).query(this::map).list();
    }

    /**
     * Hands every matching entry, newest first, to {@code sink} as the database delivers it, {@value #EXPORT_FETCH_SIZE}
     * rows at a time, so the log is never held in memory (D-377). One read-only transaction: the cursor the driver
     * opens for a fetch size lives only inside a transaction.
     */
    @Transactional(readOnly = true)
    public void streamForExport(AuditFilter filter, Consumer<ExportRow> sink) {
        WhereClause where = filter.where();
        where.bind(jdbc.sql(EXPORT_SELECT + where.sql() + " ORDER BY audit_log.created_at DESC, audit_log.id DESC"))
                .withFetchSize(EXPORT_FETCH_SIZE)
                .query((RowCallbackHandler) rs -> sink.accept(new ExportRow(
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getString("actor"),
                        rs.getString("action"),
                        rs.getString("entity_type"),
                        rs.getObject("entity_id", UUID.class),
                        rs.getString("detail"))));
    }

    public List<AuditEntry> forEntity(UUID entityId) {
        return jdbc.sql(SELECT + " WHERE entity_id = :entityId ORDER BY created_at DESC")
                .param("entityId", entityId)
                .query(this::map)
                .list();
    }

    private AuditEntry map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AuditEntry(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                json.readValue(rs.getString("detail"), MAP),
                rs.getObject("created_at", OffsetDateTime.class));
    }
}
