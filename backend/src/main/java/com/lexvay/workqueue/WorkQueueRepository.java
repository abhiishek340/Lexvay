package com.lexvay.workqueue;

import com.lexvay.assessment.MappingRepository;
import com.lexvay.auth.Role;
import com.lexvay.change.ChangeEventRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * The work queue is one statement: CTEs for the data several kinds share, then one {@code UNION ALL} branch per
 * {@link WorkQueueKind}. Each branch decides in SQL, from the bound {@code :role}, whether the caller sees it; a
 * branch the caller may not see has a constant-false filter, so PostgreSQL never executes it. The list and the
 * badge count differ only in the final {@code SELECT}.
 */
@Repository
public class WorkQueueRepository {

    /** Most ids any list in {@code ids} carries. */
    private static final int MAX_IDS = 20;

    /**
     * latest: each program's latest finished assessment, the one the signing gate reads.
     * current_mappings: its current (D-066) mappings with their latest review event (D-003).
     * affected: open change events with the programs they touch.
     */
    private static final String SHARED = """
            WITH latest AS (
                SELECT p.id AS program_id, p.name AS program_name, a.id AS assessment_id
                FROM program p
                JOIN LATERAL (SELECT a.id FROM assessment a WHERE a.program_id = p.id AND a.status = 'FINISHED'
                              ORDER BY a.finished_at DESC, a.created_at DESC LIMIT 1) a ON true
            ), current_mappings AS (
                SELECT l.program_id, l.program_name, l.assessment_id, m.jurisdiction_id, j.code AS jurisdiction_code,
                       m.id AS mapping_id, m.stale, m.created_at, le.kind AS last_kind, le.created_at AS last_at,
                       %1$s AS needs_decision, %2$s AS undecided
                FROM latest l
                JOIN mapping m ON m.assessment_id = l.assessment_id
                JOIN requirement r ON r.id = m.requirement_id
                JOIN jurisdiction j ON j.id = m.jurisdiction_id
                %3$s
                WHERE TRUE %4$s
            ), affected AS (
                SELECT ce.id AS change_id, ce.kind, p.id AS program_id
                FROM change_event ce
                LEFT JOIN rule_version rn ON ce.kind = 'RULE_VERSION' AND rn.id = ce.new_id
                LEFT JOIN course_version cn ON ce.kind = 'COURSE_VERSION' AND cn.id = ce.new_id
                LEFT JOIN course c ON c.id = cn.course_id
                JOIN program p ON %5$s
                WHERE ce.status = 'OPEN'
            )
            """.formatted(MappingRepository.NEEDS_DECISION, MappingRepository.UNDECIDED, MappingRepository.LATEST_EVENT,
            MappingRepository.CURRENT, ChangeEventRepository.AFFECTS_PROGRAM);

    /**
     * Current mappings without a decision, or reopened. Stale ones cannot be reviewed (the review endpoint
     * refuses them), so they wait under CHANGES_OPEN instead. The first branch names the columns of the union.
     */
    private static final String REQUIREMENTS_TO_REVIEW = """
            SELECT 1 AS ord, 'REQUIREMENTS_TO_REVIEW' AS kind, cm.program_id, cm.program_name, cm.jurisdiction_code,
                   count(*) AS item_count, min(coalesce(cm.last_at, cm.created_at)) AS oldest_at,
                   jsonb_build_object('assessmentId', cm.assessment_id, 'mappingIds', %s) AS ids
            FROM current_mappings cm
            WHERE CAST(:role AS text) IN ('REVIEWER', 'SIGNER') AND NOT cm.stale AND cm.needs_decision
            GROUP BY cm.program_id, cm.program_name, cm.jurisdiction_code, cm.assessment_id
            """.formatted(ids("cm.mapping_id", "coalesce(cm.last_at, cm.created_at), cm.mapping_id", null));

