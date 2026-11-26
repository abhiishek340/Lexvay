package com.lexvay.assessment.rules;

import com.lexvay.assessment.Finding;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.Requirement;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Product rule 3: numbers are checked by code. Compares a program fact with a NUMERIC or
 * SUPERVISED_HOURS requirement and writes the calculation out in plain language (D-011). This package
 * has no dependency on the language-model seam; Rule3_ArchitectureTest keeps it that way.
 */
public final class RulesEngine {

    public record Fact(NumericField key, BigDecimal value, UUID evidenceSpanId) {
    }

    public record Result(Finding finding, String rationale, UUID evidenceSpanId) {
    }

    private RulesEngine() {
    }

    public static Result evaluate(Requirement requirement, Map<NumericField, Fact> facts) {
        if (!requirement.type().isNumeric()) {
            throw new IllegalArgumentException("Rules engine only evaluates numeric requirements, got " + requirement.type());
        }
        NumericField field = requirement.numericField();
        Fact fact = facts.get(field);
        if (fact == null) {
            return new Result(Finding.EVIDENCE_MISSING, "No program fact recorded for " + field, null);
        }
        boolean satisfied = compare(fact.value(), requirement.numericOperator(), requirement.numericValue());
        String calculation = field + " " + plain(fact.value()) + " " + requirement.numericOperator() + " "
                + plain(requirement.numericValue()) + " → " + satisfied;
        return new Result(satisfied ? Finding.EVIDENCE_FOUND : Finding.EVIDENCE_MISSING, calculation, fact.evidenceSpanId());
    }

    static boolean compare(BigDecimal actual, String operator, BigDecimal expected) {
        int c = actual.compareTo(expected);
        return switch (operator) {
            case ">=" -> c >= 0;
            case "<=" -> c <= 0;
            case "=" -> c == 0;
            case ">" -> c > 0;
            case "<" -> c < 0;
            default -> throw new IllegalArgumentException("Unknown operator " + operator);
        };
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
