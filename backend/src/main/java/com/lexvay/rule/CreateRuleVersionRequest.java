package com.lexvay.rule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Legal status and dates are optional: a version with none is {@link LegalStatus#UNKNOWN} until a person records them. */
public record CreateRuleVersionRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String jurisdiction,
        @NotBlank @Size(max = 100) String licenseType,
        @NotBlank @Size(max = 200) String label,
        @NotBlank @Size(max = 2000) @Pattern(regexp = "^https?://.+") String sourceUrl,
        LegalStatus legalStatus,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LocalDate publishedAt,
        LocalDate adoptedAt,
        @Size(max = 1000) String datesBasis,
        UUID supersedesId,
        @NotBlank @Size(max = 500_000) String text) {

    LegalPosition position() {
        return new LegalPosition(legalStatus == null ? LegalStatus.UNKNOWN : legalStatus, effectiveFrom, effectiveTo,
                publishedAt, adoptedAt, datesBasis);
    }
}
