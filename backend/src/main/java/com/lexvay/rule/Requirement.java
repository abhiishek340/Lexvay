package com.lexvay.rule;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A stored requirement; {@code ordinal} is its position in the rule version's list, from 1, and {@code searchTerms}
 * the vocabulary retrieval searches besides the requirement's own words (F7c).
 */
public record Requirement(UUID id, UUID ruleVersionId, String ref, String title, String text, RequirementType type,
                          NumericField numericField, String numericOperator, BigDecimal numericValue,
                          int ordinal, List<String> searchTerms) {
}
