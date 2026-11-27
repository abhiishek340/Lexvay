package com.lexvay.assessment.match;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The seam for passage retrieval. PostgreSQL full-text search today; nothing else is assumed. */
public interface PassageSearch {

    /** Delimiters of a match inside a headline. Control characters cannot be mistaken for page text. */
    char MATCH_START = '\u0001';
    char MATCH_END = '\u0002';

    /** How a requirement's candidate pages are ranked (D-170). BM25 is the default; ts_rank is the baseline it is measured against. */
    enum Ranker {
        BM25, TS_RANK;

        /** Reads the {@code lexvay.matching.ranker} value: {@code bm25} or {@code ts_rank}, in any case. */
        public static Ranker fromProperty(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("lexvay.matching.ranker must be bm25 or ts_rank, not '" + value + "'");
            }
        }
    }

    /**
     * Candidate pages for a requirement, best first: any word of the title, the text or the search terms may match, a
     * word of the title counting double under BM25. Ranked by the configured {@link Ranker}. Each passage says which of
     * the three found it ({@link Passage#matchedVia()}); a search term is a hint for retrieval, never evidence.
     */
    List<Passage> search(UUID programId, String title, String text, List<String> searchTerms, int limit);

    /** The same with a chosen ranker, so the evaluation can compare the two. */
    List<Passage> search(UUID programId, String title, String text, List<String> searchTerms, int limit, Ranker ranker);

    /**
     * Pages matching a person's own query (web search syntax: quoted phrases, OR, -term), best first, each with a
     * headline. Rule text is never searched. Both filters are optional.
     */
    List<PassageHit> searchWithHeadlines(UUID programId, String query, String kind, UUID courseVersionId, int limit);
}
