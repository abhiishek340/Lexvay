package com.lexvay.determination.register;

import com.lexvay.determination.DeterminationStatus;
import com.lexvay.determination.Outcome;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One signed determination exactly as signed, with the versions it names (F8, spec point 1). */
public record RegisterRow(UUID determinationId, UUID programId, String programName, String licenseType, String catalogYear,
                          String jurisdictionCode, String jurisdictionName, Outcome outcome, DeterminationStatus status,
                          UUID signedBy, String signedByName, OffsetDateTime signedAt, UUID ruleVersionId,
                          String ruleVersionLabel, LocalDate ruleEffectiveFrom, String versionsHash, UUID assessmentId,
                          int courseVersionCount) {
}
