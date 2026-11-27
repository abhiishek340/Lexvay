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

/**
 * The scheduler running for real, as it does when {@code LEXVAY_RULE_SOURCES_ENABLED} is true. The context is closed
 * afterwards so that no other test class has a scheduler queueing jobs behind its back.
 */
@TestPropertySource(properties = {"lexvay.rule-sources.enabled=true", "lexvay.rule-sources.poll-interval-ms=100"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RuleSourceSchedulerLoopTest extends AbstractRuleSourceTest {

    @Autowired RuleSourceService service;
    @Autowired ApplicationContext context;

    private long queuedJobs(UUID sourceId) {
        return inTenant(() -> jdbc.sql("SELECT count(*) FROM job WHERE type = 'CHECK_RULE_SOURCE' AND payload ->> 'ruleSourceId' = :id")
                .param("id", sourceId.toString()).query(Long.class).single());
    }

    @Test
    void theSchedulerQueuesADueSourceOnceAndLeavesAnInactiveOneAlone() throws InterruptedException {
        assertThat(context.getBeanNamesForType(RuleSourceScheduler.class)).hasSize(1);
        UUID due = inTenant(() -> service.register(new CreateRuleSourceRequest("CA", LICENSE, "due", newUrl("html"), 24)));
        UUID inactive = inTenant(() -> service.register(new CreateRuleSourceRequest("CA", LICENSE, "inactive", newUrl("html"), 24)));
        inTenant(() -> service.setActive(inactive, false));

        await().atMost(Duration.ofSeconds(10)).until(() -> queuedJobs(due) == 1);
        Thread.sleep(600);

        assertThat(queuedJobs(due)).as("several polls later it is still one job").isEqualTo(1);
        assertThat(queuedJobs(inactive)).isZero();
    }
}
