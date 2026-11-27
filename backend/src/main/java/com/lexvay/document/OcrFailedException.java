package com.lexvay.document;

/** The OCR engine could not process a page. Never carries page text. Retryable: the job runner requeues it. */
public class OcrFailedException extends RuntimeException {

    public OcrFailedException(String message) {
        super(message);
    }

    public OcrFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
