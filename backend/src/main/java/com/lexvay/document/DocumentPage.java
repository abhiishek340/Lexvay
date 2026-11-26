package com.lexvay.document;

import java.util.UUID;

public record DocumentPage(UUID id, UUID documentId, int pageNumber, String text, int charStart, int charEnd) {
}
