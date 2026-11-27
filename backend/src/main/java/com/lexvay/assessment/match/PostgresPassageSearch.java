package com.lexvay.assessment.match;

import com.lexvay.document.TextSource;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Full-text search over the program's non-rule pages. Every entry point runs one query shape: the matching pages
 * are ranked and cut to the top rows first, and only then does the outer select build its columns, so an
 * expensive headline is computed for at most {@code limit} pages. The entry points differ only in how the query
 * text becomes a tsquery, how a page is ranked, and what the outer select returns ({@link Mode}).
 *
 * <p>A requirement is ranked by BM25 (D-170) or, as the baseline it is measured against, by ts_rank over the OR of
 * its words (D-051). Its words are those of its title, its text and its search terms (F7c, D-251). BM25 is computed
 * by the statement itself: the query lexemes come from to_tsvector, N, df and the average page length from the
 * program's own pages, and the only data read per page is the handful of lexemes asked for. A reviewer's query goes to websearch_to_tsquery, which accepts any text and never raises a
 * syntax error (D-150). Whatever the path, the text is a bound parameter and no tsquery is built from it in Java
 * except the baseline's, whose words are letters and digits only.
 */
@Component
public class PostgresPassageSearch implements PassageSearch {

    /** Okapi BM25 constants (D-170). */
    static final double K1 = 1.2;
    static final double B = 0.75;

    /** Weight of a lexeme of the requirement's title; one of its text takes 1.0, and a lexeme in both takes the larger. */
    static final double TITLE_BOOST = 2.0;
    static final double TEXT_BOOST = 1.0;
    /** A lexeme of a search term weighs as much as one of the text, so a page that has a word of the text itself wins a tie. */
    static final double TERMS_BOOST = 1.0;

    private static final String HEADLINE_OPTIONS = "MaxFragments=2, MaxWords=40, MinWords=15, StartSel=" + MATCH_START
            + ", StopSel=" + MATCH_END;

    /**
     * What differs between the entry points, as SQL fragments of one shared statement. They are constants of this
     * class; user input only ever reaches the database as a bound parameter.
     *
     * @param prelude    common table expressions that come before the page query, each followed by a comma
     * @param join       extra join of the page query
     * @param match      condition a page must meet to be a candidate
     * @param rank       the score, higher first
     * @param matchedVia the {@link MatchSource} that found the page, as a column of the page query
     * @param projection the columns the outer select adds after the page columns
     */
    private record Mode(String prelude, String join, String match, String rank, String matchedVia, String projection) {
    }

    private static final String TS_RANK_QUERY = "to_tsquery('english', :query)";

    /** The OR of the requirement's lexemes, as a tsquery built from quoted lexemes: no stemming, no operators. */
    private static final String BM25_QUERY = "(SELECT query FROM lexq)";

    /** The excerpt source: only a page too long to be sent whole needs a headline. */
    private static String candidateProjection(String tsquery) {
        return "h.text, CASE WHEN length(h.text) > :wholePageChars THEN ts_headline('english', h.text, " + tsquery
                + ", :excerptOptions) END AS headline, h.matched_via, " + MATCHED_TERMS;
    }

    /**
     * The search terms that occur on a page found only through terms: those sharing a lexeme with the page. Only the
     * handful of pages that survived the limit are looked at, and only when the terms were what found them.
     */
    private static final String MATCHED_TERMS = """
            CASE WHEN h.matched_via = :termsSource THEN ARRAY(
                SELECT u.term FROM unnest(CAST(:searchTerms AS text[])) WITH ORDINALITY AS u(term, n)
                WHERE tsvector_to_array(to_tsvector('english', u.term))
                      && (SELECT tsvector_to_array(p.tsv) FROM document_page p WHERE p.id = h.page_id)
                ORDER BY u.n)
            ELSE CAST('{}' AS text[]) END AS matched_terms""";

    /** ts_rank has no weights, so the title and the text are one source: a page found by either is {@code TEXT}. */
    private static final Mode TS_RANK = new Mode("", "", "dp.tsv @@ " + TS_RANK_QUERY, "ts_rank(dp.tsv, " + TS_RANK_QUERY + ")",
            "CASE WHEN dp.tsv @@ to_tsquery('english', :textQuery) THEN CAST(:textSource AS text) ELSE CAST(:termsSource AS text) END",
            candidateProjection(TS_RANK_QUERY));

