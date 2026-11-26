package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.common.TenantContext;
import com.lexvay.llm.LlmPurpose;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Product rule 1: the system proposes only EVIDENCE_FOUND, EVIDENCE_MISSING or AMBIGUOUS. A model output
 * with any other finding is rejected (and the database would refuse it anyway, see Rule5_ImmutabilityTest).
 * Only a human can confirm a gap, through a review event.
 */
class Rule1_FindingValuesTest extends AbstractAssessmentTest {

    @Autowired JdbcClient jdbc;

    @Test
    void forbiddenFindingFromTheModelIsRejectedAndRawOutputKept() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        UUID cv = fx.course(t, programId, "COUN 605", true);
        UUID syllabus = fx.document(t, programId, "coun605.txt", "SYLLABUS", cv, SYLLABUS_605);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah forbidden", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"))));
        fake.script("MATCH:UT-1", fx.matchOutput("CONFIRMED_GAP", syllabus, 1, "legal standards, confidentiality", "The system decided."));

        UUID assessmentId = fx.runAssessment(t, programId);

        var m = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(m.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(m.get("rationale").asString()).startsWith("Model output rejected");
        assertThat(m.get("evidence").isNull()).isTrue();
        assertThat(fake.callCount(LlmPurpose.MATCH_EVIDENCE)).isEqualTo(1);

        List<String> raw = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                        "SELECT response FROM llm_call WHERE assessment_id = :a").param("a", assessmentId).query(String.class).list());
        assertThat(raw).hasSize(1);
        assertThat(raw.get(0)).contains("CONFIRMED_GAP");

        List<String> findings = TenantContext.runAs(t.tenantId(), () -> jdbc.sql(
                "SELECT DISTINCT finding FROM mapping WHERE assessment_id = :a").param("a", assessmentId).query(String.class).list());
        assertThat(findings).allSatisfy(f -> assertThat(f).isIn("EVIDENCE_FOUND", "EVIDENCE_MISSING", "AMBIGUOUS"));
    }
}
