package com.lexvay.document;

public class EmptyDocumentException extends RuntimeException {

    static final String MESSAGE = "Document has no extractable text";
    static final String SCANNED_HINT = ". It looks scanned; OCR is not enabled on this server";

    public EmptyDocumentException(boolean looksScanned) {
        super(looksScanned ? MESSAGE + SCANNED_HINT : MESSAGE);
    }
}
