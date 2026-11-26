package com.lexvay.program;

import com.lexvay.rule.NumericField;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateProgramFactRequest(@NotNull NumericField key, @NotNull @Positive BigDecimal value,
                                       @NotNull UUID documentId, @Min(1) int pageNumber, @NotBlank String quote) {
}
