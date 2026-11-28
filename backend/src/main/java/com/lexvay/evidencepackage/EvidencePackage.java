package com.lexvay.evidencepackage;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One request for the ZIP behind a signed determination. The file columns are set once, when the build finishes. */
public record EvidencePackage(UUID id, UUID determinationId, PackageStatus status, String storageKey, String contentSha256,
                              Long sizeBytes, OffsetDateTime createdAt, UUID createdBy) {
}
