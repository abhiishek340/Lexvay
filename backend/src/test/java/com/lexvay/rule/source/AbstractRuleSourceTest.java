package com.lexvay.rule.source;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.audit.AuditRepository;
import com.lexvay.common.TenantContext;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobStatus;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** Fixtures for rule source tests: a tenant, the scripted fetcher, and helpers that read the tables the API hides. */
abstract class AbstractRuleSourceTest extends AbstractAssessmentTest {

    static final String LICENSE = AssessmentFixtures.LICENSE;

    @Autowired protected FakeHttpFetcher fetcher;
    @Autowired protected JdbcClient jdbc;
    @Autowired protected JobRepository jobs;
    @Autowired protected AuditRepository audit;

    /** A URL no other test uses, because the fetcher is shared. */
    protected static String newUrl(String extension) {
        return "https://" + FakeHostResolver.PUBLIC_HOST + "/" + UUID.randomUUID() + "." + extension;
    }

    protected static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    protected static String page(String... paragraphs) {
        StringBuilder html = new StringBuilder("<!doctype html><html><head><title>Board</title></head><body><nav>Menu</nav>");
        for (String paragraph : paragraphs) {
            html.append("<p>").append(paragraph).append("</p>");
        }
        return html.append("<footer>Board of Examiners</footer></body></html>").toString();
    }

    protected MvcTestResult post(String uri, Object body) {
        return mvc.post().uri(uri).with(user(t.admin())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)).exchange();
    }

    protected Map<String, Object> sourceBody(String jurisdiction, String url) {
        return Map.of("jurisdictionCode", jurisdiction, "licenseType", LICENSE, "label", "Demo rule source " + jurisdiction,
                "url", url, "checkIntervalHours", 24);
    }

    /** Registers a source through the API; its first check is queued. Returns the id. */
    protected UUID register(String jurisdiction, String url) {
        var result = post("/api/v1/rule-sources", sourceBody(jurisdiction, url));
        assertThat(result).hasStatus(201);
        return UUID.fromString(fx.read(result).get("id").asString());
    }

    /** Asks for a check through the API and returns the queued job. */
    protected UUID requestCheck(UUID sourceId) {
        var result = mvc.post().uri("/api/v1/rule-sources/" + sourceId + "/check").with(user(t.reviewer())).with(csrf()).exchange();
        assertThat(result).hasStatus(202);
        return UUID.fromString(body(result).get("jobId").asString());
    }

    /** Runs queued jobs, oldest first, until the given one is done; later jobs (extraction of a new draft) stay queued. */
    protected void run(UUID jobId) {
        for (int i = 0; i < 10 && inTenant(() -> jobs.findById(jobId)).orElseThrow().status() != JobStatus.DONE; i++) {
            runner.runOne();
        }
        assertThat(inTenant(() -> jobs.findById(jobId)).orElseThrow().status()).isEqualTo(JobStatus.DONE);
    }

    protected void checkNow(UUID sourceId) {
        run(requestCheck(sourceId));
    }

    /** The label of a rule version a check of {@code sourceBody(jurisdiction, ...)} creates. */
    protected static String fetchedLabel(String jurisdiction) {
        return "Demo rule source " + jurisdiction + " fetched " + today();
    }

    protected <T> T inTenant(Supplier<T> action) {
        return TenantContext.runAs(t.tenantId(), action);
    }

    protected Map<String, Object> latestCheck(UUID sourceId) {
        return inTenant(() -> jdbc.sql("SELECT outcome, http_status, content_hash, detail, document_id, rule_version_id "
                        + "FROM rule_source_check WHERE rule_source_id = :id ORDER BY checked_at DESC, id DESC LIMIT 1")
                .param("id", sourceId).query().singleRow());
    }

    protected long checkCount(UUID sourceId) {
        return inTenant(() -> jdbc.sql("SELECT count(*) FROM rule_source_check WHERE rule_source_id = :id")
                .param("id", sourceId).query(Long.class).single());
    }

    protected Map<String, Object> source(UUID sourceId) {
        return inTenant(() -> jdbc.sql("SELECT last_checked_at, last_content_hash, active FROM rule_source WHERE id = :id")
                .param("id", sourceId).query().singleRow());
    }

    protected long count(String table) {
        return inTenant(() -> jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single());
    }

    protected Map<String, Object> ruleVersion(Object id) {
        return inTenant(() -> jdbc.sql("SELECT supersedes_id, requirements_approved_at, source_url, label, effective_from, document_id "
                        + "FROM rule_version WHERE id = :id").param("id", id).query().singleRow());
    }

    protected List<String> auditActions(UUID entityId) {
        return inTenant(() -> audit.forEntity(entityId)).stream().map(e -> e.action()).toList();
    }

    protected JsonNode body(MvcTestResult result) {
        return fx.read(result);
    }

    /** Gives the extraction of a rule version made from a source a scripted answer, so its job can run. */
    protected void scriptExtraction(String label) {
        fake.script("EXTRACT:" + label, fx.requirements("CA", List.of(AssessmentFixtures.subject("Ethics", "coursework in professional ethics"))));
    }

    /** Runs extraction of a version made by a check and has the reviewer approve it, as a person would. */
    protected void extractAndApprove(UUID ruleVersionId, String label) {
        scriptExtraction(label);
        runner.runPending();
        var approved = mvc.post().uri("/api/v1/rule-versions/" + ruleVersionId + "/requirements/approve")
                .with(user(t.reviewer())).with(csrf()).exchange();
        assertThat(approved).hasStatus(200);
    }
}
