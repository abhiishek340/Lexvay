package com.lexvay.assessment.match;

import java.util.UUID;

/** A candidate page from the program's documents (D-016). */
public record Passage(UUID documentId, String filename, String kind, UUID courseVersionId, String courseCode,
                      Boolean courseRequired, int pageNumber, String text) {
}
