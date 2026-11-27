package com.lexvay.assessment.match;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AbstractAssessmentTest;
import com.lexvay.assessment.AssessmentFixtures;
import com.lexvay.document.QuoteVerifier;
import com.lexvay.llm.LlmPurpose;
import com.lexvay.llm.LlmRequest;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * What the model is shown of a long page (D-174, prompt match-v2) and what happens to a quote taken from it: the prompt
 * carries the excerpt and not the page, a quote from inside one part verifies against the full stored page, and a quote
 * that spans the omission marker does not.
 */
class ExcerptMatchingTest extends AbstractAssessmentTest {

    static final String EVIDENCE = "Week 11 covers the duty to warn, confidentiality and the limits of privilege in practice, with case studies.";

    /** Sixty lines, the evidence on line 40, nothing else that shares a word with the requirement. */
    static final String LONG_SYLLABUS = IntStream.rangeClosed(1, 60)
            .mapToObj(n -> n == 40 ? EVIDENCE : "Line " + n + " of the syllabus keeps going with ordinary words about nothing in particular here.")
            .collect(Collectors.joining("\n"));

    UUID document;

    private UUID assess() {
        UUID programId = fx.program(t, List.of("UT"));
        document = fx.document(t, programId, "long-syllabus.txt", "SYLLABUS", null, LONG_SYLLABUS);
        fx.approvedRuleVersion(t, "UT", "Demo rule set Utah excerpts", "Fictional.", fx.requirements("UT", List.of(
                AssessmentFixtures.subject("Confidentiality", "coursework on confidentiality and the duty to warn"))));
        return programId;
    }

    private LlmRequest matchRequest() {
        return fake.calls().stream().filter(c -> c.purpose() == LlmPurpose.MATCH_EVIDENCE).findFirst().orElseThrow();
    }

    @Test
    void thePromptCarriesTheExcerptAndNotTheWholePageAndAQuoteFromItIsVerifiedAgainstTheStoredPage() {
        assertThat(LONG_SYLLABUS.length()).isGreaterThan(PassageExcerpter.WHOLE_PAGE_CHARS);
        UUID programId = assess();
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", document, 1, EVIDENCE, "The week covers the duty to warn."));

        UUID assessmentId = fx.runAssessment(t, programId);

        String prompt = matchRequest().userPrompt();
        assertThat(prompt).contains(EVIDENCE).contains("\n" + PassageExcerpter.OMISSION + "\n").contains("Line 1 of the syllabus");
        assertThat(prompt).doesNotContain("Line 20 of the syllabus").doesNotContain("Line 59 of the syllabus");
        assertThat(prompt.length()).isLessThan(LONG_SYLLABUS.length());
        var mapping = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(mapping.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(mapping.get("evidence").get("quote").asString()).isEqualTo(EVIDENCE);
        assertThat(fx.assessment(t, assessmentId).get("promptVersion").asString()).isEqualTo("match-v2");
    }

    @Test
    void aQuoteThatSpansTheOmissionMarkerIsNotInThePageAndIsDiscarded() {
        UUID programId = assess();
        String spanning = "Line 3 of the syllabus keeps going with ordinary words about nothing in particular here.\n"
                + PassageExcerpter.OMISSION + "\n" + EVIDENCE;
        assertThat(QuoteVerifier.find(LONG_SYLLABUS, spanning)).isEmpty();
        fake.script("MATCH:UT-1", fx.matchOutput("EVIDENCE_FOUND", document, 1, spanning, "Joins two parts."));

        UUID assessmentId = fx.runAssessment(t, programId);

        var mapping = fx.mappingFor(fx.mappings(t, assessmentId, "UT"), "UT-1");
        assertThat(mapping.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(mapping.get("rationale").asString()).isEqualTo(EvidenceMatcher.NO_VERIFIABLE_PASSAGE);
        assertThat(mapping.get("evidence").isNull()).isTrue();
    }
}
