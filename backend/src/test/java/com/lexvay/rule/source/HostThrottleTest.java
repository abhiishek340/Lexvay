package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.audit.AuditService;
import com.lexvay.document.DocumentService;
import com.lexvay.document.TextExtractor;
import com.lexvay.job.JobRunner;
import com.lexvay.job.JobStatus;
import com.lexvay.job.JobType;
import com.lexvay.observability.LexvayMetrics;
import com.lexvay.rule.RuleVersionRepository;
import com.lexvay.rule.RuleVersionService;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * The per-host throttle (RG-2, D-569): two checks of sources on one host are at least the minimum interval apart, the
 * second one waits in the queue instead of in a worker, and the row that orders them is shared by every instance. Every
 * other test of the database runs with an interval of 0, so this one runs its own job runner whose check handler is
 * built with the real thirty seconds, on the shared context (one more context would cost one more connection pool).
 */
class HostThrottleTest extends AbstractRuleSourceTest {

    private static final MonitorSettings THIRTY_SECONDS = new MonitorSettings("https://monitor.example.test/about", 30, "test");

    @Autowired SourceHostRepository hosts;
    @Autowired RuleSourceRepository sources;
    @Autowired RuleVersionRepository ruleVersions;
    @Autowired RuleVersionService ruleVersionService;
    @Autowired DocumentService documents;
    @Autowired TextExtractor extractor;
    @Autowired AuditService auditService;
    @Autowired LexvayMetrics metrics;
    @Autowired ObjectMapper mapper;
    @Autowired TransactionTemplate transactionTemplate;

    /** Claims the same queue as {@link #runner}, but runs checks through an admission that enforces thirty seconds. */
    JobRunner throttled;

    @BeforeEach
    void throttledRunner() {
        var admission = new HostAdmission(hosts, fetcher, THIRTY_SECONDS);
        var handler = new CheckRuleSourceHandler(sources, ruleVersions, ruleVersionService, documents, extractor, fetcher, admission, THIRTY_SECONDS, auditService);
        throttled = new JobRunner(jobs, List.of(handler), transactionTemplate, mapper, auditService, metrics, 3);
    }

