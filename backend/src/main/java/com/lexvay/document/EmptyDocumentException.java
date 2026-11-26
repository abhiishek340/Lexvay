package com.lexvay.document;

public class EmptyDocumentException extends RuntimeException {
    public EmptyDocumentException() {
        super("Document has no extractable text");
    }
}
