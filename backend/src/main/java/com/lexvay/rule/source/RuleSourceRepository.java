package com.lexvay.rule.source;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RuleSourceRepository {

    private static final RowMapper<RuleSource> SOURCE = (rs, i) -> new RuleSource(
            rs.getObject("id", UUID.class), rs.getObject("jurisdiction_id", UUID.class), rs.getString("jurisdiction_code"),
            rs.getString("license_type"), rs.getString("label"), rs.getString("url"), rs.getInt("check_interval_hours"),
            rs.getBoolean("active"), rs.getObject("last_checked_at", OffsetDateTime.class),
            rs.getString("last_content_hash"), rs.getObject("created_at", OffsetDateTime.class));

    private static final RowMapper<RuleSourceView> VIEW = (rs, i) -> new RuleSourceView(
            rs.getObject("id", UUID.class), rs.getString("jurisdiction_code"), rs.getString("license_type"),
            rs.getString("label"), rs.getString("url"), rs.getInt("check_interval_hours"), rs.getBoolean("active"),
            rs.getObject("last_checked_at", OffsetDateTime.class), rs.getObject("created_at", OffsetDateTime.class),
            rs.getString("latest_outcome") == null ? null : new RuleSourceView.LatestCheck(
                    rs.getObject("latest_checked_at", OffsetDateTime.class),
                    CheckOutcome.valueOf(rs.getString("latest_outcome")), rs.getObject("latest_http_status", Integer.class),
                    rs.getString("latest_detail"), rs.getObject("latest_document_id", UUID.class),
                    rs.getObject("latest_rule_version_id", UUID.class)));

    /** Null for the single row a source without checks produces in {@link #checks}. */
    private static final RowMapper<RuleSourceCheck> CHECK = (rs, i) -> rs.getObject("id") == null ? null : new RuleSourceCheck(
            rs.getObject("id", UUID.class), rs.getObject("checked_at", OffsetDateTime.class),
            CheckOutcome.valueOf(rs.getString("outcome")), rs.getObject("http_status", Integer.class),
            rs.getString("content_hash"), rs.getString("detail"), rs.getObject("document_id", UUID.class),
            rs.getObject("rule_version_id", UUID.class));

    /**
     * Queues one CHECK_RULE_SOURCE job for every source the {@code %s} predicate selects, unless one is already
     * QUEUED or RUNNING for it. One statement, so the dedupe and the insert cannot interleave with themselves.
     * Public so the index-usage check can run {@code EXPLAIN} on exactly what the application runs.
     */
    public static final String ENQUEUE = """
            INSERT INTO job (tenant_id, type, entity_id, payload, created_by)
            SELECT rs.tenant_id, 'CHECK_RULE_SOURCE', rs.id, jsonb_build_object('ruleSourceId', rs.id), CAST(:createdBy AS uuid)
            FROM rule_source rs
            WHERE %s
              AND NOT EXISTS (SELECT 1 FROM job j WHERE j.type = 'CHECK_RULE_SOURCE' AND j.status IN ('QUEUED', 'RUNNING')
                              AND j.entity_id = rs.id)
            RETURNING id""";

    /** The predicate of {@link #ENQUEUE} for the scheduler: active sources never checked or past their interval. Public like it. */
    public static final String DUE = """
            rs.active AND (rs.last_checked_at IS NULL
                           OR rs.last_checked_at < now() - rs.check_interval_hours * interval '1 hour')""";

    private static final String SELECT_SOURCE = """
            SELECT rs.id, rs.jurisdiction_id, j.code AS jurisdiction_code, rs.license_type, rs.label, rs.url,
                   rs.check_interval_hours, rs.active, rs.last_checked_at, rs.last_content_hash, rs.created_at
            FROM rule_source rs JOIN jurisdiction j ON j.id = rs.jurisdiction_id
            """;

    private final JdbcClient jdbc;

    public RuleSourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID tenantId, UUID jurisdictionId, String licenseType, String label, String url,
                       int checkIntervalHours, UUID createdBy) {
        jdbc.sql("INSERT INTO rule_source (id, tenant_id, jurisdiction_id, license_type, label, url, check_interval_hours, created_by) "
                        + "VALUES (:id, :tenantId, :jurisdictionId, :licenseType, :label, :url, :interval, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("jurisdictionId", jurisdictionId)
                .param("licenseType", licenseType).param("label", label).param("url", url)
                .param("interval", checkIntervalHours).param("createdBy", createdBy).update();
    }

    public Optional<RuleSource> findById(UUID id) {
        return jdbc.sql(SELECT_SOURCE + " WHERE rs.id = :id").param("id", id).query(SOURCE).optional();
    }

    /** Every source with its latest check, in one statement. */
    public List<RuleSourceView> list() {
        return jdbc.sql("""
                SELECT rs.id, j.code AS jurisdiction_code, rs.license_type, rs.label, rs.url, rs.check_interval_hours,
                       rs.active, rs.last_checked_at, rs.created_at,
                       c.checked_at AS latest_checked_at, c.outcome AS latest_outcome, c.http_status AS latest_http_status,
                       c.detail AS latest_detail, c.document_id AS latest_document_id, c.rule_version_id AS latest_rule_version_id
                FROM rule_source rs
                JOIN jurisdiction j ON j.id = rs.jurisdiction_id
                LEFT JOIN LATERAL (SELECT checked_at, outcome, http_status, detail, document_id, rule_version_id
                                   FROM rule_source_check WHERE rule_source_id = rs.id
                                   ORDER BY checked_at DESC, id DESC LIMIT 1) c ON true
                ORDER BY j.code, rs.license_type, rs.label, rs.created_at
                """).query(VIEW).list();
    }

    /** The newest checks of a source, or empty when there is no such source. One statement either way. */
    public Optional<List<RuleSourceCheck>> checks(UUID ruleSourceId, int limit) {
        List<RuleSourceCheck> rows = jdbc.sql("""
                SELECT c.id, c.checked_at, c.outcome, c.http_status, c.content_hash, c.detail, c.document_id, c.rule_version_id
                FROM rule_source rs LEFT JOIN rule_source_check c ON c.rule_source_id = rs.id
                WHERE rs.id = :id
                ORDER BY c.checked_at DESC NULLS LAST, c.id DESC LIMIT :limit
                """).param("id", ruleSourceId).param("limit", limit).query(CHECK).list();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.stream().filter(Objects::nonNull).toList());
    }

    public UUID insertCheck(UUID tenantId, UUID ruleSourceId, CheckOutcome outcome, Integer httpStatus, String contentHash,
                            String detail, UUID documentId, UUID ruleVersionId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO rule_source_check (id, tenant_id, rule_source_id, outcome, http_status, content_hash, detail, document_id, rule_version_id) "
                        + "VALUES (:id, :tenantId, :ruleSourceId, :outcome, :httpStatus, :contentHash, :detail, :documentId, :ruleVersionId)")
                .param("id", id).param("tenantId", tenantId).param("ruleSourceId", ruleSourceId)
                .param("outcome", outcome.name()).param("httpStatus", httpStatus).param("contentHash", contentHash)
                .param("detail", detail).param("documentId", documentId).param("ruleVersionId", ruleVersionId).update();
        return id;
    }

    /** Records that a check ran now. The hash is the one to compare against next time. */
    public void markChecked(UUID id, String contentHash) {
        jdbc.sql("UPDATE rule_source SET last_checked_at = now(), last_content_hash = :hash WHERE id = :id")
                .param("hash", contentHash).param("id", id).update();
    }

    public boolean setActive(UUID id, boolean active) {
        return jdbc.sql("UPDATE rule_source SET active = :active WHERE id = :id").param("active", active).param("id", id).update() == 1;
    }

    /** Queues a check of one source for the current tenant; empty when one is already queued or running. */
    public Optional<UUID> enqueueCheck(UUID id, UUID createdBy) {
        return jdbc.sql(ENQUEUE.formatted("rs.id = :id")).param("id", id).param("createdBy", createdBy)
                .query(UUID.class).optional();
    }

    /**
     * Queues a check for every active source that is due, in every tenant, under the transaction-local worker flag
     * (D-013, V011). Returns how many jobs were queued.
     */
    @Transactional
    public int enqueueDue() {
        jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
        return jdbc.sql(ENQUEUE.formatted(DUE)).param("createdBy", null).query(UUID.class).list().size();
    }
}
