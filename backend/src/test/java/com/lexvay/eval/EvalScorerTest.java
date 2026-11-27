package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.Finding;
import com.lexvay.assessment.Mapping;
import com.lexvay.assessment.MappingMethod;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.RequirementType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The metric definitions of ROADMAP WS-EVAL, checked on a four-requirement world small enough to count by hand. */
class EvalScorerTest {

    static final String PAGE = "Students examine the ACA Code of Ethics and the legal standards.\nThe program requires 60 semester credit hours.";

    static Golden.Entry golden(String ref, RequirementType type, Finding finding, String document, String quote) {
        Golden.Numeric numeric = type.isNumeric() ? new Golden.Numeric(NumericField.TOTAL_CREDITS, ">=", BigDecimal.valueOf(60)) : null;
        return new Golden.Entry(ref, "UT", "title", "text", type, numeric, finding, document, quote, "reason");
    }

    static Mapping mapping(String ref, RequirementType type, Finding finding, MappingMethod method, String document, String quote) {
        Mapping.Evidence evidence = document == null ? null
                : new Mapping.Evidence(UUID.randomUUID(), UUID.randomUUID(), document, "SYLLABUS", null, null, null, 1, 0, quote.length(), quote);
        return new Mapping(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "UT", UUID.randomUUID(),
                new Mapping.RequirementView(UUID.randomUUID(), ref, "title", "text", type, null, null, null),
                finding, method, "rationale", false, evidence, null);
    }

    final Golden golden = new Golden(true, List.of(
            golden("UT-1", RequirementType.NUMERIC, Finding.EVIDENCE_FOUND, "degree-plan.pdf", "60 semester credit hours"),
            golden("UT-2", RequirementType.SUBJECT, Finding.EVIDENCE_FOUND, "ethics.pdf", "ACA Code of Ethics"),
            golden("UT-3", RequirementType.SUBJECT, Finding.AMBIGUOUS, "elective.pdf", "addictive disorders"),
            golden("UT-4", RequirementType.JURISDICTION_SPECIFIC, Finding.EVIDENCE_MISSING, null, null)));

    final Map<String, RequirementType> extractedAsGolden = Map.of("UT/UT-1", RequirementType.NUMERIC, "UT/UT-2", RequirementType.SUBJECT,
            "UT/UT-3", RequirementType.SUBJECT, "UT/UT-4", RequirementType.JURISDICTION_SPECIFIC);

    final List<Mapping> perfect = List.of(
            mapping("UT-1", RequirementType.NUMERIC, Finding.EVIDENCE_FOUND, MappingMethod.RULES_ENGINE, "degree-plan.pdf", "requires 60 semester credit hours."),
            mapping("UT-2", RequirementType.SUBJECT, Finding.EVIDENCE_FOUND, MappingMethod.MATCH, "ethics.pdf", "the ACA Code of Ethics and the legal standards"),
            mapping("UT-3", RequirementType.SUBJECT, Finding.AMBIGUOUS, MappingMethod.MATCH, "elective.pdf", "substance use and addictive disorders"),
            mapping("UT-4", RequirementType.JURISDICTION_SPECIFIC, Finding.EVIDENCE_MISSING, MappingMethod.MATCH, null, null));

    final List<EvalScorer.Span> goodSpan = List.of(new EvalScorer.Span("ethics.pdf", 1, "ACA  Code of\nEthics", PAGE));

    @Test
    void aRunThatMatchesTheGoldenSetScoresPerfectly() {
        EvalScorer.Score score = EvalScorer.score(golden, perfect, goodSpan, extractedAsGolden);
        Metrics m = score.metrics();
        assertThat(m.falseEvidenceFound()).isEqualTo(new Ratio(0, 2));
        assertThat(m.recall()).isEqualTo(new Ratio(2, 2));
        assertThat(m.quoteVerification()).isEqualTo(new Ratio(1, 1));
        assertThat(m.numericAccuracy()).isEqualTo(new Ratio(1, 1));
        assertThat(m.extractionAgreement()).isEqualTo(new Ratio(4, 4));
        assertThat(m.evidenceAgreement()).isEqualTo(new Ratio(3, 3));
        assertThat(score.mismatches()).isEmpty();
    }

