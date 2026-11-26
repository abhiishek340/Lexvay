package com.lexvay.assessment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Assessment(UUID id, UUID programId, AssessmentStatus status, OffsetDateTime startedAt,
                         OffsetDateTime finishedAt, String llmModel, String promptVersion, OffsetDateTime createdAt,
                         UUID createdBy) {
}
