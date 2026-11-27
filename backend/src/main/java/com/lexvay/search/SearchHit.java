package com.lexvay.search;

import java.util.List;
import java.util.UUID;

/** One matching page of a program document. {@code segments} concatenate to the headline, matches flagged. */
public record SearchHit(UUID documentId, String filename, String kind, String courseCode, Boolean courseRequired,
                        int pageNumber, float rank, List<Segment> segments) {
}
