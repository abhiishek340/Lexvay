package com.lexvay.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexvay.assessment.rules.RulesEngine;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.Requirement;
import com.lexvay.rule.RequirementType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RulesEngineTest {

    static final UUID SPAN = UUID.randomUUID();

    static Requirement numeric(String operator, String value) {
        return new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-1", "Credits", "At least " + value + " hours",
                RequirementType.NUMERIC, NumericField.TOTAL_CREDITS, operator, new BigDecimal(value), 1);
    }

    static Map<NumericField, RulesEngine.Fact> facts(String credits) {
        return Map.of(NumericField.TOTAL_CREDITS, new RulesEngine.Fact(NumericField.TOTAL_CREDITS, new BigDecimal(credits), SPAN));
    }

    @ParameterizedTest(name = "60 {0} {1} → {2}")
    @CsvSource({
            ">=, 60, EVIDENCE_FOUND", ">=, 61, EVIDENCE_MISSING", "<=, 60, EVIDENCE_FOUND", "<=, 59, EVIDENCE_MISSING",
            "=, 60, EVIDENCE_FOUND", "=, 48, EVIDENCE_MISSING", ">, 59, EVIDENCE_FOUND", ">, 60, EVIDENCE_MISSING",
            "<, 61, EVIDENCE_FOUND", "<, 60, EVIDENCE_MISSING"
    })
    void operatorsAreEvaluatedByCode(String operator, String value, Finding expected) {
        var result = RulesEngine.evaluate(numeric(operator, value), facts("60"));
        assertThat(result.finding()).isEqualTo(expected);
        assertThat(result.rationale()).isEqualTo("TOTAL_CREDITS 60 " + operator + " " + value + " → " + (expected == Finding.EVIDENCE_FOUND));
        assertThat(result.evidenceSpanId()).isEqualTo(SPAN);
    }

    @Test
    void missingFactIsEvidenceMissingWithoutSpan() {
        var result = RulesEngine.evaluate(numeric(">=", "600"), Map.of());
        assertThat(result.finding()).isEqualTo(Finding.EVIDENCE_MISSING);
        assertThat(result.rationale()).isEqualTo("No program fact recorded for TOTAL_CREDITS");
        assertThat(result.evidenceSpanId()).isNull();
    }

    @Test
    void decimalScaleDoesNotMatter() {
        var result = RulesEngine.evaluate(numeric("=", "60.0"), facts("60"));
        assertThat(result.finding()).isEqualTo(Finding.EVIDENCE_FOUND);
        assertThat(result.rationale()).isEqualTo("TOTAL_CREDITS 60 = 60 → true");
    }

    @Test
    void engineNeverProducesAmbiguousAndRefusesNonNumericTypes() {
        var subject = new Requirement(UUID.randomUUID(), UUID.randomUUID(), "X-2", "Ethics", "Ethics coursework",
                RequirementType.SUBJECT, null, null, null, 1);
        assertThatThrownBy(() -> RulesEngine.evaluate(subject, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
