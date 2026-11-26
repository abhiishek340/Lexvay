package com.lexvay.eval;

/** A count out of a total, so the report can show both the rate and what it is made of. */
public record Ratio(int numerator, int denominator) {

    /** Only meaningful when the denominator is above zero; callers check that first. */
    public double value() {
        return (double) numerator / denominator;
    }

    public boolean isComplete() {
        return denominator > 0 && numerator == denominator;
    }

    public String percent() {
        return denominator == 0 ? "n/a" : Thresholds.percent(value());
    }

    public String counts() {
        return numerator + " / " + denominator;
    }
}