    /**
     * Mappings whose latest event is an evidence request. Stale ones cannot be answered until the change is
     * re-run, so they are only counted (blockedCount), and a group with nothing answerable is not listed.
     * Visible to every role: anyone may answer.
     */
    private static final String EVIDENCE_REQUESTS_OPEN = """
            SELECT 2, 'EVIDENCE_REQUESTS_OPEN', cm.program_id, cm.program_name, cm.jurisdiction_code,
                   count(*) FILTER (WHERE NOT cm.stale), min(cm.last_at) FILTER (WHERE NOT cm.stale),
                   jsonb_build_object('assessmentId', cm.assessment_id, 'mappingIds', %s,
                                      'blockedCount', count(*) FILTER (WHERE cm.stale))
            FROM current_mappings cm
            WHERE cm.last_kind = 'EVIDENCE_REQUESTED'
            GROUP BY cm.program_id, cm.program_name, cm.jurisdiction_code, cm.assessment_id
            HAVING count(*) FILTER (WHERE NOT cm.stale) > 0
            """.formatted(ids("cm.mapping_id", "cm.last_at, cm.mapping_id", "NOT cm.stale"));

    /** Rule versions whose extracted requirements wait for a reviewer's approval. */
    private static final String RULE_VERSIONS_TO_APPROVE = """
            SELECT 3, 'RULE_VERSIONS_TO_APPROVE', NULL, NULL, j.code, count(*), min(rv.created_at),
                   jsonb_build_object('ruleVersionIds', %s)
            FROM rule_version rv
            JOIN jurisdiction j ON j.id = rv.jurisdiction_id
            WHERE CAST(:role AS text) IN ('REVIEWER', 'SIGNER') AND rv.requirements_approved_at IS NULL
              AND EXISTS (SELECT 1 FROM requirement r WHERE r.rule_version_id = rv.id)
            GROUP BY j.code
            """.formatted(ids("rv.id", "rv.created_at, rv.id", null));

    /**
     * Rule versions whose extraction job failed and which have no requirements. A rule version has one extraction
     * job (a retry requeues the same row), so "a FAILED job" is also its latest job, as RuleVersionService reads it.
     * The status test uses the (status, created_at) index; the entity test is a filter on the few failed jobs.
     */
    private static final String EXTRACTION_FAILED = """
            SELECT 4, 'EXTRACTION_FAILED', NULL, NULL, j.code, count(*), min(rv.created_at),
                   jsonb_build_object('ruleVersionIds', %s)
            FROM rule_version rv
            JOIN jurisdiction j ON j.id = rv.jurisdiction_id
            WHERE CAST(:role AS text) IN ('ADMIN', 'REVIEWER')
              AND NOT EXISTS (SELECT 1 FROM requirement r WHERE r.rule_version_id = rv.id)
              AND EXISTS (SELECT 1 FROM job x WHERE x.status = 'FAILED' AND x.type = 'EXTRACT_REQUIREMENTS'
                          AND x.entity_id = rv.id)
            GROUP BY j.code
            """.formatted(ids("rv.id", "rv.created_at, rv.id", null));

    /** Open change events per change kind, with the programs they touch. */
    private static final String CHANGES_OPEN = """
            SELECT 5, 'CHANGES_OPEN', NULL, NULL, NULL, count(*), min(ce.created_at),
                   jsonb_build_object('changeKind', ce.kind, 'changeEventIds', %s,
                       'programIds', to_jsonb(coalesce((SELECT (array_agg(DISTINCT af.program_id))[1 : %d]
                                                        FROM affected af WHERE af.kind = ce.kind), ARRAY[]::uuid[])))
            FROM change_event ce
            WHERE CAST(:role AS text) IN ('REVIEWER', 'SIGNER', 'ADMIN') AND ce.status = 'OPEN'
            GROUP BY ce.kind
            """.formatted(ids("ce.id", "ce.created_at, ce.id", null), MAX_IDS);

