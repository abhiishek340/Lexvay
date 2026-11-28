package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Conditional GET (RG-2, D-567) and the access status a check is made under (D-568). */
class ConditionalCheckTest extends AbstractRuleSourceTest {

    private static final String ETAG = "\"v1\"";
    private static final String MODIFIED = "Tue, 01 Dec 2026 10:00:00 GMT";

    private Map<String, Object> validators(UUID sourceId) {
        return inTenant(() -> jdbc.sql("SELECT last_etag, last_modified FROM rule_source WHERE id = :id").param("id", sourceId)
                .query().singleRow());
    }

    @Test
    void theFirstCheckIsUnconditionalAndKeepsTheServersValidators() {
        String url = newUrl("html");
        fetcher.conditionalHtml(url, ETAG, MODIFIED, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);

        runner.runOne();

        assertThat(latestCheck(id)).containsEntry("outcome", "CHANGED");
        assertThat(fetcher.requests()).filteredOn(request -> request.url().toString().equals(url))
                .extracting(HttpFetcher.FetchRequest::validators).containsExactly(Validators.NONE);
        assertThat(validators(id)).containsEntry("last_etag", ETAG).containsEntry("last_modified", MODIFIED);
    }

    @Test
    void aNotModifiedAnswerIsASuccessThatStoresNothingAndMovesNothing() {
        String url = newUrl("html");
        fetcher.conditionalHtml(url, ETAG, MODIFIED, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        runner.runOne();
        String baseline = (String) source(id).get("last_content_hash");
        long documents = count("document");
        long versions = count("rule_version");
        long checks = checkCount(id);
        failOnceAndRecover(url, id);

        checkNow(id);

        assertThat(fetcher.requests()).filteredOn(request -> request.url().toString().equals(url)).last()
                .extracting(HttpFetcher.FetchRequest::validators).isEqualTo(new Validators(ETAG, MODIFIED));
        var check = latestCheck(id);
        assertThat(check).containsEntry("outcome", "NOT_MODIFIED").containsEntry("http_status", 304);
        assertThat(check.get("content_hash")).isNull();
        assertThat(check.get("document_id")).isNull();
        assertThat(check.get("rule_version_id")).isNull();
        assertThat(count("document")).isEqualTo(documents);
        assertThat(count("rule_version")).isEqualTo(versions);
        assertThat(checkCount(id)).isEqualTo(checks + 3);
        assertThat(source(id)).containsEntry("last_content_hash", baseline);
        assertThat(validators(id)).containsEntry("last_etag", ETAG);
        var schedule = inTenant(() -> jdbc.sql("SELECT consecutive_failures, next_check_at > now() + interval '23 hours' AS waits_the_interval "
                + "FROM rule_source WHERE id = :id").param("id", id).query().singleRow());
        assertThat(schedule).as("a success, so the failures are forgotten and the full interval is waited")
                .containsEntry("consecutive_failures", 0).containsEntry("waits_the_interval", true);
        var entry = inTenant(() -> audit.forEntity(id)).stream().filter(e -> e.action().equals("RULE_SOURCE_CHECKED"))
                .filter(e -> "NOT_MODIFIED".equals(e.detail().get("outcome"))).findFirst().orElseThrow();
        assertThat(entry.detail()).containsEntry("outcome", "NOT_MODIFIED").containsEntry("httpStatus", 304);
    }

    /** Fails the source once and lets it recover, so that the NOT_MODIFIED that follows has failures to forget. */
    private void failOnceAndRecover(String url, UUID id) {
        fetcher.reject(url, "The board's server is down");
        checkNow(id);
        assertThat(latestCheck(id)).containsEntry("outcome", "FETCH_FAILED");
        assertThat(validators(id)).as("a failure keeps the validators of the last good fetch").containsEntry("last_etag", ETAG);
        fetcher.conditionalHtml(url, ETAG, MODIFIED, page("Applicants need 66 semester hours."));
        checkNow(id);
        assertThat(latestCheck(id)).containsEntry("outcome", "NOT_MODIFIED");
    }

    @Test
    void aChangedPageAnswersTheConditionalRequestWith200AndIsDrafted() {
        String url = newUrl("html");
        fetcher.conditionalHtml(url, ETAG, MODIFIED, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        runner.runOne();
        extractAndApprove((UUID) latestCheck(id).get("rule_version_id"), fetchedLabel("CA"), java.time.LocalDate.of(2026, 1, 1));

        fetcher.conditionalHtml(url, "\"v2\"", MODIFIED, page("Applicants need 72 semester hours."));
        checkNow(id);

        assertThat(latestCheck(id)).containsEntry("outcome", "CHANGED").containsEntry("http_status", 200);
        assertThat(validators(id)).as("the new text is the baseline, so its validators are").containsEntry("last_etag", "\"v2\"");
    }

    @Test
    void aChangeKeptBehindAWaitingDraftDoesNotAdvanceTheValidators() {
        String url = newUrl("html");
        fetcher.conditionalHtml(url, ETAG, MODIFIED, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        runner.runOne();

        fetcher.conditionalHtml(url, "\"v2\"", MODIFIED, page("Applicants need 72 semester hours."));
        checkNow(id);

        assertThat(latestCheck(id)).containsEntry("outcome", "CHANGED_QUEUED");
        assertThat(validators(id)).as("else the next check would get 304 and the text behind the draft would never be drafted")
                .containsEntry("last_etag", ETAG);
    }

    @Test
    void aServerWithoutValidatorsIsAlwaysAskedWithoutConditions() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        runner.runOne();

        checkNow(id);

        assertThat(latestCheck(id)).containsEntry("outcome", "UNCHANGED");
        assertThat(fetcher.requests()).filteredOn(request -> request.url().toString().equals(url))
                .extracting(HttpFetcher.FetchRequest::validators).containsExactly(Validators.NONE, Validators.NONE);
    }

    @Test
    void aNotModifiedAnswerToARequestWithoutAnyBaselineIsAFailure() {
        String url = newUrl("html");
        fetcher.respond(url, 304, "text/html", new byte[0]);
        UUID id = register("CA", url);

        runner.runOne();

        assertThat(latestCheck(id)).containsEntry("outcome", "FETCH_FAILED").containsEntry("http_status", 304);
    }

    @Test
    void everyRequestIsScopedToTheSourcesHostsAndPaths() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        govern(id, body -> {
            body.put("allowedHosts", List.of(FakeHostResolver.PUBLIC_HOST, "www.example.test"));
            body.put("allowedPathPrefixes", List.of(java.net.URI.create(url).getPath().substring(0, 5)));
        });

        runner.runOne();

        var request = fetcher.requests().stream().filter(r -> r.url().toString().equals(url)).findFirst().orElseThrow();
        assertThat(request.scope().allowedHosts()).containsExactly(FakeHostResolver.PUBLIC_HOST, "www.example.test");
        assertThat(request.scope().allowedPathPrefixes()).hasSize(1);
        assertThat(fetcher.requests().stream().filter(r -> r.url().getPath().equals("/robots.txt")).findFirst().orElseThrow().scope())
                .as("robots.txt is asked of this host only").isEqualTo(FetchScope.hostOnly(FakeHostResolver.PUBLIC_HOST));
    }

    @Test
    void aFetchRefusedForBeingOutOfScopeIsAFailureNamingTheReason() {
        String url = newUrl("html");
        fetcher.reject(url, "The path /private/x is outside the allowed paths of this source");
        UUID id = register("CA", url);

        runner.runOne();

        var check = latestCheck(id);
        assertThat(check).containsEntry("outcome", "FETCH_FAILED");
        assertThat((String) check.get("detail")).contains("outside the allowed paths");
        assertThat(source(id)).containsEntry("last_content_hash", null);
    }

    // --- access status ---

    @Test
    void aSourceNobodyReviewedCanBeCheckedByHandAndTheCheckSaysItWasUnreviewed() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        runner.runOne();

        assertThat(inTenant(() -> jdbc.sql("SELECT access_status FROM rule_source_check WHERE rule_source_id = :id").param("id", id)
                .query(String.class).single())).isEqualTo("UNREVIEWED");
        var checked = inTenant(() -> audit.forEntity(id)).stream().filter(e -> e.action().equals("RULE_SOURCE_CHECKED")).findFirst().orElseThrow();
        assertThat(checked.detail()).containsEntry("accessStatus", "UNREVIEWED");

        permit(id);
        checkNow(id);
        assertThat(inTenant(() -> jdbc.sql("SELECT access_status FROM rule_source_check WHERE rule_source_id = :id ORDER BY checked_at DESC LIMIT 1")
                .param("id", id).query(String.class).single())).as("the next check records the status it was made under").isEqualTo("PERMITTED");
    }

