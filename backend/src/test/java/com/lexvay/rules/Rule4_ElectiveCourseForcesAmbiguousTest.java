package com.lexvay.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Product rule 4: evidence from a course that is not on every graduation path cannot be EVIDENCE_FOUND (AC-1). */
class Rule4_ElectiveCourseForcesAmbiguousTest extends AbstractAssessmentTest {

    static final String QUOTE = "the etiology of substance use disorders, screening and assessment of addictions,\nand evidence-based treatment of addictive behaviors";

    @Test
    void evidenceFromAnElectiveOfAChoiceIsDowngradedToAmbiguous() throws Exception {
        UUID programId = fx.program(t, List.of("UT"));
        UUID cv650 = fx.course(t, programId, "COUN 650", false, "3");
        fx.course(t, programId, "COUN 655", false, "3");
        var ids = fx.courseIds(t, programId);
        fx.clearPathway(t, programId);
        fx.pathwayGroup(t, programId, "2026-27", "Electives: choose 1 of 2", "CHOOSE_COUNT", 1, null, List.of(ids.get("COUN 650"), ids.get("COUN 655")));
        UUID syllabus = fx.document(t, programId, "coun650.txt", "SYLLABUS", cv650, SYLLABUS_650);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah elective", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Addictions", "coursework in addictions and substance use disorders"))));
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", syllabus, 1, QUOTE, "The elective covers addictions directly."));

        UUID assessmentId = fx.runAssessment(t, programId);
        var m = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(m.get("finding").asString()).isEqualTo("AMBIGUOUS");
        assertThat(m.get("rationale").asString()).contains("Downgraded to ambiguous").contains("COUN 650").contains("is not on every graduation path");
        assertThat(m.get("evidence").get("courseRequired").asBoolean()).isFalse();
        assertThat(m.get("evidence").get("quote").asString()).startsWith("the etiology of substance use disorders");
    }
}
