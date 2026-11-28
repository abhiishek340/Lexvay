package com.lexvay.eval;

/**
 * The measures of the evaluation lab. Each is a count out of a total ({@link Ratio}) so that the report always shows the
 * denominator and a Wilson interval; {@link LabScorer} defines them. {@code key} is the name used in lab-thresholds.yml and in the JSON report.
 */
public enum LabMetric {
    SEVERE_FALSE_ASSURANCE("severeFalseAssurance", "Severe false assurance", Direction.MAXIMUM),
    FINDING_ACCURACY("findingAccuracy", "Finding accuracy", Direction.MINIMUM),
    ABSTENTION_RATE("abstentionRate", "Abstention rate", Direction.MAXIMUM),
    EVIDENCE_PRECISION("evidencePrecision", "Evidence precision", Direction.MINIMUM),
    EVIDENCE_RECALL("evidenceRecall", "Evidence recall", Direction.MINIMUM),
    NUMERIC_ACCURACY("numericAccuracy", "Numeric accuracy", Direction.MINIMUM);

    /** Which way a limit points: a rate that must not exceed it, or one that must reach it. */
    public enum Direction {
        MAXIMUM("<="), MINIMUM(">=");

        private final String symbol;

        Direction(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }
    }

    private final String key;
    private final String label;
    private final Direction direction;

    LabMetric(String key, String label, Direction direction) {
        this.key = key;
        this.label = label;
        this.direction = direction;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public Direction direction() {
        return direction;
    }
}
