package com.lexvay.job;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A queued unit of work. {@code entityId} is the record it works on; {@code payload} is its JSON input. */
public record Job(UUID id, UUID tenantId, JobType type, UUID entityId, String payload, JobStatus status, int attempts,
                  OffsetDateTime lockedAt, String error, OffsetDateTime createdAt) {
}
