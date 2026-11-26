package com.lexvay.assessment;

import com.lexvay.rule.NumericField;
import com.lexvay.rule.RequirementType;
import java.math.BigDecimal;
import java.util.UUID;

/** One requirement's proposed finding with its evidence, as shown to reviewers (D-053). */
public record Mapping(UUID id, UUID assessmentId, UUID jurisdictionId, String jurisdiction, UUID ruleVersionId,
                      RequirementView requirement, Finding finding, MappingMethod method, String rationale,
                      boolean stale, Evidence evidence, Decision decision) {

    /** The current human decision (D-003): the latest review event unless that event is REOPENED. */
    public record Decision(String kind, String note, UUID userId, String userDisplayName, java.time.OffsetDateTime at) {
    }

    public record RequirementView(UUID id, String ref, String title, String text, RequirementType type,
                                  NumericField numericField, String numericOperator, BigDecimal numericValue) {
    }

    public record Evidence(UUID spanId, UUID documentId, String filename, String kind, UUID courseVersionId,
                           String courseCode, Boolean courseRequired, int pageNumber, int charStart, int charEnd, String quote) {
    }
}
