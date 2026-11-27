package com.lexvay.rule.source;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import com.lexvay.job.JobStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** The rule source endpoints: roles, address checks at registration, queued checks, and the audit row of each write. */
class RuleSourceApiTest extends AbstractRuleSourceTest {

    private MvcTestResult postAs(LexvayUser actor, String uri, Object body) {
        var request = mvc.post().uri(uri).with(user(actor)).with(csrf());
        return body == null ? request.exchange()
                : request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
    }

    private MvcTestResult get(LexvayUser actor, String uri) {
        return mvc.get().uri(uri).with(user(actor)).exchange();
    }

    @Test
    void createStoresTheSourceQueuesTheFirstCheckAndIsAudited() {
        String url = newUrl("pdf");

        var result = postAs(t.reviewer(), "/api/v1/rule-sources", sourceBody("CO", url));

        assertThat(result).hasStatus(201);
        UUID id = UUID.fromString(body(result).get("id").asString());
        UUID jobId = UUID.fromString(body(result).get("jobId").asString());
        assertThat(inTenant(() -> jobs.findById(jobId)).orElseThrow().status()).isEqualTo(JobStatus.QUEUED);
        assertThat(inTenant(() -> jobs.findById(jobId)).orElseThrow().payload()).contains(id.toString());

        JsonNode listed = body(get(t.contributor(), "/api/v1/rule-sources")).get(0);
        assertThat(listed.get("id").asString()).isEqualTo(id.toString());
        assertThat(listed.get("jurisdiction").asString()).isEqualTo("CO");
        assertThat(listed.get("licenseType").asString()).isEqualTo(LICENSE);
        assertThat(listed.get("url").asString()).isEqualTo(url);
        assertThat(listed.get("checkIntervalHours").asInt()).isEqualTo(24);
        assertThat(listed.get("active").asBoolean()).isTrue();
        assertThat(listed.get("lastCheckedAt").isNull()).isTrue();
        assertThat(listed.get("latestCheck").isNull()).isTrue();

        var entry = inTenant(() -> audit.forEntity(id)).get(0);
        assertThat(entry.action()).isEqualTo("RULE_SOURCE_CREATED");
        assertThat(entry.userId()).isEqualTo(t.reviewer().id());
        assertThat(entry.detail()).containsEntry("url", url).containsEntry("jurisdiction", "CO");
    }

    @Test
    void onlyAdminsAndReviewersRegisterOrRequestChecks() {
        UUID id = register("CA", newUrl("html"));

        for (LexvayUser denied : List.of(t.contributor(), t.signer())) {
            assertThat(postAs(denied, "/api/v1/rule-sources", sourceBody("CA", newUrl("html")))).hasStatus(403);
            assertThat(postAs(denied, "/api/v1/rule-sources/" + id + "/check", null)).hasStatus(403);
        }
        assertThat(postAs(t.admin(), "/api/v1/rule-sources", sourceBody("CA", newUrl("html")))).hasStatus(201);
        for (LexvayUser role : List.of(t.contributor(), t.reviewer(), t.signer(), t.admin())) {
            assertThat(get(role, "/api/v1/rule-sources")).hasStatus(200);
            assertThat(get(role, "/api/v1/rule-sources/" + id + "/checks")).hasStatus(200);
        }
    }

    @Test
    void anAddressInsideTheNetworkIsRefusedAtRegistrationAndNothingIsStored() {
        long sources = count("rule_source");
        long queued = inTenant(() -> jdbc.sql("SELECT count(*) FROM job WHERE type = 'CHECK_RULE_SOURCE'").query(Long.class).single());
        List<String> refused = List.of("http://127.0.0.1/rules", "http://10.0.0.1/rules", "http://169.254.1.1/latest", "http://[::1]/rules",
                "ftp://" + FakeHostResolver.PUBLIC_HOST + "/rules.pdf", "https://" + FakeHostResolver.PRIVATE_HOST + "/rules",
                "https://" + FakeHostResolver.METADATA_HOST + "/rules", "https://nowhere.example.test/rules");

        for (String url : refused) {
            var result = postAs(t.admin(), "/api/v1/rule-sources", sourceBody("CA", url));
            assertThat(result).as(url).hasStatus(400);
            assertThat(result.getResponse().getContentType()).as(url).contains("application/problem+json");
        }

        assertThat(count("rule_source")).isEqualTo(sources);
        assertThat(inTenant(() -> jdbc.sql("SELECT count(*) FROM job WHERE type = 'CHECK_RULE_SOURCE'").query(Long.class).single()))
                .isEqualTo(queued);
    }

