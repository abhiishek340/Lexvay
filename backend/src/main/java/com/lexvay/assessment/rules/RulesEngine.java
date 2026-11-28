package com.lexvay.assessment.rules;

import com.lexvay.assessment.Finding;
import com.lexvay.pathway.PathwayCoverage;
import com.lexvay.rule.NumericField;
import com.lexvay.rule.Requirement;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Product rule 3: numbers are checked by code. Compares a program fact with a NUMERIC or
 * SUPERVISED_HOURS requirement and writes the calculation out in plain language (D-011). A total of credits is also
 * checked against the fewest credits of the program's graduation paths (AC-1). This package has no dependency on the
 * language-model seam; Rule3_ArchitectureTest keeps it that way.
 */
public final class RulesEngine {

    /** {@code source} says where the person found the number, for a sentence that names it. */
    public record Fact(NumericField key, BigDecimal value, UUID evidenceSpanId, String source) {
    }

    public record Result(Finding finding, String rationale, UUID evidenceSpanId) {
    }

    private RulesEngine() {
    }

    /**
     * Compares the fact with the requirement. For a total of credits whose pathway minimum is known and differs from the
     * fact, the finding is AMBIGUOUS whatever the comparison said: two recorded numbers for one thing are unsure (rule 4).
     */
    public static Result evaluate(Requirement requirement, Map<NumericField, Fact> facts, PathwayCoverage.Minimum pathwayMinimum) {
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
        if (field == NumericField.TOTAL_CREDITS && pathwayMinimum.known() && fact.value().compareTo(pathwayMinimum.credits()) != 0) {
            return new Result(Finding.AMBIGUOUS, calculation + ". Downgraded to ambiguous: the program total of " + plain(fact.value())
                    + " (" + fact.source() + ") differs from the pathway minimum of " + plain(pathwayMinimum.credits()) + " "
                    + pathwayMinimum.unit().name().toLowerCase(Locale.ROOT) + " credits (" + pathwayMinimum.basis() + ").", fact.evidenceSpanId());
        }
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
