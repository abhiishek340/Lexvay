package com.lexvay.document;

import com.lexvay.common.ConflictException;
import java.util.UUID;

public class DuplicateDocumentException extends ConflictException {

    private final UUID existingDocumentId;

    public DuplicateDocumentException(UUID existingDocumentId) {
        super("A document with the same content was already uploaded: " + existingDocumentId);
        this.existingDocumentId = existingDocumentId;
    }

    public UUID existingDocumentId() {
        return existingDocumentId;
    }
}
