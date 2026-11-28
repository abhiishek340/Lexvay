package com.lexvay.eval;

import static com.lexvay.eval.LabFixtures.DOC_A;
import static com.lexvay.eval.LabFixtures.DOC_B;
import static com.lexvay.eval.LabFixtures.cited;
import static com.lexvay.eval.LabFixtures.eightRows;
import static com.lexvay.eval.LabFixtures.requirement;
import static com.lexvay.eval.LabFixtures.row;
import static com.lexvay.eval.LabFixtures.span;
import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.Finding;
import com.lexvay.pathway.Tri;
import com.lexvay.rule.RequirementType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every metric of the lab against the eight requirements of {@link LabFixtures#eightRows}, counted by hand. */
class LabScorerTest {

    final LabScorer.Score score = LabScorer.score(eightRows("k"));

    @Test
    void severeFalseAssuranceCountsFoundWhereTheKeySaysMissingOrNotApplicableAndAnElectiveQuoteForACriticalRequirement() {
        // UT-3 (key missing), CO-1 (key not applicable), CO-2 (quote from a course not every student takes, CRITICAL); 3 of 8
        assertThat(score.overall().get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(3, 8));
    }

    @Test
    void anElectiveQuoteForAStandardRequirementIsNotSevere() {
        var standard = requirement("UT", "UT-1", RequirementType.SUBJECT, AnswerKey.Expected.EVIDENCE_FOUND, AnswerKey.Severity.STANDARD,
                span(DOC_A, 1, 0, 100));

        var rows = List.of(row("k", standard, Finding.EVIDENCE_FOUND, cited(DOC_A, 1, 0, 100, Tri.FALSE)));

        assertThat(LabScorer.score(rows).overall().get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(0, 1));
    }

    @Test
    void aCourseTheGraduationPathsDoNotSayIsOnEveryPathCountsAgainstACriticalRequirementLikeOneThatIsNot() {
        var critical = requirement("UT", "UT-1", RequirementType.SUBJECT, AnswerKey.Expected.EVIDENCE_FOUND, AnswerKey.Severity.CRITICAL,
                span(DOC_A, 1, 0, 100));

        for (Tri onEveryPath : Tri.values()) {
            var rows = List.of(row("k", critical, Finding.EVIDENCE_FOUND, cited(DOC_A, 1, 0, 100, onEveryPath)));

            assertThat(LabScorer.score(rows).overall().get(LabMetric.SEVERE_FALSE_ASSURANCE)).as(onEveryPath.name())
                    .isEqualTo(new Ratio(onEveryPath == Tri.TRUE ? 0 : 1, 1));
        }
    }

    @Test
    void findingAccuracyLeavesOutWhatTheKeyCallsNotApplicableAndCountsAMissingAnswerAsWrong() {
        // UT-1, CO-2, CO-3 and AZ-2 are right; UT-2, UT-3 and AZ-1 (no answer) are wrong; CO-1 is left out: 4 of 7
        assertThat(score.overall().get(LabMetric.FINDING_ACCURACY)).isEqualTo(new Ratio(4, 7));
    }

    @Test
    void abstentionIsAmbiguousWhereTheKeyDecidedFoundOrMissing() {
        // The key decides UT-1, UT-2, UT-3, CO-2, CO-3, AZ-2; only UT-2 was answered AMBIGUOUS: 1 of 6
        assertThat(score.overall().get(LabMetric.ABSTENTION_RATE)).isEqualTo(new Ratio(1, 6));
    }

    @Test
    void evidencePrecisionIsTheShareOfCitedSpansThatOverlapAnAcceptedSpan() {
        // Cited: UT-1 (inside the span), UT-2 (same), UT-3 (the key accepts none), CO-2 (same), CO-3 (20 of 80 characters): 3 of 5
        assertThat(score.overall().get(LabMetric.EVIDENCE_PRECISION)).isEqualTo(new Ratio(3, 5));
    }

    @Test
    void evidenceRecallIsTheShareOfAcceptedSpansTheAnswerCitesAnOverlappingOneFor() {
        // The key accepts spans for UT-1, UT-2, CO-2, CO-3 and AZ-1; the answers cite a matching one for the first three: 3 of 5
        assertThat(score.overall().get(LabMetric.EVIDENCE_RECALL)).isEqualTo(new Ratio(3, 5));
    }

    @Test
    void numericAccuracyCountsNumericAndSupervisedHoursRequirements() {
        // UT-1 (NUMERIC) and CO-3 (SUPERVISED_HOURS) are both answered as the key says: 2 of 2
        assertThat(score.overall().get(LabMetric.NUMERIC_ACCURACY)).isEqualTo(new Ratio(2, 2));
    }

    @Test
    void everyMetricIsBrokenDownByStateRequirementTypeAndSeverity() {
        assertThat(score.breakdowns()).containsOnlyKeys("state", "requirementType", "severity");
        var byState = score.breakdowns().get("state");
        assertThat(byState).containsOnlyKeys("AZ", "CO", "UT");
        assertThat(byState.get("UT").get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(1, 3));
        assertThat(byState.get("UT").get(LabMetric.FINDING_ACCURACY)).isEqualTo(new Ratio(1, 3));
        assertThat(byState.get("CO").get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(2, 3));
        assertThat(byState.get("CO").get(LabMetric.FINDING_ACCURACY)).isEqualTo(new Ratio(2, 2));
        assertThat(byState.get("AZ").get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(0, 2));
        assertThat(byState.get("AZ").get(LabMetric.FINDING_ACCURACY)).isEqualTo(new Ratio(1, 2));

        var bySeverity = score.breakdowns().get("severity");
        assertThat(bySeverity.get("CRITICAL").get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(2, 3));
        assertThat(bySeverity.get("STANDARD").get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(1, 5));

        var byType = score.breakdowns().get("requirementType");
        assertThat(byType).containsOnlyKeys("NUMERIC", "SUBJECT", "SUPERVISED_HOURS");
        assertThat(byType.get("SUPERVISED_HOURS").get(LabMetric.NUMERIC_ACCURACY)).isEqualTo(new Ratio(1, 1));
        assertThat(byType.get("SUBJECT").get(LabMetric.NUMERIC_ACCURACY)).isEqualTo(new Ratio(0, 0));
    }

    @Test
    void theBreakdownsOfAnyDimensionAddUpToTheOverallCounts() {
        score.breakdowns().forEach((dimension, byValue) -> {
            for (LabMetric metric : LabMetric.values()) {
                int numerator = byValue.values().stream().mapToInt(m -> m.get(metric).numerator()).sum();
                int denominator = byValue.values().stream().mapToInt(m -> m.get(metric).denominator()).sum();
                assertThat(new Ratio(numerator, denominator)).as(dimension + " " + metric).isEqualTo(score.overall().get(metric));
            }
        });
    }

    @Test
    void severalKeysAddAKeyBreakdown() {
        List<LabScorer.Row> rows = new ArrayList<>(eightRows("first"));
        rows.addAll(eightRows("second"));

        LabScorer.Score pooled = LabScorer.score(rows);

        assertThat(pooled.overall().get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(6, 16));
        assertThat(pooled.breakdowns()).containsOnlyKeys("state", "requirementType", "severity", "key");
        assertThat(pooled.breakdowns().get("key").get("second").get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(3, 8));
    }

    @Test
    void aSpanMatchesAtHalfOfTheShorterSpanAndNotBelow() {
        var accepted = span(DOC_A, 1, 100, 200);

        assertThat(accepted.overlaps(DOC_A, 1, 150, 250)).as("50 of 100 characters").isTrue();
        assertThat(accepted.overlaps(DOC_A, 1, 151, 251)).as("49 of 100").isFalse();
        assertThat(accepted.overlaps(DOC_A, 1, 110, 120)).as("a short quote wholly inside").isTrue();
        assertThat(accepted.overlaps(DOC_A, 1, 0, 1000)).as("a quote that covers the whole page and more").isTrue();
        assertThat(accepted.overlaps(DOC_A, 1, 200, 300)).as("adjacent").isFalse();
        assertThat(accepted.overlaps(DOC_A, 2, 100, 200)).as("another page").isFalse();
        assertThat(accepted.overlaps(DOC_B, 1, 100, 200)).as("another document").isFalse();
    }
}
