package com.lexvay.rule.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Queues a check for every rule source that is due (F12, D-185). Off unless {@code lexvay.rule-sources.enabled} is
 * true, so tests, the demo and a developer's laptop never reach for the internet. The job poller then runs the
 * queued jobs like any others. Several instances may run it: a source with a job already queued is skipped.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "lexvay.rule-sources.enabled", havingValue = "true")
public class RuleSourceScheduler {

    private static final Logger log = LoggerFactory.getLogger(RuleSourceScheduler.class);

    private final RuleSourceRepository sources;

    public RuleSourceScheduler(RuleSourceRepository sources) {
        this.sources = sources;
    }

    @Scheduled(fixedDelayString = "${lexvay.rule-sources.poll-interval-ms:300000}")
    void poll() {
        int queued = sources.enqueueDue();
        if (queued > 0) {
            log.info("Queued {} rule source check(s)", queued);
        }
    }
}
