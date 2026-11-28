package com.lexvay.program;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code credits} is null when nobody recorded them; a new value is a new version. */
public record CourseVersion(UUID id, UUID courseId, UUID programId, String catalogYear, boolean required, BigDecimal credits,
                            CreditUnit creditUnit, UUID supersedesId) {
}
