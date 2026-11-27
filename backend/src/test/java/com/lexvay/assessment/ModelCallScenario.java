package com.lexvay.assessment;

import com.lexvay.common.TestTenants;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmPurpose;
import java.util.List;
import java.util.UUID;

/**
 * A finished assessment with exactly two matching calls: UT-1 is found with a verifiable quote, UT-2 is
 * proposed with a quote that does not occur in the page. Usage and metrics tests share it.
 */
public final class ModelCallScenario {

    public static final String FABRICATED_QUOTE = "a sentence about telehealth that no page contains anywhere";

    public record Run(UUID assessmentId, long inputTokens, long outputTokens) {
    }

    private ModelCallScenario() {
    }

    public static Run run(AssessmentFixtures fx, FakeLlmClient fake, TestTenants.TenantWithUsers t) {
        UUID programId = fx.program(t, List.of("UT"));
        UUID courseVersion = fx.course(t, programId, "COUN 605", true);
        UUID syllabus = fx.document(t, programId, "coun605.txt", "SYLLABUS", courseVersion, AbstractAssessmentTest.SYLLABUS_605);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah usage", "Fictional Utah rule text.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Ethics", "coursework in professional ethics and legal standards"),
                AssessmentFixtures.subject("Telehealth", "training in telehealth practice"))));
        String found = fx.matchOutput("EVIDENCE_FOUND", syllabus, 1,
                "legal standards, confidentiality, duty to warn,\nand professional identity of clinical mental health counselors.",
                "The syllabus names legal standards and confidentiality as course content.");
        String fabricated = fx.matchOutput("EVIDENCE_FOUND", syllabus, 1, FABRICATED_QUOTE, "The syllabus covers telehealth.");
        fake.script("MATCH:UT-1", found);
        fake.script("MATCH:UT-2", fabricated);

        UUID assessmentId = fx.runAssessment(t, programId);

        long input = fake.calls().stream().filter(c -> c.purpose() == LlmPurpose.MATCH_EVIDENCE)
                .mapToLong(c -> c.userPrompt().length() / 4).sum();
        long output = (found.length() / 4) + (fabricated.length() / 4);
        return new Run(assessmentId, input, output);
    }
}
