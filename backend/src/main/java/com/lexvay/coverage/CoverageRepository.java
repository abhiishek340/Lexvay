package com.lexvay.coverage;

import com.lexvay.assessment.AssessmentRepository;
import com.lexvay.assessment.MappingRepository;
import com.lexvay.program.CourseRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The coverage matrix is one statement that returns one row: the target check, then the page's parts as JSON
 * built from CTEs, so a request never costs more than one round trip (D-110).
 */
@Repository
public class CoverageRepository {

    /** The matrix, and whether the jurisdiction is a target of the program (when not, nothing else is read). */
    public record Found(boolean target, CoverageMatrix matrix) {
    }

    /**
     * target: the program (none when it is not visible to the tenant) and the jurisdiction, which may be unknown.
     * latest: the program's latest finished assessment, read only for a target jurisdiction.
     * mappings: its current (D-066) mappings for the jurisdiction with the latest review decision (D-003).
     * courses: the program's current course versions. evidence: the mapping's own verified span (SYSTEM) and every
     * evidence response (PERSON); numeric requirements checked by the rules engine have none, their span is a
     * program fact. cited: the documents of that evidence with their course version and the current version of that
     * course. cell_rows, outdated_rows, program_rows and reach: the cited evidence per mapping, as cells of current
     * course versions, as cells of superseded ones (D-394), as program-level documents, and counted for the risk flag. Public so the index-usage check can run {@code EXPLAIN} on exactly this text.
     */
    public static final String MATRIX = """
            WITH target AS (
                SELECT p.id AS program_id, p.name AS program_name, j.id AS jurisdiction_id, j.code AS jurisdiction_code, j.name AS jurisdiction_name,
                       j.id IS NOT NULL AND EXISTS (SELECT 1 FROM program_jurisdiction pj
                                                    WHERE pj.program_id = p.id AND pj.jurisdiction_id = j.id) AS is_target
                FROM program p
                LEFT JOIN jurisdiction j ON j.code = :jurisdiction
                WHERE p.id = :program
            ), latest AS (
                SELECT a.id FROM assessment a
                WHERE a.program_id = :program AND (SELECT is_target FROM target) AND %1$s
            ), mappings AS (
                SELECT m.id, m.method, m.finding, m.stale, m.evidence_span_id, r.ref, r.title, r.type, r.ordinal,
                       rv.id AS rule_version_id, rv.label AS rule_version_label,
                       CASE WHEN le.kind = 'REOPENED' THEN NULL ELSE le.kind END AS decision
                FROM mapping m
                JOIN requirement r ON r.id = m.requirement_id
                JOIN rule_version rv ON rv.id = r.rule_version_id
                %2$s
                WHERE m.assessment_id = (SELECT id FROM latest) AND m.jurisdiction_id = (SELECT jurisdiction_id FROM target)
                %3$s
            ), courses AS (
                SELECT c.id AS course_id, cv.id AS course_version_id, c.code, c.title, cv.catalog_year, cv.required
                FROM course c
                JOIN course_version cv ON cv.course_id = c.id
                WHERE c.program_id = :program AND NOT %4$s
            ), evidence AS (
                SELECT m.id AS mapping_id, 'SYSTEM' AS source, m.evidence_span_id AS span_id
                FROM mappings m WHERE m.method = 'MATCH' AND m.evidence_span_id IS NOT NULL
                UNION ALL
                SELECT er.mapping_id, 'PERSON', er.evidence_span_id
                FROM evidence_response er JOIN mappings m ON m.id = er.mapping_id WHERE m.method = 'MATCH'
            ), cited AS (
                SELECT e.mapping_id, e.source, es.id AS span_id, d.id AS document_id, d.filename, d.kind,
                       cv.id AS course_version_id, c.code, cv.catalog_year, cv.required, cur.course_version_id AS current_version_id
                FROM evidence e
                JOIN evidence_span es ON es.id = e.span_id
                JOIN document d ON d.id = es.document_id
                LEFT JOIN course_version cv ON cv.id = d.course_version_id
                LEFT JOIN course c ON c.id = cv.course_id
                LEFT JOIN courses cur ON cur.course_id = cv.course_id
            ), cell_rows AS (
                SELECT x.mapping_id,
                       jsonb_agg(jsonb_build_object('courseVersionId', x.course_version_id, 'source', x.source, 'quoteCount', x.quote_count)
                                 ORDER BY NOT x.required, x.code, x.source = 'PERSON') AS cells
                FROM (SELECT mapping_id, course_version_id, code, required, source, count(DISTINCT span_id) AS quote_count
                      FROM cited WHERE course_version_id = current_version_id
                      GROUP BY mapping_id, course_version_id, code, required, source) x
                GROUP BY x.mapping_id
            ), outdated_rows AS (
                SELECT x.mapping_id,
                       jsonb_agg(jsonb_build_object('courseVersionId', x.course_version_id, 'code', x.code, 'catalogYear', x.catalog_year,
                                                    'supersededBy', x.current_version_id, 'source', x.source, 'quoteCount', x.quote_count)
                                 ORDER BY x.code, x.catalog_year, x.source = 'PERSON') AS cells
                FROM (SELECT mapping_id, course_version_id, code, catalog_year, current_version_id, source, count(DISTINCT span_id) AS quote_count
                      FROM cited WHERE course_version_id <> current_version_id
                      GROUP BY mapping_id, course_version_id, code, catalog_year, current_version_id, source) x
                GROUP BY x.mapping_id
            ), program_rows AS (
                SELECT x.mapping_id,
                       jsonb_agg(jsonb_build_object('documentId', x.document_id, 'filename', x.filename, 'kind', x.kind, 'source', x.source)
                                 ORDER BY x.filename, x.document_id, x.source = 'PERSON') AS documents
                FROM (SELECT DISTINCT mapping_id, document_id, filename, kind, source FROM cited WHERE course_version_id IS NULL) x
                GROUP BY x.mapping_id
            ), reach AS (
                SELECT mapping_id, count(DISTINCT course_version_id) FILTER (WHERE course_version_id = current_version_id) AS course_count,
                       count(DISTINCT course_version_id) FILTER (WHERE course_version_id = current_version_id AND required) AS required_count,
                       count(*) FILTER (WHERE course_version_id IS NULL) AS program_count,
                       count(*) FILTER (WHERE course_version_id <> current_version_id) AS outdated_count
                FROM cited GROUP BY mapping_id
            )
            SELECT t.program_id, t.program_name, t.jurisdiction_code, t.jurisdiction_name, t.is_target,
                   (SELECT id FROM latest) AS assessment_id,
                   (SELECT jsonb_build_object('id', x.rule_version_id, 'label', x.rule_version_label)
                    FROM mappings x ORDER BY x.ordinal LIMIT 1) AS rule_version,
                   coalesce((SELECT jsonb_agg(jsonb_build_object('courseId', c.course_id, 'courseVersionId', c.course_version_id,
                                                                  'code', c.code, 'title', c.title, 'catalogYear', c.catalog_year,
                                                                  'required', c.required) ORDER BY NOT c.required, c.code)
                             FROM courses c), '[]') AS courses,
                   coalesce((SELECT jsonb_agg(jsonb_build_object(
                                 'mappingId', m.id, 'ref', m.ref, 'title', m.title, 'type', m.type, 'finding', m.finding,
                                 'decision', m.decision,
                                 'cells', coalesce(cr.cells, '[]'), 'outdatedCells', coalesce(orr.cells, '[]'), 'programLevel', coalesce(pr.documents, '[]'),
                                 'risk', CASE WHEN m.method = 'RULES_ENGINE' THEN 'NONE'
                                              WHEN m.stale OR rc.outdated_count > 0 THEN 'OUTDATED_EVIDENCE'
                                              WHEN rc.mapping_id IS NULL THEN 'NO_EVIDENCE'
                                              WHEN rc.program_count > 0 THEN 'NONE'
                                              WHEN rc.required_count = 0 THEN 'ELECTIVE_ONLY'
                                              WHEN rc.course_count = 1 THEN 'SINGLE_COURSE'
                                              ELSE 'NONE' END) ORDER BY m.ordinal)
                             FROM mappings m
                             LEFT JOIN cell_rows cr ON cr.mapping_id = m.id
                             LEFT JOIN outdated_rows orr ON orr.mapping_id = m.id
                             LEFT JOIN program_rows pr ON pr.mapping_id = m.id
                             LEFT JOIN reach rc ON rc.mapping_id = m.id), '[]') AS requirements
            FROM target t
            """.formatted(AssessmentRepository.LATEST_FINISHED, MappingRepository.LATEST_EVENT,
            MappingRepository.current("= (SELECT id FROM latest)"), CourseRepository.SUPERSEDED);

    private static final TypeReference<List<CoverageMatrix.Course>> COURSES = new TypeReference<>() { };
    private static final TypeReference<List<CoverageMatrix.Requirement>> REQUIREMENTS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final RowMapper<Found> row;

    public CoverageRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.row = (rs, i) -> new Found(rs.getBoolean("is_target"), new CoverageMatrix(
                new CoverageMatrix.ProgramRef(rs.getObject("program_id", UUID.class), rs.getString("program_name")),
                new CoverageMatrix.JurisdictionRef(rs.getString("jurisdiction_code"), rs.getString("jurisdiction_name")),
                rs.getObject("assessment_id", UUID.class),
                rs.getString("rule_version") == null ? null : json.readValue(rs.getString("rule_version"), CoverageMatrix.RuleVersionRef.class),
                json.readValue(rs.getString("courses"), COURSES), json.readValue(rs.getString("requirements"), REQUIREMENTS)));
    }

    /** Empty when the program does not exist or belongs to another tenant (row-level security). */
    public Optional<Found> find(UUID programId, String jurisdiction) {
        return jdbc.sql(MATRIX).param("program", programId).param("jurisdiction", jurisdiction).query(row).optional();
    }
}
