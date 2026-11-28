package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The health of a source, from its checks alone (RG-2, D-573): never stored, and nothing about the law. */
class SourceHealthTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 12, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime CREATED = NOW.minusDays(30);

    /** A permitted source with a 24 hour interval, last read an hour ago, whose last check was UNCHANGED. */
    private static SourceHealth.Facts healthy() {
        return new SourceHealth.Facts(AccessStatus.PERMITTED, CheckOutcome.UNCHANGED, 0, NOW.minusHours(1), CREATED, 24);
    }

    private static SourceHealth of(SourceHealth.Facts facts) {
        return SourceHealth.of(facts, NOW);
    }

    private static SourceHealth.Facts with(SourceHealth.Facts base, AccessStatus access, CheckOutcome latest, int failures,
                                           OffsetDateTime lastSuccess) {
        return new SourceHealth.Facts(access, latest, failures, lastSuccess, base.createdAt(), base.checkIntervalHours());
    }

    @Test
    void aPermittedSourceReadRecentlyIsHealthy() {
        assertThat(of(healthy())).isEqualTo(SourceHealth.HEALTHY);
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.NOT_MODIFIED, 0, NOW.minusHours(1)))).isEqualTo(SourceHealth.HEALTHY);
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.CHANGED_QUEUED, 0, NOW.minusHours(1)))).isEqualTo(SourceHealth.HEALTHY);
    }

    @Test
    void aSourceNobodyReviewedIsUnreviewedWhateverItsChecksSay() {
        assertThat(of(with(healthy(), AccessStatus.UNREVIEWED, CheckOutcome.UNCHANGED, 0, NOW.minusHours(1)))).isEqualTo(SourceHealth.UNREVIEWED);
        assertThat(of(with(healthy(), AccessStatus.UNREVIEWED, null, 0, null))).as("never checked").isEqualTo(SourceHealth.UNREVIEWED);
    }

    @Test
    void restrictedAndBlockedAccessAndRobotsAreBlocked() {
        assertThat(of(with(healthy(), AccessStatus.RESTRICTED, CheckOutcome.UNCHANGED, 0, NOW))).isEqualTo(SourceHealth.BLOCKED);
        assertThat(of(with(healthy(), AccessStatus.BLOCKED, CheckOutcome.FETCH_FAILED, 5, null))).isEqualTo(SourceHealth.BLOCKED);
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.BLOCKED_BY_ROBOTS, 0, NOW.minusHours(1)))).isEqualTo(SourceHealth.BLOCKED);
        assertThat(of(with(healthy(), AccessStatus.UNREVIEWED, CheckOutcome.BLOCKED_BY_ROBOTS, 0, null))).as("blocked outranks unreviewed")
                .isEqualTo(SourceHealth.BLOCKED);
    }

    @Test
    void suspectContentInTheLastCheckIsSuspect() {
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.SUSPECT_CONTENT, 0, NOW.minusHours(30)))).isEqualTo(SourceHealth.SUSPECT);
    }

    @Test
    void threeFailuresInARowAreFailingAndTwoAreNot() {
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.FETCH_FAILED, 3, NOW.minusHours(3)))).isEqualTo(SourceHealth.FAILING);
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.FETCH_FAILED, 2, NOW.minusHours(3)))).isEqualTo(SourceHealth.HEALTHY);
    }

    @Test
    void noSuccessWithinTwiceTheIntervalIsStale() {
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.UNCHANGED, 0, NOW.minusHours(48)))).as("exactly twice is still fine")
                .isEqualTo(SourceHealth.HEALTHY);
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.UNCHANGED, 0, NOW.minusHours(49)))).isEqualTo(SourceHealth.STALE);
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.FETCH_FAILED, 1, NOW.minusHours(49)))).isEqualTo(SourceHealth.STALE);
    }

    @Test
    void aSourceNeverReadIsStaleOnlyOnceItIsOlderThanTwiceItsInterval() {
        var fresh = new SourceHealth.Facts(AccessStatus.PERMITTED, null, 0, null, NOW.minusHours(5), 24);
        var old = new SourceHealth.Facts(AccessStatus.PERMITTED, CheckOutcome.FETCH_FAILED, 1, null, NOW.minusHours(60), 24);

        assertThat(of(fresh)).isEqualTo(SourceHealth.HEALTHY);
        assertThat(of(old)).isEqualTo(SourceHealth.STALE);
    }

    @Test
    void severalProblemsShowTheFirstOfBlockedUnreviewedSuspectFailingStale() {
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.SUSPECT_CONTENT, 4, NOW.minusDays(9)))).isEqualTo(SourceHealth.SUSPECT);
        assertThat(of(with(healthy(), AccessStatus.PERMITTED, CheckOutcome.FETCH_FAILED, 4, NOW.minusDays(9)))).isEqualTo(SourceHealth.FAILING);
    }
}
