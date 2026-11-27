package com.lexvay.common;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * The keys of the logging context (MDC) and the one place that sets them. Values are identifiers only:
 * never document text, quotes, rule text, prompts, model output or email addresses.
 */
public final class LogContext {

    public static final String REQUEST_ID = "requestId";
    public static final String TENANT_ID = "tenantId";
    public static final String USER_ID = "userId";
    public static final String JOB_ID = "jobId";

    private LogContext() {
    }

    /** Sets the key until the returned handle is closed, then restores whatever it held before. */
    public static Scope with(String key, UUID value) {
        return with(key, value.toString());
    }

    public static Scope with(String key, String value) {
        String previous = MDC.get(key);
        MDC.put(key, value);
        return new Scope(key, previous);
    }

    /** Removes the key until the returned handle is closed. */
    public static Scope without(String key) {
        String previous = MDC.get(key);
        MDC.remove(key);
        return new Scope(key, previous);
    }

    public static String current(String key) {
        return MDC.get(key);
    }

    public static final class Scope implements AutoCloseable {
        private final String key;
        private final String previous;

        private Scope(String key, String previous) {
            this.key = key;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        }
    }
}
