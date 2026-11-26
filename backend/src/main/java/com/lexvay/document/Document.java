package com.lexvay.document;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Document(UUID id, UUID programId, UUID courseVersionId, String filename, String sha256, String storageKey,
                       DocumentKind kind, OffsetDateTime createdAt, UUID createdBy) {
}
