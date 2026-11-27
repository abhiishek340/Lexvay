package com.lexvay.assessment;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.llm.LlmPurpose;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AssessmentUsageApiTest extends AbstractAssessmentTest {

    private JsonNode usage(UUID assessmentId) {
        var result = mvc.get().uri("/api/v1/assessments/" + assessmentId + "/usage").with(user(t.contributor())).exchange();
        assertThat(result).hasStatus(200);
        return fx.read(result);
    }

    @Test
    void reportsCallsTokensTimeAndEstimatedCost() {
        var run = ModelCallScenario.run(fx, fake, t);
        assertThat(fake.callCount(LlmPurpose.MATCH_EVIDENCE)).isEqualTo(2);

        JsonNode usage = usage(run.assessmentId());

        assertThat(usage.get("assessmentId").asString()).isEqualTo(run.assessmentId().toString());
        assertThat(usage.get("model").asString()).isEqualTo("fake-llm");
        assertThat(usage.get("calls").asLong()).isEqualTo(2);
        assertThat(usage.get("inputTokens").asLong()).isEqualTo(run.inputTokens()).isPositive();
        assertThat(usage.get("outputTokens").asLong()).isEqualTo(run.outputTokens()).isPositive();
        assertThat(usage.get("modelTimeMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(usage.get("wallTimeMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(usage.get("prices").get("inputPerMtok").decimalValue()).isEqualByComparingTo("2.00");
        assertThat(usage.get("prices").get("outputPerMtok").decimalValue()).isEqualByComparingTo("10.00");
        BigDecimal expectedCost = BigDecimal.valueOf(run.inputTokens() * 2 + run.outputTokens() * 10)
                .divide(BigDecimal.valueOf(1_000_000), 6, java.math.RoundingMode.HALF_UP);
        assertThat(usage.get("estimatedCostUsd").decimalValue()).isEqualByComparingTo(expectedCost);

        assertThat(usage.get("byPurpose")).hasSize(1);
        JsonNode match = usage.get("byPurpose").get(0);
        assertThat(match.get("purpose").asString()).isEqualTo("MATCH_EVIDENCE");
        assertThat(match.get("calls").asLong()).isEqualTo(2);
        assertThat(match.get("inputTokens").asLong()).isEqualTo(run.inputTokens());
        assertThat(match.get("outputTokens").asLong()).isEqualTo(run.outputTokens());
    }

    @Test
    void extractionCallsBelongToTheRuleVersionNotTheAssessment() {
        var run = ModelCallScenario.run(fx, fake, t);
        assertThat(fake.callCount(LlmPurpose.EXTRACT_REQUIREMENTS)).isEqualTo(1);
        assertThat(usage(run.assessmentId()).get("calls").asLong()).isEqualTo(2);
    }

    @Test
    void anAssessmentThatHasNotRunReportsZeroAndNoWallTime() {
        UUID programId = fx.program(t, List.of("UT"));
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah queued", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "60 hours"))));
        var started = mvc.post().uri("/api/v1/programs/" + programId + "/assessments").with(user(t.reviewer())).with(csrf()).exchange();
        assertThat(started).hasStatus(202);
        UUID assessmentId = UUID.fromString(fx.read(started).get("assessmentId").asString());

        JsonNode usage = usage(assessmentId);

        assertThat(usage.get("calls").asLong()).isZero();
        assertThat(usage.get("inputTokens").asLong()).isZero();
        assertThat(usage.get("estimatedCostUsd").decimalValue()).isEqualByComparingTo("0");
        assertThat(usage.get("wallTimeMs").isNull()).isTrue();
        assertThat(usage.get("byPurpose")).isEmpty();
    }

    @Test
    void anotherTenantGets404AndAnonymousGets401() {
        var run = ModelCallScenario.run(fx, fake, t);
        var other = tenants.create("usage-other");
        String uri = "/api/v1/assessments/" + run.assessmentId() + "/usage";

        assertThat(mvc.get().uri(uri).with(user(other.admin())).exchange()).hasStatus(404);
        assertThat(mvc.get().uri(uri).with(user(t.admin())).exchange()).hasStatus(200);
        assertThat(mvc.get().uri(uri).exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/api/v1/assessments/" + UUID.randomUUID() + "/usage").with(user(t.admin())).exchange()).hasStatus(404);
    }
}
