package com.lexvay.audit;

import com.lexvay.audit.AuditCanonical.Entry;
import com.lexvay.audit.AuditSealRepository.Head;
import com.lexvay.audit.AuditSealRepository.NewSeal;
import com.lexvay.common.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Chains the audit entries of every tenant (SEC-3, D-530). Entries are queued by the statement that writes them; this
 * seals them afterwards, up to {@value #BATCH_SIZE} per transaction, each tenant in its own transaction and as that
 * tenant. A sealer that dies between transactions loses nothing: the next one continues from the stored head.
 *
 * <p>Seal order is the order of sealing, which is the order of creation except for an entry that commits after a
 * later one was sealed: it takes the next number whatever its time (D-532).
 */
@Component
public class AuditSealer {

    public static final int BATCH_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(AuditSealer.class);

    private final AuditSealRepository seals;
    private final TransactionTemplate transaction;

    public AuditSealer(AuditSealRepository seals, TransactionTemplate transaction) {
        this.seals = seals;
        this.transaction = transaction;
    }

    /** Seals everything queued; returns how many entries were sealed. */
    public int sealPending() {
        return sealWhile(() -> true);
    }

    /**
     * Seals the queues of all tenants while {@code keepSealing} holds; a batch in progress always finishes. A tenant
     * that fails is logged and left for the next run, so it does not hold up the others.
     */
    public int sealWhile(BooleanSupplier keepSealing) {
        int sealed = 0;
        for (UUID tenantId : seals.tenantsWithQueue()) {
            try {
                int batch;
                do {
                    batch = keepSealing.getAsBoolean() ? sealBatch(tenantId) : 0;
                    sealed += batch;
                } while (batch == BATCH_SIZE);
            } catch (RuntimeException e) {
                log.error("Sealing the audit entries of tenant {} failed with {}; it will be retried", tenantId, e.getClass().getName());
            }
        }
        return sealed;
    }

    /** Seals the oldest queued entries of one tenant in one transaction; 0 when none are queued or another sealer holds the tenant. */
    public int sealBatch(UUID tenantId) {
        return TenantContext.runAs(tenantId, () -> transaction.execute(status -> seal(tenantId)));
    }

    private int seal(UUID tenantId) {
        if (!seals.tryLock(tenantId)) {
            return 0;
        }
        Head head = seals.head(tenantId).orElse(null);
        long seq = head == null ? 0 : head.seq();
        String prevHash = head == null ? AuditCanonical.GENESIS_HASH : head.rowHash();
        List<Entry> queued = seals.queued(tenantId, BATCH_SIZE);
        if (queued.isEmpty()) {
            return 0;
        }
        long previousSeq = seq;
        List<NewSeal> chain = new ArrayList<>(queued.size());
        for (Entry entry : queued) {
            seq++;
            String rowHash = AuditCanonical.rowHash(tenantId, seq, entry, prevHash);
            chain.add(new NewSeal(seq, entry.id(), prevHash, rowHash));
            prevHash = rowHash;
        }
        seals.insertSeals(tenantId, chain);
        seals.dequeueSealedAfter(tenantId, previousSeq);
        return chain.size();
    }
}
