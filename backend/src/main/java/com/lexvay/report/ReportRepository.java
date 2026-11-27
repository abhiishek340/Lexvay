package com.lexvay.report;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {

    private static final RowMapper<Report> ROW = (rs, i) -> new Report(rs.getObject("id", UUID.class),
            rs.getObject("determination_id", UUID.class), rs.getString("storage_key"), ReportStatus.valueOf(rs.getString("status")),
            rs.getObject("created_at", java.time.OffsetDateTime.class), rs.getObject("created_by", UUID.class));

    private final JdbcClient jdbc;

    public ReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID tenantId, UUID determinationId, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO report (id, tenant_id, determination_id, created_by) VALUES (:id, :tenantId, :d, :by)")
                .param("id", id).param("tenantId", tenantId).param("d", determinationId).param("by", createdBy).update();
        return id;
    }

    public Optional<Report> findById(UUID id) {
        return jdbc.sql("SELECT id, determination_id, storage_key, status, created_at, created_by FROM report WHERE id = :id")
                .param("id", id).query(ROW).optional();
    }

    /** The report that is current for a determination: the newest one whose PDF exists. */
    public Optional<Report> findLatestReady(UUID determinationId) {
        return jdbc.sql("SELECT id, determination_id, storage_key, status, created_at, created_by FROM report "
                        + "WHERE determination_id = :d AND status = 'READY' ORDER BY created_at DESC, id DESC LIMIT 1")
                .param("d", determinationId).query(ROW).optional();
    }

    public void markReady(UUID id, String storageKey) {
        jdbc.sql("UPDATE report SET status = 'READY', storage_key = :key WHERE id = :id").param("key", storageKey).param("id", id).update();
    }

    public void markFailed(UUID id) {
        jdbc.sql("UPDATE report SET status = 'FAILED' WHERE id = :id").param("id", id).update();
    }
}
