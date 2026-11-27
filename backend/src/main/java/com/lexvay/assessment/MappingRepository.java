package com.lexvay.assessment;

import com.lexvay.document.TextSource;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.RequirementType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MappingRepository {

    private static final String SELECT = """
            SELECT m.id, m.assessment_id, m.jurisdiction_id, j.code AS jurisdiction, r.rule_version_id,
                   r.id AS requirement_id, r.ref, r.title, r.text, r.type, r.numeric_field, r.numeric_operator, r.numeric_value,
                   m.finding, m.method, m.rationale, m.stale, m.evidence_span_id,
                   es.document_id, d.filename, d.kind, d.course_version_id, c.code AS course_code, cv.required AS course_required,
                   es.page_number, es.char_start, es.char_end, es.quote, dp.text_source, CAST(dp.ocr_confidence AS double precision) AS ocr_confidence,
                   le.kind AS decision_kind, le.note AS decision_note, le.user_id AS decision_user_id,
                   le.created_at AS decision_at, u.display_name AS decision_user_name
            FROM mapping m
            JOIN jurisdiction j ON j.id = m.jurisdiction_id
            JOIN requirement r ON r.id = m.requirement_id
            LEFT JOIN evidence_span es ON es.id = m.evidence_span_id
            LEFT JOIN document d ON d.id = es.document_id
            LEFT JOIN document_page dp ON dp.document_id = es.document_id AND dp.page_number = es.page_number
            LEFT JOIN course_version cv ON cv.id = d.course_version_id
            LEFT JOIN course c ON c.id = cv.course_id
            LEFT JOIN LATERAL (
                SELECT re.kind, re.note, re.user_id, re.created_at FROM review_event re
                WHERE re.mapping_id = m.id ORDER BY re.created_at DESC, re.id DESC LIMIT 1) le ON true
            LEFT JOIN app_user u ON u.id = le.user_id
            """;

    /**
     * D-066: a mapping is "current" unless its rule version has a successor whose mappings already exist in
     * the same assessment. After a re-run the new list shows; until then the old list stays reviewable.
     */
    public static final String CURRENT = """
             AND NOT EXISTS (
                SELECT 1 FROM rule_version nv
                JOIN requirement nr ON nr.rule_version_id = nv.id
                JOIN mapping nm ON nm.requirement_id = nr.id
                WHERE nv.supersedes_id = r.rule_version_id AND nm.assessment_id = m.assessment_id)
            """;

    private static final RowMapper<Mapping> ROW = (rs, i) -> {
        Mapping.Evidence evidence = null;
        if (rs.getObject("evidence_span_id", UUID.class) != null) {
            evidence = new Mapping.Evidence(rs.getObject("evidence_span_id", UUID.class), rs.getObject("document_id", UUID.class),
                    rs.getString("filename"), rs.getString("kind"), rs.getObject("course_version_id", UUID.class),
                    rs.getString("course_code"), rs.getObject("course_required", Boolean.class), rs.getInt("page_number"),
                    rs.getInt("char_start"), rs.getInt("char_end"), rs.getString("quote"),
                    TextSource.valueOf(rs.getString("text_source")), rs.getObject("ocr_confidence", Double.class));
        }
        Mapping.Decision decision = null;
        String decisionKind = rs.getString("decision_kind");
        if (decisionKind != null && !decisionKind.equals("REOPENED")) {
            decision = new Mapping.Decision(decisionKind, rs.getString("decision_note"), rs.getObject("decision_user_id", UUID.class),
                    rs.getString("decision_user_name"), rs.getObject("decision_at", java.time.OffsetDateTime.class));
        }
        return new Mapping(rs.getObject("id", UUID.class), rs.getObject("assessment_id", UUID.class),
                rs.getObject("jurisdiction_id", UUID.class), rs.getString("jurisdiction"), rs.getObject("rule_version_id", UUID.class),
                new Mapping.RequirementView(rs.getObject("requirement_id", UUID.class), rs.getString("ref"), rs.getString("title"),
                        rs.getString("text"), RequirementType.valueOf(rs.getString("type")),
                        rs.getString("numeric_field") == null ? null : NumericField.valueOf(rs.getString("numeric_field")),
                        rs.getString("numeric_operator"), rs.getBigDecimal("numeric_value")),
                Finding.valueOf(rs.getString("finding")), MappingMethod.valueOf(rs.getString("method")), rs.getString("rationale"),
                rs.getBoolean("stale"), evidence, decision);
    };

    private final JdbcClient jdbc;

    public MappingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Idempotent: a retried run never duplicates or overwrites a mapping. Returns true when inserted. */
    public boolean insertIfAbsent(UUID tenantId, UUID assessmentId, UUID requirementId, UUID jurisdictionId, Finding finding,
                                  MappingMethod method, String rationale, UUID evidenceSpanId, UUID createdBy) {
        return jdbc.sql("INSERT INTO mapping (tenant_id, assessment_id, requirement_id, jurisdiction_id, finding, method, rationale, evidence_span_id, created_by) "
                        + "VALUES (:tenantId, :assessmentId, :requirementId, :jurisdictionId, :finding, :method, :rationale, :evidenceSpanId, :createdBy) "
                        + "ON CONFLICT (assessment_id, requirement_id) DO NOTHING")
                .param("tenantId", tenantId).param("assessmentId", assessmentId).param("requirementId", requirementId)
                .param("jurisdictionId", jurisdictionId).param("finding", finding.name()).param("method", method.name())
                .param("rationale", rationale).param("evidenceSpanId", evidenceSpanId).param("createdBy", createdBy)
                .update() == 1;
    }

    public boolean exists(UUID assessmentId, UUID requirementId) {
        return jdbc.sql("SELECT count(*) FROM mapping WHERE assessment_id = :a AND requirement_id = :r")
                .param("a", assessmentId).param("r", requirementId).query(Long.class).single() > 0;
    }

    public boolean existsById(UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM mapping WHERE id = :id)").param("id", id).query(Boolean.class).single();
    }

    public Optional<Mapping> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE m.id = :id").param("id", id).query(ROW).optional();
    }

    public List<Mapping> findByAssessmentAndJurisdiction(UUID assessmentId, String jurisdictionCode) {
        return jdbc.sql(SELECT + " WHERE m.assessment_id = :a AND j.code = :code" + CURRENT + " ORDER BY r.ordinal")
                .param("a", assessmentId).param("code", jurisdictionCode).query(ROW).list();
    }

    /** Mappings of one jurisdiction that block signing: stale, or without a current decision. */
    public long countUndecided(UUID assessmentId, UUID jurisdictionId) {
        return jdbc.sql("""
                SELECT count(*) FROM mapping m JOIN requirement r ON r.id = m.requirement_id
                LEFT JOIN LATERAL (SELECT re.kind FROM review_event re WHERE re.mapping_id = m.id
                                   ORDER BY re.created_at DESC, re.id DESC LIMIT 1) le ON true
                WHERE m.assessment_id = :a AND m.jurisdiction_id = :j
                  AND (m.stale OR le.kind IS NULL OR le.kind = 'REOPENED')
                """ + CURRENT).param("a", assessmentId).param("j", jurisdictionId).query(Long.class).single();
    }

    public long countByJurisdiction(UUID assessmentId, UUID jurisdictionId) {
        return jdbc.sql("SELECT count(*) FROM mapping m JOIN requirement r ON r.id = m.requirement_id WHERE m.assessment_id = :a AND m.jurisdiction_id = :j" + CURRENT)
                .param("a", assessmentId).param("j", jurisdictionId).query(Long.class).single();
    }

    /** Lookup without the current-mappings filter, for change handling. */
    public Optional<Mapping> findByAssessmentAndRequirement(UUID assessmentId, UUID requirementId) {
        return jdbc.sql(SELECT + " WHERE m.assessment_id = :a AND m.requirement_id = :r")
                .param("a", assessmentId).param("r", requirementId).query(ROW).optional();
    }

    public int markStaleByRuleVersion(UUID ruleVersionId) {
        return jdbc.sql("UPDATE mapping SET stale = true WHERE requirement_id IN (SELECT id FROM requirement WHERE rule_version_id = :rv)")
                .param("rv", ruleVersionId).update();
    }

    public int markStaleByCourseVersion(UUID courseVersionId) {
        return jdbc.sql("""
                UPDATE mapping SET stale = true WHERE evidence_span_id IN (
                    SELECT es.id FROM evidence_span es JOIN document d ON d.id = es.document_id WHERE d.course_version_id = :cv)
                """).param("cv", courseVersionId).update();
    }

    public List<Mapping> findStaleCitingCourseVersion(UUID courseVersionId) {
        return jdbc.sql(SELECT + " WHERE m.stale AND d.course_version_id = :cv ORDER BY m.created_at")
                .param("cv", courseVersionId).query(ROW).list();
    }

    public void updateEvidence(UUID mappingId, UUID evidenceSpanId) {
        jdbc.sql("UPDATE mapping SET evidence_span_id = :es, stale = false WHERE id = :id")
                .param("es", evidenceSpanId).param("id", mappingId).update();
    }

    public void updateToMissing(UUID mappingId, String rationale) {
        jdbc.sql("UPDATE mapping SET finding = 'EVIDENCE_MISSING', evidence_span_id = NULL, rationale = :r, stale = false WHERE id = :id")
                .param("r", rationale).param("id", mappingId).update();
    }

    /** Latest finished assessment per program that holds mappings for the given rule version. */
    public List<Map<String, Object>> latestAssessmentsUsing(UUID ruleVersionId) {
        return jdbc.sql("""
                SELECT DISTINCT a.id AS assessment_id, a.program_id, a.created_by
                FROM mapping m JOIN requirement r ON r.id = m.requirement_id JOIN assessment a ON a.id = m.assessment_id
                WHERE r.rule_version_id = :rv AND a.status = 'FINISHED'
                  AND a.id = (SELECT a2.id FROM assessment a2 WHERE a2.program_id = a.program_id AND a2.status = 'FINISHED'
                              ORDER BY a2.finished_at DESC, a2.created_at DESC LIMIT 1)
                """).param("rv", ruleVersionId).query().listOfRows();
    }

    public List<Mapping> findByAssessment(UUID assessmentId) {
        return jdbc.sql(SELECT + " WHERE m.assessment_id = :a" + CURRENT + " ORDER BY j.code, r.ordinal")
                .param("a", assessmentId).query(ROW).list();
    }
}
