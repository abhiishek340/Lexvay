package com.lexvay.eval;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads what the scorer and the cost report need. Runs under the evaluation tenant, so row-level security scopes it. */
@Repository
class EvalRepository {

    record ModelUse(int calls, long inputTokens, long outputTokens) {
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

    ModelUse modelUse() {
        return jdbc.sql("SELECT count(*) AS calls, coalesce(sum(input_tokens), 0) AS input, coalesce(sum(output_tokens), 0) AS output FROM llm_call")
                .query((rs, i) -> new ModelUse(rs.getInt("calls"), rs.getLong("input"), rs.getLong("output")))
                .single();
    }
}
