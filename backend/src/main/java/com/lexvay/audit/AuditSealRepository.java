package com.lexvay.audit;

import com.lexvay.audit.AuditCanonical.Entry;
import com.lexvay.audit.AuditChainCheck.Sealed;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SQL of the audit seal (SEC-3): the sealer's queue and chain head, and the page-by-page read the verification
 * walks. Row-level security scopes everything to the current tenant except {@link #tenantsWithQueue()}.
 */
@Repository
public class AuditSealRepository {

    /** The newest seal of a tenant; the next one links to its hash. */
    record Head(long seq, String rowHash, OffsetDateTime sealedAt) {
    }

    /** A seal to store. */
    record NewSeal(long seq, UUID auditId, String prevHash, String rowHash) {
    }

    private static final String ENTRY_COLUMNS =
            "a.id, a.user_id, a.action, a.entity_type, a.entity_id, a.detail::text AS detail, a.created_at";

    /** The sealer's read of the queue, oldest first; public so that the performance test plans the real text. */
    public static final String QUEUED = "SELECT " + ENTRY_COLUMNS + " FROM audit_unsealed u JOIN audit_log a ON a.id = u.audit_id "
            + "WHERE u.tenant_id = :tenantId ORDER BY u.created_at, u.audit_id LIMIT :limit";

    /** Takes the entries sealed after {@code :seq} out of the queue; public for the same reason as {@link #QUEUED}. */
    public static final String DEQUEUE = "DELETE FROM audit_unsealed u USING audit_seal s "
            + "WHERE s.audit_id = u.audit_id AND s.tenant_id = :tenantId AND s.seq > :seq";

    private final JdbcClient jdbc;

    public AuditSealRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tenants with entries waiting to be sealed, read across tenants under the transaction-local worker flag
     * (D-013, V023). The flag opens this read and nothing else.
     */
    @Transactional(readOnly = true)
    public List<UUID> tenantsWithQueue() {
        jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
        return jdbc.sql("SELECT DISTINCT tenant_id FROM audit_unsealed").query(UUID.class).list();
    }

    /**
     * Takes the tenant's sealing lock until the transaction ends. False when another sealer holds it, so several
     * application instances never seal the same tenant at once (D-533).
     */
    public boolean tryLock(UUID tenantId) {
        return jdbc.sql("SELECT pg_try_advisory_xact_lock(hashtextextended('audit-seal:' || :tenantId, 0))")
                .param("tenantId", tenantId.toString()).query(Boolean.class).single();
    }

    public Optional<Head> head(UUID tenantId) {
        return jdbc.sql("SELECT seq, row_hash, sealed_at FROM audit_seal WHERE tenant_id = :tenantId ORDER BY seq DESC LIMIT 1")
                .param("tenantId", tenantId)
                .query((rs, row) -> new Head(rs.getLong("seq"), rs.getString("row_hash"), rs.getObject("sealed_at", OffsetDateTime.class)))
                .optional();
    }

    /** The oldest queued entries, from the queue's index; the same order the chain is built in. */
    public List<Entry> queued(UUID tenantId, int limit) {
        return jdbc.sql(QUEUED)
                .param("tenantId", tenantId).param("limit", limit)
                .query((rs, row) -> entry(rs)).list();
    }

    /** Stores the seals in one statement. */
    public void insertSeals(UUID tenantId, List<NewSeal> seals) {
        jdbc.sql("""
                INSERT INTO audit_seal (tenant_id, seq, audit_id, prev_hash, row_hash)
                SELECT :tenantId, s.seq, s.audit_id, s.prev_hash, s.row_hash
                FROM unnest(CAST(:seqs AS bigint[]), CAST(:auditIds AS uuid[]), CAST(:prevHashes AS text[]), CAST(:rowHashes AS text[]))
                     AS s(seq, audit_id, prev_hash, row_hash)""")
                .param("tenantId", tenantId)
                .param("seqs", seals.stream().mapToLong(NewSeal::seq).toArray())
                .param("auditIds", seals.stream().map(seal -> seal.auditId().toString()).toArray(String[]::new))
                .param("prevHashes", seals.stream().map(NewSeal::prevHash).toArray(String[]::new))
                .param("rowHashes", seals.stream().map(NewSeal::rowHash).toArray(String[]::new))
                .update();
    }

    /** Takes the entries sealed after {@code seq} out of the queue. */
    public int dequeueSealedAfter(UUID tenantId, long seq) {
        return jdbc.sql(DEQUEUE)
                .param("tenantId", tenantId).param("seq", seq).update();
    }

    /** Up to {@code limit} seals after {@code afterSeq}, in order, each with the entry it covers. */
    public List<Sealed> sealsAfter(UUID tenantId, long afterSeq, int limit) {
        return jdbc.sql("SELECT s.seq, s.prev_hash, s.row_hash, " + ENTRY_COLUMNS
                        + " FROM audit_seal s JOIN audit_log a ON a.id = s.audit_id "
                        + "WHERE s.tenant_id = :tenantId AND s.seq > :afterSeq ORDER BY s.seq LIMIT :limit")
                .param("tenantId", tenantId).param("afterSeq", afterSeq).param("limit", limit)
                .query((rs, row) -> new Sealed(rs.getLong("seq"), rs.getString("prev_hash"), rs.getString("row_hash"), entry(rs)))
                .list();
    }

    public long sealedCount(UUID tenantId) {
        return jdbc.sql("SELECT count(*) FROM audit_seal WHERE tenant_id = :tenantId").param("tenantId", tenantId)
                .query(Long.class).single();
    }

    public long pendingCount(UUID tenantId) {
        return jdbc.sql("SELECT count(*) FROM audit_unsealed WHERE tenant_id = :tenantId").param("tenantId", tenantId)
                .query(Long.class).single();
    }

    private static Entry entry(ResultSet rs) throws SQLException {
        return new Entry(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("detail"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
