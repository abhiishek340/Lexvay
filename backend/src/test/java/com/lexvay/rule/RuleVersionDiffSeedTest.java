package com.lexvay.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TestTenants;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.seed.SeedLoader;
import com.lexvay.tenant.AppUserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The diff API on the seeded Colorado revision. The seed is loaded from a copy of the manifest with its own
 * tenant and email domain, so this class does not depend on whether SeedLoaderTest ran first.
 */
class RuleVersionDiffSeedTest extends AbstractDatabaseTest {

    private static final String EMAIL_DOMAIN = "diff-api.example";

    @Autowired SeedLoader loader;
    @Autowired AppUserRepository users;
    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;
    @Autowired TestTenants tenants;

    private static Path seedCopy;
    private static boolean loaded;

    @BeforeAll
    static void copySeed() throws IOException {
        Path seed = Path.of("..", "seed").toAbsolutePath().normalize();
        seedCopy = Files.createTempDirectory("lexvay-seed-");
        for (String shared : List.of("fake-llm.yml", "rules", "pdf")) {
            Files.createSymbolicLink(seedCopy.resolve(shared), seed.resolve(shared));
        }
        String manifest = Files.readString(seed.resolve("manifest.yml"));
        manifest = manifest.substring(0, manifest.indexOf("  - name: Westbrook College"))
                .replace("northfield.example", EMAIL_DOMAIN).replace("Northfield University", "Diff API University");
        Files.writeString(seedCopy.resolve("manifest.yml"), manifest);
    }

    private LexvayUser dana() {
        if (!loaded) {
            ((FakeLlmClient) llm).reset();
            runner.runPending();
            assertThat(loader.load(seedCopy, "demo-pass")).singleElement().satisfies(r -> assertThat(r.skipped()).isFalse());
            loaded = true;
        }
        return users.findForLogin("dana.whitfield@" + EMAIL_DOMAIN).orElseThrow();
    }

