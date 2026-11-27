package com.lexvay.audit;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /**
     * Newest first. Only the predicates the filter sets are in the SQL text, so every query shape gets its
     * own plan instead of one generic plan with {@code :x IS NULL OR} branches.
     */
    public List<AuditEntry> newestFirst(AuditFilter filter, int limit, int offset) {
        var where = new ArrayList<String>();
        var params = new LinkedHashMap<String, Object>();
        filterBy(where, params, "action = :action", "action", filter.action());
        filterBy(where, params, "entity_type = :entityType", "entityType", filter.entityType());
        filterBy(where, params, "entity_id = :entityId", "entityId", filter.entityId());
        filterBy(where, params, "user_id = :userId", "userId", filter.userId());
        filterBy(where, params, "created_at >= :from", "from", filter.from());
        filterBy(where, params, "created_at <= :to", "to", filter.to());
        var spec = jdbc.sql(SELECT + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset");
        params.forEach(spec::param);
        return spec.param("limit", limit).param("offset", offset).query(this::map).list();
    }

    public List<AuditEntry> forEntity(UUID entityId) {
        return jdbc.sql(SELECT + " WHERE entity_id = :entityId ORDER BY created_at DESC")
                .param("entityId", entityId)
                .query(this::map)
                .list();
    }

    private static void filterBy(List<String> where, Map<String, Object> params, String predicate, String name, Object value) {
        if (value != null) {
            where.add(predicate);
            params.put(name, value);
        }
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
