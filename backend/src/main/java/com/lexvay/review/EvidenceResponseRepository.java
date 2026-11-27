package com.lexvay.review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Append only (product rule 5); the database rejects updates and deletes. */
@Repository
public class EvidenceResponseRepository {

    private static final String SELECT = """
            SELECT er.id, er.review_event_id, es.document_id, d.filename, d.kind, c.code AS course_code,
                   cv.required AS course_required, es.page_number, es.char_start, es.char_end, es.quote,
                   er.comment, er.created_by, u.display_name, er.created_at
            FROM evidence_response er
            JOIN evidence_span es ON es.id = er.evidence_span_id
            JOIN document d ON d.id = es.document_id
            LEFT JOIN course_version cv ON cv.id = d.course_version_id
            LEFT JOIN course c ON c.id = cv.course_id
            JOIN app_user u ON u.id = er.created_by
            """;

    private static final RowMapper<EvidenceResponseView> ROW = (rs, i) -> new EvidenceResponseView(
            rs.getObject("id", UUID.class), rs.getObject("review_event_id", UUID.class), rs.getObject("document_id", UUID.class),
            rs.getString("filename"), rs.getString("kind"), rs.getString("course_code"),
            rs.getObject("course_required", Boolean.class), rs.getInt("page_number"), rs.getInt("char_start"),
            rs.getInt("char_end"), rs.getString("quote"), rs.getString("comment"), rs.getObject("created_by", UUID.class),
            rs.getString("display_name"), rs.getObject("created_at", java.time.OffsetDateTime.class));

    private final JdbcClient jdbc;

    public EvidenceResponseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID tenantId, UUID mappingId, UUID reviewEventId, UUID evidenceSpanId, String comment, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO evidence_response (id, tenant_id, mapping_id, review_event_id, evidence_span_id, comment, created_by) "
                        + "VALUES (:id, :tenantId, :mappingId, :reviewEventId, :spanId, :comment, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("mappingId", mappingId).param("reviewEventId", reviewEventId)
                .param("spanId", evidenceSpanId).param("comment", comment).param("createdBy", createdBy).update();
        return id;
    }

    public Optional<EvidenceResponseView> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE er.id = :id").param("id", id).query(ROW).optional();
    }

    /** Newest first. */
    public List<EvidenceResponseView> findByMapping(UUID mappingId) {
        return jdbc.sql(SELECT + " WHERE er.mapping_id = :m ORDER BY er.created_at DESC, er.id DESC")
                .param("m", mappingId).query(ROW).list();
    }
}
