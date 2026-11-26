package com.lexvay.review;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One requirement for which a reviewer asked the program for evidence. */
public record EvidenceRequestView(UUID mappingId, String jurisdiction, String requirementRef, String requirementTitle,
                                  EvidenceRequestStatus status, String requestNote, UUID requestedBy, String requestedByName,
                                  OffsetDateTime requestedAt, int responseCount, LatestResponse latestResponse) {

    /** The newest response on the mapping, null when nobody has answered yet. */
    public record LatestResponse(UUID id, UUID providedBy, String providedByName, OffsetDateTime createdAt,
                                 String filename, int pageNumber) {
    }
}
