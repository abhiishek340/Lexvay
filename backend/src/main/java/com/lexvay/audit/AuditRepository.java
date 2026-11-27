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

    /**
     * One line of the CSV export; the actor is the display name, or "System" for work no person started. {@code seq}
     * and {@code rowHash} are null while the entry waits to be sealed. The ids and the detail are what
     * {@code AuditCanonical} hashes, so the file can be checked without the database (D-535).
     */
    public record ExportRow(Instant time, String actor, UUID actorId, UUID entryId, String action, String entityType,
                            UUID entityId, String detail, Long seq, String rowHash) {
    }

    static final int EXPORT_FETCH_SIZE = 500;

    private static final String EXPORT_SELECT = "SELECT audit_log.created_at, coalesce(app_user.display_name, 'System') AS actor, "
            + "audit_log.user_id, audit_log.id, audit_log.action, audit_log.entity_type, audit_log.entity_id, "
            + "audit_log.detail::text AS detail, audit_seal.seq, audit_seal.row_hash "
            + "FROM audit_log LEFT JOIN app_user ON app_user.id = audit_log.user_id "
            + "LEFT JOIN audit_seal ON audit_seal.audit_id = audit_log.id";

    private static final String SELECT =
            "SELECT id, user_id, action, entity_type, entity_id, detail::text AS detail, created_at FROM audit_log";

    /** The statement of the audit list for the predicates of {@code where}; public so that {@code IndexUsageTest} runs the real text. */
    public static String listSql(WhereClause where) {
        return SELECT + where.sql() + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";
    }

    /** The statement of the CSV export for the predicates of {@code where}; public for the same reason as {@link #listSql}. */
    public static String exportSql(WhereClause where) {
        return EXPORT_SELECT + where.sql() + " ORDER BY audit_log.created_at DESC, audit_log.id DESC";
    }

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public AuditRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * The only statement that writes the audit log: the entry and its place in the sealer's queue commit together, so
     * no entry can escape sealing (SEC-3, D-530).
     */
    public void insert(UUID tenantId, UUID userId, String action, String entityType, UUID entityId,
                       Map<String, ?> detail) {
        jdbc.sql("WITH entry AS (INSERT INTO audit_log (tenant_id, user_id, action, entity_type, entity_id, detail, created_by) "
                        + "VALUES (:tenantId, :userId, :action, :entityType, :entityId, :detail, :userId) "
                        + "RETURNING id, tenant_id, created_at) "
                        + "INSERT INTO audit_unsealed (audit_id, tenant_id, created_at) SELECT id, tenant_id, created_at FROM entry")
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
        return where.bind(jdbc.sql(listSql(where)))
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
        where.bind(jdbc.sql(exportSql(where)))
                .withFetchSize(EXPORT_FETCH_SIZE)
                .query((RowCallbackHandler) rs -> sink.accept(new ExportRow(
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getString("actor"),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("id", UUID.class),
                        rs.getString("action"),
                        rs.getString("entity_type"),
                        rs.getObject("entity_id", UUID.class),
                        rs.getString("detail"),
                        rs.getObject("seq", Long.class),
                        rs.getString("row_hash"))));
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
