package com.lexvay.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Production polling loop. Tests disable it and call {@link JobRunner#runPending()} directly. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "lexvay.jobs.polling-enabled", havingValue = "true", matchIfMissing = true)
public class JobPoller {

    private final JobRunner runner;

    public JobPoller(JobRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${lexvay.jobs.poll-interval-ms:1000}")
    void poll() {
        runner.runPending();
    }
}
