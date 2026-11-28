package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** The Wilson 95 % interval, against values worked out by hand from p, n and z = 1.96. */
class RatioTest {

    @Test
    void eightOutOfTenIsFortyNineToNinetyFourPercent() {
        // centre (0.8 + 1.96^2 / 20) / (1 + 1.96^2 / 10) = 0.7167; half-width 1.96 * sqrt(0.016 + 0.0096) / 1.3842 = 0.2266
        Ratio.Interval interval = new Ratio(8, 10).wilson95();

        assertThat(interval.low()).isCloseTo(0.4902, within(0.0001));
        assertThat(interval.high()).isCloseTo(0.9433, within(0.0001));
    }

    @Test
    void zeroOutOfThirtyThreeStillAllowsAboutTenPercent() {
        // At 0 successes the upper end is z^2 / (n + z^2) = 3.8415 / 36.8415
        Ratio.Interval interval = new Ratio(0, 33).wilson95();

        assertThat(interval.low()).isZero();
        assertThat(interval.high()).isCloseTo(0.1043, within(0.0001));
    }

    @Test
    void everyOneOfThirtyThreeStillAllowsAboutTenPercentBelow() {
        // At n successes the lower end is n / (n + z^2) = 33 / 36.8415
        Ratio.Interval interval = new Ratio(33, 33).wilson95();

        assertThat(interval.high()).isEqualTo(1.0);
        assertThat(interval.low()).isCloseTo(0.8957, within(0.0001));
    }

    @Test
    void theIntervalAlwaysContainsTheRateAndStaysBetweenZeroAndOne() {
        for (int n = 1; n <= 40; n++) {
            for (int k = 0; k <= n; k++) {
                Ratio ratio = new Ratio(k, n);
                Ratio.Interval interval = ratio.wilson95();
                assertThat(interval.low()).as(k + "/" + n).isBetween(0.0, ratio.value());
                assertThat(interval.high()).as(k + "/" + n).isBetween(ratio.value(), 1.0);
            }
        }
    }

    @Test
    void aLargerSampleGivesANarrowerIntervalForTheSameRate() {
        Ratio.Interval small = new Ratio(8, 10).wilson95();
        Ratio.Interval large = new Ratio(80, 100).wilson95();

        assertThat(large.high() - large.low()).isLessThan(small.high() - small.low());
    }

    @Test
    void theReportShowsTheIntervalAsPercentAndNothingWhenThereIsNothingToMeasure() {
        assertThat(new Ratio(0, 33).interval()).isEqualTo("[0.0 %, 10.4 %]");
        assertThat(new Ratio(0, 0).interval()).isEqualTo("n/a");
    }
}
