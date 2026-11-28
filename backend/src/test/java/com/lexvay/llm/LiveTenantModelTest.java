package com.lexvay.llm;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.job.Job;
import com.lexvay.job.JobRepository;
import com.lexvay.job.JobRunner;
import com.lexvay.job.JobStatus;
import com.lexvay.rule.RuleVersionApiTest;
import com.lexvay.tenant.DataMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * MODE-1: real data never meets the scripted model, and demo data is unaffected. The model is reached through the
 * jobs of the real pipeline, so each case goes through the API and the job runner.
 */
class LiveTenantModelTest extends AbstractDatabaseTest {

    private static final String REASON = "No model is configured for real data";

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired JobRunner runner;
    @Autowired JobRepository jobs;
    @Autowired LlmClient llmClient;
    @Autowired JdbcClient jdbc;

    FakeLlmClient fake;

    @BeforeEach
    void setUp() {
        fake = (FakeLlmClient) llmClient;
        runner.runPending();
        fake.reset();
    }

    private record Created(UUID ruleVersionId, UUID jobId) {
    }

    private Created createRule(TestTenants.TenantWithUsers t, String label) throws Exception {
        fake.script("EXTRACT:" + label, RuleVersionApiTest.validOutput("UT"));
        var created = mvc.post().uri("/api/v1/rule-versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jurisdiction", "UT", "licenseType", "CLINICAL_MENTAL_HEALTH_COUNSELING",
                        "label", label, "sourceUrl", "https://example.invalid/demo/" + label.replace(' ', '-'),
                        "effectiveFrom", LocalDate.of(2026, 7, 1).toString(), "text", RuleVersionApiTest.RULE_TEXT + "\nLabel: " + label)))
                .exchange();
        assertThat(created).hasStatus(202);
        JsonNode body = json.readTree(created.getResponse().getContentAsString());
        return new Created(UUID.fromString(body.get("ruleVersionId").asString()), UUID.fromString(body.get("jobId").asString()));
    }

    private Job job(TestTenants.TenantWithUsers t, UUID jobId) {
        return TenantContext.runAs(t.tenantId(), () -> jobs.findById(jobId)).orElseThrow();
    }

    private List<Map<String, Object>> audited(TestTenants.TenantWithUsers t, String action) {
        return TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT detail::text AS detail, entity_type FROM audit_log WHERE action = :action")
                .param("action", action).query().listOfRows());
    }

    private int requirementCount(TestTenants.TenantWithUsers t, UUID ruleVersionId) throws Exception {
        var view = mvc.get().uri("/api/v1/rule-versions/" + ruleVersionId).with(user(t.reviewer())).exchange();
        return json.readTree(view.getResponse().getContentAsString()).get("requirementCount").asInt();
    }

    @Test
    void aLiveTenantsExtractionFailsAtOnceWithoutTheScriptedModelAnsweringAndTheRefusalIsAudited() throws Exception {
        var t = tenants.create("live-extract", DataMode.LIVE);
        Created rule = createRule(t, "Demo rule set live extraction");

        runner.runPending();

        Job job = job(t, rule.jobId());
        assertThat(job.status()).isEqualTo(JobStatus.FAILED);
        assertThat(job.attempts()).as("a refusal is permanent, so the job is not retried").isEqualTo(1);
        assertThat(job.error()).isEqualTo(REASON);
        assertThat(fake.callCount(LlmPurpose.EXTRACT_REQUIREMENTS)).as("the scripted model was never asked").isZero();
        assertThat(requirementCount(t, rule.ruleVersionId())).isZero();
        assertThat(audited(t, "LLM_CALL_REFUSED")).singleElement().satisfies(row -> {
            assertThat(row.get("entity_type")).isEqualTo("llm_call");
            JsonNode detail = json.readTree((String) row.get("detail"));
            assertThat(detail.get("reason").asString()).isEqualTo("NO_MODEL_FOR_REAL_DATA");
            assertThat(detail.get("message").asString()).isEqualTo(REASON);
            assertThat(detail.get("purpose").asString()).isEqualTo("EXTRACT_REQUIREMENTS");
            assertThat(detail.get("model").asString()).isEqualTo(FakeLlmClient.MODEL_ID);
            assertThat(row.get("detail").toString()).as("no prompt, rule text or script reaches the audit trail")
                    .doesNotContain("R156-60-302").doesNotContain("EXTRACT:");
        });
        assertThat(audited(t, "JOB_FAILED")).singleElement().satisfies(row -> assertThat(row.get("detail").toString()).contains(REASON));
        long stored = TenantContext.runAs(t.tenantId(), () -> jdbc.sql("SELECT count(*) FROM llm_call").query(Long.class).single());
        assertThat(stored).as("nothing was answered, so nothing is recorded as a model call").isZero();
    }

    @Test
    void aDemoTenantKeepsEverythingAsToday() throws Exception {
        var t = tenants.create("demo-extract", DataMode.DEMO);
        Created rule = createRule(t, "Demo rule set demo extraction");

        runner.runPending();

        assertThat(job(t, rule.jobId()).status()).isEqualTo(JobStatus.DONE);
        assertThat(requirementCount(t, rule.ruleVersionId())).isEqualTo(4);
        assertThat(audited(t, "LLM_CALL_REFUSED")).isEmpty();
    }

    @Test
    void aTenantMovedToLiveStopsGettingTheScriptedModelFromThatMoment() throws Exception {
        var t = tenants.create("live-later", DataMode.DEMO);
        Created before = createRule(t, "Demo rule set before the move");
        runner.runPending();
        assertThat(job(t, before.jobId()).status()).isEqualTo(JobStatus.DONE);

        tenants.setDataMode(t.tenantId(), DataMode.LIVE);
        Created after = createRule(t, "Demo rule set after the move");
        runner.runPending();

        assertThat(job(t, after.jobId()).status()).isEqualTo(JobStatus.FAILED);
        assertThat(job(t, after.jobId()).error()).isEqualTo(REASON);
        assertThat(fake.callCount(LlmPurpose.EXTRACT_REQUIREMENTS)).as("only the call made while the tenant was DEMO").isEqualTo(1);
    }

    @Test
    void requirementsCannotBeApprovedForRealDataFromARuleVersionWithoutARecordedDatesBasis() throws Exception {
        var t = tenants.create("live-approve", DataMode.DEMO);
        Created rule = createRule(t, "Demo rule set to approve");
        runner.runPending();
        tenants.setDataMode(t.tenantId(), DataMode.LIVE);

        var refused = mvc.post().uri("/api/v1/rule-versions/" + rule.ruleVersionId() + "/requirements/approve")
                .with(user(t.reviewer())).with(csrf()).exchange();

        assertThat(refused).hasStatus(409);
        assertThat(refused.getResponse().getContentAsString()).contains("basis of the rule version's dates");
        assertThat(audited(t, "REQUIREMENTS_APPROVED")).isEmpty();

        tenants.setDataMode(t.tenantId(), DataMode.DEMO);
        var approved = mvc.post().uri("/api/v1/rule-versions/" + rule.ruleVersionId() + "/requirements/approve")
                .with(user(t.reviewer())).with(csrf()).exchange();

        assertThat(approved).hasStatus(200);
        assertThat(audited(t, "REQUIREMENTS_APPROVED")).hasSize(1);
    }
}
