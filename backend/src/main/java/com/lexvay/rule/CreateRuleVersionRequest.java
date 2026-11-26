package com.lexvay.rule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateRuleVersionRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String jurisdiction,
        @NotBlank @Size(max = 100) String licenseType,
        @NotBlank @Size(max = 200) String label,
        @NotBlank @Size(max = 2000) @Pattern(regexp = "^https?://.+") String sourceUrl,
        @NotNull LocalDate effectiveFrom,
        UUID supersedesId,
        @NotBlank @Size(max = 500_000) String text) {
}
