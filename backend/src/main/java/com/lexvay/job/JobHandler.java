package com.lexvay.job;

import java.util.Map;

/** One implementation per {@link JobType}. Runs inside a transaction under the job's tenant. */
public interface JobHandler {

    JobType type();

    void handle(Job job, Map<String, Object> payload);
}
