package com.lexvay.job;

import java.time.Duration;

/**
 * Thrown by a handler that must not run yet, for example because the server it needs was asked too recently. The job
 * goes back to the queue and is claimed again after {@link #delay()}; the attempt does not count, and no worker sleeps.
 */
public class JobDeferred extends RuntimeException {

    private final Duration delay;

    public JobDeferred(Duration delay, String reason) {
        super(reason);
        this.delay = delay;
    }

    public Duration delay() {
        return delay;
    }
}
