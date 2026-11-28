package com.lexvay.audit;

import com.lexvay.audit.AuditChainCheck.Broken;
import com.lexvay.audit.AuditChainCheck.Sealed;
import com.lexvay.audit.AuditSealRepository.Head;
import com.lexvay.common.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomputes the hash chain of the current tenant from what is stored (SEC-3, D-534): every seal in order, in pages of
 * {@value #PAGE_SIZE} by keyset, stopping at the first that does not hold. One transaction, which also records that
 * someone checked: asking is an action people are accountable for, as handing out the log is (D-377).
 *
 * <p>The walk proves that sealed entries are unchanged and that no seal inside the chain was removed. It cannot see
 * seals removed from the end; that is what comparing {@code headHash} and {@code sealed} with an earlier
 * {@code AUDIT_VERIFIED} entry, or with an exported copy, is for.
 */
@Service
public class AuditVerificationService {

    static final int PAGE_SIZE = 1000;

    private final AuditSealRepository seals;
    private final AuditService audit;

    public AuditVerificationService(AuditSealRepository seals, AuditService audit) {
        this.seals = seals;
        this.audit = audit;
    }

    @Transactional
    public AuditVerification verify() {
        UUID tenantId = TenantContext.require();
        Optional<Broken> broken = firstBroken(tenantId);
        Optional<Head> head = seals.head(tenantId);
        AuditVerification result = new AuditVerification(
                seals.sealedCount(tenantId), seals.pendingCount(tenantId), broken.isEmpty(),
                broken.map(Broken::seq).orElse(null), broken.map(Broken::reason).orElse(null),
                head.map(Head::sealedAt).orElse(null), head.map(Head::rowHash).orElse(null));
        audit.record("AUDIT_VERIFIED", "audit_log", null, auditDetail(result));
        return result;
    }

    private Optional<Broken> firstBroken(UUID tenantId) {
        AuditChainCheck check = new AuditChainCheck(tenantId);
        long after = 0;
        List<Sealed> page;
        do {
            page = seals.sealsAfter(tenantId, after, PAGE_SIZE);
            for (Sealed seal : page) {
                Optional<Broken> broken = check.next(seal);
                if (broken.isPresent()) {
                    return broken;
                }
                after = seal.seq();
            }
        } while (page.size() == PAGE_SIZE);
        return Optional.empty();
    }

    /** The audit entry of a verification: the outcome, the seals covered and the head they ended in. */
    private static Map<String, Object> auditDetail(AuditVerification result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("intact", result.intact());
        detail.put("sealed", result.sealed());
        if (result.headHash() != null) {
            detail.put("headHash", result.headHash());
        }
        if (result.firstBrokenSeq() != null) {
            detail.put("firstBrokenSeq", result.firstBrokenSeq());
        }
        return detail;
    }
}
