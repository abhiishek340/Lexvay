package com.lexvay.document;

/**
 * One page of extracted text with its offsets in the concatenated document text (D-041). {@code ocrConfidence}
 * (0 to 100) is set exactly when the text was read by OCR.
 */
public record ExtractedPage(int pageNumber, String text, int charStart, int charEnd, TextSource textSource,
                            Double ocrConfidence) {

    public static ExtractedPage textLayer(int pageNumber, String text, int charStart, int charEnd) {
        return new ExtractedPage(pageNumber, text, charStart, charEnd, TextSource.TEXT_LAYER, null);
    }

    public ExtractedPage {
        if ((textSource == TextSource.OCR) != (ocrConfidence != null)) {
            throw new IllegalArgumentException("A page has an OCR confidence exactly when it was read by OCR");
        }
    }
}
