package com.lexvay.program;

import com.lexvay.rule.NumericField;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProgramFactRepository {

    private static final String SELECT = """
            SELECT f.id, f.program_id, f.key, f.value, f.evidence_span_id, es.document_id, d.filename, es.page_number,
                   es.quote, f.created_at, f.created_by,
                   (f.id = first_value(f.id) OVER (PARTITION BY f.program_id, f.key ORDER BY f.created_at DESC, f.id DESC)) AS current
            FROM program_fact f
            JOIN evidence_span es ON es.id = f.evidence_span_id
            JOIN document d ON d.id = es.document_id
            """;

    private static final RowMapper<ProgramFact> ROW = (rs, i) -> new ProgramFact(
            rs.getObject("id", UUID.class), rs.getObject("program_id", UUID.class), NumericField.valueOf(rs.getString("key")),
            rs.getBigDecimal("value"), rs.getObject("evidence_span_id", UUID.class), rs.getObject("document_id", UUID.class),
            rs.getString("filename"), rs.getInt("page_number"), rs.getString("quote"),
            rs.getObject("created_at", java.time.OffsetDateTime.class), rs.getObject("created_by", UUID.class),
            rs.getBoolean("current"));

    private final JdbcClient jdbc;

    public ProgramFactRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, UUID programId, NumericField key, BigDecimal value, UUID evidenceSpanId, UUID createdBy) {
        jdbc.sql("INSERT INTO program_fact (id, tenant_id, program_id, key, value, evidence_span_id, created_by) "
                        + "VALUES (:id, :tenantId, :programId, :key, :value, :evidenceSpanId, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("programId", programId).param("key", key.name())
                .param("value", value).param("evidenceSpanId", evidenceSpanId).param("createdBy", createdBy).update();
    }

    /** All facts, newest first, with the current one per key flagged (D-036). */
    public List<ProgramFact> findByProgram(UUID programId) {
        return jdbc.sql(SELECT + " WHERE f.program_id = :programId ORDER BY f.created_at DESC, f.id DESC")
                .param("programId", programId).query(ROW).list();
    }

    /** The current fact per key, for the rules engine. */
    public List<ProgramFact> currentFacts(UUID programId) {
        return findByProgram(programId).stream().filter(ProgramFact::current).toList();
    }
}
