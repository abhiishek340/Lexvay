package com.lexvay.program;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** {@code credits} may be left out (unknown); {@code creditUnit} then and otherwise defaults to SEMESTER. */
public record AddCourseVersionRequest(@NotBlank @Size(max = 20) String catalogYear, @NotNull Boolean required,
                                      @Positive @Digits(integer = 3, fraction = 2) BigDecimal credits, CreditUnit creditUnit,
                                      UUID supersedesId) {

    public AddCourseVersionRequest {
        creditUnit = creditUnit == null ? CreditUnit.SEMESTER : creditUnit;
    }
}