    @Test
    void invalidRequestsAreRefused() {
        Map<String, Object> valid = sourceBody("CA", newUrl("html"));
        for (Map.Entry<String, Object> change : Map.<String, Object>of("jurisdictionCode", "ZZ", "checkIntervalHours", 0, "label", " ").entrySet()) {
            Map<String, Object> body = new LinkedHashMap<>(valid);
            body.put(change.getKey(), change.getValue());
            assertThat(postAs(t.admin(), "/api/v1/rule-sources", body)).as(change.getKey()).hasStatus(400);
        }
        assertThat(postAs(t.admin(), "/api/v1/rule-sources", withInterval(valid, 721))).hasStatus(400);
        assertThat(postAs(t.admin(), "/api/v1/rule-sources", withInterval(valid, 720))).hasStatus(201);
        assertThat(postAs(t.admin(), "/api/v1/rule-sources", withInterval(sourceBody("CA", newUrl("html")), 1))).hasStatus(201);
    }

    private static Map<String, Object> withInterval(Map<String, Object> body, int hours) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.put("checkIntervalHours", hours);
        return copy;
    }

    @Test
    void theListShowsTheLatestCheckInOneRowPerSource() {
        approvedRules();
        String changed = newUrl("html");
        fetcher.html(changed, page("Applicants need 66 semester hours."));
        UUID withCheck = register("CA", changed);
        runner.runOne();
        UUID withoutCheck = register("TX", newUrl("html"));

        JsonNode list = body(get(t.contributor(), "/api/v1/rule-sources"));

        assertThat(list).hasSize(2);
        JsonNode checked = find(list, withCheck);
        assertThat(checked.get("latestCheck").get("outcome").asString()).isEqualTo("CHANGED");
        assertThat(checked.get("latestCheck").get("ruleVersionId").isNull()).isFalse();
        assertThat(checked.get("lastCheckedAt").isNull()).isFalse();
        assertThat(find(list, withoutCheck).get("latestCheck").isNull()).isTrue();

        scriptExtraction(fetchedLabel("CA"));
        fetcher.respond(changed, 500, "text/plain", new byte[0]);
        checkNow(withCheck);
        JsonNode again = find(body(get(t.contributor(), "/api/v1/rule-sources")), withCheck);
        assertThat(again.get("latestCheck").get("outcome").asString()).isEqualTo("FETCH_FAILED");
        assertThat(again.get("latestCheck").get("httpStatus").asInt()).isEqualTo(500);
    }

    private void approvedRules() {
        fx.approvedRuleVersion(t, "CA", "Demo rule set California 2026", "Fictional rule text for California.",
                fx.requirements("CA", List.of(com.lexvay.assessment.AssessmentFixtures.subject("Ethics", "coursework in professional ethics"))));
    }

    private static JsonNode find(JsonNode list, UUID id) {
        for (JsonNode node : list) {
            if (node.get("id").asString().equals(id.toString())) {
                return node;
            }
        }
        throw new AssertionError("Source " + id + " is not listed");
    }

    @Test
    void checksAreListedNewestFirstAndLimited() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        assertThat(body(get(t.reviewer(), "/api/v1/rule-sources/" + id + "/checks"))).as("none yet").isEmpty();
        scriptExtraction(fetchedLabel("CA"));
        runner.runOne();
        checkNow(id);
        fetcher.respond(url, 502, "text/plain", new byte[0]);
        checkNow(id);

        JsonNode all = body(get(t.reviewer(), "/api/v1/rule-sources/" + id + "/checks"));
        assertThat(all).extracting(n -> n.get("outcome").asString()).containsExactly("FETCH_FAILED", "UNCHANGED", "CHANGED");
        assertThat(all.get(2).get("contentHash").asString()).matches("[0-9a-f]{64}");
        assertThat(all.get(2).get("documentId").isNull()).isFalse();

        JsonNode newest = body(get(t.reviewer(), "/api/v1/rule-sources/" + id + "/checks?limit=1"));
        assertThat(newest).hasSize(1);
        assertThat(newest.get(0).get("outcome").asString()).isEqualTo("FETCH_FAILED");
        assertThat(get(t.reviewer(), "/api/v1/rule-sources/" + UUID.randomUUID() + "/checks")).hasStatus(404);
    }

    @Test
    void aCheckCanBeRequestedOnceAtATimeAndIsAudited() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);

        var conflict = postAs(t.reviewer(), "/api/v1/rule-sources/" + id + "/check", null);
        assertThat(conflict).as("the first check is still queued").hasStatus(409);
        assertThat(body(conflict).get("detail").asString()).contains("already queued or running");

        scriptExtraction(fetchedLabel("CA"));
        runner.runOne();
        var accepted = postAs(t.reviewer(), "/api/v1/rule-sources/" + id + "/check", null);
        assertThat(accepted).hasStatus(202);
        UUID jobId = UUID.fromString(body(accepted).get("jobId").asString());
        assertThat(inTenant(() -> jobs.findById(jobId)).orElseThrow().status()).isEqualTo(JobStatus.QUEUED);
        assertThat(postAs(t.admin(), "/api/v1/rule-sources/" + id + "/check", null)).hasStatus(409);

        run(jobId);
        assertThat(postAs(t.admin(), "/api/v1/rule-sources/" + id + "/check", null)).as("free again once it ran").hasStatus(202);
        assertThat(postAs(t.admin(), "/api/v1/rule-sources/" + UUID.randomUUID() + "/check", null)).hasStatus(404);

        var requested = inTenant(() -> audit.forEntity(id)).stream().filter(e -> e.action().equals("RULE_SOURCE_CHECK_REQUESTED")).toList();
        assertThat(requested).as("the refused requests are not audited").hasSize(2);
        assertThat(requested.get(1).userId()).isEqualTo(t.reviewer().id());
        assertThat(requested.get(1).detail()).containsEntry("jobId", jobId.toString());
        assertThat(requested.get(0).userId()).isEqualTo(t.admin().id());
    }

    @Test
    void onlyAnAdminTurnsMonitoringOffAndOnAndItIsAudited() {
        UUID id = register("CA", newUrl("html"));

        assertThat(putActive(t.reviewer(), id, false)).hasStatus(403);
        var off = putActive(t.admin(), id, false);
        assertThat(off).hasStatus(200);
        assertThat(body(off).get("active").asBoolean()).isFalse();
        assertThat(find(body(get(t.contributor(), "/api/v1/rule-sources")), id).get("active").asBoolean()).isFalse();
        assertThat(putActive(t.admin(), id, true)).hasStatus(200);
        assertThat(source(id).get("active")).isEqualTo(true);
        assertThat(putActive(t.admin(), UUID.randomUUID(), true)).hasStatus(404);

        var changes = inTenant(() -> audit.forEntity(id)).stream().filter(e -> e.action().equals("RULE_SOURCE_ACTIVE_CHANGED")).toList();
        assertThat(changes).extracting(e -> e.detail().get("active")).containsExactly(true, false);
        assertThat(changes).allSatisfy(e -> assertThat(e.userId()).isEqualTo(t.admin().id()));
    }

    private MvcTestResult putActive(LexvayUser actor, UUID id, boolean active) {
        return mvc.put().uri("/api/v1/rule-sources/" + id + "/active").with(user(actor)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("active", active))).exchange();
    }

    @Test
    void aDeactivatedSourceCanStillBeCheckedByHand() {
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        runner.runOne();
        assertThat(putActive(t.admin(), id, false)).hasStatus(200);

        checkNow(id);

        assertThat(latestCheck(id)).containsEntry("outcome", "UNCHANGED");
    }
}
