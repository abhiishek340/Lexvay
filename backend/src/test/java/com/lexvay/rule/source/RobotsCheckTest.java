package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.TenantContext;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * robots.txt as the monitor meets it (RG-2, D-571): fetched per host at most daily, cached, and obeyed. The rules of
 * its grammar are in {@link RobotsRulesTest}; here are the decisions a check makes on them.
 */
class RobotsCheckTest extends AbstractRuleSourceTest {

    private static final String HOST = FakeHostResolver.PUBLIC_HOST;

    @Autowired RuleSourceService service;

    private static String pageUrl(String path) {
        return "https://" + HOST + path + "/" + UUID.randomUUID() + ".html";
    }

    private long robotsRequests() {
        return fetcher.requests().stream().filter(r -> r.url().getPath().equals("/robots.txt")).count();
    }

    private boolean fetched(String url) {
        return fetcher.requested().contains(URI.create(url));
    }

    private long robotsCachedCount() {
        return transaction.execute(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            return jdbc.sql("SELECT count(*) FROM source_host_fetch WHERE host = :host AND robots_fetched_at IS NOT NULL")
                    .param("host", HOST).query(Long.class).single();
        });
    }

    private Map<String, Object> robotsRow() {
        return transaction.execute(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            return jdbc.sql("SELECT robots_status, robots_body, robots_fetched_at FROM source_host_fetch WHERE host = :host")
                    .param("host", HOST).query().singleRow();
        });
    }

    @Test
    void aDisallowForTheAgentOrStarBlocksThePageAndNothingMoreIsFetched() {
        String url = pageUrl("/rules");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        fetcher.robots(url, 200, "User-agent: *\nDisallow: /rules/\n");
        UUID id = register("CA", url);

        runner.runOne();

        var check = latestCheck(id);
        assertThat(check).containsEntry("outcome", "BLOCKED_BY_ROBOTS");
        assertThat(check.get("http_status")).isNull();
        assertThat((String) check.get("detail")).contains("robots.txt of " + HOST).contains("forbids").contains("Disallow: /rules/")
                .contains("LexvayMonitor");
        assertThat(fetched(url)).as("the page was not requested").isFalse();
        assertThat(count("rule_version")).isZero();
        assertThat(source(id)).containsEntry("last_content_hash", null);
        var schedule = inTenant(() -> jdbc.sql("SELECT consecutive_failures, next_check_at > now() + interval '23 hours' AS waits_the_interval "
                + "FROM rule_source WHERE id = :id").param("id", id).query().singleRow());
        assertThat(schedule).as("being asked to stay away is not a failure").containsEntry("consecutive_failures", 0)
                .containsEntry("waits_the_interval", true);
        var health = body(mvc.get().uri("/api/v1/rule-sources").with(user(t.reviewer())).exchange()).get(0);
        assertThat(health.get("health").asString()).isEqualTo("BLOCKED");
    }

    @Test
    void ourOwnGroupAndTheLongestMatchDecide() {
        String allowed = pageUrl("/rules/public");
        String refused = pageUrl("/rules/private");
        fetcher.html(allowed, page("Applicants need 66 semester hours."));
        fetcher.html(refused, page("Applicants need 66 semester hours."));
        fetcher.robots(allowed, 200, """
                User-agent: *
                Disallow: /
                User-agent: LexvayMonitor
                Disallow: /rules/
                Allow: /rules/public/
                """);
        UUID allowedSource = register("CA", allowed);
        UUID refusedSource = register("TX", refused);

        runner.runPending();

        assertThat(latestCheck(allowedSource)).containsEntry("outcome", "CHANGED");
        assertThat(latestCheck(refusedSource)).containsEntry("outcome", "BLOCKED_BY_ROBOTS");
    }

    @Test
    void aMissingOrForbiddenRobotsFileMeansEverythingIsAllowed() {
        Map<Integer, String> statesByStatus = Map.of(404, "CA", 403, "TX", 410, "UT");
        for (int status : List.of(404, 403, 410)) {
            String url = pageUrl("/rules");
            fetcher.html(url, page("Applicants need 66 semester hours."));
            fetcher.robots(url, status, "Forbidden");
            UUID id = register(statesByStatus.get(status), url);
            forgetHostRows();

            runner.runPending();

            assertThat(latestCheck(id)).as("robots.txt answered " + status).containsEntry("outcome", "CHANGED");
            assertThat(robotsRow()).containsEntry("robots_status", status).containsEntry("robots_body", null);
        }
    }

    @Test
    void aRobotsFileThatFailsWithAServerErrorMeansTryLaterAndTheSiteIsNotTouched() {
        for (int status : List.of(500, 503, 429)) {
            String url = pageUrl("/rules");
            fetcher.html(url, page("Applicants need 66 semester hours."));
            fetcher.robots(url, status, "oops");
            UUID id = register("CA", url);
            forgetHostRows();

            runner.runOne();

            var check = latestCheck(id);
            assertThat(check).as("robots.txt answered " + status).containsEntry("outcome", "FETCH_FAILED");
            assertThat((String) check.get("detail")).contains("robots.txt of " + HOST + " answered HTTP " + status);
            assertThat(fetched(url)).isFalse();
            assertThat(inTenant(() -> jdbc.sql("SELECT consecutive_failures FROM rule_source WHERE id = :id").param("id", id)
                    .query(Integer.class).single())).as("the backoff of D-381 applies").isEqualTo(1);
            assertThat(robotsCachedCount()).as("an unreadable robots.txt is not cached").isZero();
        }
    }

    @Test
    void aRobotsFileThatCannotBeReachedIsTreatedLikeAServerError() {
        String url = pageUrl("/rules");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        fetcher.reject(URI.create(url).resolve("/robots.txt").toString(), "Connection refused");
        UUID id = register("CA", url);

        runner.runOne();

        assertThat(latestCheck(id)).containsEntry("outcome", "FETCH_FAILED");
        assertThat((String) latestCheck(id).get("detail")).contains("robots.txt of " + HOST + " could not be fetched").contains("Connection refused");
        assertThat(fetched(url)).isFalse();
    }

    @Test
    void robotsTxtIsFetchedOncePerHostPerDayAcrossSourcesAndTenants() {
        String first = pageUrl("/rules");
        String second = pageUrl("/rules");
        String third = pageUrl("/rules");
        fetcher.html(first, page("Applicants need 66 semester hours."));
        fetcher.html(second, page("Applicants need 72 semester hours."));
        fetcher.html(third, page("Applicants need 90 semester hours."));
        fetcher.robots(first, 200, "User-agent: *\nDisallow: /nothing/\n");
        UUID inThisTenant = register("CA", first);
        UUID alsoInThisTenant = register("TX", second);
        var other = tenants.create("other-tenant");
        UUID inAnotherTenant = TenantContext.runAs(other.tenantId(), () -> service.register(new CreateRuleSourceRequest("CA", LICENSE, "other", third, 24)));
        TenantContext.runAs(other.tenantId(), () -> jobs.enqueue(com.lexvay.job.JobType.CHECK_RULE_SOURCE, inAnotherTenant, Map.of("ruleSourceId", inAnotherTenant)));

        runner.runPending();

        assertThat(latestCheck(inThisTenant)).containsEntry("outcome", "CHANGED");
        assertThat(latestCheck(alsoInThisTenant)).containsEntry("outcome", "CHANGED");
        assertThat(robotsRequests()).as("one read of robots.txt served three checks of two tenants").isEqualTo(1);
        assertThat(robotsRow()).containsEntry("robots_status", 200);

        // Twenty-five hours later it is read again.
        transaction.executeWithoutResult(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            jdbc.sql("UPDATE source_host_fetch SET robots_fetched_at = now() - interval '25 hours' WHERE host = :host").param("host", HOST).update();
        });
        checkNow(inThisTenant);
        assertThat(robotsRequests()).isEqualTo(2);
        checkNow(inThisTenant);
        assertThat(robotsRequests()).as("and then trusted for another day").isEqualTo(2);
    }

    @Test
    void aCachedDisallowBlocksWithoutAskingTheHostAgain() {
        String url = pageUrl("/rules");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        fetcher.robots(url, 200, "User-agent: *\nDisallow: /\n");
        UUID id = register("CA", url);
        runner.runOne();
        long requestsAfterFirst = fetcher.requests().size();

        checkNow(id);

        assertThat(latestCheck(id)).containsEntry("outcome", "BLOCKED_BY_ROBOTS");
        assertThat(fetcher.requests()).as("neither robots.txt nor the page").hasSize((int) requestsAfterFirst);
    }

    @Test
    void aRobotsFileChangedToAllowLetsTheNextCheckThroughOnceTheCacheExpires() {
        String url = pageUrl("/rules");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        fetcher.robots(url, 200, "User-agent: *\nDisallow: /\n");
        UUID id = register("CA", url);
        runner.runOne();
        assertThat(latestCheck(id)).containsEntry("outcome", "BLOCKED_BY_ROBOTS");

        fetcher.robots(url, 200, "User-agent: *\nDisallow: /elsewhere/\n");
        transaction.executeWithoutResult(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            jdbc.sql("UPDATE source_host_fetch SET robots_fetched_at = now() - interval '25 hours' WHERE host = :host").param("host", HOST).update();
        });
        checkNow(id);

        assertThat(latestCheck(id)).containsEntry("outcome", "CHANGED");
    }

    @Test
    void aServerThatAsksUsToWaitHoldsEverySourceOnItsHost() {
        String busy = pageUrl("/rules");
        String other = pageUrl("/rules");
        fetcher.retryAfter(busy, 429, Duration.ofSeconds(120));
        fetcher.html(other, page("Applicants need 66 semester hours."));
        UUID busySource = register("CA", busy);
        UUID otherSource = register("TX", other);

        runner.runOne();
        assertThat(latestCheck(busySource)).containsEntry("outcome", "FETCH_FAILED").containsEntry("http_status", 429);
        boolean ranTheOther = runner.runOne();

        assertThat(ranTheOther).as("the second job was claimed and put back, not run").isTrue();
        assertThat(checkCount(otherSource)).isZero();
        assertThat(fetched(other)).isFalse();
        var job = inTenant(() -> jobs.latest(com.lexvay.job.JobType.CHECK_RULE_SOURCE, otherSource)).orElseThrow();
        assertThat(job.status()).isEqualTo(com.lexvay.job.JobStatus.QUEUED);
        assertThat(job.attempts()).as("waiting is not an attempt").isZero();
        var wait = secondsUntilRunnable(job.id());
        assertThat(wait).as("about the two minutes the server asked for").isBetween(100L, 121L);
        assertThat(runner.runOne()).as("nothing is claimable until then").isFalse();

        releaseHost(job.id());
        runner.runPending();
        assertThat(latestCheck(otherSource)).containsEntry("outcome", "CHANGED");
    }

    @Test
    void aWaitLongerThanAWeekIsHeldForAWeek() {
        String url = pageUrl("/rules");
        fetcher.retryAfter(url, 503, Duration.ofDays(30));
        UUID id = register("CA", url);

        runner.runOne();

        long seconds = transaction.execute(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            return jdbc.sql("SELECT extract(epoch FROM hold_until - now())::bigint FROM source_host_fetch WHERE host = :host")
                    .param("host", HOST).query(Long.class).single();
        });
        assertThat(latestCheck(id)).containsEntry("http_status", 503);
        assertThat(seconds).isBetween(Duration.ofDays(7).toSeconds() - 60, Duration.ofDays(7).toSeconds());
    }

    private long secondsUntilRunnable(UUID jobId) {
        return inTenant(() -> jdbc.sql("SELECT extract(epoch FROM run_after - now())::bigint FROM job WHERE id = :id").param("id", jobId)
                .query(Long.class).single());
    }

    /** Lets the waiting job run now: the host's hold is lifted and the job's time has come. */
    private void releaseHost(UUID jobId) {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("SELECT set_config('lexvay.worker', 'on', true)").query().singleRow();
            jdbc.sql("UPDATE source_host_fetch SET hold_until = NULL, last_fetch_at = NULL WHERE host = :host").param("host", HOST).update();
            jdbc.sql("UPDATE job SET run_after = now() WHERE id = :id").param("id", jobId).update();
        });
    }
}
