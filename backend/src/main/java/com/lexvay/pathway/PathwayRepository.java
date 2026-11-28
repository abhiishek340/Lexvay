package com.lexvay.pathway;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The pathway read is one statement: the groups a person has not retired, each with its members and the credits of the
 * course version in force, as one JSON document. {@link #ACTIVE_GROUPS} is a common table expression that other
 * statements embed (the coverage matrix does), so that they read the pathway in the round trip they already make.
 */
@Repository
public class PathwayRepository {

    /**
     * {@code pathway}: one row per active group of program {@code :program} in the catalog year of the one row of
     * {@code pathway_year}, which the statement that embeds this defines (and which is empty for an unknown program).
     * A member's credits are those of the course's version of that catalog year, else of its newest version.
     * Ordered by {@code position}: ALL_OF first, then by label.
     */
    public static final String ACTIVE_GROUPS = """
            pathway AS (
                SELECT row_number() OVER (ORDER BY g.kind <> 'ALL_OF', g.label, g.id) AS position,
                       jsonb_build_object(
                           'id', g.id, 'label', g.label, 'kind', g.kind, 'minCount', g.min_count, 'minCredits', g.min_credits,
                           'citation', CASE WHEN es.id IS NULL THEN NULL ELSE jsonb_build_object(
                               'spanId', es.id, 'documentId', es.document_id, 'filename', d.filename,
                               'pageNumber', es.page_number, 'quote', es.quote) END,
                           'members', coalesce((SELECT jsonb_agg(jsonb_build_object(
                                       'courseId', c.id, 'code', c.code, 'title', c.title,
                                       'credits', v.credits, 'creditUnit', coalesce(v.credit_unit, 'SEMESTER')) ORDER BY c.code)
                                   FROM pathway_group_course gc
                                   JOIN course c ON c.id = gc.course_id
                                   LEFT JOIN LATERAL (SELECT cv.credits, cv.credit_unit FROM course_version cv
                                                      WHERE cv.course_id = c.id
                                                      ORDER BY cv.catalog_year = g.catalog_year DESC, cv.created_at DESC LIMIT 1) v ON true
                                   WHERE gc.group_id = g.id), '[]')) AS doc
                FROM pathway_year y
                JOIN pathway_group g ON g.program_id = :program AND g.catalog_year = y.year
                LEFT JOIN evidence_span es ON es.id = g.evidence_span_id
                LEFT JOIN document d ON d.id = es.document_id
                WHERE NOT EXISTS (SELECT 1 FROM pathway_group_retirement r WHERE r.group_id = g.id)
            )""";

    /** The groups as one JSON array, in {@code position} order; to be selected from after {@link #ACTIVE_GROUPS}. */
    public static final String GROUPS_AS_JSON = "coalesce((SELECT jsonb_agg(doc ORDER BY position) FROM pathway), '[]')";

    /** One row, and only when the program is visible to the tenant: the catalog year read and its groups. Public so the index-usage check can run {@code EXPLAIN} on it. */
    public static final String READ = "WITH pathway_year AS (SELECT coalesce(CAST(:catalogYear AS text), p.catalog_year) AS year "
            + "FROM program p WHERE p.id = :program), " + ACTIVE_GROUPS
            + " SELECT (SELECT year FROM pathway_year) AS catalog_year, " + GROUPS_AS_JSON + " AS groups FROM pathway_year";

    private static final String GROUP = """
            SELECT g.id, g.program_id, g.catalog_year, g.label, EXISTS (SELECT 1 FROM pathway_group_retirement r WHERE r.group_id = g.id) AS retired
            FROM pathway_group g WHERE g.id = :id""";

    private static final TypeReference<List<PathwayGroup>> GROUPS = new TypeReference<>() { };

    /** The groups of one catalog year of a program. */
    public record Read(String catalogYear, List<PathwayGroup> groups) {
    }

    /** A stored group, for retiring it. */
    public record Stored(UUID id, UUID programId, String catalogYear, String label, boolean retired) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public PathwayRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Empty when the program does not exist or belongs to another tenant (row-level security). {@code catalogYear} null reads the program's own. */
    public Optional<Read> read(UUID programId, String catalogYear) {
        return jdbc.sql(READ).param("program", programId).param("catalogYear", catalogYear)
                .query((rs, i) -> new Read(rs.getString("catalog_year"), json.readValue(rs.getString("groups"), GROUPS))).optional();
    }

    public void insertGroup(UUID id, UUID tenantId, UUID programId, String catalogYear, String label, GroupKind kind, Integer minCount,
                            BigDecimal minCredits, UUID evidenceSpanId, UUID createdBy) {
        jdbc.sql("INSERT INTO pathway_group (id, tenant_id, program_id, catalog_year, label, kind, min_count, min_credits, evidence_span_id, created_by) "
                        + "VALUES (:id, :tenantId, :programId, :catalogYear, :label, :kind, :minCount, :minCredits, :evidenceSpanId, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("programId", programId).param("catalogYear", catalogYear)
                .param("label", label).param("kind", kind.name()).param("minCount", minCount).param("minCredits", minCredits)
                .param("evidenceSpanId", evidenceSpanId).param("createdBy", createdBy).update();
    }

    public void insertMembers(UUID groupId, UUID tenantId, Collection<UUID> courseIds) {
        for (UUID courseId : courseIds) {
            jdbc.sql("INSERT INTO pathway_group_course (group_id, course_id, tenant_id) VALUES (:groupId, :courseId, :tenantId)")
                    .param("groupId", groupId).param("courseId", courseId).param("tenantId", tenantId).update();
        }
    }

    /** Of the given courses, those that belong to the program. */
    public Set<UUID> coursesOfProgram(UUID programId, Collection<UUID> courseIds) {
        return jdbc.sql("SELECT id FROM course WHERE program_id = :programId AND id IN (:ids)")
                .param("programId", programId).param("ids", courseIds).query(UUID.class).set();
    }

    /** The active group of the program and catalog year with this label and kind, if a person has not retired it. */
    public Optional<UUID> findActiveGroup(UUID programId, String catalogYear, String label, GroupKind kind) {
        return jdbc.sql("SELECT g.id FROM pathway_group g WHERE g.program_id = :programId AND g.catalog_year = :catalogYear "
                        + "AND g.label = :label AND g.kind = :kind AND NOT EXISTS (SELECT 1 FROM pathway_group_retirement r WHERE r.group_id = g.id) "
                        + "ORDER BY g.created_at, g.id LIMIT 1")
                .param("programId", programId).param("catalogYear", catalogYear).param("label", label).param("kind", kind.name())
                .query(UUID.class).optional();
    }

    /** Whether an active group of the program and catalog year already holds the course. */
    public boolean isPlaced(UUID programId, String catalogYear, UUID courseId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM pathway_group g JOIN pathway_group_course gc ON gc.group_id = g.id "
                        + "WHERE g.program_id = :programId AND g.catalog_year = :catalogYear AND gc.course_id = :courseId "
                        + "AND NOT EXISTS (SELECT 1 FROM pathway_group_retirement r WHERE r.group_id = g.id))")
                .param("programId", programId).param("catalogYear", catalogYear).param("courseId", courseId)
                .query(Boolean.class).single();
    }

    public Optional<Stored> findGroup(UUID id) {
        return jdbc.sql(GROUP).param("id", id).query((rs, i) -> new Stored(rs.getObject("id", UUID.class),
                rs.getObject("program_id", UUID.class), rs.getString("catalog_year"), rs.getString("label"),
                rs.getBoolean("retired"))).optional();
    }

    public void retire(UUID groupId, UUID tenantId, UUID retiredBy, String reason) {
        jdbc.sql("INSERT INTO pathway_group_retirement (group_id, tenant_id, retired_by, reason) VALUES (:groupId, :tenantId, :retiredBy, :reason)")
                .param("groupId", groupId).param("tenantId", tenantId).param("retiredBy", retiredBy).param("reason", reason).update();
    }

    /** The groups of the program's own catalog year, for an assessment; empty when the program is not visible. */
    public List<PathwayGroup> activeGroups(UUID programId) {
        return read(programId, null).map(Read::groups).orElse(List.of());
    }
}
