package com.lexvay.workqueue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** What needs the signed-in user, across programs and jurisdictions. {@code counts} omits kinds with nothing waiting. */
public record WorkQueue(OffsetDateTime generatedAt, Map<WorkQueueKind, Long> counts, List<WorkItem> items) {
}
