package com.lexvay.rules;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.TestTenants;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.rule.RuleVersionApiTest;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.ObjectMapper;

/** Product rule 5 at the API: once approved, a requirement list cannot be changed through any path. */
class Rule5_ApprovedRequirementsRejectEditsTest extends AbstractDatabaseTest {

    @Autowired MockMvcTester mvc;
    @Autowired TestTenants tenants;
    @Autowired ObjectMapper json;
    @Autowired JobRunner runner;
    @Autowired LlmClient llmClient;
    @Autowired JdbcClient jdbc;

    @Test
    void approvedListRejectsReplacementAndDirectEdits() throws Exception {
        var t = tenants.create("locked");
        var fake = (FakeLlmClient) llmClient;
        fake.reset();
        runner.runPending();
        String label = "Demo rule set locked";
        fake.script("EXTRACT:" + label, RuleVersionApiTest.validOutput("UT"));
        var created = mvc.post().uri("/api/v1/rule-versions").with(user(t.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("jurisdiction", "UT", "licenseType", "L", "label", label,
                        "sourceUrl", "https://example.invalid/locked", "effectiveFrom", LocalDate.of(2026, 1, 1).toString(),
                        "text", RuleVersionApiTest.RULE_TEXT)))
                .exchange();
        UUID id = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).get("ruleVersionId").asString());
        runner.runPending();
        assertThat(mvc.post().uri("/api/v1/rule-versions/" + id + "/requirements/approve").with(user(t.signer())).with(csrf()).exchange())
                .hasStatus(200);

        var put = mvc.put().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(RuleVersionApiTest.validOutput("UT")).exchange();
        assertThat(put).hasStatus(409);
        assertThat(put.getResponse().getContentAsString()).contains("approved");

        assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () ->
                jdbc.sql("UPDATE requirement SET title = 'tampered' WHERE rule_version_id = :id").param("id", id).update()))
                .hasMessageContaining("approved rule version are immutable");
        assertThatThrownBy(() -> TenantContext.runAs(t.tenantId(), () ->
                jdbc.sql("DELETE FROM requirement WHERE rule_version_id = :id").param("id", id).update()))
                .hasMessageContaining("approved rule version are immutable");
        Long count = TenantContext.runAs(t.tenantId(), () ->
                jdbc.sql("SELECT count(*) FROM requirement WHERE rule_version_id = :id").param("id", id).query(Long.class).single());
        assertThat(count).isEqualTo(4);
    }
}
