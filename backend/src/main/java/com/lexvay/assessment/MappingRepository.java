package com.lexvay.assessment;

import com.lexvay.rule.NumericField;
import com.lexvay.rule.RequirementType;
import java.util.List;
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
                   es.page_number, es.char_start, es.char_end, es.quote
            FROM mapping m
            JOIN jurisdiction j ON j.id = m.jurisdiction_id
            JOIN requirement r ON r.id = m.requirement_id
            LEFT JOIN evidence_span es ON es.id = m.evidence_span_id
            LEFT JOIN document d ON d.id = es.document_id
            LEFT JOIN course_version cv ON cv.id = d.course_version_id
            LEFT JOIN course c ON c.id = cv.course_id
            """;

    private static final RowMapper<Mapping> ROW = (rs, i) -> {
        Mapping.Evidence evidence = null;
        if (rs.getObject("evidence_span_id", UUID.class) != null) {
            evidence = new Mapping.Evidence(rs.getObject("evidence_span_id", UUID.class), rs.getObject("document_id", UUID.class),
                    rs.getString("filename"), rs.getString("kind"), rs.getObject("course_version_id", UUID.class),
                    rs.getString("course_code"), rs.getObject("course_required", Boolean.class), rs.getInt("page_number"),
                    rs.getInt("char_start"), rs.getInt("char_end"), rs.getString("quote"));
        }
        return new Mapping(rs.getObject("id", UUID.class), rs.getObject("assessment_id", UUID.class),
                rs.getObject("jurisdiction_id", UUID.class), rs.getString("jurisdiction"), rs.getObject("rule_version_id", UUID.class),
                new Mapping.RequirementView(rs.getObject("requirement_id", UUID.class), rs.getString("ref"), rs.getString("title"),
                        rs.getString("text"), RequirementType.valueOf(rs.getString("type")),
                        rs.getString("numeric_field") == null ? null : NumericField.valueOf(rs.getString("numeric_field")),
                        rs.getString("numeric_operator"), rs.getBigDecimal("numeric_value")),
                Finding.valueOf(rs.getString("finding")), MappingMethod.valueOf(rs.getString("method")), rs.getString("rationale"),
                rs.getBoolean("stale"), evidence);
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

    public Optional<Mapping> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE m.id = :id").param("id", id).query(ROW).optional();
    }

    public List<Mapping> findByAssessmentAndJurisdiction(UUID assessmentId, String jurisdictionCode) {
        return jdbc.sql(SELECT + " WHERE m.assessment_id = :a AND j.code = :code ORDER BY r.created_at, r.ref")
                .param("a", assessmentId).param("code", jurisdictionCode).query(ROW).list();
    }

    public List<Mapping> findByAssessment(UUID assessmentId) {
        return jdbc.sql(SELECT + " WHERE m.assessment_id = :a ORDER BY j.code, r.created_at, r.ref")
                .param("a", assessmentId).query(ROW).list();
    }
}
