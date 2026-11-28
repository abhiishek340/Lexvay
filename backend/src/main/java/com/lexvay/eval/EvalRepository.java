package com.lexvay.eval;

import com.lexvay.llm.LlmPurpose;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads what the scorer and the cost report need. Runs under the evaluation tenant, so row-level security scopes it. */
@Repository
class EvalRepository {

    record ModelUse(int calls, long inputTokens, long outputTokens, boolean tokensEstimated, long durationMs) {
    }

    /**
     * One stored page of a program document, with the document's content hash and whether its course is required of every
     * student ({@code courseRequired} is null for a document that belongs to no course).
     */
    record PageText(UUID documentId, String filename, String sha256, Boolean courseRequired, int page, String text) {
    }

    private final JdbcClient jdbc;

    EvalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every stored span with the stored page it cites. */
    List<EvalScorer.Span> spans() {
        return jdbc.sql("""
                SELECT d.filename, es.page_number, es.quote, dp.text AS page_text
                FROM evidence_span es
                JOIN document d ON d.id = es.document_id
                LEFT JOIN document_page dp ON dp.document_id = es.document_id AND dp.page_number = es.page_number
                ORDER BY d.filename, es.page_number, es.char_start
                """)
                .query((rs, i) -> new EvalScorer.Span(rs.getString("filename"), rs.getInt("page_number"), rs.getString("quote"),
                        rs.getString("page_text")))
                .list();
    }

    /** Every page of every program document, in file-name and page order. Rule text belongs to no program and is left out. */
    List<PageText> pages() {
        return jdbc.sql("""
                SELECT d.id, d.filename, d.sha256, cv.required, dp.page_number, dp.text
                FROM document d
                JOIN document_page dp ON dp.document_id = d.id
                LEFT JOIN course_version cv ON cv.id = d.course_version_id
                WHERE d.kind <> 'RULE_TEXT'
                ORDER BY d.filename, dp.page_number
                """)
                .query((rs, i) -> new PageText(rs.getObject("id", UUID.class), rs.getString("filename"), rs.getString("sha256"),
                        rs.getObject("required", Boolean.class), rs.getInt("page_number"), rs.getString("text")))
                .list();
    }

    /** The model calls of the pipeline, or, with {@code baseline}, those of the generic baseline, which share the tenant. */
    ModelUse modelUse(boolean baseline) {
        return jdbc.sql("""
                SELECT count(*) AS calls, coalesce(sum(input_tokens), 0) AS input, coalesce(sum(output_tokens), 0) AS output,
                       coalesce(bool_or(request ->> 'tokensEstimated' = 'true'), false) AS estimated,
                       coalesce(sum(duration_ms), 0) AS duration
                FROM llm_call
                WHERE (purpose = :baselinePurpose) = :baseline
                """)
                .param("baselinePurpose", LlmPurpose.EVAL_BASELINE.name()).param("baseline", baseline)
                .query((rs, i) -> new ModelUse(rs.getInt("calls"), rs.getLong("input"), rs.getLong("output"), rs.getBoolean("estimated"),
                        rs.getLong("duration")))
                .single();
    }
}
