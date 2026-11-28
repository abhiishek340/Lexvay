package com.lexvay.rule.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import com.lexvay.common.TestCsrf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

/** PUT /rule-sources/{id}/governance (RG-2, D-566, D-568): who may, what is checked, and what is audited. */
class SourceGovernanceApiTest extends AbstractRuleSourceTest {

    private MvcTestResult putAs(LexvayUser actor, UUID id, Object body) {
        return mvc.put().uri("/api/v1/rule-sources/" + id + "/governance").with(user(actor)).with(TestCsrf.csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)).exchange();
    }

    private JsonNode listed(UUID id) {
        for (JsonNode node : body(mvc.get().uri("/api/v1/rule-sources").with(user(t.contributor())).exchange())) {
            if (node.get("id").asString().equals(id.toString())) {
                return node;
            }
        }
        throw new AssertionError("Source " + id + " is not listed");
    }

    @Test
    void aNewSourceIsGovernedByItsOwnHostAsOtherAndUnreviewed() {
        UUID id = register("CA", newUrl("html"));

        JsonNode governance = listed(id).get("governance");

        assertThat(governance.get("authorityClass").asString()).isEqualTo("OTHER");
        assertThat(governance.get("allowedHosts")).extracting(JsonNode::asString).containsExactly(FakeHostResolver.PUBLIC_HOST);
        assertThat(governance.get("allowedPathPrefixes")).isEmpty();
        assertThat(governance.get("expectedIdentifiers")).isEmpty();
        assertThat(governance.get("accessStatus").asString()).isEqualTo("UNREVIEWED");
        assertThat(governance.get("accessNote").isNull()).isTrue();
        assertThat(governance.get("ownerUserId").isNull()).isTrue();
        assertThat(listed(id).get("health").asString()).isEqualTo("UNREVIEWED");
    }

    @Test
    void anAdministratorReplacesTheGovernanceAndTheAuditNamesBeforeAndAfter() {
        String url = newUrl("html");
        UUID id = register("CA", url);
        String path = java.net.URI.create(url).getPath();
        Map<String, Object> body = governanceBody();
        body.put("authorityClass", "ADMIN_RULE");
        body.put("allowedHosts", List.of(FakeHostResolver.PUBLIC_HOST, "WWW.Example.Test", "www.example.test"));
        body.put("allowedPathPrefixes", List.of(path.substring(0, 6)));
        body.put("expectedIdentifiers", List.of("R156-60e", " Section 12 "));
        body.put("accessStatus", "PERMITTED");
        body.put("accessNote", "  Terms read 2026-11-28 by the compliance lead; robots.txt allows /rules/.  ");
        body.put("ownerUserId", t.reviewer().id().toString());

        var result = putAs(t.admin(), id, body);

        assertThat(result).hasStatus(200);
        JsonNode governance = listed(id).get("governance");
        assertThat(governance.get("authorityClass").asString()).isEqualTo("ADMIN_RULE");
        assertThat(governance.get("allowedHosts")).as("lower case and without repeats").extracting(JsonNode::asString)
                .containsExactly(FakeHostResolver.PUBLIC_HOST, "www.example.test");
        assertThat(governance.get("allowedPathPrefixes")).extracting(JsonNode::asString).containsExactly(path.substring(0, 6));
        assertThat(governance.get("expectedIdentifiers")).extracting(JsonNode::asString).containsExactly("R156-60e", "Section 12");
        assertThat(governance.get("accessStatus").asString()).isEqualTo("PERMITTED");
        assertThat(governance.get("accessNote").asString()).startsWith("Terms read").endsWith("/rules/.");
        assertThat(governance.get("ownerUserId").asString()).isEqualTo(t.reviewer().id().toString());
        assertThat(listed(id).get("health").asString()).isEqualTo("HEALTHY");

        var entry = inTenant(() -> audit.forEntity(id)).stream().filter(e -> e.action().equals("RULE_SOURCE_GOVERNANCE_CHANGED")).findFirst().orElseThrow();
        assertThat(entry.userId()).isEqualTo(t.admin().id());
        @SuppressWarnings("unchecked") Map<String, Object> before = (Map<String, Object>) entry.detail().get("before");
        @SuppressWarnings("unchecked") Map<String, Object> after = (Map<String, Object>) entry.detail().get("after");
        assertThat(before).containsEntry("authorityClass", "OTHER").containsEntry("accessStatus", "UNREVIEWED")
                .containsEntry("allowedHosts", List.of(FakeHostResolver.PUBLIC_HOST)).containsEntry("ownerUserId", null);
        assertThat(after).containsEntry("authorityClass", "ADMIN_RULE").containsEntry("accessStatus", "PERMITTED")
                .containsEntry("allowedHosts", List.of(FakeHostResolver.PUBLIC_HOST, "www.example.test"))
                .containsEntry("expectedIdentifiers", List.of("R156-60e", "Section 12"))
                .containsEntry("ownerUserId", t.reviewer().id().toString());
        assertThat((String) after.get("accessNote")).startsWith("Terms read");
    }

    @Test
    void everyChangeIsAuditedEvenWhenNothingDiffers() {
        UUID id = register("CA", newUrl("html"));
        govern(id, body -> { });
        govern(id, body -> { });

        assertThat(auditActions(id).stream().filter("RULE_SOURCE_GOVERNANCE_CHANGED"::equals)).hasSize(2);
    }

    @Test
    void onlyAnAdministratorMayChangeGovernance() {
        UUID id = register("CA", newUrl("html"));

        for (LexvayUser denied : List.of(t.reviewer(), t.contributor(), t.signer())) {
            assertThat(putAs(denied, id, governanceBody())).as(denied.role().name()).hasStatus(403);
        }
        assertThat(putAs(t.admin(), id, governanceBody())).hasStatus(200);
        assertThat(mvc.put().uri("/api/v1/rule-sources/" + id + "/governance").with(TestCsrf.csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}").exchange()).as("not signed in").hasStatus(401);
        assertThat(auditActions(id).stream().filter("RULE_SOURCE_GOVERNANCE_CHANGED"::equals)).as("refusals are not audited").hasSize(1);
    }

    @Test
    void aSourceOfAnotherTenantIsNotFound() {
        UUID id = register("CA", newUrl("html"));
        var other = tenants.create("governance-other");

        assertThat(putAs(other.admin(), id, governanceBody())).hasStatus(404);
        assertThat(putAs(t.admin(), UUID.randomUUID(), governanceBody())).hasStatus(404);
    }

    @Test
    void theRequestMustBeCompleteAndWellFormed() {
        UUID id = register("CA", newUrl("html"));
        List<Map<String, Object>> refused = new ArrayList<>();
        refused.add(with("authorityClass", "NEWSPAPER"));
        refused.add(with("authorityClass", null));
        refused.add(with("accessStatus", "MAYBE"));
        refused.add(with("accessStatus", null));
        refused.add(with("allowedHosts", List.of()));
        refused.add(with("allowedHosts", null));
        refused.add(with("allowedHosts", List.of("bad host")));
        refused.add(with("allowedHosts", List.of("https://rules.example.test")));
        refused.add(with("allowedHosts", List.of("rules.example.test:8443")));
        refused.add(with("allowedHosts", List.of(FakeHostResolver.PUBLIC_HOST, "")));
        refused.add(with("allowedHosts", java.util.stream.IntStream.range(0, 21).mapToObj(i -> "h" + i + ".example.test").toList()));
        refused.add(with("allowedPathPrefixes", null));
        refused.add(with("allowedPathPrefixes", List.of("rules/")));
        refused.add(with("allowedPathPrefixes", List.of("/has space")));
        refused.add(with("expectedIdentifiers", null));
        refused.add(with("expectedIdentifiers", List.of("   ")));
        refused.add(with("expectedIdentifiers", List.of("x".repeat(201))));
        refused.add(with("accessNote", "n".repeat(2001)));
        refused.add(with("ownerUserId", "not-a-uuid"));

        for (Map<String, Object> body : refused) {
            var result = putAs(t.admin(), id, body);
            assertThat(result).as(body.toString()).hasStatus(400);
            assertThat(result.getResponse().getContentType()).contains("application/problem+json");
        }
        assertThat(auditActions(id)).doesNotContain("RULE_SOURCE_GOVERNANCE_CHANGED");
    }

    private static Map<String, Object> with(String key, Object value) {
        Map<String, Object> body = governanceBody();
        body.put(key, value);
        return body;
    }

    @Test
    void theSourcesOwnAddressMustStayAllowed() {
        String url = newUrl("html");
        UUID id = register("CA", url);

        var wrongHost = putAs(t.admin(), id, with("allowedHosts", List.of("www.example.test")));
        assertThat(wrongHost).hasStatus(400);
        assertThat(body(wrongHost).get("detail").asString()).contains(FakeHostResolver.PUBLIC_HOST);

        var wrongPath = putAs(t.admin(), id, with("allowedPathPrefixes", List.of("/other/")));
        assertThat(wrongPath).hasStatus(400);
        assertThat(body(wrongPath).get("detail").asString()).contains("allowed paths");

        assertThat(putAs(t.admin(), id, with("allowedPathPrefixes", List.of("/other/", "/")))).as("any one prefix will do").hasStatus(200);
    }

    @Test
    void theOwnerMustBeAnActiveUserOfThisTenant() {
        UUID id = register("CA", newUrl("html"));
        var other = tenants.create("governance-owner");

        assertThat(putAs(t.admin(), id, with("ownerUserId", other.admin().id().toString()))).as("a user of another tenant").hasStatus(400);
        assertThat(putAs(t.admin(), id, with("ownerUserId", UUID.randomUUID().toString()))).as("nobody").hasStatus(400);
        assertThat(putAs(t.admin(), id, with("ownerUserId", t.signer().id().toString()))).hasStatus(200);
        assertThat(putAs(t.admin(), id, with("ownerUserId", null))).as("an owner can be removed").hasStatus(200);
        assertThat(listed(id).get("governance").get("ownerUserId").isNull()).isTrue();
    }

    @Test
    void changingGovernanceMakesTheNextCheckUnconditional() {
        String url = newUrl("html");
        fetcher.conditionalHtml(url, "\"v1\"", "Tue, 01 Dec 2026 10:00:00 GMT", page("Applicants need 66 semester hours."));
        UUID id = register("CA", url);
        runner.runOne();
        assertThat(inTenant(() -> jdbc.sql("SELECT last_etag FROM rule_source WHERE id = :id").param("id", id).query(String.class).single()))
                .isEqualTo("\"v1\"");

        govern(id, body -> body.put("expectedIdentifiers", List.of("semester hours")));

        assertThat(inTenant(() -> jdbc.sql("SELECT last_etag, last_modified FROM rule_source WHERE id = :id").param("id", id).query().singleRow()))
                .containsEntry("last_etag", null).containsEntry("last_modified", null);
        checkNow(id);
        assertThat(latestCheck(id)).as("a 304 would have skipped the new identifier check").containsEntry("outcome", "UNCHANGED");
    }

    @Test
    void theGovernanceOfASourceIsPartOfTheListForEveryRole() {
        UUID id = register("CA", newUrl("html"));

        for (LexvayUser role : List.of(t.contributor(), t.reviewer(), t.signer(), t.admin())) {
            JsonNode node = null;
            for (JsonNode candidate : body(mvc.get().uri("/api/v1/rule-sources").with(user(role)).exchange())) {
                node = candidate.get("id").asString().equals(id.toString()) ? candidate : node;
            }
            assertThat(node).as(role.role().name()).isNotNull();
            assertThat(node.get("governance").get("accessStatus").asString()).isEqualTo("UNREVIEWED");
            assertThat(node.get("health").asString()).isEqualTo("UNREVIEWED");
        }
    }
}