    /**
     * Exactly what POST /determinations accepts (DeterminationService.sign): a target jurisdiction of the
     * program, at least one current mapping in its latest finished assessment, none undecided, and no signed
     * determination yet for the program and jurisdiction. Waiting since the last decision made it ready.
     */
    private static final String READY_TO_SIGN = """
            SELECT 6, 'READY_TO_SIGN', cm.program_id, cm.program_name, cm.jurisdiction_code, 1, max(cm.last_at),
                   jsonb_build_object('assessmentId', cm.assessment_id)
            FROM current_mappings cm
            WHERE CAST(:role AS text) = 'SIGNER'
              AND EXISTS (SELECT 1 FROM program_jurisdiction pj
                          WHERE pj.program_id = cm.program_id AND pj.jurisdiction_id = cm.jurisdiction_id)
              AND NOT EXISTS (SELECT 1 FROM determination d WHERE d.program_id = cm.program_id
                              AND d.jurisdiction_id = cm.jurisdiction_id AND d.status = 'SIGNED')
            GROUP BY cm.program_id, cm.program_name, cm.jurisdiction_id, cm.jurisdiction_code, cm.assessment_id
            HAVING count(*) FILTER (WHERE cm.undecided) = 0
            """;

    /** Assessments that failed, per program. */
    private static final String ASSESSMENTS_FAILED = """
            SELECT 7, 'ASSESSMENTS_FAILED', p.id, p.name, NULL, count(*), min(coalesce(a.finished_at, a.created_at)),
                   jsonb_build_object('assessmentIds', %s)
            FROM assessment a
            JOIN program p ON p.id = a.program_id
            WHERE CAST(:role AS text) = 'ADMIN' AND a.status = 'FAILED'
            GROUP BY p.id, p.name
            """.formatted(ids("a.id", "coalesce(a.finished_at, a.created_at), a.id", null));

    private static final String ITEMS = SHARED + ", items AS (\n" + String.join("UNION ALL\n", REQUIREMENTS_TO_REVIEW,
            EVIDENCE_REQUESTS_OPEN, RULE_VERSIONS_TO_APPROVE, EXTRACTION_FAILED, CHANGES_OPEN, READY_TO_SIGN, ASSESSMENTS_FAILED)
            + ")\n";

    /** The list statement, public so the index-usage check can run {@code EXPLAIN} on exactly what the application runs. */
    public static final String LIST = ITEMS + """
            SELECT kind, program_id, program_name, jurisdiction_code, item_count, oldest_at, CAST(ids AS text) AS ids
            FROM items ORDER BY ord, oldest_at, program_name, jurisdiction_code
            """;

    private static final String TOTAL = ITEMS + "SELECT coalesce(sum(item_count), 0) FROM items";

    private final JdbcClient jdbc;
    private final RowMapper<WorkItem> row;

    public WorkQueueRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.row = (rs, i) -> new WorkItem(WorkQueueKind.valueOf(rs.getString("kind")), rs.getObject("program_id", UUID.class),
                rs.getString("program_name"), rs.getString("jurisdiction_code"), rs.getLong("item_count"),
                rs.getObject("oldest_at", OffsetDateTime.class), json.readTree(rs.getString("ids")));
    }

    /** The items the role sees, kinds in {@link WorkQueueKind} order, then the longest-waiting first. */
    public List<WorkItem> items(Role role) {
        return jdbc.sql(LIST).param("role", role.name()).query(row).list();
    }

    /** The sum of the counts of {@link #items}. */
    public long total(Role role) {
        return jdbc.sql(TOTAL).param("role", role.name()).query(Long.class).single();
    }

    /**
     * SQL for a JSON array of the first {@value #MAX_IDS} ids of a group, in the given order, optionally filtered.
     * The slice is written {@code [1 : n]} because Spring reads {@code :n} as a named parameter.
     */
    private static String ids(String id, String orderBy, String filter) {
        return "to_jsonb((array_agg(" + id + " ORDER BY " + orderBy + ")"
                + (filter == null ? "" : " FILTER (WHERE " + filter + ")") + ")[1 : " + MAX_IDS + "])";
    }
}
