package com.lexvay.assessment.match;

import java.util.UUID;

/**
 * A candidate page from the program's documents (D-016). {@code text} is the full stored page, against which a
 * quote is verified; {@code excerpt} is what the model is shown of it (D-174); {@code rank} is the score the
 * ranker gave it.
 */
public record Passage(UUID documentId, String filename, String kind, UUID courseVersionId, String courseCode,
                      Boolean courseRequired, int pageNumber, String text, String excerpt, double rank) {
}
