package com.lexvay.rule;

import com.lexvay.document.QuoteVerifier;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** The single definition of "same requirement" (D-120): re-run carry-over and the rule version diff both use it. */
public final class RequirementComparison {

    /** What can differ between two requirements. A title-only change does not affect how the requirement is evaluated. */
    public enum Aspect {
        TITLE(false), TEXT(true), TYPE(true), NUMERIC(true);

        private final boolean affectsEvaluation;

        Aspect(boolean affectsEvaluation) {
            this.affectsEvaluation = affectsEvaluation;
        }

        public boolean affectsEvaluation() {
            return affectsEvaluation;
        }
    }

    private RequirementComparison() {
    }

    /** The aspects in which {@code b} differs from {@code a}; empty when they are the same requirement. */
    public static Set<Aspect> changes(Requirement a, Requirement b) {
        Set<Aspect> changes = EnumSet.noneOf(Aspect.class);
        if (!QuoteVerifier.normalizeWhitespace(a.title()).equals(QuoteVerifier.normalizeWhitespace(b.title()))) {
            changes.add(Aspect.TITLE);
        }
        if (!QuoteVerifier.normalizeWhitespace(a.text()).equals(QuoteVerifier.normalizeWhitespace(b.text()))) {
            changes.add(Aspect.TEXT);
        }
        if (a.type() != b.type()) {
            changes.add(Aspect.TYPE);
        }
        if (a.numericField() != b.numericField() || !Objects.equals(a.numericOperator(), b.numericOperator())
                || !sameValue(a.numericValue(), b.numericValue())) {
            changes.add(Aspect.NUMERIC);
        }
        return changes;
    }

    /** True when the earlier evaluation of {@code a} still holds for {@code b}: nothing but the title differs. */
    public static boolean sameForEvaluation(Requirement a, Requirement b) {
        return changes(a, b).stream().noneMatch(Aspect::affectsEvaluation);
    }

    private static boolean sameValue(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }
}
