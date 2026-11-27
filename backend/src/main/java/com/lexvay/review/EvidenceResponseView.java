package com.lexvay.review;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A stored answer to an evidence request, with the passage it cites. Offsets are relative to the page text. */
public record EvidenceResponseView(UUID id, UUID reviewEventId, UUID documentId, String filename, String kind,
                                   String courseCode, Boolean courseRequired, int pageNumber, int charStart, int charEnd,
                                   String quote, String comment, UUID providedBy, String providedByName,
                                   OffsetDateTime createdAt) {
}
