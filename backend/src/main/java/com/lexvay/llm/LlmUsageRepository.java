package com.lexvay.llm;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the stored model calls: totals only, never the stored input or output. */
@Repository
public class LlmUsageRepository {

    public record PurposeUsage(LlmPurpose purpose, long calls, long inputTokens, long outputTokens, long durationMs) {
    }

    /** The tokens one model used in a period, summed over calls. */
    public record ModelUsage(String model, long inputTokens, long outputTokens) {
    }

    private static final String SPEND_GATE_FLAG = "SELECT set_config('lexvay.spend_gate', 'on', true)";

    private final JdbcClient jdbc;

    public LlmUsageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One aggregate over the assessment's calls, grouped by purpose. Calls without token counts add zero. */
    public List<PurposeUsage> forAssessment(UUID assessmentId) {
        return jdbc.sql("""
                SELECT purpose, count(*) AS calls,
                       coalesce(sum(input_tokens), 0) AS input_tokens,
                       coalesce(sum(output_tokens), 0) AS output_tokens,
                       coalesce(sum(duration_ms), 0) AS duration_ms
                FROM llm_call WHERE assessment_id = :assessmentId
                GROUP BY purpose ORDER BY purpose
                """)
                .param("assessmentId", assessmentId)
                .query((rs, i) -> new PurposeUsage(LlmPurpose.valueOf(rs.getString("purpose")), rs.getLong("calls"),
                        rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getLong("duration_ms")))
                .list();
    }

    /**
     * What every tenant has used since the start of the current UTC month, by model, leaving out one model whose calls
     * cost nothing (the scripted one). The budget belongs to the deployment's one provider account, so this crosses
     * tenants under the transaction-local spend-gate flag that only the {@code llm_call} SELECT policy honours (V022).
     * It runs in its own transaction so the flag never outlives this query inside a caller's transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<ModelUsage> thisMonthExcluding(String freeModel) {
        jdbc.sql(SPEND_GATE_FLAG).query().singleRow();
        return jdbc.sql("""
                SELECT model, coalesce(sum(input_tokens), 0) AS input_tokens, coalesce(sum(output_tokens), 0) AS output_tokens
                FROM llm_call
                WHERE created_at >= date_trunc('month', now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AND model <> :freeModel
                GROUP BY model
                """)
                .param("freeModel", freeModel)
                .query((rs, i) -> new ModelUsage(rs.getString("model"), rs.getLong("input_tokens"), rs.getLong("output_tokens")))
                .list();
    }
}
