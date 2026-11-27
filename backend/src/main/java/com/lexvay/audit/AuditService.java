package com.lexvay.audit;

import com.lexvay.auth.CurrentUser;
import com.lexvay.common.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Product rule 9: every action is audited with who, when, what and on which record. Called inside the
 * same transaction as the mutation it describes. Background work passes {@code null} as the actor and
 * puts the job id in the detail (D-018).
 */
@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /** Records an action by the currently authenticated user, in the current tenant. */
    public void record(String action, String entityType, UUID entityId, Map<String, ?> detail) {
        recordAs(CurrentUser.idOrNull(), action, entityType, entityId, detail);
    }

    /** Records an action by an explicit actor (or {@code null} for the system), in the current tenant. */
    public void recordAs(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> detail) {
        repository.insert(TenantContext.require(), actorId, action, entityType, entityId, detail);
    }
}
