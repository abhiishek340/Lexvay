package com.lexvay.workqueue;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only and open to every signed-in role: the queue itself shows each role only what concerns it. */
@RestController
@RequestMapping("/api/v1/work-queue")
public class WorkQueueController {

    private final WorkQueueService service;

    public WorkQueueController(WorkQueueService service) {
        this.service = service;
    }

    @GetMapping
    WorkQueue queue() {
        return service.queue();
    }

    @GetMapping("/count")
    WorkQueueCount count() {
        return service.count();
    }
}
