package com.lexvay.assessment.match;

import java.util.List;
import java.util.UUID;

/** The seam for candidate retrieval. PostgreSQL full-text search today; nothing else is assumed. */
public interface PassageSearch {

    List<Passage> search(UUID programId, String query, int limit);
}
