package com.lexvay.pathway;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A new group of a program's graduation paths. {@code minCount} belongs to CHOOSE_COUNT and {@code minCredits} to
 * CHOOSE_CREDITS; left out, the minimum is recorded as not known. {@code citation} is optional; when given, its quote is
 * verified against the stored page (product rule 2).
 */
public record CreatePathwayGroupRequest(@NotBlank @Size(max = 20) String catalogYear,
                                        @NotBlank @Size(max = 100) String label,
                                        @NotNull GroupKind kind,
                                        @Min(1) Integer minCount,
                                        @Positive @Digits(integer = 5, fraction = 2) BigDecimal minCredits,
                                        @NotEmpty @Size(max = 100) List<@NotNull UUID> courseIds,
                                        @Valid Citation citation) {

    public record Citation(@NotNull UUID documentId, @Min(1) int pageNumber, @NotBlank String quote) {
    }
}
