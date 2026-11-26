package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Product rule 4: a quote too short to establish coverage is AMBIGUOUS even if the model said EVIDENCE_FOUND (D-023). */
class Rule4_ThinQuoteForcesAmbiguousTest extends AbstractAssessmentTest {

    @Test
    void shortQuoteIsDowngradedToAmbiguous() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        UUID cv = fx.course(t, programId, "COUN 605", true);
        UUID syllabus = fx.document(t, programId, "coun605.txt", "SYLLABUS", cv, SYLLABUS_605);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah thin", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"))));
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1, "ACA Code of Ethics", "Names the code."));

        UUID assessmentId = fx.runAssessment(t, programId);
        var m = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(m.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(m.get("rationale").asString()).contains("shorter than 40 characters");
        assertThat(m.get("evidence").get("quote").asString()).isEqualTo("ACA Code of Ethics");
    }
}
