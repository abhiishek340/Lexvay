package com.lexvay.program;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(@NotBlank @Size(max = 30) String code,
                                  @NotBlank @Size(max = 200) String title,
                                  @NotNull Boolean required) {
}
