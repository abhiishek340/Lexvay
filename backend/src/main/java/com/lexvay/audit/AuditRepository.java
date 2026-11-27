package com.lexvay.audit;

import com.lexvay.common.WhereClause;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AuditRepository {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

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
        WhereClause where = new WhereClause()
                .andIfPresent("action = :action", "action", filter.action())
                .andIfPresent("entity_type = :entityType", "entityType", filter.entityType())
                .andIfPresent("entity_id = :entityId", "entityId", filter.entityId())
                .andIfPresent("user_id = :userId", "userId", filter.userId())
                .andIfPresent("created_at >= :from", "from", filter.from())
                .andIfPresent("created_at <= :to", "to", filter.to());
        return where.bind(jdbc.sql(SELECT + where.sql() + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset"))
                .param("limit", limit).param("offset", offset).query(this::map).list();
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
                rs.getObject("created_at", java.time.OffsetDateTime.class));
    }
}
