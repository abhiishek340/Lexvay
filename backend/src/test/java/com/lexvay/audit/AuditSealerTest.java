package com.lexvay.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.common.TenantContext;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/** SEC-3: the sealer builds one contiguous chain per tenant, and the verification finds what was changed behind it. */
class AuditSealerTest extends AbstractAuditSealTest {

    /**
     * The recomputation an auditor can run in SQL against a restored backup (docs/OPERATIONS.md): the same fields, the
     * same separator, the same time format. The test runs the text of the documentation's recipe.
     */
    static final String RECOMPUTE_IN_SQL = """
            SELECT s.seq FROM audit_seal s JOIN audit_log a ON a.id = s.audit_id
            WHERE s.row_hash <> encode(sha256(convert_to(concat_ws(E'\\x1f', s.tenant_id, s.seq, a.id, coalesce(a.user_id::text, ''),
                  a.action, a.entity_type, coalesce(a.entity_id::text, ''), a.detail::text,
                  to_char(a.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"'), s.prev_hash), 'UTF8')), 'hex')
            ORDER BY s.seq""";

    @Autowired TransactionTemplate transaction;
    @Autowired AuditSealRepository repository;

    UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = tenants.create("sealer").tenantId();
    }

    private List<Long> seqs() {
        return TenantContext.runAs(tenantId, () -> jdbc.sql("SELECT seq FROM audit_seal ORDER BY seq").query(Long.class).list());
    }

    private long entries() {
        return inTenant(tenantId, Long.class, "SELECT count(*) FROM audit_log");
    }

    private List<Long> recomputedInSqlThatDiffer() {
        return TenantContext.runAs(tenantId, () -> jdbc.sql(RECOMPUTE_IN_SQL).query(Long.class).list());
    }

    @Test
    void everyEntryIsQueuedWithItsWriteAndSealedFromOne() {
        record(tenantId, 25);
        assertThat(inTenant(tenantId, Long.class, "SELECT count(*) FROM audit_unsealed")).isEqualTo(entries());
        assertThat(seqs()).isEmpty();

        int sealed = sealAll(tenantId);

        assertThat(sealed).isEqualTo(entries());
        assertThat(seqs()).isEqualTo(LongStream.rangeClosed(1, sealed).boxed().toList());
        assertThat(inTenant(tenantId, Long.class, "SELECT count(*) FROM audit_unsealed")).as("queue emptied").isZero();
        assertThat(inTenant(tenantId, String.class, "SELECT prev_hash FROM audit_seal WHERE seq = 1")).isEqualTo("0".repeat(64));
        AuditVerification result = verify(tenantId);
        assertThat(result.intact()).isTrue();
        assertThat(result.sealed()).isEqualTo(sealed);
        assertThat(result.pending()).isZero();
        assertThat(result.firstBrokenSeq()).isNull();
        assertThat(result.headHash()).isEqualTo(inTenant(tenantId, String.class, "SELECT row_hash FROM audit_seal ORDER BY seq DESC LIMIT 1"));
        assertThat(result.lastSealedAt()).isNotNull();
    }

    @Test
    void theHashesAreWhatTheDocumentedSqlRecipeComputes() {
        record(tenantId, 10);
        TenantContext.runAs(tenantId, () -> audit.recordAs(null, "SEAL_TEST", "program", null, java.util.Map.of("quote", "say \"hi\"", "unicode", "Zoë – 日本")));
        sealAll(tenantId);

        assertThat(seqs()).isNotEmpty();
        assertThat(recomputedInSqlThatDiffer()).isEmpty();
    }

    @Test
    void nothingIsSealedWhenNothingIsQueuedAndANewEntryContinuesTheChain() {
        record(tenantId, 3);
        int first = sealAll(tenantId);
        assertThat(sealer.sealBatch(tenantId)).isZero();

        record(tenantId, 2);
        int second = sealAll(tenantId);

        assertThat(second).isEqualTo(2);
        assertThat(seqs()).isEqualTo(LongStream.rangeClosed(1, first + second).boxed().toList());
        assertThat(verify(tenantId).intact()).isTrue();
    }

    @Test
    void aSealerStoppedBetweenBatchesAndAnotherStartedContinuesTheChain() {
        record(tenantId, 1_200);
        long queued = entries();

        assertThat(sealer.sealBatch(tenantId)).as("one batch, then the sealer stops").isEqualTo(AuditSealer.BATCH_SIZE);
        assertThat(seqs()).hasSize(AuditSealer.BATCH_SIZE);

        AuditSealer restarted = new AuditSealer(repository, transaction);
        int rest = 0;
        for (int batch; (batch = restarted.sealBatch(tenantId)) > 0; ) {
            rest += batch;
        }

        assertThat(AuditSealer.BATCH_SIZE + rest).isEqualTo(queued);
        assertThat(seqs()).isEqualTo(LongStream.rangeClosed(1, queued).boxed().toList());
        assertThat(verify(tenantId).intact()).isTrue();
        assertThat(recomputedInSqlThatDiffer()).isEmpty();
    }

    @Test
    void sealPendingSealsEveryTenantsQueue() {
        UUID other = tenants.create("sealer-other").tenantId();
        record(tenantId, 5);
        record(other, 5);

        sealer.sealPending();

        assertThat(inTenant(tenantId, Long.class, "SELECT count(*) FROM audit_unsealed")).isZero();
        assertThat(inTenant(other, Long.class, "SELECT count(*) FROM audit_unsealed")).isZero();
        assertThat(verify(tenantId).intact()).isTrue();
        assertThat(verify(other).intact()).isTrue();
    }

    @Test
    void eightThreadsAuditingWhileTwoSealersRunGiveOneContiguousChain() throws Exception {
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads + 2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writing = new AtomicBoolean(true);
        List<Future<?>> writers = new ArrayList<>();
        List<Future<?>> sealers = new ArrayList<>();
        try {
            for (int w = 0; w < threads; w++) {
                writers.add(pool.submit(() -> {
                    start.await();
                    record(tenantId, perThread);
                    return null;
                }));
            }
            for (int s = 0; s < 2; s++) {
                sealers.add(pool.submit(() -> {
                    start.await();
                    while (writing.get()) {
                        sealer.sealBatch(tenantId);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> writer : writers) {
                writer.get();
            }
            writing.set(false);
            for (Future<?> sealerRun : sealers) {
                sealerRun.get();
            }
        } finally {
            pool.shutdownNow();
        }
        sealAll(tenantId);

        long total = entries();
        assertThat(total).isGreaterThanOrEqualTo((long) threads * perThread);
        assertThat(seqs()).isEqualTo(LongStream.rangeClosed(1, total).boxed().toList());
        AuditVerification result = verify(tenantId);
        assertThat(result.intact()).isTrue();
        assertThat(result.sealed()).isEqualTo(total);
        assertThat(recomputedInSqlThatDiffer()).isEmpty();
    }

    @Test
    void anEntryCommittedLateTakesTheNextNumberWhateverItsTime() throws Exception {
        record(tenantId, 3);
        sealAll(tenantId);
        UUID late = UUID.randomUUID();
        // An entry that was created earlier than the sealed ones but became visible only now.
        asSuperuser("audit_log", "audit_log_immutable", "WITH entry AS (INSERT INTO audit_log (id, tenant_id, action, entity_type, created_at) "
                + "VALUES (?, ?, 'LATE_ENTRY', 'program', now() - interval '1 day') RETURNING id, tenant_id, created_at) "
                + "INSERT INTO audit_unsealed (audit_id, tenant_id, created_at) SELECT id, tenant_id, created_at FROM entry", late, tenantId);

        sealAll(tenantId);

        assertThat(inTenant(tenantId, Long.class, "SELECT seq FROM audit_seal WHERE audit_id = ?", late)).isEqualTo(seqs().size());
        assertThat(verify(tenantId).intact()).isTrue();
        assertThat(recomputedInSqlThatDiffer()).isEmpty();
    }

    @Test
    void changingTheDetailOfASealedEntryIsReportedAtItsSeq() throws Exception {
        record(tenantId, 10);
        sealAll(tenantId);
        UUID victim = auditIdOfSeq(tenantId, 6);

        assertThat(asSuperuser("audit_log", "audit_log_immutable", "UPDATE audit_log SET detail = '{\"n\": 999}'::jsonb WHERE id = ?", victim)).isOne();

        AuditVerification result = verify(tenantId);
        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(6);
        assertThat(result.firstBrokenReason()).contains("no longer matches its seal");
        assertThat(recomputedInSqlThatDiffer()).containsExactly(6L);
    }

    @Test
    void changingAnythingElseOfASealedEntryIsReportedToo() throws Exception {
        record(tenantId, 6);
        sealAll(tenantId);

        asSuperuser("audit_log", "audit_log_immutable", "UPDATE audit_log SET action = 'SOMETHING_ELSE' WHERE id = ?", auditIdOfSeq(tenantId, 4));
        asSuperuser("audit_log", "audit_log_immutable", "UPDATE audit_log SET created_at = created_at + interval '1 microsecond' WHERE id = ?",
                auditIdOfSeq(tenantId, 2));

        assertThat(verify(tenantId).firstBrokenSeq()).as("the first of the two").isEqualTo(2);
    }

    @Test
    void aSealedAuditRowCannotBeDeletedEvenWithItsTriggerOff() {
        record(tenantId, 3);
        sealAll(tenantId);

        assertThatThrownBy(() -> asSuperuser("audit_log", "audit_log_immutable", "DELETE FROM audit_log WHERE id = ?", auditIdOfSeq(tenantId, 2)))
                .isInstanceOf(SQLException.class).hasMessageContaining("audit_seal");
        assertThat(verify(tenantId).intact()).isTrue();
    }

    @Test
    void aDeletedSealLeavesAGapThatIsReported() throws Exception {
        record(tenantId, 8);
        sealAll(tenantId);

        assertThat(asSuperuser("audit_seal", "audit_seal_immutable", "DELETE FROM audit_seal WHERE audit_id = ?", auditIdOfSeq(tenantId, 5))).isOne();

        AuditVerification result = verify(tenantId);
        assertThat(result.intact()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(5);
        assertThat(result.firstBrokenReason()).contains("missing");
    }

    @Test
    void aRewrittenSealIsReportedAtThatSeq() throws Exception {
        record(tenantId, 8);
        sealAll(tenantId);

        asSuperuser("audit_seal", "audit_seal_immutable", "UPDATE audit_seal SET prev_hash = ? WHERE audit_id = ?", "e".repeat(64), auditIdOfSeq(tenantId, 3));

        AuditVerification result = verify(tenantId);
        assertThat(result.firstBrokenSeq()).isEqualTo(3);
        assertThat(result.firstBrokenReason()).contains("does not follow");
    }

    @Test
    void anEntryNotYetSealedIsPendingAndNotCovered() {
        record(tenantId, 4);
        sealAll(tenantId);
        record(tenantId, 3);
        long written = entries();

        AuditVerification result = verify(tenantId);

        assertThat(result.intact()).isTrue();
        assertThat(result.pending()).isEqualTo(3);
        assertThat(result.sealed()).isEqualTo(written - 3);
    }

    @Test
    void verifyingAWalkLongerThanOnePageChecksEverySeal() throws Exception {
        record(tenantId, AuditVerificationService.PAGE_SIZE + 250);
        sealAll(tenantId);
        long total = entries();
        assertThat(total).isGreaterThan(AuditVerificationService.PAGE_SIZE);

        assertThat(verify(tenantId).intact()).isTrue();

        asSuperuser("audit_log", "audit_log_immutable", "UPDATE audit_log SET detail = '{}'::jsonb WHERE id = ?", auditIdOfSeq(tenantId, total - 1));
        assertThat(verify(tenantId).firstBrokenSeq()).as("a break on the second page").isEqualTo(total - 1);
    }
}