    /**
     * The query lexemes are those of the title (boost 2), the text (1) and the search terms (1); a lexeme of several
     * sources keeps the highest boost and, on a tie, the source of the requirement's own words. A page is labelled with
     * the source of the highest-boost lexeme that matched it (D-251).
     *
     * Score(p) = sum over the query lexemes t in p of boost(t) * idf(t) * tf(t,p) * (k1 + 1) / (tf + k1 * (1 - b + b * len(p) / avglen)),
     * with idf(t) = ln(1 + (N - df(t) + 0.5) / (df(t) + 0.5)). N, df and avglen are over the program's non-rule pages
     * and len(p) is length(tsv). setweight and ts_filter cut each page's tsvector down to the query lexemes before
     * unnest, which is what keeps this fast on hundreds of pages.
     */
    private static final String BM25_PRELUDE = """
            q AS (
                SELECT DISTINCT ON (lexeme) lexeme, boost, source, priority
                FROM (SELECT unnest(tsvector_to_array(to_tsvector('english', :requirementTitle))) AS lexeme,
                             CAST(:titleBoost AS float8) AS boost, CAST(:titleSource AS text) AS source, 1 AS priority
                      UNION ALL
                      SELECT unnest(tsvector_to_array(to_tsvector('english', :requirementText))),
                             CAST(:textBoost AS float8), CAST(:textSource AS text), 2
                      UNION ALL
                      SELECT unnest(tsvector_to_array(to_tsvector('english', array_to_string(CAST(:searchTerms AS text[]), ' ')))),
                             CAST(:termsBoost AS float8), CAST(:termsSource AS text), 3) AS t
                ORDER BY lexeme, boost DESC, priority
            ), lexq AS (
                SELECT string_agg('''' || replace(replace(lexeme, '\\', '\\\\'), '''', '''''') || '''', ' | ')::tsquery AS query FROM q
            ), wanted AS (
                SELECT array_agg(lexeme) AS lexemes FROM q
            ), pages AS (
                SELECT dp.id, dp.tsv, length(dp.tsv) AS len
                FROM document_page dp
                JOIN document d ON d.id = dp.document_id
                WHERE d.program_id = :programId AND d.kind <> 'RULE_TEXT'
            ), stats AS (
                SELECT count(*)::float8 AS n, avg(len)::float8 AS avglen FROM pages
            ), hits AS (
                SELECT p.id, p.len, u.lexeme, cardinality(u.positions) AS tf
                FROM pages p
                CROSS JOIN wanted w
                CROSS JOIN LATERAL unnest(ts_filter(setweight(p.tsv, 'A', w.lexemes), '{a}')) u
            ), df AS (
                SELECT lexeme, count(*)::float8 AS df FROM hits GROUP BY lexeme
            ), bm25 AS (
                SELECT h.id AS page_id,
                       (array_agg(q.source ORDER BY q.boost DESC, q.priority))[1] AS matched_via,
                       sum(q.boost * ln(1 + (s.n - df.df + 0.5) / (df.df + 0.5))
                           * h.tf * (:bm25K1 + 1) / (h.tf + :bm25K1 * (1 - :bm25B + :bm25B * h.len / s.avglen))) AS score
                FROM hits h
                JOIN q ON q.lexeme = h.lexeme
                JOIN df ON df.lexeme = h.lexeme
                CROSS JOIN stats s
                GROUP BY h.id
            ),
            """;

    private static final Mode BM25 = new Mode(BM25_PRELUDE, "JOIN bm25 ON bm25.page_id = dp.id", "TRUE", "bm25.score",
            "bm25.matched_via", candidateProjection(BM25_QUERY));

    private static final String SEARCH_QUERY = "websearch_to_tsquery('english', :query)";

    private static final Mode SEARCH = new Mode("", "", "dp.tsv @@ " + SEARCH_QUERY, "ts_rank_cd(dp.tsv, " + SEARCH_QUERY + ", 32)",
            "CAST(NULL AS text)",
            "ts_headline('english', translate(h.text, :delimiters, ''), " + SEARCH_QUERY + ", :headlineOptions) AS headline");

    private static final String ORDER = "rank DESC, created_at, page_number, document_id";

    private final JdbcClient jdbc;
    private final Ranker ranker;

    public PostgresPassageSearch(JdbcClient jdbc, @Value("${lexvay.matching.ranker:bm25}") String ranker) {
        this.jdbc = jdbc;
        this.ranker = Ranker.fromProperty(ranker);
    }

    @Override
    public List<Passage> search(UUID programId, String title, String text, List<String> searchTerms, int limit) {
        return search(programId, title, text, searchTerms, limit, ranker);
    }

