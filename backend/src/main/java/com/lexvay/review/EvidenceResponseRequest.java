package com.lexvay.review;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A person's answer to an evidence request: a passage of a program document, typed exactly as it appears. */
public record EvidenceResponseRequest(@NotNull UUID documentId, @Min(1) int pageNumber, @NotBlank String quote,
                                      @Size(max = 4000) String comment) {
}
