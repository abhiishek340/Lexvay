package com.lexvay.workqueue;

import com.lexvay.auth.CurrentUser;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One read-only transaction and one statement per call (D-110). */
@Service
public class WorkQueueService {

    private final WorkQueueRepository queue;

    public WorkQueueService(WorkQueueRepository queue) {
        this.queue = queue;
    }

    @Transactional(readOnly = true)
    public WorkQueue queue() {
        List<WorkItem> items = queue.items(CurrentUser.require().role());
        var counts = items.stream().collect(Collectors.groupingBy(WorkItem::kind,
                () -> new EnumMap<>(WorkQueueKind.class), Collectors.summingLong(WorkItem::count)));
        return new WorkQueue(OffsetDateTime.now(), counts, items);
    }

    @Transactional(readOnly = true)
    public WorkQueueCount count() {
        return new WorkQueueCount(queue.total(CurrentUser.require().role()));
    }
}
