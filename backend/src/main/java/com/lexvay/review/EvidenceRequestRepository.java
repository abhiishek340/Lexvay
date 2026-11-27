package com.lexvay.review;

import com.lexvay.assessment.MappingRepository;
import com.lexvay.determination.DeterminationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read model of evidence requests, derived from review events (D-003) and evidence responses. One query, no N+1. */
@Repository
public class EvidenceRequestRepository {

    /**
     * le: the latest event, which decides the state. rq: the latest request. rs: the latest response on the
     * mapping with the count of all of them. A REOPENED carrying a user can only have been written by a
     * response (people cannot send REOPENED; system reopenings have no user), so ANSWERED means: latest event
     * is such a REOPENED and the latest response answers the latest request. {@code signed}: a signed
     * determination covers the mapping's assessment and jurisdiction (D-361), so an answer is refused.
     */
    private static final String SELECT = """
            SELECT * FROM (
                SELECT m.id AS mapping_id, j.code AS jurisdiction, r.ref, r.title,
                       CASE WHEN le.kind = 'EVIDENCE_REQUESTED' THEN 'OPEN'
                            WHEN le.kind = 'REOPENED' AND le.user_id IS NOT NULL AND rs.review_event_id = rq.id THEN 'ANSWERED'
                       END AS status, %s AS signed,
                       rq.note AS request_note, rq.user_id AS requested_by, ru.display_name AS requested_by_name,
                       rq.created_at AS requested_at,
                       coalesce(rs.total, 0) AS response_count, rs.id AS response_id, rs.created_by AS response_by,
                       rs.display_name AS response_by_name, rs.created_at AS response_at, rs.filename, rs.page_number
                FROM mapping m
                JOIN jurisdiction j ON j.id = m.jurisdiction_id
                JOIN requirement r ON r.id = m.requirement_id
                JOIN LATERAL (SELECT re.kind, re.user_id FROM review_event re WHERE re.mapping_id = m.id
                              ORDER BY re.created_at DESC, re.id DESC LIMIT 1) le ON true
                JOIN LATERAL (SELECT re.id, re.note, re.user_id, re.created_at FROM review_event re
                              WHERE re.mapping_id = m.id AND re.kind = 'EVIDENCE_REQUESTED'
                              ORDER BY re.created_at DESC, re.id DESC LIMIT 1) rq ON true
                LEFT JOIN app_user ru ON ru.id = rq.user_id
                LEFT JOIN LATERAL (SELECT er.id, er.review_event_id, er.created_by, er.created_at, u.display_name, d.filename,
                                          es.page_number, count(*) OVER () AS total
                                   FROM evidence_response er
                                   JOIN evidence_span es ON es.id = er.evidence_span_id
                                   JOIN document d ON d.id = es.document_id
                                   JOIN app_user u ON u.id = er.created_by
                                   WHERE er.mapping_id = m.id
                                   ORDER BY er.created_at DESC, er.id DESC LIMIT 1) rs ON true
                WHERE m.assessment_id = :assessmentId
            """.formatted(DeterminationRepository.signedFor("m.assessment_id", "m.jurisdiction_id"));

    private static final String FILTER = """
                ) q WHERE q.status IS NOT NULL AND (:status = 'ALL' OR q.status = :status)
                ORDER BY q.requested_at DESC, q.mapping_id
            """;

    private static final RowMapper<EvidenceRequestView> ROW = (rs, i) -> {
        EvidenceRequestView.LatestResponse latest = rs.getObject("response_id", UUID.class) == null ? null
                : new EvidenceRequestView.LatestResponse(rs.getObject("response_id", UUID.class),
                        rs.getObject("response_by", UUID.class), rs.getString("response_by_name"),
                        rs.getObject("response_at", java.time.OffsetDateTime.class), rs.getString("filename"), rs.getInt("page_number"));
        return new EvidenceRequestView(rs.getObject("mapping_id", UUID.class), rs.getString("jurisdiction"), rs.getString("ref"),
                rs.getString("title"), EvidenceRequestStatus.valueOf(rs.getString("status")), rs.getBoolean("signed"), rs.getString("request_note"),
                rs.getObject("requested_by", UUID.class), rs.getString("requested_by_name"),
                rs.getObject("requested_at", java.time.OffsetDateTime.class), rs.getInt("response_count"), latest);
    };

    private final JdbcClient jdbc;

    public EvidenceRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Current mappings only (D-066), newest request first. */
    public List<EvidenceRequestView> findByAssessment(UUID assessmentId, EvidenceRequestStatus status) {
        return jdbc.sql(SELECT + MappingRepository.current("= :assessmentId") + FILTER)
                .param("assessmentId", assessmentId).param("status", status.name()).query(ROW).list();
    }
}
