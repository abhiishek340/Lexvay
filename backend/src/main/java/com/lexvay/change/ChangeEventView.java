package com.lexvay.change;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A change event with what it affects, for the Changes screen. */
public record ChangeEventView(UUID id, ChangeKind kind, UUID oldId, UUID newId, ChangeStatus status, OffsetDateTime createdAt,
                              String jurisdiction, String oldLabel, String newLabel, boolean newApproved,
                              String courseCode, String oldCatalogYear, String newCatalogYear, boolean newVersionHasDocument,
                              List<UUID> reopenedDeterminationIds) {
}
