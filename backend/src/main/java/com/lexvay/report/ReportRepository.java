package com.lexvay.report;

import com.lexvay.determination.DeterminationRepository;
import com.lexvay.determination.DeterminationStatus;
import com.lexvay.determination.Outcome;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {

    private static final RowMapper<Report> ROW = (rs, i) -> new Report(rs.getObject("id", UUID.class),
            rs.getObject("determination_id", UUID.class), rs.getString("storage_key"), ReportStatus.valueOf(rs.getString("status")),
            rs.getObject("created_at", java.time.OffsetDateTime.class), rs.getObject("created_by", UUID.class));

    /**
     * The reports of the caller's tenant whose stored PDF has the SHA-256 {@code :h}, newest first, with the
     * determination each records as it stands now. One statement; row-level security adds the tenant, and
     * {@code report_content_sha_idx} serves both conditions. At most two rows: the newest is the answer, and a second
     * only tells that the hash is shared. Public so that {@code IndexUsageTest} plans the real text.
     */
    public static final String CHECK = """
            SELECT r.id AS report_id, r.created_at AS generated_at, d.id AS determination_id, p.id AS program_id,
                   p.name AS program_name, j.code AS jurisdiction_code, rv.label AS rule_version_label, d.outcome,
                   u.display_name AS signed_by_name, d.signed_at, d.status, d.versions_hash,
                   CASE WHEN d.status = 'REOPENED' THEN %s END AS reopened_at
            FROM report r
            JOIN determination d ON d.id = r.determination_id
            JOIN program p ON p.id = d.program_id
            JOIN jurisdiction j ON j.id = d.jurisdiction_id
            JOIN rule_version rv ON rv.id = d.rule_version_id
            JOIN app_user u ON u.id = d.signed_by
            WHERE r.content_sha256 = :h
            ORDER BY r.created_at DESC, r.id DESC
            LIMIT 2
            """.formatted(DeterminationRepository.reopenedAt("d.id"));

    private static final RowMapper<ReportCheck.Match> MATCH = (rs, i) -> new ReportCheck.Match(
            rs.getObject("report_id", UUID.class), rs.getObject("generated_at", OffsetDateTime.class),
            rs.getObject("determination_id", UUID.class), rs.getObject("program_id", UUID.class), rs.getString("program_name"),
            rs.getString("jurisdiction_code"), rs.getString("rule_version_label"), Outcome.valueOf(rs.getString("outcome")),
            rs.getString("signed_by_name"), rs.getObject("signed_at", OffsetDateTime.class), DeterminationStatus.valueOf(rs.getString("status")),
            rs.getObject("reopened_at", OffsetDateTime.class), rs.getString("versions_hash"));

    private final JdbcClient jdbc;

    public ReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID tenantId, UUID determinationId, UUID createdBy) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO report (id, tenant_id, determination_id, created_by) VALUES (:id, :tenantId, :d, :by)")
                .param("id", id).param("tenantId", tenantId).param("d", determinationId).param("by", createdBy).update();
        return id;
    }

    public Optional<Report> findById(UUID id) {
        return jdbc.sql("SELECT id, determination_id, storage_key, status, created_at, created_by FROM report WHERE id = :id")
                .param("id", id).query(ROW).optional();
    }

    /** The report that is current for a determination: the newest one whose PDF exists. */
    public Optional<Report> findLatestReady(UUID determinationId) {
        return jdbc.sql("SELECT id, determination_id, storage_key, status, created_at, created_by FROM report "
                        + "WHERE determination_id = :d AND status = 'READY' ORDER BY created_at DESC, id DESC LIMIT 1")
                .param("d", determinationId).query(ROW).optional();
    }

    /** Ready, with the key of the stored PDF and the SHA-256 of exactly the bytes stored under it (V018). */
    public void markReady(UUID id, String storageKey, String contentSha256) {
        jdbc.sql("UPDATE report SET status = 'READY', storage_key = :key, content_sha256 = :sha WHERE id = :id")
                .param("key", storageKey).param("sha", contentSha256).param("id", id).update();
    }

    /** {@link #CHECK}: newest first, at most two. */
    public List<ReportCheck.Match> findByContent(String sha256) {
        return jdbc.sql(CHECK).param("h", sha256).query(MATCH).list();
    }

    public void markFailed(UUID id) {
        jdbc.sql("UPDATE report SET status = 'FAILED' WHERE id = :id").param("id", id).update();
    }
}
