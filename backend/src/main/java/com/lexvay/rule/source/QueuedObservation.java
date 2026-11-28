package com.lexvay.rule.source;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A change a rule source showed while a draft waited for review: kept, with its document, behind that draft. */
public record QueuedObservation(UUID checkId, OffsetDateTime observedAt, UUID documentId, String contentHash) {
}
