package com.lexvay.change;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChangeEvent(UUID id, ChangeKind kind, UUID oldId, UUID newId, ChangeStatus status, OffsetDateTime createdAt,
                          UUID createdBy) {
}
