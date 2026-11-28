package com.lexvay.eval;

/** A count out of a total, so the report can show both the rate and what it is made of. */
public record Ratio(int numerator, int denominator) {

    /** The 97.5th percentile of the standard normal distribution: the two-sided 95 % interval. */
    private static final double Z = 1.959963984540054;

    /** A confidence interval for the true rate, both ends between 0 and 1. */
    public record Interval(double low, double high) {
    }

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

    /**
     * The Wilson score interval at 95 %. Unlike the plain normal interval it is never empty at 0 or at all, and it stays
     * inside 0 to 1, which matters for the small denominators of an answer key. Only meaningful when the denominator is above zero.
     */
    public Interval wilson95() {
        double n = denominator;
        double p = value();
        double z2 = Z * Z;
        double scale = 1 + z2 / n;
        double centre = (p + z2 / (2 * n)) / scale;
        double half = Z * Math.sqrt(p * (1 - p) / n + z2 / (4 * n * n)) / scale;
        // At 0 successes the lower end is exactly 0, and at n the upper end is exactly 1; rounding must not move them.
        return new Interval(numerator == 0 ? 0 : Math.max(0, centre - half), numerator == denominator ? 1 : Math.min(1, centre + half));
    }

    /** The interval as the report prints it, or {@code n/a} when there is nothing to measure. */
    public String interval() {
        if (denominator == 0) {
            return "n/a";
        }
        Interval interval = wilson95();
        return "[" + Thresholds.percent(interval.low()) + ", " + Thresholds.percent(interval.high()) + "]";
    }
}
