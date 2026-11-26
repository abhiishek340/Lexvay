package com.lexvay.determination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SignRequest(@NotBlank @Pattern(regexp = "^[A-Z]{2}$") String jurisdiction, @NotNull Outcome outcome) {
}
