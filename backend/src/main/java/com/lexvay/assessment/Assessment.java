package com.lexvay.assessment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** {@code assessmentAsOf} is the date whose rules in force the assessment is made against (D-556). */
public record Assessment(UUID id, UUID programId, AssessmentStatus status, OffsetDateTime startedAt,
                         OffsetDateTime finishedAt, String llmModel, String promptVersion, LocalDate assessmentAsOf,
                         OffsetDateTime createdAt, UUID createdBy) {
}
