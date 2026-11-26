package com.lexvay.document;

public class UnparsableDocumentException extends RuntimeException {
    public UnparsableDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
