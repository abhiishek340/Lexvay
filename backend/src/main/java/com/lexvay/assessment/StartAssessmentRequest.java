package com.lexvay.assessment;

import java.time.LocalDate;

/** {@code asOf} is the date whose rules in force the assessment uses; null means today. */
public record StartAssessmentRequest(LocalDate asOf) {
}
