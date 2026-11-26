package com.lexvay.determination;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record Determination(UUID id, UUID programId, String jurisdiction, UUID jurisdictionId, UUID ruleVersionId,
                            String ruleVersionLabel, UUID assessmentId, Outcome outcome, UUID signedBy, String signedByName,
                            OffsetDateTime signedAt, DeterminationStatus status, String versionsHash,
                            List<UUID> courseVersionIds) {
}
