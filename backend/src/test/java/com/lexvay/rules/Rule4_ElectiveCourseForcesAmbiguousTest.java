package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Product rule 4: evidence from a course not every student takes cannot be EVIDENCE_FOUND. */
class Rule4_ElectiveCourseForcesAmbiguousTest extends AbstractAssessmentTest {

    @Test
    void evidenceFromAnElectiveIsDowngradedToAmbiguous() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        UUID cv650 = fx.course(t, programId, "COUN 650", false);
        UUID syllabus = fx.document(t, programId, "coun650.txt", "SYLLABUS", cv650, SYLLABUS_650);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah elective", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Addictions", "coursework in addictions and substance use disorders"))));
        String quote = "the etiology of substance use disorders, screening and assessment of addictions,\nand evidence-based treatment of addictive behaviors";
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1, quote, "The elective covers addictions directly."));

        UUID assessmentId = fx.runAssessment(t, programId);
        var m = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(m.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(m.get("rationale").asString()).contains("Downgraded to ambiguous").contains("COUN 650").contains("not required");
        assertThat(m.get("evidence").get("courseRequired").asBoolean()).isFalse();
        assertThat(m.get("evidence").get("quote").asString()).startsWith("the etiology of substance use disorders");
    }
}
