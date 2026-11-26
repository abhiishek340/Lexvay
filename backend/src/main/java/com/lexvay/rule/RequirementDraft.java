package com.lexvay.rule;

import java.math.BigDecimal;

/** A requirement before it is stored: model output or a reviewer's replacement list. */
public record RequirementDraft(String ref, String title, String text, RequirementType type, NumericField numericField,
                               String numericOperator, BigDecimal numericValue) {
}
