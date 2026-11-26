package com.lexvay.assessment.match;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Full-text search over the program's non-rule pages. The query is the OR of the requirement's words so
 * that pages sharing any key terms are candidates; ranking by ts_rank puts the best matches first
 * (D-051). Only words of letters and digits reach to_tsquery, so no operator injection is possible.
 */
@Component
public class PostgresPassageSearch implements PassageSearch {

    private final JdbcClient jdbc;

    public PostgresPassageSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Passage> search(UUID programId, String query, int limit) {
        String tsquery = toOrQuery(query);
        if (tsquery.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT d.id AS document_id, d.filename, d.kind, d.course_version_id, c.code AS course_code,
                       cv.required AS course_required, dp.page_number, dp.text
                FROM document_page dp
                JOIN document d ON d.id = dp.document_id
                LEFT JOIN course_version cv ON cv.id = d.course_version_id
                LEFT JOIN course c ON c.id = cv.course_id
                WHERE d.program_id = :programId AND d.kind <> 'RULE_TEXT'
                  AND dp.tsv @@ to_tsquery('english', :query)
                ORDER BY ts_rank(dp.tsv, to_tsquery('english', :query)) DESC, d.created_at, dp.page_number
                LIMIT :limit
                """)
                .param("programId", programId).param("query", tsquery).param("limit", limit)
                .query((rs, i) -> new Passage(rs.getObject("document_id", UUID.class), rs.getString("filename"), rs.getString("kind"),
                        rs.getObject("course_version_id", UUID.class), rs.getString("course_code"),
                        rs.getObject("course_required", Boolean.class), rs.getInt("page_number"), rs.getString("text")))
                .list();
    }

    static String toOrQuery(String text) {
        if (text == null) {
            return "";
        }
        Set<String> words = Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(w -> w.length() >= 3)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return String.join(" | ", words);
    }
}
