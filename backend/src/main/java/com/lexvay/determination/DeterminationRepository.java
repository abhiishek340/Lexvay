package com.lexvay.determination;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DeterminationRepository {

    private static final String SELECT = """
            SELECT d.id, d.program_id, j.code AS jurisdiction, d.jurisdiction_id, d.rule_version_id, rv.label AS rule_version_label,
                   d.assessment_id, d.outcome, d.signed_by, u.display_name AS signed_by_name, d.signed_at, d.status, d.versions_hash,
                   ARRAY(SELECT dcv.course_version_id FROM determination_course_version dcv
                         WHERE dcv.determination_id = d.id ORDER BY dcv.course_version_id) AS course_version_ids
            FROM determination d
            JOIN jurisdiction j ON j.id = d.jurisdiction_id
            JOIN rule_version rv ON rv.id = d.rule_version_id
            JOIN app_user u ON u.id = d.signed_by
            """;

    private final JdbcClient jdbc;

    public DeterminationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, UUID programId, UUID jurisdictionId, UUID ruleVersionId, UUID assessmentId,
                       Outcome outcome, UUID signedBy, String versionsHash, List<UUID> courseVersionIds) {
        jdbc.sql("INSERT INTO determination (id, tenant_id, program_id, jurisdiction_id, rule_version_id, assessment_id, outcome, signed_by, versions_hash, created_by) "
                        + "VALUES (:id, :tenantId, :programId, :jurisdictionId, :ruleVersionId, :assessmentId, :outcome, :signedBy, :hash, :signedBy)")
                .param("id", id).param("tenantId", tenantId).param("programId", programId).param("jurisdictionId", jurisdictionId)
                .param("ruleVersionId", ruleVersionId).param("assessmentId", assessmentId).param("outcome", outcome.name())
                .param("signedBy", signedBy).param("hash", versionsHash).update();
        for (UUID courseVersionId : courseVersionIds) {
            jdbc.sql("INSERT INTO determination_course_version (determination_id, course_version_id, tenant_id, created_by) "
                            + "VALUES (:d, :cv, :tenantId, :by)")
                    .param("d", id).param("cv", courseVersionId).param("tenantId", tenantId).param("by", signedBy).update();
        }
    }

    public boolean hasSigned(UUID assessmentId, UUID jurisdictionId) {
        return jdbc.sql("SELECT count(*) FROM determination WHERE assessment_id = :a AND jurisdiction_id = :j AND status = 'SIGNED'")
                .param("a", assessmentId).param("j", jurisdictionId).query(Long.class).single() > 0;
    }

    public boolean hasSignedForProgram(UUID programId, UUID jurisdictionId) {
        return jdbc.sql("SELECT count(*) FROM determination WHERE program_id = :p AND jurisdiction_id = :j AND status = 'SIGNED'")
                .param("p", programId).param("j", jurisdictionId).query(Long.class).single() > 0;
    }

    public List<Determination> findByProgram(UUID programId) {
        return jdbc.sql(SELECT + " WHERE d.program_id = :p ORDER BY d.signed_at DESC, d.id DESC")
                .param("p", programId).query(this::map).list();
    }

    public java.util.Optional<Determination> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE d.id = :id").param("id", id).query(this::map).optional();
    }

    private Determination map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        List<UUID> courseVersions = List.of((UUID[]) rs.getArray("course_version_ids").getArray());
        return new Determination(rs.getObject("id", UUID.class), rs.getObject("program_id", UUID.class), rs.getString("jurisdiction"),
                rs.getObject("jurisdiction_id", UUID.class), rs.getObject("rule_version_id", UUID.class), rs.getString("rule_version_label"),
                rs.getObject("assessment_id", UUID.class), Outcome.valueOf(rs.getString("outcome")), rs.getObject("signed_by", UUID.class),
                rs.getString("signed_by_name"), rs.getObject("signed_at", java.time.OffsetDateTime.class),
                DeterminationStatus.valueOf(rs.getString("status")), rs.getString("versions_hash"), courseVersions);
    }

    /** Newest version of every course in the program: what the determination is locked to. */
    public List<UUID> currentCourseVersionIds(UUID programId) {
        return jdbc.sql("""
                SELECT DISTINCT ON (c.id) cv.id FROM course c JOIN course_version cv ON cv.course_id = c.id
                WHERE c.program_id = :p ORDER BY c.id, cv.created_at DESC, cv.id DESC
                """).param("p", programId).query(UUID.class).list().stream().sorted().collect(Collectors.toList());
    }
}
