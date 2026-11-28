package com.lexvay.rule.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Queues a check for every rule source that is due and whose access a person has permitted (F12, D-185; RG-2, D-568).
 * Off unless {@code lexvay.rule-sources.enabled} is true, so tests, the demo and a developer's laptop never reach for
 * the internet. It will not start without a monitoring contact URL, because the monitor must say who runs it (D-566).
 * The job poller then runs the queued jobs like any others. Several instances may run it: a source with a job already
 * queued is skipped.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "lexvay.rule-sources.enabled", havingValue = "true")
public class RuleSourceScheduler {

    private static final Logger log = LoggerFactory.getLogger(RuleSourceScheduler.class);

    private final RuleSourceRepository sources;

    public RuleSourceScheduler(RuleSourceRepository sources, MonitorSettings settings) {
        if (!settings.hasContactUrl()) {
            throw new IllegalStateException("Rule source monitoring is on (LEXVAY_RULE_SOURCES_ENABLED) but LEXVAY_MONITOR_CONTACT_URL is not set: "
                    + "the monitor must give boards a way to reach whoever runs it");
        }
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
