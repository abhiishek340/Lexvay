package com.lexvay.review;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One entry of a mapping's review history. {@code userId} and {@code userDisplayName} are null for the system. */
public record ReviewEventView(UUID id, ReviewKind kind, String note, UUID userId, String userDisplayName,
                              OffsetDateTime createdAt) {
}
