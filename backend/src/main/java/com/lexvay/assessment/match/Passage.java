package com.lexvay.assessment.match;

import com.lexvay.document.TextSource;
import java.util.UUID;

/** A candidate page from the program's documents (D-016). {@code ocrConfidence} is set when the text was read by OCR. */
public record Passage(UUID documentId, String filename, String kind, UUID courseVersionId, String courseCode,
                      Boolean courseRequired, int pageNumber, String text, TextSource textSource, Double ocrConfidence) {
}
