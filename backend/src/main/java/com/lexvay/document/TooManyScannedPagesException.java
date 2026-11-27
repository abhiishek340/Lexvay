package com.lexvay.document;

/** A document needs OCR on more pages than the server allows per document. Retrying cannot help. */
public class TooManyScannedPagesException extends RuntimeException {

    public TooManyScannedPagesException(int pages, int limit) {
        super("Document has " + pages + " scanned pages that need OCR; the limit is " + limit
                + " pages per document. Split the document or upload a text-based PDF");
    }
}
