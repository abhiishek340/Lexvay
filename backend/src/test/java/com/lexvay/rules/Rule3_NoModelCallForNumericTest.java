package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.llm.LlmPurpose;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Product rule 3 end to end: numeric requirements never reach the model. */
class Rule3_NoModelCallForNumericTest extends AbstractAssessmentTest {

    @Test
    void numericRequirementsAreEvaluatedWithoutTheModel() throws Exception {
        UUID programId = fx.program(t, List.of("CO"));
        UUID plan = fx.document(t, programId, "degree-plan.txt", "DEGREE_PLAN", null, DEGREE_PLAN);
        assertThat(fx.fact(t, programId, "TOTAL_CREDITS", "60", plan, 1, "a total of 60 semester credit hours")).hasStatus(201);
        assertThat(fx.fact(t, programId, "PRACTICUM_HOURS", "100", plan, 1, "a 100 clock hour practicum")).hasStatus(201);
        assertThat(fx.fact(t, programId, "INTERNSHIP_HOURS", "600", plan, 1, "a 600 clock hour internship")).hasStatus(201);
        fx.approvedRuleVersion(t, "CO", "Demo rule set Colorado numeric", "Fictional.", fx.requirements("CO", List.of(
                AssessmentFixtures.numeric("NUMERIC", "TOTAL_CREDITS", ">=", 60, "60 semester hours"),
                AssessmentFixtures.numeric("SUPERVISED_HOURS", "PRACTICUM_HOURS", ">=", 100, "100 practicum hours"),
                AssessmentFixtures.numeric("SUPERVISED_HOURS", "INTERNSHIP_HOURS", ">=", 700, "700 internship hours"))));

        UUID assessmentId = fx.runAssessment(t, programId);
        var mappings = fx.mappings(t, assessmentId, "CO");
        assertThat(mappings).hasSize(3);
        for (var m : mappings) {
            assertThat(m.get("method").asString()).isEqualTo("RULES_ENGINE");
            assertThat(m.get("rationale").asString()).matches("[A-Z_]+ \\d+ >= \\d+ → (true|false)");
        }
        assertThat(fx.mappingFor(mappings, "CO-3").get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(fx.mappingFor(mappings, "CO-3").get("rationale").asString()).isEqualTo("INTERNSHIP_HOURS 600 >= 700 → false");
        assertThat(fake.callCount(LlmPurpose.MATCH_EVIDENCE)).isZero();
        assertThat(fake.calls()).noneMatch(c -> c.purpose() == LlmPurpose.MATCH_EVIDENCE);
    }
}
