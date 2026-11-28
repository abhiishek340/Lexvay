package com.lexvay.llm;

import com.lexvay.audit.AuditService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records a refused model call as {@code LLM_CALL_REFUSED} (product rule 9) and logs it without any content. It writes
 * in its own transaction, like {@link LlmCallRecorder}, so the record survives the failing job rolling back.
 */
@Component
public class LlmRefusalAudit {

    private static final Logger log = LoggerFactory.getLogger(LlmRefusalAudit.class);

    private final AuditService audit;
    private final TransactionTemplate newTransaction;

    public LlmRefusalAudit(AuditService audit, PlatformTransactionManager transactionManager) {
        this.audit = audit;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(LlmRequest request, String model, LlmCallBlockedException refusal) {
        log.warn("Model call refused: {} for {} with model {}", refusal.reason(), request.purpose(), model);
        newTransaction.executeWithoutResult(status -> audit.record("LLM_CALL_REFUSED", "llm_call", null, Map.of(
                "reason", refusal.reason().name(), "message", refusal.getMessage(),
                "purpose", request.purpose().name(), "model", model)));
    }
}
