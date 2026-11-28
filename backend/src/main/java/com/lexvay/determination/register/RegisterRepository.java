package com.lexvay.determination.register;

import com.lexvay.common.WhereClause;
import com.lexvay.determination.DeterminationRepository;
import com.lexvay.determination.DeterminationStatus;
import com.lexvay.determination.Outcome;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The register's two reads, one statement each. Tenant scoping comes from row-level security. */
@Repository
public class RegisterRepository {

    private static final String REGISTER = """
            SELECT d.id, d.program_id, p.name AS program_name, p.license_type, p.catalog_year,
                   j.code AS jurisdiction_code, j.name AS jurisdiction_name, d.outcome, d.status,
                   d.signed_by, u.display_name AS signed_by_name, d.signed_at,
                   d.rule_version_id, rv.label AS rule_version_label, rv.effective_from AS rule_effective_from,
                   d.versions_hash, d.assessment_id,
                   (SELECT count(*) FROM determination_course_version dcv WHERE dcv.determination_id = d.id) AS course_version_count
            FROM determination d
            JOIN program p ON p.id = d.program_id
            JOIN jurisdiction j ON j.id = d.jurisdiction_id
            JOIN rule_version_current rv ON rv.id = d.rule_version_id
            JOIN app_user u ON u.id = d.signed_by
            """;

    /**
     * One row per target jurisdiction of the program (one row with a null code when it has none). {@code s} is the
     * current SIGNED determination; {@code r}, read only when there is none, the latest REOPENED one, dated by its
     * DETERMINATION_REOPENED audit entry ({@link DeterminationRepository#reopenedAt}). {@code now()} is the
     * transaction's start, the moment the lists describe.
     */
    private static final String TARGETS = """
            SELECT p.id AS program_id, p.name AS program_name, p.license_type, p.catalog_year, now() AS generated_at,
                   j.code AS jurisdiction_code, j.name AS jurisdiction_name,
                   s.id AS determination_id, s.outcome, s.signed_by, u.display_name AS signed_by_name, s.signed_at,
                   rv.label AS rule_version_label, s.versions_hash,
                   r.id AS reopened_id, %s AS reopened_at
            FROM program p
            LEFT JOIN program_jurisdiction pj ON pj.program_id = p.id
            LEFT JOIN jurisdiction j ON j.id = pj.jurisdiction_id
            LEFT JOIN LATERAL (
                SELECT d.id, d.outcome, d.signed_by, d.signed_at, d.rule_version_id, d.versions_hash FROM determination d
                WHERE d.program_id = p.id AND d.jurisdiction_id = pj.jurisdiction_id AND d.status = 'SIGNED'
                ORDER BY d.signed_at DESC, d.id DESC LIMIT 1) s ON true
            LEFT JOIN app_user u ON u.id = s.signed_by
            LEFT JOIN rule_version rv ON rv.id = s.rule_version_id
            LEFT JOIN LATERAL (
                SELECT d.id FROM determination d
                WHERE s.id IS NULL AND d.program_id = p.id AND d.jurisdiction_id = pj.jurisdiction_id AND d.status = 'REOPENED'
                ORDER BY d.signed_at DESC, d.id DESC LIMIT 1) r ON true
            WHERE p.id = :programId
            ORDER BY j.code
            """.formatted(DeterminationRepository.reopenedAt("r.id"));

    private static final ResultSetExtractor<Optional<DeterminationLists>> LISTS = RegisterRepository::readLists;

    private final JdbcClient jdbc;

    public RegisterRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Ordered by program name, jurisdiction code, then newest signature first (ids break ties). */
    public List<RegisterRow> register(RegisterFilter filter) {
        WhereClause where = new WhereClause()
                .andIfPresent("d.program_id = :programId", "programId", filter.programId())
                .andIfPresent("j.code = :jurisdiction", "jurisdiction", filter.jurisdiction())
                .andIfPresent("d.outcome = :outcome", "outcome", filter.outcome() == null ? null : filter.outcome().name())
                .andIf(filter.current(), "d.status = 'SIGNED'");
        return where.bind(jdbc.sql(REGISTER + where.sql() + " ORDER BY p.name, p.id, j.code, d.signed_at DESC, d.id DESC"))
                .query(RegisterRepository::row).list();
    }

    /** Empty when the program does not exist in the caller's tenant. */
    public Optional<DeterminationLists> lists(UUID programId) {
        return jdbc.sql(TARGETS).param("programId", programId).query(LISTS);
    }

    private static RegisterRow row(ResultSet rs, int rowNum) throws SQLException {
        return new RegisterRow(rs.getObject("id", UUID.class), rs.getObject("program_id", UUID.class), rs.getString("program_name"),
                rs.getString("license_type"), rs.getString("catalog_year"), rs.getString("jurisdiction_code"),
                rs.getString("jurisdiction_name"), Outcome.valueOf(rs.getString("outcome")),
                DeterminationStatus.valueOf(rs.getString("status")), rs.getObject("signed_by", UUID.class),
                rs.getString("signed_by_name"), rs.getObject("signed_at", OffsetDateTime.class),
                rs.getObject("rule_version_id", UUID.class), rs.getString("rule_version_label"),
                rs.getObject("rule_effective_from", LocalDate.class), rs.getString("versions_hash"),
                rs.getObject("assessment_id", UUID.class), rs.getInt("course_version_count"));
    }

    private static Optional<DeterminationLists> readLists(ResultSet rs) throws SQLException {
        DeterminationLists.ProgramSummary program = null;
        OffsetDateTime generatedAt = null;
        List<DeterminationLists.Target> targets = new ArrayList<>();
        while (rs.next()) {
            if (program == null) {
                program = new DeterminationLists.ProgramSummary(rs.getObject("program_id", UUID.class), rs.getString("program_name"),
                        rs.getString("license_type"), rs.getString("catalog_year"));
                generatedAt = rs.getObject("generated_at", OffsetDateTime.class);
            }
            String code = rs.getString("jurisdiction_code");
            if (code == null) {
                continue;
            }
            String outcome = rs.getString("outcome");
            targets.add(new DeterminationLists.Target(code, rs.getString("jurisdiction_name"),
                    outcome == null ? null : Outcome.valueOf(outcome), rs.getObject("determination_id", UUID.class),
                    rs.getObject("signed_by", UUID.class), rs.getString("signed_by_name"),
                    rs.getObject("signed_at", OffsetDateTime.class), rs.getString("rule_version_label"),
                    rs.getString("versions_hash"), rs.getObject("reopened_id", UUID.class),
                    rs.getObject("reopened_at", OffsetDateTime.class)));
        }
        return program == null ? Optional.empty() : Optional.of(DeterminationLists.of(program, generatedAt, targets));
    }
}
