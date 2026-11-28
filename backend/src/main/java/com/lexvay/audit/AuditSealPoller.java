package com.lexvay.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs {@link AuditSealer} on a timer (SEC-3). Tests turn it off ({@code lexvay.audit.sealing-enabled=false}) and call
 * the sealer directly. Once the application starts shutting down no further batch starts; the one in hand finishes,
 * and the scheduler waits for it ({@code spring.task.scheduling.shutdown}), like the job poller. Several instances may
 * run it: the sealer takes a per-tenant lock.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "lexvay.audit.sealing-enabled", havingValue = "true", matchIfMissing = true)
public class AuditSealPoller {

    private final AuditSealer sealer;
    private volatile boolean stopping;

    public AuditSealPoller(AuditSealer sealer) {
        this.sealer = sealer;
    }

    @Scheduled(fixedDelayString = "${lexvay.audit.seal-interval-ms:2000}")
    void poll() {
        sealer.sealWhile(() -> !stopping);
    }

    @EventListener(ContextClosedEvent.class)
    void stopSealing() {
        stopping = true;
    }
}
