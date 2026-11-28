package com.lexvay.determination;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A signed determination as stored, plus its newest READY evidence report ({@code latestReadyReportId} and
 * {@code latestReadyReportAt}, both null until a report has been generated): the report screen offers the stored PDF and
 * the evidence package from it without generating again (D-515).
 */
public record Determination(UUID id, UUID programId, String jurisdiction, UUID jurisdictionId, UUID ruleVersionId,
                            String ruleVersionLabel, UUID assessmentId, Outcome outcome, UUID signedBy, String signedByName,
                            OffsetDateTime signedAt, DeterminationStatus status, String versionsHash,
                            List<UUID> courseVersionIds, UUID latestReadyReportId, OffsetDateTime latestReadyReportAt) {
}
