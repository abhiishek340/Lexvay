package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.audit.AuditService;
import com.lexvay.common.ConflictException;
import com.lexvay.document.DocumentService;
import com.lexvay.document.TextExtractor;
import com.lexvay.job.JobRunner;
import com.lexvay.job.JobStatus;
import com.lexvay.job.JobType;
import com.lexvay.observability.LexvayMetrics;
import com.lexvay.rule.JurisdictionRepository;
import com.lexvay.rule.RuleVersionRepository;
import com.lexvay.rule.RuleVersionService;
import com.lexvay.tenant.AppUserRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Nothing is fetched anonymously (RG-2, D-566): without a monitoring contact URL a check cannot be requested, a source
 * cannot be registered with its first check, and a check job that was queued earlier fails before any request. The
 * shared test context has a fictional contact, so these run the same classes built with settings that have none.
 */
class MonitorContactRequiredTest extends AbstractRuleSourceTest {

    private static final MonitorSettings NO_CONTACT = new MonitorSettings("", 0, "test");

    @Autowired RuleSourceRepository sources;
    @Autowired JurisdictionRepository jurisdictions;
    @Autowired AppUserRepository users;
    @Autowired UrlPolicy urlPolicy;
    @Autowired AuditService auditService;
    @Autowired Clock clock;
    @Autowired SourceHostRepository hosts;
    @Autowired RuleVersionRepository ruleVersions;
    @Autowired RuleVersionService ruleVersionService;
    @Autowired DocumentService documents;
    @Autowired TextExtractor extractor;
    @Autowired LexvayMetrics metrics;
    @Autowired ObjectMapper mapper;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void aCheckCannotBeRequestedAndASourceCannotBeRegisteredWithoutAContact() {
        var service = new RuleSourceService(sources, jurisdictions, users, urlPolicy, NO_CONTACT, auditService, clock);
        UUID existing = register("CA", newUrl("html"));
        long registered = count("rule_source");

        assertThatThrownBy(() -> inTenant(() -> service.checkNow(existing))).isInstanceOf(ConflictException.class)
                .hasMessage("Set LEXVAY_MONITOR_CONTACT_URL before fetching");
        assertThatThrownBy(() -> inTenant(() -> service.create(new CreateRuleSourceRequest("TX", LICENSE, "No contact", newUrl("html"), 24))))
                .isInstanceOf(ConflictException.class).hasMessage("Set LEXVAY_MONITOR_CONTACT_URL before fetching");
        assertThat(count("rule_source")).as("nothing was registered").isEqualTo(registered);
    }

    @Test
    void aCheckQueuedBeforeTheContactWasRemovedFailsWithoutAnyRequest() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        var admission = new HostAdmission(hosts, fetcher, NO_CONTACT);
        var handler = new CheckRuleSourceHandler(sources, ruleVersions, ruleVersionService, documents, extractor, fetcher, admission,
                NO_CONTACT, auditService);
        var anonymous = new JobRunner(jobs, List.of(handler), transactionTemplate, mapper, auditService, metrics, 3);

        anonymous.runOne();

        var job = inTenant(() -> jobs.latest(JobType.CHECK_RULE_SOURCE, id)).orElseThrow();
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.error()).isEqualTo("Set LEXVAY_MONITOR_CONTACT_URL before fetching");
        assertThat(fetcher.requested()).as("not even robots.txt was asked for").isEmpty();
        assertThat(checkCount(id)).isZero();
    }
}
