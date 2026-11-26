package com.lexvay.assessment.match;

import java.util.UUID;

/**
 * A page that matched a reviewer's search, with the database's headline of its best fragments. The headline marks
 * matches with {@link PassageSearch#MATCH_START} and {@link PassageSearch#MATCH_END} and is never sent to a client
 * as it is.
 */
public record PassageHit(UUID documentId, String filename, String kind, String courseCode, Boolean courseRequired,
                         int pageNumber, float rank, String headline) {
}