    @Test
    void aRestrictedOrBlockedSourceCannotBeCheckedByHandAndNothingIsQueuedOrFetched() {
        String url = newUrl("html");
        UUID id = register("CA", url);
        runner.runPending();
        for (String status : List.of("RESTRICTED", "BLOCKED")) {
            setAccess(id, status);
            long queued = inTenant(() -> jdbc.sql("SELECT count(*) FROM job WHERE type = 'CHECK_RULE_SOURCE' AND entity_id = :id")
                    .param("id", id).query(Long.class).single());

            var refused = mvc.post().uri("/api/v1/rule-sources/" + id + "/check").with(user(t.reviewer()))
                    .with(com.lexvay.common.TestCsrf.csrf()).exchange();

            assertThat(refused).as(status).hasStatus(409);
            assertThat(body(refused).get("detail").asString()).contains(status).contains("never fetched");
            assertThat(inTenant(() -> jdbc.sql("SELECT count(*) FROM job WHERE type = 'CHECK_RULE_SOURCE' AND entity_id = :id")
                    .param("id", id).query(Long.class).single())).isEqualTo(queued);
        }
    }

    @Test
    void aCheckQueuedBeforeAccessWasWithdrawnFailsWithoutAnyRequest() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        setAccess(id, "BLOCKED");

        runner.runOne();

        assertThat(fetcher.requested()).as("not even robots.txt was asked for").isEmpty();
        assertThat(checkCount(id)).isZero();
        var job = inTenant(() -> jobs.latest(com.lexvay.job.JobType.CHECK_RULE_SOURCE, id)).orElseThrow();
        assertThat(job.status()).isEqualTo(com.lexvay.job.JobStatus.FAILED);
        assertThat(job.error()).contains("BLOCKED").contains("never fetched");
    }
}
