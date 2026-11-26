package com.lexvay.rule;

import java.math.BigDecimal;
import java.util.UUID;

public record Requirement(UUID id, UUID ruleVersionId, String ref, String title, String text, RequirementType type,
                          NumericField numericField, String numericOperator, BigDecimal numericValue) {
}