    /** Whatever is still waiting is released and run, so that no job of this test is claimed by a later test. */
    @AfterEach
    void leaveNothingWaiting() {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            jdbc.sql("UPDATE job SET run_after = now() WHERE status = 'QUEUED'").update();
            jdbc.sql("UPDATE source_host_fetch SET last_fetch_at = NULL, hold_until = NULL").update();
        });
        runner.runPending();
    }

    private void releaseHost(String host, UUID jobId) {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            jdbc.sql("UPDATE source_host_fetch SET last_fetch_at = now() - interval '31 seconds' WHERE host = :host").param("host", host).update();
            jdbc.sql("UPDATE job SET run_after = now() WHERE id = :id").param("id", jobId).update();
        });
    }

    private long secondsUntilRunnable(UUID jobId) {
        return inTenant(() -> jdbc.sql("SELECT extract(epoch FROM run_after - now())::bigint FROM job WHERE id = :id").param("id", jobId)
                .query(Long.class).single());
    }

    @Test
    void aSecondCheckOnTheSameHostWaitsInTheQueueAndRunsOnceTheIntervalHasPassed() {
        String firstUrl = newUrl("html");
        String secondUrl = newUrl("html");
        fetcher.html(firstUrl, page("Applicants need 66 semester hours."));
        fetcher.html(secondUrl, page("Applicants need 72 semester hours."));
        UUID first = register("CA", firstUrl);
        UUID second = register("TX", secondUrl);

        throttled.runOne();
        assertThat(latestCheck(first)).containsEntry("outcome", "CHANGED");
        long started = System.nanoTime();
        boolean claimed = throttled.runOne();
        Duration spent = Duration.ofNanos(System.nanoTime() - started);

        assertThat(claimed).as("the job was claimed and put back").isTrue();
        assertThat(spent).as("a worker did not sleep for it").isLessThan(Duration.ofSeconds(10));
        assertThat(fetcher.requested()).as("nothing was asked of the host for the second source").doesNotContain(URI.create(secondUrl));
        assertThat(checkCount(second)).as("a wait is not a check").isZero();
        var job = inTenant(() -> jobs.latest(JobType.CHECK_RULE_SOURCE, second)).orElseThrow();
        assertThat(job.status()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.attempts()).as("waiting does not use up an attempt").isZero();
        assertThat(secondsUntilRunnable(job.id())).isBetween(20L, 31L);
        assertThat(checkCount(second)).as("the job is not claimed before its time").isZero();
        runner.runPending();
        assertThat(throttled.runOne()).isFalse();

        releaseHost(FakeHostResolver.PUBLIC_HOST, job.id());
        throttled.runOne();

        assertThat(latestCheck(second)).containsEntry("outcome", "CHANGED");
        assertThat(inTenant(() -> jobs.findById(job.id())).orElseThrow().status()).isEqualTo(JobStatus.DONE);
    }

    @Test
    void theWaitIsNotAuditedAsACheck() {
        String firstUrl = newUrl("html");
        String secondUrl = newUrl("html");
        fetcher.html(firstUrl, page("Applicants need 66 semester hours."));
        fetcher.html(secondUrl, page("Applicants need 72 semester hours."));
        register("CA", firstUrl);
        UUID second = register("TX", secondUrl);
        throttled.runOne();
        throttled.runOne();

        assertThat(auditActions(second)).containsExactly("RULE_SOURCE_CREATED");
    }

    @Test
    void anotherHostIsNotAffected() {
        String here = newUrl("html");
        String there = "https://" + FakeHostResolver.OTHER_PUBLIC_HOST + "/" + UUID.randomUUID() + ".html";
        fetcher.html(here, page("Applicants need 66 semester hours."));
        fetcher.html(there, page("Applicants need 72 semester hours."));
        UUID first = register("CA", here);
        UUID second = register("TX", there);

        throttled.runOne();
        throttled.runOne();

        assertThat(latestCheck(first)).containsEntry("outcome", "CHANGED");
        assertThat(latestCheck(second)).containsEntry("outcome", "CHANGED");
    }

    @Test
    void theSlotIsGivenOnceAndTheWaitShrinksAsTimePasses() {
        String host = "slots.example.test";

        assertThat(hosts.reserve(host, Duration.ofSeconds(30))).isEqualTo(Duration.ZERO);
        Duration wait = hosts.reserve(host, Duration.ofSeconds(30));

        assertThat(wait).isBetween(Duration.ofSeconds(28), Duration.ofSeconds(31));
        assertThat(hosts.reserve(host, Duration.ofSeconds(30))).as("asking does not take the slot").isLessThanOrEqualTo(wait);
        assertThat(hosts.reserve(host, Duration.ZERO)).as("a shorter interval has already passed").isEqualTo(Duration.ZERO);
    }

    @Test
    void manyInstancesAskingAtOnceGetOneSlot() throws Exception {
        String host = "crowd.example.test";
        int instances = 8;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(instances);
        try {
            List<Future<Duration>> answers = new ArrayList<>();
            for (int i = 0; i < instances; i++) {
                answers.add(pool.submit(() -> {
                    go.await();
                    return hosts.reserve(host, Duration.ofSeconds(30));
                }));
            }
            go.countDown();
            List<Duration> waits = new ArrayList<>();
            for (Future<Duration> answer : answers) {
                waits.add(answer.get());
            }

            assertThat(waits).filteredOn(Duration::isZero).as("exactly one was admitted").hasSize(1);
            assertThat(waits).filteredOn(wait -> !wait.isZero()).hasSize(instances - 1).allSatisfy(wait ->
                    assertThat(wait).isBetween(Duration.ofSeconds(25), Duration.ofSeconds(31)));
        } finally {
            pool.shutdownNow();
        }
    }
}
