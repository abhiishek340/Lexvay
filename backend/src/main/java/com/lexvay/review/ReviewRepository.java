package com.lexvay.review;

import java.util.List;
import java.util.Optional;
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

    /** Oldest first; a null user means the system recorded the event. */
    public List<ReviewEventView> findByMapping(UUID mappingId) {
        return jdbc.sql("""
                SELECT re.id, re.kind, re.note, re.user_id, u.display_name AS user_display_name, re.created_at
                FROM review_event re LEFT JOIN app_user u ON u.id = re.user_id
                WHERE re.mapping_id = :mappingId ORDER BY re.created_at, re.id
                """).param("mappingId", mappingId)
                .query((rs, i) -> new ReviewEventView(rs.getObject("id", UUID.class), ReviewKind.valueOf(rs.getString("kind")),
                        rs.getString("note"), rs.getObject("user_id", UUID.class), rs.getString("user_display_name"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class)))
                .list();
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

    /** The newest event of a mapping: what the mapping's current state derives from (D-003). */
    public record LatestEvent(UUID id, ReviewKind kind) {
    }

    public Optional<LatestEvent> latest(UUID mappingId) {
        return jdbc.sql("SELECT id, kind FROM review_event WHERE mapping_id = :m ORDER BY created_at DESC, id DESC LIMIT 1")
                .param("m", mappingId)
                .query((rs, i) -> new LatestEvent(rs.getObject("id", UUID.class), ReviewKind.valueOf(rs.getString("kind"))))
                .optional();
    }
}
