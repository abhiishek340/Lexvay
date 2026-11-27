package com.lexvay.rule;

import java.math.BigDecimal;
import java.util.List;

/** A requirement before it is stored: model output or a reviewer's replacement list. {@code searchTerms} are already normalised ({@link SearchTerms}). */
public record RequirementDraft(String ref, String title, String text, RequirementType type, NumericField numericField,
                               String numericOperator, BigDecimal numericValue, List<String> searchTerms) {
}
