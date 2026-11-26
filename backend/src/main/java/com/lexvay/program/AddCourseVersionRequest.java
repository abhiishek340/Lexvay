package com.lexvay.program;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AddCourseVersionRequest(@NotBlank @Size(max = 20) String catalogYear, @NotNull Boolean required, UUID supersedesId) {
}
