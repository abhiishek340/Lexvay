package com.lexvay.review;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(@NotNull ReviewKind kind, @Size(max = 4000) String note) {
}
