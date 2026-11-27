package com.lexvay.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Production polling loop. Tests disable it and call {@link JobRunner#runPending()} directly. When the
 * application starts shutting down the poller claims no further job; the one running finishes, and the
 * scheduler waits for it ({@code spring.task.scheduling.shutdown}).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "lexvay.jobs.polling-enabled", havingValue = "true", matchIfMissing = true)
public class JobPoller {

    private final JobRunner runner;
    private volatile boolean stopping;

    public JobPoller(JobRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${lexvay.jobs.poll-interval-ms:1000}")
    void poll() {
        runner.runWhile(() -> !stopping);
    }

    @EventListener(ContextClosedEvent.class)
    void stopClaiming() {
        stopping = true;
    }
}
