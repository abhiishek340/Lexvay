package com.lexvay.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.assessment.rules.RulesEngine;
import com.lexvay.pathway.PathwayCoverage;
import com.lexvay.program.CreditUnit;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.Requirement;
import com.lexvay.rule.RequirementType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RulesEngineTest {

    static final UUID SPAN = UUID.randomUUID();
    static final PathwayCoverage.Minimum UNKNOWN_MINIMUM = new PathwayCoverage.Minimum(null, null, "basis", List.of("Electives: minimum not recorded"));

    static Requirement numeric(NumericField field, String operator, String value) {
        return new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-1", "Credits", "At least " + value + " hours",
                RequirementType.NUMERIC, field, operator, new BigDecimal(value), 1, List.of());
    }

    static Requirement numeric(String operator, String value) {
        return numeric(NumericField.TOTAL_CREDITS, operator, value);
    }

    static Map<NumericField, RulesEngine.Fact> facts(NumericField field, String value) {
        return Map.of(field, new RulesEngine.Fact(field, new BigDecimal(value), SPAN, "degree-plan.pdf, page 1"));
    }

    static Map<NumericField, RulesEngine.Fact> facts(String credits) {
        return facts(NumericField.TOTAL_CREDITS, credits);
    }

    static PathwayCoverage.Minimum minimum(String credits, CreditUnit unit) {
        return new PathwayCoverage.Minimum(new BigDecimal(credits), unit, "the credits of the courses in Required courses; Electives", List.of());
    }

    @ParameterizedTest(name = "60 {0} {1} → {2}")
    @CsvSource({
            ">=, 60, EVIDENCE_FOUND", ">=, 61, EVIDENCE_MISSING", "<=, 60, EVIDENCE_FOUND", "<=, 59, EVIDENCE_MISSING",
            "=, 60, EVIDENCE_FOUND", "=, 48, EVIDENCE_MISSING", ">, 59, EVIDENCE_FOUND", ">, 60, EVIDENCE_MISSING",
            "<, 61, EVIDENCE_FOUND", "<, 60, EVIDENCE_MISSING"
    })
    void operatorsAreEvaluatedByCode(String operator, String value, Finding expected) {
        var result = RulesEngine.evaluate(numeric(operator, value), facts("60"), UNKNOWN_MINIMUM);
        assertThat(result.finding()).isEqualTo(expected);
        assertThat(result.rationale()).isEqualTo("TOTAL_CREDITS 60 " + operator + " " + value + " → " + (expected == Finding.EVIDENCE_FOUND));
        assertThat(result.evidenceSpanId()).isEqualTo(SPAN);
    }

    @Test
    void missingFactIsEvidenceMissingWithoutSpan() {
        var result = RulesEngine.evaluate(numeric(">=", "600"), Map.of(), minimum("36", CreditUnit.SEMESTER));
        assertThat(result.finding()).isEqualTo(Finding.EVIDENCE_MISSING);
        assertThat(result.rationale()).isEqualTo("No program fact recorded for TOTAL_CREDITS");
        assertThat(result.evidenceSpanId()).isNull();
    }

    @Test
    void decimalScaleDoesNotMatter() {
        var result = RulesEngine.evaluate(numeric("=", "60.0"), facts("60"), UNKNOWN_MINIMUM);
        assertThat(result.finding()).isEqualTo(Finding.EVIDENCE_FOUND);
        assertThat(result.rationale()).isEqualTo("TOTAL_CREDITS 60 = 60 → true");
    }

    @Test
    void nonNumericTypesAreRefused() {
        var subject = new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-2", "Ethics", "Ethics coursework",
                RequirementType.SUBJECT, null, null, null, 1, List.of());
        assertThatThrownBy(() -> RulesEngine.evaluate(subject, Map.of(), UNKNOWN_MINIMUM)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPathwayMinimumEqualToTheFactChangesNothing() {
        var result = RulesEngine.evaluate(numeric(">=", "60"), facts("60"), minimum("60.00", CreditUnit.SEMESTER));
        assertThat(result.finding()).isEqualTo(Finding.EVIDENCE_FOUND);
        assertThat(result.rationale()).isEqualTo("TOTAL_CREDITS 60 >= 60 → true");
    }

    @Test
    void aPathwayMinimumThatDiffersMakesTheFindingAmbiguousAndNamesBothNumbersAndTheirSources() {
        var result = RulesEngine.evaluate(numeric(">=", "60"), facts("60"), minimum("36.00", CreditUnit.SEMESTER));
        assertThat(result.finding()).isEqualTo(Finding.AMBIGUOUS);
        assertThat(result.rationale()).startsWith("TOTAL_CREDITS 60 >= 60 → true.")
                .contains("the program total of 60 (degree-plan.pdf, page 1)")
                .contains("differs from the pathway minimum of 36 semester credits (the credits of the courses in Required courses; Electives)");
        assertThat(result.evidenceSpanId()).isEqualTo(SPAN);
    }

    @Test
    void aDifferingMinimumAlsoDowngradesAFindingThatWasMissing() {
        var result = RulesEngine.evaluate(numeric(">=", "90"), facts("60"), minimum("72", CreditUnit.QUARTER));
        assertThat(result.finding()).isEqualTo(Finding.AMBIGUOUS);
        assertThat(result.rationale()).contains("pathway minimum of 72 quarter credits");
    }

    @Test
    void anUnknownMinimumLeavesTheComparisonAlone() {
        var result = RulesEngine.evaluate(numeric(">=", "60"), facts("60"), UNKNOWN_MINIMUM);
        assertThat(result.finding()).isEqualTo(Finding.EVIDENCE_FOUND);
    }

    @Test
    void onlyATotalOfCreditsIsComparedWithThePathway() {
        var result = RulesEngine.evaluate(numeric(NumericField.PRACTICUM_HOURS, ">=", "100"), facts(NumericField.PRACTICUM_HOURS, "100"),
                minimum("36", CreditUnit.SEMESTER));
        assertThat(result.finding()).isEqualTo(Finding.EVIDENCE_FOUND);
    }
}
