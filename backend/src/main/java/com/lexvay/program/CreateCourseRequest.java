package com.lexvay.program;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** {@code credits} may be left out (unknown); {@code creditUnit} then and otherwise defaults to SEMESTER. */
public record CreateCourseRequest(@NotBlank @Size(max = 30) String code,
                                  @NotBlank @Size(max = 200) String title,
                                  @NotNull Boolean required,
                                  @Positive @Digits(integer = 3, fraction = 2) BigDecimal credits,
                                  CreditUnit creditUnit) {

    public CreateCourseRequest {
        creditUnit = creditUnit == null ? CreditUnit.SEMESTER : creditUnit;
    }
}
