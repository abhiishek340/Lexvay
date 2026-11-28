package com.lexvay.rule.source;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
            rs.getString("last_content_hash"), rs.getObject("created_at", OffsetDateTime.class), governance(rs),
            new Validators(rs.getString("last_etag"), rs.getString("last_modified")));

    /** Null for the single row a source without checks produces in {@link #checks}. */
    private static final RowMapper<RuleSourceCheck> CHECK = (rs, i) -> rs.getObject("id") == null ? null : new RuleSourceCheck(
            rs.getObject("id", UUID.class), rs.getObject("checked_at", OffsetDateTime.class),
            CheckOutcome.valueOf(rs.getString("outcome")), AccessStatus.valueOf(rs.getString("access_status")),
            rs.getObject("http_status", Integer.class), rs.getString("content_hash"), rs.getString("detail"),
            rs.getObject("document_id", UUID.class), rs.getObject("rule_version_id", UUID.class));

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

    /**
     * The predicate of {@link #ENQUEUE} for the scheduler: active sources whose next check has come and whose access a
     * person has permitted (RG-2). Public like it.
     */
    public static final String DUE = "rs.active AND rs.access_status = 'PERMITTED' AND rs.next_check_at <= now()";

    /** The wait after a first failed check; it doubles with each further one until it reaches the source's interval (D-381). */
    static final int RETRY_BASE_MINUTES = 15;

    /** 2^12 times the base already exceeds the longest interval (720 hours), so a larger exponent changes nothing and could overflow. */
    private static final int MAX_BACKOFF_EXPONENT = 12;

    private static final String GOVERNANCE_COLUMNS = """
            rs.authority_class, rs.allowed_hosts, rs.allowed_path_prefixes, rs.expected_identifiers, rs.access_status,
            rs.access_note, rs.owner_user_id""";

    private static final String SELECT_SOURCE = """
            SELECT rs.id, rs.jurisdiction_id, j.code AS jurisdiction_code, rs.license_type, rs.label, rs.url,
                   rs.check_interval_hours, rs.active, rs.last_checked_at, rs.last_content_hash, rs.created_at,
                   rs.last_etag, rs.last_modified, %s
            FROM rule_source rs JOIN jurisdiction j ON j.id = rs.jurisdiction_id
            """.formatted(GOVERNANCE_COLUMNS);

    /** A source's newest check and its newest check that read the source, for what is shown and for its health. */
    private static final String CHECK_JOINS = """
            LEFT JOIN LATERAL (SELECT checked_at, outcome, http_status, detail, document_id, rule_version_id
                               FROM rule_source_check WHERE rule_source_id = rs.id
                               ORDER BY checked_at DESC, id DESC LIMIT 1) c ON true
            LEFT JOIN LATERAL (SELECT checked_at FROM rule_source_check
                               WHERE rule_source_id = rs.id AND outcome IN (:successes)
                               ORDER BY checked_at DESC, id DESC LIMIT 1) s ON true
            """;

    private static final Set<String> SUCCESSES = CheckOutcome.SUCCESSES.stream().map(Enum::name).collect(Collectors.toSet());

    private final JdbcClient jdbc;

    public RuleSourceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static SourceGovernance governance(ResultSet rs) throws SQLException {
        return new SourceGovernance(AuthorityClass.valueOf(rs.getString("authority_class")), strings(rs, "allowed_hosts"),
                strings(rs, "allowed_path_prefixes"), strings(rs, "expected_identifiers"),
                AccessStatus.valueOf(rs.getString("access_status")), rs.getString("access_note"),
                rs.getObject("owner_user_id", UUID.class));
    }

    private static List<String> strings(ResultSet rs, String column) throws SQLException {
        return List.of((String[]) rs.getArray(column).getArray());
    }

    private static SourceHealth.Facts facts(ResultSet rs) throws SQLException {
        String latest = rs.getString("latest_outcome");
        return new SourceHealth.Facts(AccessStatus.valueOf(rs.getString("access_status")),
                latest == null ? null : CheckOutcome.valueOf(latest), rs.getInt("consecutive_failures"),
                rs.getObject("last_success_at", OffsetDateTime.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getInt("check_interval_hours"));
    }

    public void insert(UUID id, UUID tenantId, UUID jurisdictionId, String licenseType, String label, String url,
                       int checkIntervalHours, SourceGovernance governance, UUID createdBy) {
        jdbc.sql("INSERT INTO rule_source (id, tenant_id, jurisdiction_id, license_type, label, url, check_interval_hours, "
                        + "authority_class, allowed_hosts, allowed_path_prefixes, expected_identifiers, access_status, access_note, "
                        + "owner_user_id, created_by) "
                        + "VALUES (:id, :tenantId, :jurisdictionId, :licenseType, :label, :url, :interval, :authorityClass, "
                        + "CAST(:allowedHosts AS text[]), CAST(:allowedPathPrefixes AS text[]), CAST(:expectedIdentifiers AS text[]), "
                        + ":accessStatus, :accessNote, :ownerUserId, :createdBy)")
                .param("id", id).param("tenantId", tenantId).param("jurisdictionId", jurisdictionId)
                .param("licenseType", licenseType).param("label", label).param("url", url)
                .param("interval", checkIntervalHours).param("authorityClass", governance.authorityClass().name())
                .param("allowedHosts", governance.allowedHosts().toArray(String[]::new))
                .param("allowedPathPrefixes", governance.allowedPathPrefixes().toArray(String[]::new))
                .param("expectedIdentifiers", governance.expectedIdentifiers().toArray(String[]::new))
                .param("accessStatus", governance.accessStatus().name()).param("accessNote", governance.accessNote())
                .param("ownerUserId", governance.ownerUserId()).param("createdBy", createdBy).update();
    }

    public Optional<RuleSource> findById(UUID id) {
        return jdbc.sql(SELECT_SOURCE + " WHERE rs.id = :id").param("id", id).query(SOURCE).optional();
    }

    /** Like {@link #findById}, and holds the row until the transaction ends, so a governance edit reads what it replaces. */
    public Optional<RuleSource> lockById(UUID id) {
        return jdbc.sql(SELECT_SOURCE + " WHERE rs.id = :id FOR UPDATE OF rs").param("id", id).query(SOURCE).optional();
    }

    /** Replaces the governance of a source. The validators go too: the next check asks again without conditions. */
    public void updateGovernance(UUID id, SourceGovernance governance) {
        jdbc.sql("UPDATE rule_source SET authority_class = :authorityClass, allowed_hosts = CAST(:allowedHosts AS text[]), "
                        + "allowed_path_prefixes = CAST(:allowedPathPrefixes AS text[]), "
                        + "expected_identifiers = CAST(:expectedIdentifiers AS text[]), access_status = :accessStatus, "
                        + "access_note = :accessNote, owner_user_id = :ownerUserId, last_etag = NULL, last_modified = NULL "
                        + "WHERE id = :id")
                .param("authorityClass", governance.authorityClass().name())
                .param("allowedHosts", governance.allowedHosts().toArray(String[]::new))
                .param("allowedPathPrefixes", governance.allowedPathPrefixes().toArray(String[]::new))
                .param("expectedIdentifiers", governance.expectedIdentifiers().toArray(String[]::new))
                .param("accessStatus", governance.accessStatus().name()).param("accessNote", governance.accessNote())
                .param("ownerUserId", governance.ownerUserId()).param("id", id).update();
    }

    /** Every source with its latest check and its health at {@code now}, in one statement. */
    public List<RuleSourceView> list(OffsetDateTime now) {
        return jdbc.sql("""
                SELECT rs.id, j.code AS jurisdiction_code, rs.license_type, rs.label, rs.url, rs.check_interval_hours,
                       rs.active, rs.last_checked_at, rs.next_check_at, rs.consecutive_failures, rs.created_at, %s,
                       c.checked_at AS latest_checked_at, c.outcome AS latest_outcome, c.http_status AS latest_http_status,
                       c.detail AS latest_detail, c.document_id AS latest_document_id, c.rule_version_id AS latest_rule_version_id,
                       s.checked_at AS last_success_at
                FROM rule_source rs
                JOIN jurisdiction j ON j.id = rs.jurisdiction_id
                %s
                ORDER BY j.code, rs.license_type, rs.label, rs.created_at
                """.formatted(GOVERNANCE_COLUMNS, CHECK_JOINS)).param("successes", SUCCESSES).query((rs, i) -> view(rs, now)).list();
    }

    private static RuleSourceView view(ResultSet rs, OffsetDateTime now) throws SQLException {
        return new RuleSourceView(
                rs.getObject("id", UUID.class), rs.getString("jurisdiction_code"), rs.getString("license_type"),
                rs.getString("label"), rs.getString("url"), rs.getInt("check_interval_hours"), rs.getBoolean("active"),
                rs.getObject("last_checked_at", OffsetDateTime.class), rs.getObject("last_success_at", OffsetDateTime.class),
                rs.getObject("next_check_at", OffsetDateTime.class), rs.getInt("consecutive_failures"),
                rs.getObject("created_at", OffsetDateTime.class), governance(rs), SourceHealth.of(facts(rs), now),
                rs.getString("latest_outcome") == null ? null : new RuleSourceView.LatestCheck(
                        rs.getObject("latest_checked_at", OffsetDateTime.class),
                        CheckOutcome.valueOf(rs.getString("latest_outcome")), rs.getObject("latest_http_status", Integer.class),
                        rs.getString("latest_detail"), rs.getObject("latest_document_id", UUID.class),
                        rs.getObject("latest_rule_version_id", UUID.class)));
    }

    /**
     * The jurisdictions, among {@code jurisdictionIds}, in which an active source of the licence type is not HEALTHY at
     * {@code now}. One statement however many there are. A state with no source is not in the set: nothing monitors it,
     * which is not the same as a monitor that fell behind.
     */
    public Set<UUID> jurisdictionsWithUnhealthySource(Collection<UUID> jurisdictionIds, String licenseType, OffsetDateTime now) {
        if (jurisdictionIds.isEmpty()) {
            return Set.of();
        }
        return jdbc.sql("""
                SELECT rs.jurisdiction_id, rs.access_status, rs.consecutive_failures, rs.check_interval_hours, rs.created_at,
                       c.outcome AS latest_outcome, s.checked_at AS last_success_at
                FROM rule_source rs
                %s
                WHERE rs.active AND rs.license_type = :licenseType AND rs.jurisdiction_id IN (:jurisdictionIds)
                """.formatted(CHECK_JOINS)).param("successes", SUCCESSES).param("licenseType", licenseType)
                .param("jurisdictionIds", jurisdictionIds)
                .query((rs, i) -> Map.entry(rs.getObject("jurisdiction_id", UUID.class), SourceHealth.of(facts(rs), now))).list()
                .stream().filter(entry -> entry.getValue() != SourceHealth.HEALTHY).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /** The newest checks of a source, or empty when there is no such source. One statement either way. */
    public Optional<List<RuleSourceCheck>> checks(UUID ruleSourceId, int limit) {
        List<RuleSourceCheck> rows = jdbc.sql("""
                SELECT c.id, c.checked_at, c.outcome, c.access_status, c.http_status, c.content_hash, c.detail, c.document_id,
                       c.rule_version_id
                FROM rule_source rs LEFT JOIN rule_source_check c ON c.rule_source_id = rs.id
                WHERE rs.id = :id
                ORDER BY c.checked_at DESC NULLS LAST, c.id DESC LIMIT :limit
                """).param("id", ruleSourceId).param("limit", limit).query(CHECK).list();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.stream().filter(Objects::nonNull).toList());
    }

    /**
     * Records a check made at {@code checkedAt}: the time the page was observed, which a draft made from it keeps as
     * its own {@code observed_at}. {@code access} is the status the source had, so a check of a source nobody reviewed
     * says so.
     */
    public UUID insertCheck(UUID tenantId, UUID ruleSourceId, OffsetDateTime checkedAt, AccessStatus access, CheckFinding finding) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO rule_source_check (id, tenant_id, rule_source_id, checked_at, outcome, access_status, http_status, "
                        + "content_hash, detail, document_id, rule_version_id) "
                        + "VALUES (:id, :tenantId, :ruleSourceId, :checkedAt, :outcome, :accessStatus, :httpStatus, :contentHash, "
                        + ":detail, :documentId, :ruleVersionId)")
                .param("id", id).param("tenantId", tenantId).param("ruleSourceId", ruleSourceId).param("checkedAt", checkedAt)
                .param("outcome", finding.outcome().name()).param("accessStatus", access.name())
                .param("httpStatus", finding.httpStatus()).param("contentHash", finding.contentHash())
                .param("detail", finding.detail()).param("documentId", finding.documentId())
                .param("ruleVersionId", finding.ruleVersionId()).update();
        return id;
    }

    /** The text hash of the newest change this source queued behind the draft, so the same text is not kept twice. */
    public Optional<String> latestQueuedHash(UUID ruleSourceId, UUID ruleVersionId) {
        return jdbc.sql("""
                SELECT content_hash FROM rule_source_check
                WHERE outcome = 'CHANGED_QUEUED' AND rule_version_id = :ruleVersionId AND rule_source_id = :ruleSourceId
                ORDER BY checked_at DESC, id DESC LIMIT 1""").param("ruleVersionId", ruleVersionId)
                .param("ruleSourceId", ruleSourceId).query(String.class).optional();
    }

    /** The changes queued behind each of the given drafts, oldest first, in one statement. Drafts with none are absent. */
    public Map<UUID, List<QueuedObservation>> queuedObservations(Collection<UUID> ruleVersionIds) {
        if (ruleVersionIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                SELECT rule_version_id, id, checked_at, document_id, content_hash FROM rule_source_check
                WHERE outcome = 'CHANGED_QUEUED' AND rule_version_id IN (:ids)
                ORDER BY checked_at, id""").param("ids", ruleVersionIds)
                .query((rs, i) -> Map.entry(rs.getObject("rule_version_id", UUID.class), new QueuedObservation(
                        rs.getObject("id", UUID.class), rs.getObject("checked_at", OffsetDateTime.class),
                        rs.getObject("document_id", UUID.class), rs.getString("content_hash"))))
                .list().stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    /**
     * Records that a check ran now and schedules the next one. The hash is the one to compare against next time and
     * the validators those to send with it. A check that reached the page waits the source's interval and clears the
     * failures; a failed fetch waits {@link #RETRY_BASE_MINUTES} minutes doubled for every failure before it, but never
     * longer than the interval. Both values are computed from the stored row, so two checks cannot corrupt the counter.
     */
    public void markChecked(UUID id, String contentHash, Validators validators, CheckOutcome outcome) {
        jdbc.sql("""
                UPDATE rule_source
                SET last_checked_at = now(), last_content_hash = :hash, last_etag = :etag, last_modified = :lastModified,
                    consecutive_failures = CASE WHEN :failed THEN consecutive_failures + 1 ELSE 0 END,
                    next_check_at = now() + CASE WHEN :failed
                        THEN least(check_interval_hours * interval '1 hour',
                                   :baseMinutes * interval '1 minute' * power(2, least(consecutive_failures, :maxExponent)))
                        ELSE check_interval_hours * interval '1 hour' END
                WHERE id = :id""")
                .param("hash", contentHash).param("etag", validators.etag()).param("lastModified", validators.lastModified())
                .param("failed", outcome == CheckOutcome.FETCH_FAILED)
                .param("baseMinutes", RETRY_BASE_MINUTES).param("maxExponent", MAX_BACKOFF_EXPONENT).param("id", id).update();
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
     * Queues a check for every permitted, active source that is due, in every tenant, under the transaction-local
     * worker flag (D-013, V011). Returns how many jobs were queued.
     */
    @Transactional
    public int enqueueDue() {
        jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
        return jdbc.sql(ENQUEUE.formatted(DUE)).param("createdBy", null).query(UUID.class).list().size();
    }
}
