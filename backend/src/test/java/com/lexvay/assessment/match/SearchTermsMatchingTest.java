package com.lexvay.assessment.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.llm.LlmPurpose;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What the matcher does with a page that only the requirement's search terms found (F7c): the model is shown the page like any
 * other and its quote is verified against the stored page like any other (product rule 2); the only difference is one sentence of
 * the rationale that tells the reviewer how the page was found.
 */
class SearchTermsMatchingTest extends AbstractAssessmentTest {

    static final String QUOTE = "Students study the classification of psychiatric syndromes and how presentations are described in adults.";
    static final String PAGE = "Seminar in psychopathology. " + QUOTE;

    UUID document;

    private UUID program(String... terms) {
        UUID programId = fx.program(t, List.of("UT"));
        document = fx.document(t, programId, "seminar.txt", "SYLLABUS", null, PAGE);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah terms " + terms.length, "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.withTerms(AssessmentFixtures.subject("Diagnosis", "coursework in the diagnosis of mental and emotional disorders"), terms))));
        return programId;
    }

    @Test
    void aPageFoundOnlyThroughTermsIsOfferedVerifiedAndExplainedInTheRationale() {
        UUID programId = program("psychopathology", "psychiatric syndromes", "marble");
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", document, 1, QUOTE, "The seminar covers the classification of syndromes."));

        UUID assessmentId = fx.runAssessment(t, programId);

        var mapping = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(mapping.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(mapping.get("evidence").get("quote").asString()).as("still verified against the stored page").isEqualTo(QUOTE);
        assertThat(mapping.get("rationale").asString())
                .isEqualTo("The seminar covers the classification of syndromes. Found through the search terms: psychopathology, psychiatric syndromes.");
        assertThat(fake.calls().stream().filter(c -> c.purpose() == LlmPurpose.MATCH_EVIDENCE).findFirst().orElseThrow().userPrompt())
                .as("the model is shown the page, not the terms").contains(PAGE).doesNotContain("psychopathology,");
    }

    @Test
    void aQuoteThatIsNotOnThePageIsStillDiscardedWhateverFoundThePage() {
        UUID programId = program("psychopathology");
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", document, 1,
                "Students study the classification of every disorder in the manual and how it is diagnosed.", "Invented."));

        UUID assessmentId = fx.runAssessment(t, programId);

        var mapping = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(mapping.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(mapping.get("rationale").asString()).isEqualTo(EvidenceMatcher.NO_VERIFIABLE_PASSAGE);
        assertThat(mapping.get("evidence").isNull()).isTrue();
    }

    @Test
    void withoutTermsTheSamePageIsNotAChoiceAtAll() {
        UUID programId = program();

        UUID assessmentId = fx.runAssessment(t, programId);

        var mapping = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(mapping.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(mapping.get("rationale").asString()).isEqualTo("No candidate passages found in the program's documents");
        assertThat(fake.callCount(LlmPurpose.MATCH_EVIDENCE)).isZero();
    }

    @Test
    void aPageThatSharesAWordWithTheRequirementCarriesNoSuchSentence() {
        UUID programId = fx.program(t, List.of("UT"));
        UUID plain = fx.document(t, programId, "plain.txt", "SYLLABUS", null,
                "Seminar in psychopathology. Students learn the diagnosis of disorders and how a presentation is described in adults.");
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah terms own words", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.withTerms(AssessmentFixtures.subject("Diagnosis", "coursework in the diagnosis of mental and emotional disorders"),
                        "psychopathology"))));
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", plain, 1,
                "Students learn the diagnosis of disorders and how a presentation is described in adults.", "Covers diagnosis."));

        UUID assessmentId = fx.runAssessment(t, programId);

        var mapping = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(mapping.get("rationale").asString()).isEqualTo("Covers diagnosis.");
    }
}