    private JsonNode body(MvcTestResult result) {
        try {
            return json.readTree(result.getResponse().getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID version(LexvayUser viewer, String jurisdiction, boolean revision) {
        JsonNode all = body(mvc.get().uri("/api/v1/rule-versions").with(user(viewer)).exchange());
        for (JsonNode v : all) {
            if (v.get("jurisdiction").asString().equals(jurisdiction) && v.get("label").asString().endsWith("revision 2") == revision) {
                return UUID.fromString(v.get("id").asString());
            }
        }
        throw new AssertionError("No " + jurisdiction + " rule version, revision=" + revision);
    }

    private static JsonNode row(JsonNode diff, String ref) {
        for (JsonNode r : diff.get("requirements")) {
            JsonNode side = r.get("after").isNull() ? r.get("before") : r.get("after");
            if (side.get("ref").asString().equals(ref)) {
                return r;
            }
        }
        throw new AssertionError("No row for " + ref);
    }

    private static List<String> texts(JsonNode segments, String op) {
        List<String> texts = new ArrayList<>();
        for (JsonNode s : segments) {
            if (s.get("op").asString().equals(op)) {
                texts.add(s.get("text").asString());
            }
        }
        return texts;
    }

    @Test
    void theColoradoRevisionChangesExactlyOneRequirementAndDefaultsToTheSupersededVersion() {
        LexvayUser dana = dana();
        UUID original = version(dana, "CO", false);
        UUID revision = version(dana, "CO", true);

        var result = mvc.get().uri("/api/v1/rule-versions/" + revision + "/diff").with(user(dana)).exchange();
        assertThat(result).hasStatus(200);
        JsonNode diff = body(result);

        assertThat(diff.get("from").get("id").asString()).isEqualTo(original.toString());
        assertThat(diff.get("from").get("label").asString()).isEqualTo("Demo rule set - Colorado (fictional) 2026");
        assertThat(diff.get("from").get("effectiveFrom").asString()).isEqualTo("2026-07-01");
        assertThat(diff.get("to").get("id").asString()).isEqualTo(revision.toString());
        assertThat(diff.get("to").get("effectiveFrom").asString()).isEqualTo("2027-01-01");
        JsonNode summary = diff.get("summary");
        assertThat(summary.get("added").asInt()).isZero();
        assertThat(summary.get("removed").asInt()).isZero();
        assertThat(summary.get("changed").asInt()).isEqualTo(1);
        assertThat(summary.get("unchanged").asInt()).isEqualTo(8);

        assertThat(diff.get("requirements")).hasSize(9);
        JsonNode changed = row(diff, "CO-5");
        assertThat(changed.get("status").asString()).isEqualTo("CHANGED");
        assertThat(changed.get("refChanged").asBoolean()).isFalse();
        assertThat(changed.get("changes")).extracting(JsonNode::asString).containsExactly("TEXT", "NUMERIC");
        assertThat(changed.get("before").get("text").asString()).contains("600 clock hours");
        assertThat(changed.get("before").get("numericValue").decimalValue()).isEqualByComparingTo("600");
        assertThat(changed.get("after").get("text").asString()).contains("700 clock hours");
        assertThat(changed.get("after").get("numericValue").decimalValue()).isEqualByComparingTo("700");
        assertThat(texts(changed.get("textDiff"), "DELETE")).containsExactly("600");
        assertThat(texts(changed.get("textDiff"), "INSERT")).containsExactly("700");
        for (int n : new int[] {1, 2, 3, 4, 6, 7, 8, 9}) {
            JsonNode unchanged = row(diff, "CO-" + n);
            assertThat(unchanged.get("status").asString()).isEqualTo("UNCHANGED");
            assertThat(unchanged.get("refChanged").asBoolean()).isFalse();
            assertThat(unchanged.get("changes")).isEmpty();
            assertThat(unchanged.get("textDiff").isNull()).isTrue();
        }

        JsonNode ruleText = diff.get("ruleTextDiff");
        assertThat(texts(ruleText, "DELETE")).contains("). ", "600");
        assertThat(texts(ruleText, "INSERT")).contains("), revision 2. ", "700");
        assertThat(String.join("", texts(ruleText, "EQUAL"))).contains("Rule 1.").contains("Not the law of any state.");
    }

    @Test
    void anExplicitAgainstGivesTheSameDiffAndTheReverseDirectionSwapsInsertAndDelete() {
        LexvayUser dana = dana();
        UUID original = version(dana, "CO", false);
        UUID revision = version(dana, "CO", true);

        JsonNode implicit = body(mvc.get().uri("/api/v1/rule-versions/" + revision + "/diff").with(user(dana)).exchange());
        JsonNode explicit = body(mvc.get().uri("/api/v1/rule-versions/" + revision + "/diff?against=" + original).with(user(dana)).exchange());
        assertThat(explicit).isEqualTo(implicit);

        var reverse = mvc.get().uri("/api/v1/rule-versions/" + original + "/diff?against=" + revision).with(user(dana)).exchange();
        assertThat(reverse).hasStatus(200);
        JsonNode changed = row(body(reverse), "CO-5");
        assertThat(texts(changed.get("textDiff"), "DELETE")).containsExactly("700");
        assertThat(texts(changed.get("textDiff"), "INSERT")).containsExactly("600");
    }

    @Test
    void aVersionWithoutASupersededOneNeedsAnAgainstParameter() {
        LexvayUser dana = dana();
        var result = mvc.get().uri("/api/v1/rule-versions/" + version(dana, "CO", false) + "/diff").with(user(dana)).exchange();
        assertThat(result).hasStatus(400);
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
        assertThat(body(result).get("detail").asString()).contains("does not supersede");
    }

    @Test
    void versionsOfDifferentJurisdictionsCannotBeCompared() {
        LexvayUser dana = dana();
        UUID colorado = version(dana, "CO", true);
        UUID utah = version(dana, "UT", false);
        var result = mvc.get().uri("/api/v1/rule-versions/" + colorado + "/diff?against=" + utah).with(user(dana)).exchange();
        assertThat(result).hasStatus(400);
        assertThat(body(result).get("detail").asString()).contains("same jurisdiction and license type");
    }

    @Test
    void anotherTenantSeesNeitherVersion() {
        LexvayUser dana = dana();
        UUID original = version(dana, "CO", false);
        UUID revision = version(dana, "CO", true);
        TestTenants.TenantWithUsers other = tenants.create("diff-other");

        assertThat(mvc.get().uri("/api/v1/rule-versions/" + revision + "/diff").with(user(other.reviewer())).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/rule-versions/" + revision + "/diff?against=" + original).with(user(other.admin())).exchange()).hasStatus(404);
        assertThat(mvc.get().uri("/api/v1/rule-versions/" + UUID.randomUUID() + "/diff").with(user(dana)).exchange()).hasStatus(404);
    }

    @Test
    void theDiffRequiresAuthentication() {
        LexvayUser dana = dana();
        assertThat(mvc.get().uri("/api/v1/rule-versions/" + version(dana, "CO", true) + "/diff").exchange()).hasStatus(401);
    }
}
