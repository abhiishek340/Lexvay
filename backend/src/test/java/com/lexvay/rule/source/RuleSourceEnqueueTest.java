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

    private UUID register(TestTenants.TenantWithUsers tenant, String label, int intervalHours) {
        return TenantContext.runAs(tenant.tenantId(), () -> service.register(
                new CreateRuleSourceRequest("CA", LICENSE, label, newUrl("html"), intervalHours)));
    }

    /** Pretends the source was last checked this many hours ago. */
    private void checkedHoursAgo(TestTenants.TenantWithUsers tenant, UUID sourceId, int hours) {
        TenantContext.runAs(tenant.tenantId(), () -> jdbc.sql("UPDATE rule_source SET last_checked_at = now() - :hours * interval '1 hour' WHERE id = :id")
                .param("hours", hours).param("id", sourceId).update());
    }

    private long jobs(TestTenants.TenantWithUsers tenant, UUID sourceId) {
        return TenantContext.runAs(tenant.tenantId(), () -> jdbc.sql("SELECT count(*) FROM job WHERE type = 'CHECK_RULE_SOURCE' "
                + "AND payload ->> 'ruleSourceId' = :id").param("id", sourceId.toString()).query(Long.class).single());
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
        checkedHoursAgo(t, overdue, 30);
        UUID recent = register(t, "recent", 24);
        checkedHoursAgo(t, recent, 1);
        UUID inactive = register(t, "inactive", 24);
        TenantContext.runAs(t.tenantId(), () -> repository.setActive(inactive, false));
        UUID otherTenants = register(other, "other tenant", 1);

        int queued = repository.enqueueDue();

        assertThat(queued).isGreaterThanOrEqualTo(3);
        assertThat(jobs(t, neverChecked)).isEqualTo(1);
        assertThat(jobs(t, overdue)).isEqualTo(1);
        assertThat(jobs(t, recent)).as("checked an hour ago, interval a day").isZero();
        assertThat(jobs(t, inactive)).as("monitoring is off").isZero();
        assertThat(jobs(other, otherTenants)).as("another tenant is served too").isEqualTo(1);
        assertThat(jobs(t, otherTenants)).as("and the job belongs to that tenant only").isZero();
        var job = TenantContext.runAs(other.tenantId(), () -> jdbc.sql("SELECT status, created_by FROM job WHERE payload ->> 'ruleSourceId' = :id")
                .param("id", otherTenants.toString()).query().singleRow());
        assertThat(job).containsEntry("status", "QUEUED").containsEntry("created_by", null);

        repository.enqueueDue();
        assertThat(jobs(t, neverChecked)).as("no second job while one is queued").isEqualTo(1);
        assertThat(jobs(other, otherTenants)).isEqualTo(1);
    }

    @Test
    void aSourceIsDueAgainOnlyOnceItsCheckHasRunAndItsIntervalHasPassed() {
        UUID id = register(t, "cycle", 24);
        repository.enqueueDue();
        assertThat(jobs(t, id)).isEqualTo(1);

        runner.runPending();
        repository.enqueueDue();
        assertThat(jobs(t, id)).as("it was just checked").isEqualTo(1);

        checkedHoursAgo(t, id, 25);
        repository.enqueueDue();
        assertThat(jobs(t, id)).as("the interval has passed").isEqualTo(2);
        repository.enqueueDue();
        assertThat(jobs(t, id)).isEqualTo(2);
    }
}
