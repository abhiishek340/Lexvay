package com.lexvay.program;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateProgramRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String licenseType,
        @NotBlank @Size(max = 20) String catalogYear,
        @NotEmpty List<@NotBlank @Pattern(regexp = "^[A-Z]{2}$") String> jurisdictions) {
}
