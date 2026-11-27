package com.lexvay.assessment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AssessmentRepository {

    private static final String COLUMNS = "id, program_id, status, started_at, finished_at, llm_model, prompt_version, created_at, created_by";

    /**
     * The tail of "the program's latest finished assessment" over {@code assessment a}: the newest finish, then the
     * newest creation. The caller adds {@code WHERE a.program_id = ...} (the status test is included here).
     */
    public static final String LATEST_FINISHED = "a.status = 'FINISHED' ORDER BY a.finished_at DESC, a.created_at DESC LIMIT 1";

    private static final RowMapper<Assessment> ROW = (rs, i) -> new Assessment(
            rs.getObject("id", UUID.class), rs.getObject("program_id", UUID.class), AssessmentStatus.valueOf(rs.getString("status")),
            rs.getObject("started_at", java.time.OffsetDateTime.class), rs.getObject("finished_at", java.time.OffsetDateTime.class),
            rs.getString("llm_model"), rs.getString("prompt_version"), rs.getObject("created_at", java.time.OffsetDateTime.class),
            rs.getObject("created_by", UUID.class));

    private final JdbcClient jdbc;

    public AssessmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, UUID programId, String llmModel, String promptVersion, UUID createdBy) {
        jdbc.sql("INSERT INTO assessment (id, tenant_id, program_id, status, llm_model, prompt_version, created_by) "
                        + "VALUES (:id, :tenantId, :programId, 'QUEUED', :llmModel, :promptVersion, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("programId", programId).param("llmModel", llmModel)
                .param("promptVersion", promptVersion).param("createdBy", createdBy).update();
    }

    public Optional<Assessment> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM assessment WHERE id = :id").param("id", id).query(ROW).optional();
    }

    public Optional<Assessment> latestFinished(UUID programId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM assessment a WHERE a.program_id = :programId AND " + LATEST_FINISHED)
                .param("programId", programId).query(ROW).optional();
    }

    public List<Assessment> findByProgram(UUID programId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM assessment WHERE program_id = :programId ORDER BY created_at DESC")
                .param("programId", programId).query(ROW).list();
    }

    public void markRunning(UUID id) {
        jdbc.sql("UPDATE assessment SET status = 'RUNNING', started_at = coalesce(started_at, now()) WHERE id = :id")
                .param("id", id).update();
    }

    public void markFinished(UUID id) {
        jdbc.sql("UPDATE assessment SET status = 'FINISHED', finished_at = now() WHERE id = :id").param("id", id).update();
    }

    public void markFailed(UUID id) {
        jdbc.sql("UPDATE assessment SET status = 'FAILED', finished_at = now() WHERE id = :id").param("id", id).update();
    }

    /**
     * Finding and decision counts per jurisdiction code for the overview. "Decided" is the negation of
     * {@link MappingRepository#UNDECIDED}, the predicate the signing gate and the work queue use (D-192).
     */
    public List<Map<String, Object>> findingCounts(UUID assessmentId) {
        return jdbc.sql("SELECT j.code AS jurisdiction, m.finding, count(*) AS n, count(*) FILTER (WHERE NOT "
                        + MappingRepository.UNDECIDED + ") AS decided "
                        + "FROM mapping m JOIN jurisdiction j ON j.id = m.jurisdiction_id JOIN requirement r ON r.id = m.requirement_id"
                        + MappingRepository.LATEST_EVENT
                        + " WHERE m.assessment_id = :id" + MappingRepository.current("= :id")
                        + " GROUP BY j.code, m.finding ORDER BY j.code, m.finding")
                .param("id", assessmentId).query().listOfRows();
    }
}
