package com.lexvay.document;

/** One page of extracted text with its offsets in the concatenated document text (D-041). */
public record ExtractedPage(int pageNumber, String text, int charStart, int charEnd) {
}
