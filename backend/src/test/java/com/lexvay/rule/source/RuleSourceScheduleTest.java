package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.common.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * When a source is due again (FIX-4, D-381): a failed fetch is retried after 15, 30, 60 minutes and so on but never later
 * than the interval, and a check that read the page waits the full interval and forgets the failures.
 */
class RuleSourceScheduleTest extends AbstractRuleSourceTest {

    private static final int INTERVAL_HOURS = 2;

    @Autowired RuleSourceService service;
    @Autowired RuleSourceRepository repository;

    private final String url = newUrl("html");

    /** A source with a two hour interval and no queued job, so every check in a test is one the test asks for. */
    private UUID newSource() {
        return inTenant(() -> service.register(new CreateRuleSourceRequest("TX", LICENSE, "Schedule source", url, INTERVAL_HOURS)));
    }

    private void failOnce(UUID sourceId) {
        fetcher.reject(url, "The board's server is down");
        checkNow(sourceId);
        assertThat(latestCheck(sourceId)).containsEntry("outcome", "FETCH_FAILED");
    }

    /** Minutes from the last check to the next one, and the failures in a row. Both are set in one statement, so the difference is exact. */
    private Map<String, Object> schedule(UUID sourceId) {
        return inTenant(() -> jdbc.sql("SELECT CAST(round(extract(epoch FROM next_check_at - last_checked_at) / 60) AS integer) AS minutes, "
                + "consecutive_failures AS failures FROM rule_source WHERE id = :id").param("id", sourceId).query().singleRow());
    }

    @Test
    void aNewSourceIsDueAtOnce() {
        UUID sourceId = newSource();

        assertThat(inTenant(() -> jdbc.sql("SELECT next_check_at <= now() AS due, consecutive_failures FROM rule_source WHERE id = :id")
                .param("id", sourceId).query().singleRow())).containsEntry("due", true).containsEntry("consecutive_failures", 0);
    }

    @Test
    void failedFetchesBackOffFifteenThirtySixtyMinutesAndNeverPastTheInterval() {
        UUID sourceId = newSource();
        List<Object> minutes = new ArrayList<>();
        List<Object> failures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            failOnce(sourceId);
            minutes.add(schedule(sourceId).get("minutes"));
            failures.add(schedule(sourceId).get("failures"));
        }

        assertThat(minutes).containsExactly(15, 30, 60, INTERVAL_HOURS * 60, INTERVAL_HOURS * 60);
        assertThat(failures).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void aFailureNeverMovesTheBaselineHash() {
        UUID sourceId = newSource();
        fetcher.html(url, page("Applicants need 66 semester hours."));
        checkNow(sourceId);
        Object baseline = source(sourceId).get("last_content_hash");

        failOnce(sourceId);

        assertThat(source(sourceId).get("last_content_hash")).isEqualTo(baseline);
    }

    @Test
    void aCheckThatReadTheFirstTimeClearsTheFailuresAndWaitsTheInterval() {
        UUID sourceId = newSource();
        failOnce(sourceId);
        failOnce(sourceId);
        assertThat(schedule(sourceId)).containsEntry("failures", 2).containsEntry("minutes", 30);

        fetcher.html(url, page("Applicants need 66 semester hours."));
        checkNow(sourceId);

        assertThat(latestCheck(sourceId)).containsEntry("outcome", "CHANGED");
        assertThat(schedule(sourceId)).containsEntry("failures", 0).containsEntry("minutes", INTERVAL_HOURS * 60);
    }

    @Test
    void anUnchangedPageClearsTheFailuresAndWaitsTheInterval() {
        UUID sourceId = newSource();
        fetcher.html(url, page("Applicants need 66 semester hours."));
        checkNow(sourceId);
        failOnce(sourceId);
        failOnce(sourceId);

        fetcher.html(url, page("Applicants need 66 semester hours."));
        checkNow(sourceId);

        assertThat(latestCheck(sourceId)).containsEntry("outcome", "UNCHANGED");
        assertThat(schedule(sourceId)).containsEntry("failures", 0).containsEntry("minutes", INTERVAL_HOURS * 60);
    }

    @Test
    void aChangedPageWhileADraftWaitsClearsTheFailuresAndWaitsTheInterval() {
        UUID sourceId = newSource();
        fetcher.html(url, page("Applicants need 66 semester hours."));
        checkNow(sourceId);
        failOnce(sourceId);
        failOnce(sourceId);

        fetcher.html(url, page("Applicants need 72 semester hours."));
        checkNow(sourceId);

        assertThat(latestCheck(sourceId)).containsEntry("outcome", "CHANGED_QUEUED");
        assertThat(schedule(sourceId)).containsEntry("failures", 0).containsEntry("minutes", INTERVAL_HOURS * 60);
    }

    @Test
    void aVeryLongRunOfFailuresStillWaitsTheIntervalAndDoesNotOverflow() {
        UUID sourceId = newSource();
        inTenant(() -> jdbc.sql("UPDATE rule_source SET consecutive_failures = 5000 WHERE id = :id").param("id", sourceId).update());

        failOnce(sourceId);

        assertThat(schedule(sourceId)).containsEntry("failures", 5001).containsEntry("minutes", INTERVAL_HOURS * 60);
    }

    @Test
    void askingForACheckLeavesTheScheduleAloneUntilItRuns() {
        UUID sourceId = newSource();
        failOnce(sourceId);
        var before = inTenant(() -> jdbc.sql("SELECT next_check_at, consecutive_failures, last_checked_at FROM rule_source WHERE id = :id")
                .param("id", sourceId).query().singleRow());

        UUID jobId = requestCheck(sourceId);

        assertThat(inTenant(() -> jdbc.sql("SELECT next_check_at, consecutive_failures, last_checked_at FROM rule_source WHERE id = :id")
                .param("id", sourceId).query().singleRow())).isEqualTo(before);
        run(jobId);
        assertThat(schedule(sourceId)).containsEntry("failures", 2).containsEntry("minutes", 30);
    }

    @Test
    void theApiShowsWhenASourceIsNextCheckedAndHowManyFetchesFailedInARow() {
        UUID sourceId = newSource();
        failOnce(sourceId);

        var listed = TenantContext.runAs(t.tenantId(), () -> repository.list(java.time.OffsetDateTime.now())).stream()
                .filter(view -> view.id().equals(sourceId)).findFirst().orElseThrow();

        assertThat(listed.consecutiveFailures()).isEqualTo(1);
        assertThat(listed.nextCheckAt()).isEqualTo(listed.lastCheckedAt().plusMinutes(15));
    }
}
