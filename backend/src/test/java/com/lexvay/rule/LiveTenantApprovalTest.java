package com.lexvay.rule;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.tenant.DataMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * RG-1 with MODE-1 (D-561): in a tenant with real data a rule version is approved only when the basis of its dates is
 * recorded and does not say the dates are fictional demo data. The requirements are entered by the reviewer, because
 * the scripted model is refused for real data.
 */
class LiveTenantApprovalTest extends AbstractAssessmentTest {

    @BeforeEach
    void liveTenant() {
        t = tenants.create("live-approval", DataMode.LIVE);
    }

    private UUID versionWithRequirements(String label, String datesBasis) throws Exception {
        var body = new HashMap<String, Object>(Map.of("jurisdiction", "CA", "licenseType", AssessmentFixtures.LICENSE, "label", label,
                "sourceUrl", "https://example.invalid/live", "text", "Fictional rule text. " + label, "legalStatus", "IN_FORCE",
                "effectiveFrom", "2026-07-01", "datesBasis", datesBasis));
        var created = mvc.post().uri("/api/v1/rule-versions").with(user(t.contributor())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
        assertThat(created).hasStatus(202);
        UUID id = UUID.fromString(fx.read(created).get("ruleVersionId").asString());
        var entered = mvc.put().uri("/api/v1/rule-versions/" + id + "/requirements").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(fx.requirements("CA", List.of(AssessmentFixtures.subject("Ethics", "coursework in professional ethics")))).exchange();
        assertThat(entered).hasStatus(200);
        return id;
    }

    private MvcTestResult approve(UUID id) {
        return mvc.post().uri("/api/v1/rule-versions/" + id + "/requirements/approve").with(user(t.reviewer())).with(csrf()).exchange();
    }

    @Test
    void datesThatAreFictionalDemoDataCannotBeApprovedForRealData() throws Exception {
        UUID id = versionWithRequirements("Live rule set with demo dates", "Fictional demo data");

        var refused = approve(id);

        assertThat(refused).hasStatus(409);
        assertThat(fx.read(refused).get("detail").asString()).contains("fictional demo data");
    }

    @Test
    void aRealBasisIsApprovedAndACorrectionOfTheBasisMakesAFictionalOneApprovable() throws Exception {
        UUID real = versionWithRequirements("Live rule set with a real basis", "Fictional bulletin notice 4, section 2");
        assertThat(approve(real)).hasStatus(200);

        UUID corrected = versionWithRequirements("Live rule set corrected", "Fictional demo data");
        assertThat(approve(corrected)).hasStatus(409);
        var correction = mvc.post().uri("/api/v1/rule-versions/" + corrected + "/status").with(user(t.reviewer())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("legalStatus", "IN_FORCE",
                        "effectiveFrom", "2026-07-01", "datesBasis", "Fictional board notice 7, paragraph 1")))
                .exchange();
        assertThat(correction).hasStatus(200);
        assertThat(approve(corrected)).hasStatus(200);
    }
}