    @Test
    void anEvidenceFoundThatTheGoldenSetDoesNotExpectIsFalseAndNamed() {
        List<Mapping> run = List.of(perfect.get(0), perfect.get(1),
                mapping("UT-3", RequirementType.SUBJECT, Finding.EVIDENCE_FOUND, MappingMethod.MATCH, "elective.pdf", "substance use and addictive disorders"),
                perfect.get(3));
        EvalScorer.Score score = EvalScorer.score(golden, run, goodSpan, extractedAsGolden);
        assertThat(score.metrics().falseEvidenceFound()).isEqualTo(new Ratio(1, 3));
        assertThat(score.mismatches()).extracting(EvalScorer.Mismatch::subject, EvalScorer.Mismatch::metric)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("UT/UT-3", "false EVIDENCE_FOUND"));
    }

    @Test
    void aMissedEvidenceFoundLowersRecallButNotTheFalseRate() {
        List<Mapping> run = List.of(perfect.get(0),
                mapping("UT-2", RequirementType.SUBJECT, Finding.AMBIGUOUS, MappingMethod.MATCH, "ethics.pdf", "the ACA Code of Ethics"),
                perfect.get(2), perfect.get(3));
        Metrics m = EvalScorer.score(golden, run, goodSpan, extractedAsGolden).metrics();
        assertThat(m.recall()).isEqualTo(new Ratio(1, 2));
        assertThat(m.falseEvidenceFound()).isEqualTo(new Ratio(0, 1));
    }

    @Test
    void aSpanWhoseQuoteIsNotInTheStoredPageFailsVerification() {
        List<EvalScorer.Span> spans = List.of(goodSpan.get(0), new EvalScorer.Span("x.pdf", 1, "a sentence that is not on the page", PAGE),
                new EvalScorer.Span("gone.pdf", 2, "quote", null));
        Metrics m = EvalScorer.score(golden, perfect, spans, extractedAsGolden).metrics();
        assertThat(m.quoteVerification()).isEqualTo(new Ratio(1, 3));
        assertThat(m.quoteVerification().isComplete()).isFalse();
    }

    @Test
    void numericAccuracyNeedsTheRulesEngineNotJustTheRightFinding() {
        List<Mapping> run = List.of(
                mapping("UT-1", RequirementType.NUMERIC, Finding.EVIDENCE_FOUND, MappingMethod.MATCH, "degree-plan.pdf", "60 semester credit hours"),
                perfect.get(1), perfect.get(2), perfect.get(3));
        assertThat(EvalScorer.score(golden, run, goodSpan, extractedAsGolden).metrics().numericAccuracy()).isEqualTo(new Ratio(0, 1));
    }

    @Test
    void extractionAgreementCountsRefAndType() {
        Map<String, RequirementType> extracted = Map.of("UT/UT-1", RequirementType.NUMERIC, "UT/UT-2", RequirementType.ACCREDITATION);
        assertThat(EvalScorer.score(golden, perfect, goodSpan, extracted).metrics().extractionAgreement()).isEqualTo(new Ratio(1, 4));
    }

    @Test
    void citingTheWrongDocumentOrQuoteLowersEvidenceAgreementWithoutChangingTheFinding() {
        List<Mapping> run = List.of(perfect.get(0),
                mapping("UT-2", RequirementType.SUBJECT, Finding.EVIDENCE_FOUND, MappingMethod.MATCH, "other.pdf", "the ACA Code of Ethics"),
                mapping("UT-3", RequirementType.SUBJECT, Finding.AMBIGUOUS, MappingMethod.MATCH, "elective.pdf", "an unrelated passage"),
                perfect.get(3));
        EvalScorer.Score score = EvalScorer.score(golden, run, goodSpan, extractedAsGolden);
        assertThat(score.metrics().evidenceAgreement()).isEqualTo(new Ratio(1, 3));
        assertThat(score.metrics().falseEvidenceFound()).isEqualTo(new Ratio(0, 2));
    }

    @Test
    void thresholdsTurnMetricsIntoBreaches() {
        Thresholds.Limits strict = new Thresholds.Limits(0.0, 1.0, 1.0, 1.0, 1.0);
        assertThat(Thresholds.breaches(strict, EvalScorer.score(golden, perfect, goodSpan, extractedAsGolden).metrics())).isEmpty();

        List<Mapping> run = List.of(perfect.get(0), perfect.get(1),
                mapping("UT-3", RequirementType.SUBJECT, Finding.EVIDENCE_FOUND, MappingMethod.MATCH, "elective.pdf", "substance use and addictive disorders"),
                perfect.get(3));
        List<EvalScorer.Span> badSpan = List.of(new EvalScorer.Span("x.pdf", 1, "not on the page", PAGE));
        assertThat(Thresholds.breaches(strict, EvalScorer.score(golden, run, badSpan, extractedAsGolden).metrics()))
                .anyMatch(b -> b.startsWith("false EVIDENCE_FOUND rate 33.3 %"))
                .anyMatch(b -> b.startsWith("quote verification 0.0 %"));

        Thresholds.Limits lenient = new Thresholds.Limits(0.5, 0.0, 0.0, 0.0, 0.0);
        assertThat(Thresholds.breaches(lenient, EvalScorer.score(golden, run, goodSpan, extractedAsGolden).metrics())).isEmpty();
        assertThat(Thresholds.breaches(lenient, EvalScorer.score(golden, run, badSpan, extractedAsGolden).metrics()))
                .as("quote verification has no limit to loosen").hasSize(1);
    }

    @Test
    void anEmptyRunCannotPass() {
        Thresholds.Limits strict = new Thresholds.Limits(0.0, 1.0, 1.0, 1.0, 1.0);
        Metrics m = EvalScorer.score(golden, List.of(), List.of(), Map.of()).metrics();
        assertThat(Thresholds.breaches(strict, m)).isNotEmpty().anyMatch(b -> b.startsWith("quote verification"));
    }
}
