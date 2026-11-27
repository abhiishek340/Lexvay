package com.lexvay.document;

/** Recognised text of one page image and the mean word confidence, 0 to 100. */
public record OcrPage(String text, double meanConfidence) {

    /** What a page that was sent to the engine and came back without words reads as. */
    public static final OcrPage NOTHING = new OcrPage("", 0);

    public boolean isNothing() {
        return text.isEmpty();
    }
}
