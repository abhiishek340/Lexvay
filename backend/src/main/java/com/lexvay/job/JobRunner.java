package com.lexvay.job;

import com.lexvay.audit.AuditService;
import com.lexvay.common.LogContext;
import com.lexvay.common.TenantContext;
import com.lexvay.llm.LlmCallBlockedException;
import com.lexvay.observability.LexvayMetrics;
import com.lexvay.observability.LexvayMetrics.JobOutcome;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
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
 * {@link PermanentJobFailure} or a model call the product refused ({@link LlmCallBlockedException}) fails it at once.
 * A {@link JobDeferred} puts the job back to be claimed later, without using up an attempt.
 * Final failures are audited.
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
    private final LexvayMetrics metrics;
    private final int maxAttempts;

    public JobRunner(JobRepository jobs, List<JobHandler> handlers, TransactionTemplate transaction, ObjectMapper json,
                     AuditService audit, LexvayMetrics metrics, @Value("${lexvay.jobs.max-attempts:3}") int maxAttempts) {
        this.jobs = jobs;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
        this.transaction = transaction;
        this.json = json;
        this.audit = audit;
        this.metrics = metrics;
        this.maxAttempts = maxAttempts;
    }

    /** Runs jobs until the queue is empty; returns how many were processed. */
    public int runPending() {
        return runWhile(() -> true);
    }

    /** Claims and runs jobs while {@code keepClaiming} holds and the queue is not empty; a running job always finishes. */
    public int runWhile(BooleanSupplier keepClaiming) {
        int count = 0;
        while (keepClaiming.getAsBoolean() && runOne()) {
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
        long started = System.nanoTime();
        JobOutcome outcome;
        try (LogContext.Scope ignored = LogContext.with(LogContext.JOB_ID, job.id())) {
            outcome = attempt(job);
        }
        metrics.jobFinished(job.type(), outcome, Duration.ofNanos(System.nanoTime() - started));
    }

    private JobOutcome attempt(Job job) {
        try {
            JobHandler handler = handlers.get(job.type());
            if (handler == null) {
                throw new PermanentJobFailure("No handler registered for job type " + job.type());
            }
            Map<String, Object> payload = json.readValue(job.payload(), MAP);
            transaction.executeWithoutResult(status -> handler.handle(job, payload));
            jobs.markDone(job.id());
            return JobOutcome.DONE;
        } catch (JobDeferred e) {
            jobs.defer(job.id(), e.delay());
            return JobOutcome.DEFERRED;
        } catch (PermanentJobFailure | LlmCallBlockedException e) {
            fail(job, e.getMessage(), e);
            return JobOutcome.FAILED;
        } catch (RuntimeException e) {
            if (job.attempts() >= maxAttempts) {
                fail(job, rootMessage(e), e);
                return JobOutcome.FAILED;
            }
            // The message can carry model output or document text: it goes to the job row, not the log.
            log.warn("Job {} attempt {} failed with {}, will retry", job.type(), job.attempts(), rootCause(e).getClass().getName());
            jobs.requeue(job.id(), rootMessage(e));
            return JobOutcome.RETRIED;
        }
    }

    private void fail(Job job, String message, RuntimeException cause) {
        log.error("Job {} failed permanently with {}; the reason is stored on the job", job.type(), rootCause(cause).getClass().getName());
        jobs.markFailed(job.id(), message);
        audit.recordAs(null, "JOB_FAILED", "job", job.id(), Map.of("type", job.type().name(), "error", message));
        JobHandler handler = handlers.get(job.type());
        if (handler != null) {
            try {
                transaction.executeWithoutResult(status -> handler.onFailed(job));
            } catch (RuntimeException e) {
                log.error("Job {} failed permanently and its record could not be marked failed: {}", job.type(), rootCause(e).getClass().getName());
            }
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    static String rootMessage(Throwable t) {
        Throwable root = rootCause(t);
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }
}
