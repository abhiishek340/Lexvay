package com.lexvay.rule.source;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRuleSourceRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String jurisdictionCode,
        @NotBlank @Size(max = 100) String licenseType,
        @NotBlank @Size(max = 150) String label,
        @NotBlank @Size(max = 2000) @Pattern(regexp = "^https?://\\S+$") String url,
        @NotNull @Min(1) @Max(720) Integer checkIntervalHours) {
}
