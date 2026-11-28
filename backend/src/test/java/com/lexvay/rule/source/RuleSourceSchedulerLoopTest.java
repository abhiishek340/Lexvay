package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.lexvay.common.TenantContext;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The scheduler running for real, as it does when {@code LEXVAY_RULE_SOURCES_ENABLED} is true. The context is closed
 * afterwards so that no other test class has a scheduler queueing jobs behind its back.
 */
@TestPropertySource(properties = {"lexvay.rule-sources.enabled=true", "lexvay.rule-sources.poll-interval-ms=100",
        "lexvay.rule-sources.contact-url=https://monitor.example.test/about"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RuleSourceSchedulerLoopTest extends AbstractRuleSourceTest {

    @Autowired RuleSourceService service;
    @Autowired ApplicationContext context;
    @Autowired TransactionTemplate transaction;

    private long queuedJobs(UUID sourceId) {
        return inTenant(() -> jdbc.sql("SELECT count(*) FROM job WHERE type = 'CHECK_RULE_SOURCE' AND entity_id = :id")
                .param("id", sourceId).query(Long.class).single());
    }

    @Test
    void theSchedulerQueuesADueSourceOnceAndLeavesAnInactiveOneAlone() throws InterruptedException {
        assertThat(context.getBeanNamesForType(RuleSourceScheduler.class)).hasSize(1);
        UUID unreviewed = inTenant(() -> service.register(new CreateRuleSourceRequest("CA", LICENSE, "unreviewed", newUrl("html"), 24)));
        UUID due = inTenant(() -> service.register(new CreateRuleSourceRequest("CA", LICENSE, "due", newUrl("html"), 24)));
        permit(due);
        // One transaction: the running scheduler must never see the source while it is still active.
        UUID inactive = inTenant(() -> transaction.execute(status -> {
            UUID id = service.register(new CreateRuleSourceRequest("CA", LICENSE, "inactive", newUrl("html"), 24));
            service.setActive(id, false);
            jdbc.sql("UPDATE rule_source SET access_status = 'PERMITTED' WHERE id = :id").param("id", id).update();
            return id;
        }));

        await().atMost(Duration.ofSeconds(10)).until(() -> queuedJobs(due) == 1);
        Thread.sleep(600);

        assertThat(queuedJobs(due)).as("several polls later it is still one job").isEqualTo(1);
        assertThat(queuedJobs(inactive)).isZero();
        assertThat(queuedJobs(unreviewed)).as("access was never reviewed").isZero();
    }
}
