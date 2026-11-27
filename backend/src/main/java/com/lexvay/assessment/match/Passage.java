package com.lexvay.assessment.match;

import com.lexvay.document.TextSource;
import java.util.List;
import java.util.UUID;

/**
 * A candidate page from the program's documents (D-016). {@code text} is the full stored page, against which a
 * quote is verified; {@code excerpt} is what the model is shown of it (D-174); {@code rank} is the score the
 * ranker gave it. {@code ocrConfidence} is set when the page was read by OCR (D-167). {@code matchedVia} says which
 * part of the requirement found the page; {@code matchedTerms} are the search terms that occur on it, filled when that
 * is {@link MatchSource#TERMS} and empty otherwise (F7c).
 */
public record Passage(UUID documentId, String filename, String kind, UUID courseVersionId, String courseCode,
                      Boolean courseRequired, int pageNumber, String text, String excerpt, double rank,
                      TextSource textSource, Double ocrConfidence, MatchSource matchedVia, List<String> matchedTerms) {
}
