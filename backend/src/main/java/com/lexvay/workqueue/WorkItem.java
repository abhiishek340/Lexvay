package com.lexvay.workqueue;

import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * One line of the work queue. {@code count} is how many records wait (requirements, rule versions, change
 * events, assessments; one for a jurisdiction ready to sign) and {@code oldestAt} since when the oldest has
 * waited. {@code ids} holds what the screen needs to navigate, never more than 20 ids per list; program and
 * jurisdiction are null for kinds that belong to neither.
 */
public record WorkItem(WorkQueueKind kind, UUID programId, String programName, String jurisdictionCode, long count,
                       OffsetDateTime oldestAt, JsonNode ids) {
}
