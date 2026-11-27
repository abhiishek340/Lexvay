package com.lexvay.llm;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read side of the stored model calls: totals only, never the stored input or output. */
@Repository
public class LlmUsageRepository {

    public record PurposeUsage(LlmPurpose purpose, long calls, long inputTokens, long outputTokens, long durationMs) {
    }

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
}
