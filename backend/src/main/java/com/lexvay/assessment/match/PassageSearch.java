package com.lexvay.assessment.match;

import java.util.List;
import java.util.UUID;

/** The seam for passage retrieval. PostgreSQL full-text search today; nothing else is assumed. */
public interface PassageSearch {

    /** Delimiters of a match inside a headline. Control characters cannot be mistaken for page text. */
    char MATCH_START = '\u0001';
    char MATCH_END = '\u0002';

    /** Candidate pages for a requirement: any of the query's words may match. */
    List<Passage> search(UUID programId, String query, int limit);

    /**
     * Pages matching a person's own query (web search syntax: quoted phrases, OR, -term), best first, each with a
     * headline. Rule text is never searched. Both filters are optional.
     */
    List<PassageHit> searchWithHeadlines(UUID programId, String query, String kind, UUID courseVersionId, int limit);
}
