package com.lexvay.evidencepackage;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PackageRepository {

    private static final RowMapper<EvidencePackage> ROW = (rs, i) -> new EvidencePackage(rs.getObject("id", UUID.class),
            rs.getObject("determination_id", UUID.class), PackageStatus.valueOf(rs.getString("status")), rs.getString("storage_key"),
            rs.getString("content_sha256"), rs.getObject("size_bytes", Long.class), rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("created_by", UUID.class));

    private final JdbcClient jdbc;

    public PackageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID tenantId, UUID determinationId, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO evidence_package (id, tenant_id, determination_id, created_by) VALUES (:id, :tenantId, :d, :by)")
                .param("id", id).param("tenantId", tenantId).param("d", determinationId).param("by", createdBy).update();
        return id;
    }

    public Optional<EvidencePackage> findById(UUID id) {
        return jdbc.sql("SELECT id, determination_id, status, storage_key, content_sha256, size_bytes, created_at, created_by "
                        + "FROM evidence_package WHERE id = :id")
                .param("id", id).query(ROW).optional();
    }

    public void markReady(UUID id, String storageKey, String contentSha256, long sizeBytes) {
        jdbc.sql("UPDATE evidence_package SET status = 'READY', storage_key = :key, content_sha256 = :sha, size_bytes = :size WHERE id = :id")
                .param("key", storageKey).param("sha", contentSha256).param("size", sizeBytes).param("id", id).update();
    }

    /** A package that is not waiting any more keeps the status it has. */
    public void markFailed(UUID id) {
        jdbc.sql("UPDATE evidence_package SET status = 'FAILED' WHERE id = :id AND status = 'QUEUED'").param("id", id).update();
    }
}
