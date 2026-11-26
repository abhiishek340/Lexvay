package com.lexvay.llm;

import com.lexvay.common.TenantContext;
import java.sql.Types;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Stores every model call with its raw input and output (D-014). Writes in its own transaction so the
 * record survives when the surrounding job fails and rolls back.
 */
@Service
public class LlmCallRecorder {

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate newTransaction;

    public LlmCallRecorder(JdbcClient jdbc, ObjectMapper json, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.json = json;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public UUID record(LlmRequest request, LlmResponse response, String promptVersion, UUID ruleVersionId,
                       UUID assessmentId, UUID requirementId) {
        UUID id = UUID.randomUUID();
        UUID tenantId = TenantContext.require();
        String requestJson = json.writeValueAsString(java.util.Map.of(
                "scriptKey", request.scriptKey(),
                "system", request.systemPrompt(),
                "user", request.userPrompt(),
                "outputSchema", request.outputSchema(),
                "maxOutputTokens", request.maxOutputTokens()));
        newTransaction.executeWithoutResult(status -> jdbc.sql("""
                INSERT INTO llm_call (id, tenant_id, rule_version_id, assessment_id, requirement_id, purpose, model,
                                      prompt_version, request, response, input_tokens, output_tokens, duration_ms)
                VALUES (:id, :tenantId, :ruleVersionId, :assessmentId, :requirementId, :purpose, :model,
                        :promptVersion, :request, :response, :inputTokens, :outputTokens, :durationMs)
                """)
                .param("id", id).param("tenantId", tenantId).param("ruleVersionId", ruleVersionId)
                .param("assessmentId", assessmentId).param("requirementId", requirementId)
                .param("purpose", request.purpose().name()).param("model", response.model())
                .param("promptVersion", promptVersion).param("request", requestJson, Types.OTHER)
                .param("response", response.text()).param("inputTokens", response.inputTokens())
                .param("outputTokens", response.outputTokens()).param("durationMs", (int) response.durationMs())
                .update());
        return id;
    }

    public long countForRuleVersion(UUID ruleVersionId) {
        return jdbc.sql("SELECT count(*) FROM llm_call WHERE rule_version_id = :id").param("id", ruleVersionId)
                .query(Long.class).single();
    }
}
