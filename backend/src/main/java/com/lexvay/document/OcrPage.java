package com.lexvay.document;

/** Recognised text of one page image and the mean word confidence, 0 to 100. */
public record OcrPage(String text, double meanConfidence) {
}
