package com.lexvay.rule.source;

import static com.lexvay.common.TestCsrf.csrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.common.TenantContext;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * Health as the list and the program overview show it (RG-2, D-573): computed from the checks at the time of the
 * request, never stored, and kept apart from the legal status of any rule.
 */
class SourceHealthApiTest extends AbstractRuleSourceTest {

    @Autowired RuleSourceRepository repository;
    @Autowired RuleSourceService service;

    private JsonNode listed(UUID id) {
        for (JsonNode node : body(mvc.get().uri("/api/v1/rule-sources").with(user(t.reviewer())).exchange())) {
            if (node.get("id").asString().equals(id.toString())) {
                return node;
            }
        }
        throw new AssertionError("Source " + id + " is not listed");
    }

    private String healthOf(UUID id) {
        return listed(id).get("health").asString();
    }

    /** A source whose access was permitted, on whatever host its address names. */
    private UUID permitted(String jurisdiction, String url) {
        UUID id = register(jurisdiction, url);
        govern(id, body -> body.put("allowedHosts", List.of(URI.create(url).getHost())));
        return id;
    }

    /** Records a check as it would have been made this many hours ago. */
    private void checkMade(UUID id, int hoursAgo, CheckOutcome outcome) {
        inTenant(() -> repository.insertCheck(t.tenantId(), id, OffsetDateTime.now().minusHours(hoursAgo), AccessStatus.PERMITTED,
                new CheckFinding(outcome, outcome == CheckOutcome.FETCH_FAILED ? 500 : 200, null, null, null, null, null)));
    }

    private Map<String, JsonNode> overviewRules(UUID programId) {
        var overview = body(mvc.get().uri("/api/v1/programs/" + programId + "/overview").with(user(t.reviewer())).exchange());
        Map<String, JsonNode> rules = new HashMap<>();
        overview.get("rules").forEach(rule -> rules.put(rule.get("jurisdiction").asString(), rule));
        return rules;
    }

    @Test
    void theListShowsEachHealthTheChecksImply() {
        String ok = newUrl("html");
        fetcher.html(ok, page("Applicants need 66 semester hours."));
        UUID healthy = permitted("CA", ok);

        UUID unreviewed = register("TX", newUrl("html"));

        UUID blocked = permitted("UT", newUrl("html"));
        setAccess(blocked, "BLOCKED");

        String down = newUrl("html");
        fetcher.reject(down, "The board's server is down");
        UUID failing = permitted("CO", down);

        String challenge = newUrl("html");
        fetcher.html(challenge, page("Access denied."));
        UUID suspect = permitted("FL", challenge);

        UUID stale = permitted("NM", newUrl("html"));
        checkMade(stale, 60, CheckOutcome.UNCHANGED);

        String disallowed = "https://" + FakeHostResolver.OTHER_PUBLIC_HOST + "/" + UUID.randomUUID() + ".html";
        fetcher.robots(disallowed, 200, "User-agent: *\nDisallow: /\n");
        UUID robots = permitted("OR", disallowed);

        runner.runPending();
        checkNow(failing);
        checkNow(failing);

        assertThat(healthOf(healthy)).isEqualTo("HEALTHY");
        assertThat(healthOf(unreviewed)).isEqualTo("UNREVIEWED");
        assertThat(healthOf(blocked)).isEqualTo("BLOCKED");
        assertThat(healthOf(failing)).as("three fetches in a row failed").isEqualTo("FAILING");
        assertThat(healthOf(suspect)).isEqualTo("SUSPECT");
        assertThat(healthOf(stale)).as("its last good reading is 60 hours old and it is checked every 24").isEqualTo("STALE");
        assertThat(healthOf(robots)).isEqualTo("BLOCKED");
    }

    @Test
    void healthIsNeverStoredAnywhere() {
        var columns = jdbc.sql("SELECT column_name FROM information_schema.columns WHERE table_name IN ('rule_source', 'rule_source_check')")
                .query(String.class).list();

        assertThat(columns).noneMatch(column -> column.contains("health") || column.contains("stale"));
    }

    @Test
    void theListCarriesTheLastSuccessSoAPersonCanSeeHowOldTheReadingIs() {
        UUID id = permitted("CA", newUrl("html"));
        checkMade(id, 5, CheckOutcome.UNCHANGED);
        checkMade(id, 1, CheckOutcome.FETCH_FAILED);

        JsonNode node = listed(id);

        assertThat(OffsetDateTime.parse(node.get("lastSuccessAt").asString())).isBefore(OffsetDateTime.now().minusHours(4));
        assertThat(node.get("latestCheck").get("outcome").asString()).isEqualTo("FETCH_FAILED");
    }

    @Test
    void theOverviewShowsSourceStaleOnlyForStatesWhoseSourceIsNotHealthy() {
        UUID program = fx.program(t, List.of("CA", "UT", "TX", "CO"));
        permitted("CA", newUrl("html"));
        UUID aged = permitted("UT", newUrl("html"));
        checkMade(aged, 60, CheckOutcome.UNCHANGED);
        UUID oldAndPaused = permitted("CO", newUrl("html"));
        checkMade(oldAndPaused, 60, CheckOutcome.UNCHANGED);
        assertThat(mvc.put().uri("/api/v1/rule-sources/" + oldAndPaused + "/active").with(user(t.admin())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}").exchange()).hasStatus(200);

        var rules = overviewRules(program);

        assertThat(rules).containsOnlyKeys("CA", "UT", "TX", "CO");
        assertThat(rules.get("CA").get("sourceNotice").isNull()).as("read recently").isTrue();
        assertThat(rules.get("UT").get("sourceNotice").asString()).isEqualTo("Source stale");
        assertThat(rules.get("TX").get("sourceNotice").isNull()).as("no source, nothing to be stale").isTrue();
        assertThat(rules.get("CO").get("sourceNotice").isNull()).as("a paused source is not claiming coverage").isTrue();
    }

    @Test
    void anyUnhealthySourceOfTheStateIsEnough() {
        UUID program = fx.program(t, List.of("CA"));
        permitted("CA", newUrl("html"));
        assertThat(overviewRules(program).get("CA").get("sourceNotice").isNull()).isTrue();

        register("CA", newUrl("html"));

        assertThat(overviewRules(program).get("CA").get("sourceNotice").asString()).as("a second source nobody reviewed").isEqualTo("Source stale");
    }

    @Test
    void aSourceOutageShowsStaleCoverageNotNoChange() {
        UUID program = fx.program(t, List.of("CA"));
        String url = newUrl("html");
        fetcher.html(url, page("Applicants need 66 semester hours."));
        UUID id = permitted("CA", url);
        runner.runOne();
        assertThat(overviewRules(program).get("CA").get("sourceNotice").isNull()).isTrue();

        fetcher.reject(url, "The board's server is down");
        for (int i = 0; i < 3; i++) {
            checkNow(id);
        }

        assertThat(healthOf(id)).isEqualTo("FAILING");
        assertThat(overviewRules(program).get("CA").get("sourceNotice").asString()).isEqualTo("Source stale");
    }

    @Test
    void anotherTenantsSourcesDoNotMakeThisTenantsStatesStale() {
        UUID program = fx.program(t, List.of("CA"));
        var other = tenants.create("health-other");
        TenantContext.runAs(other.tenantId(), () -> service.register(new CreateRuleSourceRequest("CA", LICENSE, "other tenant", newUrl("html"), 24)));

        assertThat(overviewRules(program).get("CA").get("sourceNotice").isNull()).isTrue();
    }
}
