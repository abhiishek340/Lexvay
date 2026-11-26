package com.lexvay.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.audit.AuditRepository;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class JobRunnerTest extends AbstractDatabaseTest {

    @Autowired JobRepository jobs;
    @Autowired JobRunner runner;
    @Autowired TestTenants tenants;
    @Autowired TransactionTemplate transaction;
    @Autowired AuditRepository audit;
    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;

    /** A handler that records the tenant it ran under and fails a configurable number of times. */
    static class ProbeHandler implements JobHandler {
        final AtomicInteger calls = new AtomicInteger();
        volatile UUID seenTenant;
        volatile int failTimes;
        volatile boolean permanent;

        @Override public JobType type() { return JobType.GENERATE_REPORT; }

        @Override public void handle(Job job, Map<String, Object> payload) {
            calls.incrementAndGet();
            seenTenant = TenantContext.require();
            if (permanent) {
                throw new PermanentJobFailure("bad input");
            }
            if (failTimes > 0) {
                failTimes--;
                throw new IllegalStateException("transient failure");
            }
        }
    }

    /** The queue is shared by every test in the JVM; start each test with nothing left to claim. */
    @BeforeEach
    void drainQueue() {
        runner.runPending();
    }

    private JobRunner runnerWith(ProbeHandler probe) {
        return new JobRunner(jobs, List.of(probe), transaction, json, auditService(), 3);
    }

    private com.lexvay.audit.AuditService auditService() {
        return new com.lexvay.audit.AuditService(audit);
    }

    @Test
    void handlerRunsUnderTheJobsTenantAndJobEndsDone() {
        var t = tenants.create("jobs");
        var probe = new ProbeHandler();
        UUID jobId = TenantContext.runAs(t.tenantId(), () -> jobs.enqueue(JobType.GENERATE_REPORT, Map.of("k", "v")));

        runnerWith(probe).runPending();

        assertThat(probe.calls.get()).isEqualTo(1);
        assertThat(probe.seenTenant).isEqualTo(t.tenantId());
        Job job = TenantContext.runAs(t.tenantId(), () -> jobs.findById(jobId)).orElseThrow();
        assertThat(job.status()).isEqualTo(JobStatus.DONE);
        assertThat(job.attempts()).isEqualTo(1);
        assertThat(job.error()).isNull();
    }

    @Test
    void transientFailuresRetryUpToThreeAttemptsThenFailAndAudit() {
        var t = tenants.create("jobs");
        var probe = new ProbeHandler();
        probe.failTimes = 5;
        UUID jobId = TenantContext.runAs(t.tenantId(), () -> jobs.enqueue(JobType.GENERATE_REPORT, Map.of()));
        var r = runnerWith(probe);

        assertThat(r.runOne()).isTrue();
        Job afterFirst = TenantContext.runAs(t.tenantId(), () -> jobs.findById(jobId)).orElseThrow();
        assertThat(afterFirst.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(afterFirst.attempts()).isEqualTo(1);
        assertThat(afterFirst.error()).isEqualTo("transient failure");

        assertThat(r.runOne()).isTrue();
        assertThat(r.runOne()).isTrue();
        Job finalState = TenantContext.runAs(t.tenantId(), () -> jobs.findById(jobId)).orElseThrow();
        assertThat(finalState.status()).isEqualTo(JobStatus.FAILED);
        assertThat(finalState.attempts()).isEqualTo(3);
        assertThat(probe.calls.get()).isEqualTo(3);
        assertThat(r.runOne()).as("nothing left to claim").isFalse();

        var entries = TenantContext.runAs(t.tenantId(), () -> audit.forEntity(jobId));
        assertThat(entries).extracting(e -> e.action()).containsExactly("JOB_FAILED");
        assertThat(entries.get(0).userId()).isNull();
    }

    @Test
    void permanentFailureFailsOnFirstAttempt() {
        var t = tenants.create("jobs");
        var probe = new ProbeHandler();
        probe.permanent = true;
        UUID jobId = TenantContext.runAs(t.tenantId(), () -> jobs.enqueue(JobType.GENERATE_REPORT, Map.of()));
        runnerWith(probe).runPending();
        Job job = TenantContext.runAs(t.tenantId(), () -> jobs.findById(jobId)).orElseThrow();
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.attempts()).isEqualTo(1);
        assertThat(job.error()).isEqualTo("bad input");
    }

    @Test
    void jobWithoutHandlerFailsCleanly() {
        var t = tenants.create("jobs");
        UUID jobId = TenantContext.runAs(t.tenantId(), () -> jobs.enqueue(JobType.RERUN_CHANGE, Map.of()));
        new JobRunner(jobs, List.of(), transaction, json, auditService(), 3).runPending();
        Job job = TenantContext.runAs(t.tenantId(), () -> jobs.findById(jobId)).orElseThrow();
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.error()).contains("No handler registered");
    }

    @Test
    void jobsAreTenantScopedThroughTheApi() throws Exception {
        var a = tenants.create("alpha");
        var b = tenants.create("beta");
        UUID jobId = TenantContext.runAs(a.tenantId(), () -> jobs.enqueue(JobType.GENERATE_REPORT, Map.of()));
        assertThat(mvc.get().uri("/api/v1/jobs/" + jobId).with(user(b.admin())).exchange()).hasStatus(404);
        var own = mvc.get().uri("/api/v1/jobs/" + jobId).with(user(a.admin())).exchange();
        assertThat(own).hasStatus(200);
        assertThat(json.readTree(own.getResponse().getContentAsString()).get("status").asString()).isEqualTo("QUEUED");
    }
}
