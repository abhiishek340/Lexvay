package com.lexvay.job;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Job(UUID id, UUID tenantId, JobType type, String payload, JobStatus status, int attempts,
                  OffsetDateTime lockedAt, String error, OffsetDateTime createdAt) {
}
