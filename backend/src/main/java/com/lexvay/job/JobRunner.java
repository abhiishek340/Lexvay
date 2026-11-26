package com.lexvay.job;

import com.lexvay.audit.AuditService;
import com.lexvay.common.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs queued jobs one at a time. Each handler runs in its own transaction under the job's tenant.
 * Retryable failures requeue the job until {@code lexvay.jobs.max-attempts}; a
 * {@link PermanentJobFailure} fails it at once. Final failures are audited.
 */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final JobRepository jobs;
    private final Map<JobType, JobHandler> handlers;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final AuditService audit;
    private final int maxAttempts;

    public JobRunner(JobRepository jobs, List<JobHandler> handlers, TransactionTemplate transaction, ObjectMapper json,
                     AuditService audit, @Value("${lexvay.jobs.max-attempts:3}") int maxAttempts) {
        this.jobs = jobs;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
        this.transaction = transaction;
        this.json = json;
        this.audit = audit;
        this.maxAttempts = maxAttempts;
    }

    /** Runs jobs until the queue is empty; returns how many were processed. */
    public int runPending() {
        int count = 0;
        while (runOne()) {
            count++;
        }
        return count;
    }

    /** Claims and runs one job. Returns false when nothing was queued. */
    public boolean runOne() {
        Optional<Job> claimed = jobs.claim();
        if (claimed.isEmpty()) {
            return false;
        }
        Job job = claimed.get();
        TenantContext.runAs(job.tenantId(), () -> execute(job));
        return true;
    }

    private void execute(Job job) {
        try {
            JobHandler handler = handlers.get(job.type());
            if (handler == null) {
                throw new PermanentJobFailure("No handler registered for job type " + job.type());
            }
            Map<String, Object> payload = json.readValue(job.payload(), MAP);
            transaction.executeWithoutResult(status -> handler.handle(job, payload));
            jobs.markDone(job.id());
        } catch (PermanentJobFailure e) {
            fail(job, e.getMessage());
        } catch (RuntimeException e) {
            String message = rootMessage(e);
            if (job.attempts() >= maxAttempts) {
                fail(job, message);
            } else {
                log.warn("Job {} ({}) attempt {} failed, will retry: {}", job.id(), job.type(), job.attempts(), message);
                jobs.requeue(job.id(), message);
            }
        }
    }

    private void fail(Job job, String message) {
        log.error("Job {} ({}) failed permanently: {}", job.id(), job.type(), message);
        jobs.markFailed(job.id(), message);
        audit.recordAs(null, "JOB_FAILED", "job", job.id(), Map.of("type", job.type().name(), "error", message));
    }

    static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
