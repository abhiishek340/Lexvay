package com.lexvay.job;

import java.util.Map;

/** One implementation per {@link JobType}. Runs inside a transaction under the job's tenant. */
public interface JobHandler {

    JobType type();

    void handle(Job job, Map<String, Object> payload);

    /**
     * Called once, in a transaction of its own under the job's tenant, after the job failed for good: a permanent
     * failure, or the last attempt. The attempt's own transaction was rolled back, so this is where a handler marks
     * the record the job worked on ({@link Job#entityId()}) as failed. Not called for an attempt that will be retried.
     */
    default void onFailed(Job job) {
    }
}
