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
 * Full-text search over the program's non-rule pages. Both entry points run one query shape: the matching pages
 * are ranked and cut to the top rows first, and only then does the outer select build its columns, so an
 * expensive headline is computed for at most {@code limit} pages. They differ only in how the query text becomes
 * a tsquery, how a page is ranked, and what the outer select returns ({@link Mode}).
 *
 * <p>A requirement's query is the OR of its words ranked by ts_rank (D-051); only words of letters and digits
 * reach to_tsquery, so no operator injection is possible. A reviewer's query goes to websearch_to_tsquery, which
 * accepts any text and never raises a syntax error (D-150). In both cases the text is a bound parameter.
 */
@Component
public class PostgresPassageSearch implements PassageSearch {

    private static final String HEADLINE_OPTIONS = "MaxFragments=2, MaxWords=40, MinWords=15, StartSel=" + MATCH_START
            + ", StopSel=" + MATCH_END;

    /**
     * SQL fragments, each taking the tsquery expression as its one %s. They are constants of this class; user
     * input only ever reaches the database as the bound :query.
     */
    private record Mode(String tsquery, String rank, String projection) {

        String rankExpression() {
            return rank.formatted(tsquery);
        }

        String projectionExpression() {
            return projection.formatted(tsquery);
        }
    }

    private static final Mode CANDIDATES = new Mode("to_tsquery('english', :query)", "ts_rank(dp.tsv, %s)", "h.text");

    private static final Mode SEARCH = new Mode("websearch_to_tsquery('english', :query)", "ts_rank_cd(dp.tsv, %s, 32)",
            "ts_headline('english', translate(h.text, :delimiters, ''), %s, :headlineOptions) AS headline");

    private static final String ORDER = "rank DESC, created_at, page_number, document_id";

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
        return statement(CANDIDATES, programId, tsquery, null, null, limit)
                .query((rs, i) -> new Passage(rs.getObject("document_id", UUID.class), rs.getString("filename"), rs.getString("kind"),
                        rs.getObject("course_version_id", UUID.class), rs.getString("course_code"),
                        rs.getObject("course_required", Boolean.class), rs.getInt("page_number"), rs.getString("text")))
                .list();
    }

    @Override
    public List<PassageHit> searchWithHeadlines(UUID programId, String query, String kind, UUID courseVersionId, int limit) {
        return statement(SEARCH, programId, query, kind, courseVersionId, limit)
                .param("delimiters", String.valueOf(MATCH_START) + MATCH_END)
                .param("headlineOptions", HEADLINE_OPTIONS)
                .query((rs, i) -> new PassageHit(rs.getObject("document_id", UUID.class), rs.getString("filename"),
                        rs.getString("kind"), rs.getString("course_code"), rs.getObject("course_required", Boolean.class),
                        rs.getInt("page_number"), rs.getFloat("rank"), rs.getString("headline")))
                .list();
    }

    private JdbcClient.StatementSpec statement(Mode mode, UUID programId, String query, String kind, UUID courseVersionId,
                                               int limit) {
        return jdbc.sql(sql(mode))
                .param("programId", programId).param("query", query).param("kind", kind)
                .param("courseVersionId", courseVersionId).param("limit", limit);
    }

    private static String sql(Mode mode) {
        return """
                WITH scored AS (
                    SELECT d.id AS document_id, d.filename, d.kind, d.course_version_id, c.code AS course_code,
                           cv.required AS course_required, d.created_at, dp.page_number, dp.text,
                           %2$s AS rank
                    FROM document_page dp
                    JOIN document d ON d.id = dp.document_id
                    LEFT JOIN course_version cv ON cv.id = d.course_version_id
                    LEFT JOIN course c ON c.id = cv.course_id
                    WHERE d.program_id = :programId AND d.kind <> 'RULE_TEXT'
                      AND (CAST(:kind AS text) IS NULL OR d.kind = :kind)
                      AND (CAST(:courseVersionId AS uuid) IS NULL OR d.course_version_id = :courseVersionId)
                      AND dp.tsv @@ %1$s
                ), top AS (
                    SELECT * FROM scored ORDER BY %4$s LIMIT :limit
                )
                SELECT h.document_id, h.filename, h.kind, h.course_version_id, h.course_code, h.course_required,
                       h.page_number, h.rank, %3$s
                FROM top h
                ORDER BY %4$s
                """.formatted(mode.tsquery(), mode.rankExpression(), mode.projectionExpression(), ORDER);
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