    @Override
    public List<Passage> search(UUID programId, String title, String text, List<String> searchTerms, int limit, Ranker ranker) {
        JdbcClient.StatementSpec statement;
        if (ranker == Ranker.BM25) {
            statement = statement(BM25, programId, null, null, limit)
                    .param("requirementTitle", title).param("requirementText", text)
                    .param("titleBoost", TITLE_BOOST).param("textBoost", TEXT_BOOST).param("termsBoost", TERMS_BOOST)
                    .param("bm25K1", K1).param("bm25B", B);
        } else {
            String tsquery = toOrQuery(title + " " + text + " " + String.join(" ", searchTerms));
            if (tsquery.isEmpty()) {
                return List.of();
            }
            statement = statement(TS_RANK, programId, null, null, limit).param("query", tsquery)
                    .param("textQuery", toOrQuery(title + " " + text));
        }
        return statement
                .param("searchTerms", searchTerms.toArray(String[]::new))
                .param("titleSource", MatchSource.TITLE.name()).param("textSource", MatchSource.TEXT.name())
                .param("termsSource", MatchSource.TERMS.name())
                .param("wholePageChars", PassageExcerpter.WHOLE_PAGE_CHARS).param("excerptOptions", PassageExcerpter.HEADLINE_OPTIONS)
                .query((rs, i) -> {
                    String page = rs.getString("text");
                    return new Passage(rs.getObject("document_id", UUID.class), rs.getString("filename"), rs.getString("kind"),
                            rs.getObject("course_version_id", UUID.class), rs.getString("course_code"),
                            rs.getObject("course_required", Boolean.class), rs.getInt("page_number"), page,
                            PassageExcerpter.excerpt(page, rs.getString("headline")), rs.getDouble("rank"),
                            TextSource.valueOf(rs.getString("text_source")), rs.getObject("ocr_confidence", Double.class),
                            MatchSource.valueOf(rs.getString("matched_via")), List.of((String[]) rs.getArray("matched_terms").getArray()));
                })
                .list();
    }

    @Override
    public List<PassageHit> searchWithHeadlines(UUID programId, String query, String kind, UUID courseVersionId, int limit) {
        return statement(SEARCH, programId, kind, courseVersionId, limit)
                .param("query", query)
                .param("delimiters", String.valueOf(MATCH_START) + MATCH_END)
                .param("headlineOptions", HEADLINE_OPTIONS)
                .query((rs, i) -> new PassageHit(rs.getObject("document_id", UUID.class), rs.getString("filename"),
                        rs.getString("kind"), rs.getString("course_code"), rs.getObject("course_required", Boolean.class),
                        rs.getInt("page_number"), rs.getFloat("rank"), rs.getString("headline")))
                .list();
    }

    private JdbcClient.StatementSpec statement(Mode mode, UUID programId, String kind, UUID courseVersionId, int limit) {
        return jdbc.sql(sql(mode))
                .param("programId", programId).param("kind", kind)
                .param("courseVersionId", courseVersionId).param("limit", limit);
    }

    private static String sql(Mode mode) {
        return """
                WITH %1$s
                scored AS (
                    SELECT d.id AS document_id, d.filename, d.kind, d.course_version_id, c.code AS course_code,
                           cv.required AS course_required, d.created_at, dp.page_number, dp.text,
                           dp.text_source, CAST(dp.ocr_confidence AS double precision) AS ocr_confidence,
                           dp.id AS page_id, %4$s AS rank, %7$s AS matched_via
                    FROM document_page dp
                    JOIN document d ON d.id = dp.document_id
                    %2$s
                    LEFT JOIN course_version cv ON cv.id = d.course_version_id
                    LEFT JOIN course c ON c.id = cv.course_id
                    WHERE d.program_id = :programId AND d.kind <> 'RULE_TEXT'
                      AND (CAST(:kind AS text) IS NULL OR d.kind = :kind)
                      AND (CAST(:courseVersionId AS uuid) IS NULL OR d.course_version_id = :courseVersionId)
                      AND %3$s
                ), top AS (
                    SELECT * FROM scored ORDER BY %6$s LIMIT :limit
                )
                SELECT h.document_id, h.filename, h.kind, h.course_version_id, h.course_code, h.course_required,
                       h.page_number, h.rank, h.text_source, h.ocr_confidence, %5$s
                FROM top h
                ORDER BY %6$s
                """.formatted(mode.prelude(), mode.join(), mode.match(), mode.rank(), mode.projection(), ORDER, mode.matchedVia());
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
