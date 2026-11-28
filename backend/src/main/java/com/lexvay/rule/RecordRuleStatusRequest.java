package com.lexvay.rule;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** A correction of a rule version's legal status and dates; the whole position is stated, not a difference. */
public record RecordRuleStatusRequest(
        @NotNull LegalStatus legalStatus,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LocalDate publishedAt,
        LocalDate adoptedAt,
        @Size(max = 1000) String datesBasis) {

    LegalPosition position() {
        return new LegalPosition(legalStatus, effectiveFrom, effectiveTo, publishedAt, adoptedAt, datesBasis);
    }
}
