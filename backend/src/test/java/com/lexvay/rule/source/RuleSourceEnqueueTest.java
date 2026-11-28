package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

/**
 * Which sources the scheduler queues (D-185), through the statement it runs: every tenant, only active and due
 * sources, never a second job while one is queued or running. The scheduler bean itself is off in tests.
 */
class RuleSourceEnqueueTest extends AbstractRuleSourceTest {

    @Autowired RuleSourceRepository repository;
    @Autowired RuleSourceService service;
    @Autowired ApplicationContext context;

    /** A source whose access a person has permitted, as only such sources are queued by the scheduler. */
    private UUID register(TestTenants.TenantWithUsers tenant, String label, int intervalHours) {
        UUID id = TenantContext.runAs(tenant.tenantId(), () -> service.register(
                new CreateRuleSourceRequest("CA", LICENSE, label, newUrl("html"), intervalHours)));
        TenantContext.runAs(tenant.tenantId(), () -> jdbc.sql("UPDATE rule_source SET access_status = 'PERMITTED' WHERE id = :id")
                .param("id", id).update());
        return id;
    }

    /** Pretends the source is next due this many minutes from now (negative: that long ago). */
    private void dueInMinutes(TestTenants.TenantWithUsers tenant, UUID sourceId, int minutes) {
        TenantContext.runAs(tenant.tenantId(), () -> jdbc.sql("UPDATE rule_source SET next_check_at = now() + :minutes * interval '1 minute' WHERE id = :id")
                .param("minutes", minutes).param("id", sourceId).update());
    }

    private long jobs(TestTenants.TenantWithUsers tenant, UUID sourceId) {
        return TenantContext.runAs(tenant.tenantId(), () -> jdbc.sql("SELECT count(*) FROM job WHERE type = 'CHECK_RULE_SOURCE' "
                + "AND entity_id = :id").param("id", sourceId).query(Long.class).single());
    }

    @Test
    void theSchedulerIsOffUnlessTheOwnerTurnsItOn() {
        assertThat(context.getBeanNamesForType(RuleSourceScheduler.class)).isEmpty();
    }

    @Test
    void everyTenantsDueActiveSourceIsQueuedExactlyOnce() {
        var other = tenants.create("other");
        UUID neverChecked = register(t, "never checked", 24);
        UUID overdue = register(t, "overdue", 24);
        dueInMinutes(t, overdue, -6 * 60);
        UUID recent = register(t, "recent", 24);
        dueInMinutes(t, recent, 23 * 60);
        UUID inactive = register(t, "inactive", 24);
        TenantContext.runAs(t.tenantId(), () -> repository.setActive(inactive, false));
        UUID otherTenants = register(other, "other tenant", 1);

        int queued = repository.enqueueDue();

        assertThat(queued).isGreaterThanOrEqualTo(3);
        assertThat(jobs(t, neverChecked)).isEqualTo(1);
        assertThat(jobs(t, overdue)).isEqualTo(1);
        assertThat(jobs(t, recent)).as("checked an hour ago, due in 23 hours").isZero();
        assertThat(jobs(t, inactive)).as("monitoring is off").isZero();
        assertThat(jobs(other, otherTenants)).as("another tenant is served too").isEqualTo(1);
        assertThat(jobs(t, otherTenants)).as("and the job belongs to that tenant only").isZero();
        var job = TenantContext.runAs(other.tenantId(), () -> jdbc.sql("SELECT status, created_by FROM job WHERE entity_id = :id")
                .param("id", otherTenants).query().singleRow());
        assertThat(job).containsEntry("status", "QUEUED").containsEntry("created_by", null);

        repository.enqueueDue();
        assertThat(jobs(t, neverChecked)).as("no second job while one is queued").isEqualTo(1);
        assertThat(jobs(other, otherTenants)).isEqualTo(1);
    }

    @Test
    void onlyPermittedSourcesAreQueuedWhateverTheirScheduleSays() {
        UUID permitted = register(t, "permitted", 24);
        UUID unreviewed = register(t, "unreviewed", 24);
        UUID restricted = register(t, "restricted", 24);
        UUID blocked = register(t, "blocked", 24);
        setAccess(unreviewed, "UNREVIEWED");
        setAccess(restricted, "RESTRICTED");
        setAccess(blocked, "BLOCKED");

        repository.enqueueDue();

        assertThat(jobs(t, permitted)).isEqualTo(1);
        assertThat(jobs(t, unreviewed)).as("nobody reviewed whether it may be fetched").isZero();
        assertThat(jobs(t, restricted)).isZero();
        assertThat(jobs(t, blocked)).isZero();
    }

    @Test
    void aSourceIsDueAgainOnlyOnceItsCheckHasRunAndItsIntervalHasPassed() {
        UUID id = register(t, "cycle", 24);
        repository.enqueueDue();
        assertThat(jobs(t, id)).isEqualTo(1);

        runner.runPending();
        repository.enqueueDue();
        assertThat(jobs(t, id)).as("it was just checked").isEqualTo(1);

        dueInMinutes(t, id, -1);
        repository.enqueueDue();
        assertThat(jobs(t, id)).as("the interval has passed").isEqualTo(2);
        repository.enqueueDue();
        assertThat(jobs(t, id)).isEqualTo(2);
    }

    @Test
    void aFailedSourceIsQueuedAgainOnlyWhenItsBackoffHasPassed() {
        UUID id = register(t, "backoff", 24);
        repository.enqueueDue();
        runner.runPending();

        repository.enqueueDue();
        assertThat(jobs(t, id)).as("the first check was just done").isEqualTo(1);

        TenantContext.runAs(t.tenantId(), () -> jdbc.sql("UPDATE rule_source SET consecutive_failures = 1, next_check_at = now() + interval '15 minutes' WHERE id = :id")
                .param("id", id).update());
        repository.enqueueDue();
        assertThat(jobs(t, id)).as("fifteen minutes have not passed").isEqualTo(1);

        dueInMinutes(t, id, -1);
        repository.enqueueDue();
        assertThat(jobs(t, id)).as("the retry is due, long before the interval").isEqualTo(2);
    }
}
