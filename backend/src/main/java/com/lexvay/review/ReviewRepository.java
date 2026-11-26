package com.lexvay.review;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Append only (product rule 5); the database rejects updates and deletes. */
@Repository
public class ReviewRepository {

    private final JdbcClient jdbc;

    public ReviewRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code clock_timestamp()} so two events in one transaction still order correctly. */
    public UUID insert(UUID tenantId, UUID mappingId, ReviewKind kind, String note, UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO review_event (id, tenant_id, mapping_id, kind, note, user_id, created_at, created_by) "
                        + "VALUES (:id, :tenantId, :mappingId, :kind, :note, :userId, clock_timestamp(), :userId)")
                .param("id", id).param("tenantId", tenantId).param("mappingId", mappingId).param("kind", kind.name())
                .param("note", note).param("userId", userId).update();
        return id;
    }
}
