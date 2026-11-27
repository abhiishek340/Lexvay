package com.lexvay.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.rule.RequirementComparison.Aspect;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequirementComparisonTest {

    private static Requirement subject(String title, String text) {
        return new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-1", title, text, RequirementType.SUBJECT, null, null, null, 1, List.of());
    }

    private static Requirement hours(String text, String operator, String value) {
        return new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-2", "Hours", text, RequirementType.SUPERVISED_HOURS,
                NumericField.INTERNSHIP_HOURS, operator, value == null ? null : new BigDecimal(value), 1, List.of());
    }

    @Test
    void identicalRequirementsHaveNoChanges() {
        var a = subject("Ethics", "coursework in ethics");
        var b = subject("Ethics", "coursework in ethics");
        assertThat(RequirementComparison.changes(a, b)).isEmpty();
        assertThat(RequirementComparison.sameForEvaluation(a, b)).isTrue();
    }

    @Test
    void textIsComparedAfterWhitespaceNormalization() {
        assertThat(RequirementComparison.changes(subject("Ethics", "coursework  in\n ethics"), subject("Ethics", " coursework in ethics "))).isEmpty();
        assertThat(RequirementComparison.changes(subject("Ethics", "coursework in ethics"), subject("Ethics", "coursework in professional ethics")))
                .containsExactly(Aspect.TEXT);
    }

    @Test
    void textComparisonIsCaseSensitive() {
        assertThat(RequirementComparison.changes(subject("Ethics", "coursework in ethics"), subject("Ethics", "Coursework in ethics")))
                .containsExactly(Aspect.TEXT);
    }

    @Test
    void aTitleOnlyChangeStillCarriesOver() {
        var a = subject("Ethics", "coursework in ethics");
        var b = subject("Professional ethics", "coursework in ethics");
        assertThat(RequirementComparison.changes(a, b)).containsExactly(Aspect.TITLE);
        assertThat(RequirementComparison.sameForEvaluation(a, b)).isTrue();
    }

    @Test
    void aSearchTermsChangeIsReportedButStillCarriesOver() {
        var a = subject("Ethics", "coursework in ethics");
        var b = new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-1", "Ethics", "coursework in ethics",
                RequirementType.SUBJECT, null, null, null, 1, List.of("code of ethics", "confidentiality"));
        assertThat(RequirementComparison.changes(a, b)).containsExactly(Aspect.SEARCH_TERMS);
        assertThat(RequirementComparison.sameForEvaluation(a, b)).isTrue();
        assertThat(Aspect.SEARCH_TERMS.affectsEvaluation()).isFalse();
    }

    @Test
    void theOrderOfSearchTermsIsPartOfTheirComparisonAndTermsComeWithOtherChanges() {
        var ab = new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-1", "Ethics", "coursework in ethics",
                RequirementType.SUBJECT, null, null, null, 1, List.of("a", "b"));
        var ba = new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-1", "Ethics", "coursework in ethics and law",
                RequirementType.SUBJECT, null, null, null, 1, List.of("b", "a"));
        assertThat(RequirementComparison.changes(ab, ba)).containsExactlyInAnyOrder(Aspect.TEXT, Aspect.SEARCH_TERMS);
        assertThat(RequirementComparison.sameForEvaluation(ab, ba)).isFalse();
    }

    @Test
    void aTypeChangeIsReportedAndDoesNotCarryOver() {
        var a = subject("Ethics", "coursework in ethics");
        var b = new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-1", "Ethics", "coursework in ethics",
                RequirementType.JURISDICTION_SPECIFIC, null, null, null, 1, List.of());
        assertThat(RequirementComparison.changes(a, b)).containsExactly(Aspect.TYPE);
        assertThat(RequirementComparison.sameForEvaluation(a, b)).isFalse();
    }

    @Test
    void numericValuesCompareByValueNotScale() {
        assertThat(RequirementComparison.changes(hours("at least 600 hours", ">=", "600"), hours("at least 600 hours", ">=", "600.00"))).isEmpty();
        assertThat(RequirementComparison.changes(hours("at least 600 hours", ">=", "600"), hours("at least 700 hours", ">=", "700")))
                .containsExactlyInAnyOrder(Aspect.TEXT, Aspect.NUMERIC);
    }

    @Test
    void numericOperatorAndFieldChangesAreDetected() {
        assertThat(RequirementComparison.changes(hours("hours", ">=", "600"), hours("hours", ">", "600"))).containsExactly(Aspect.NUMERIC);
        var otherField = new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-2", "Hours", "hours", RequirementType.SUPERVISED_HOURS,
                NumericField.PRACTICUM_HOURS, ">=", new BigDecimal("600"), 1, List.of());
        assertThat(RequirementComparison.changes(hours("hours", ">=", "600"), otherField)).containsExactly(Aspect.NUMERIC);
        assertThat(RequirementComparison.sameForEvaluation(hours("hours", ">=", "600"), otherField)).isFalse();
    }

    @Test
    void aMissingNumericValueDiffersFromAPresentOne() {
        assertThat(RequirementComparison.changes(hours("hours", ">=", null), hours("hours", ">=", "0"))).containsExactly(Aspect.NUMERIC);
        assertThat(RequirementComparison.changes(hours("hours", ">=", "0"), hours("hours", ">=", null))).containsExactly(Aspect.NUMERIC);
        assertThat(RequirementComparison.changes(hours("hours", ">=", null), hours("hours", ">=", null))).isEmpty();
    }
}
