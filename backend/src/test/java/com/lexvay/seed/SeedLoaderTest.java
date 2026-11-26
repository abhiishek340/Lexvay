package com.lexvay.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.tenant.AppUserRepository;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Loads the real seed directory into the test database and checks the demo mix the spec asks for. */
class SeedLoaderTest extends AbstractDatabaseTest {

    @Autowired SeedLoader loader;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;

    @Test
    void seedLoadsBothTenantsWithTheExpectedFindingMixAndIsIdempotent() throws Exception {
        ((FakeLlmClient) llm).reset();
        runner.runPending();
        List<SeedLoader.Result> results = loader.load(Path.of("..", "seed"), "demo-pass");
        assertThat(results).hasSize(2);
        SeedLoader.Result northfield = results.get(0);
        assertThat(northfield.skipped()).isFalse();
        assertThat(northfield.documents()).isEqualTo(14);
        assertThat(northfield.requirements()).isEqualTo(42);
        assertThat(northfield.assessmentId()).isNotNull();

        LexvayUser dana = users.findForLogin("dana.whitfield@northfield.example").orElseThrow();
        assertThat(dana.role().name()).isEqualTo("SIGNER");
        assertThat(passwordEncoder.matches("demo-pass", dana.passwordHash())).isTrue();

        JsonNode view = json.readTree(mvc.get().uri("/api/v1/assessments/" + northfield.assessmentId()).with(user(dana)).exchange()
                .getResponse().getContentAsString());
        assertThat(view.get("status").asString()).isEqualTo("FINISHED");
        Map<String, Integer> totals = new HashMap<>();
        for (JsonNode j : view.get("jurisdictions")) {
            totals.merge("found", j.get("evidenceFound").asInt(), Integer::sum);
            totals.merge("missing", j.get("evidenceMissing").asInt(), Integer::sum);
            totals.merge("ambiguous", j.get("ambiguous").asInt(), Integer::sum);
        }
        assertThat(totals).containsEntry("found", 33).containsEntry("ambiguous", 6).containsEntry("missing", 3);

        JsonNode ca = json.readTree(mvc.get().uri("/api/v1/assessments/" + northfield.assessmentId() + "/mappings?jurisdiction=CA")
                .with(user(dana)).exchange().getResponse().getContentAsString()).get("mappings");
        assertThat(ca).hasSize(8);
        Map<String, String> findings = new HashMap<>();
        for (JsonNode m : ca) {
            findings.put(m.get("requirement").get("ref").asString(), m.get("finding").asString());
        }
        assertThat(findings).containsEntry("CA-1", "EVIDENCE_FOUND").containsEntry("CA-2", "EVIDENCE_FOUND")
                .containsEntry("CA-6", "AMBIGUOUS").containsEntry("CA-7", "EVIDENCE_MISSING");
        for (JsonNode m : ca) {
            if (m.get("requirement").get("ref").asString().equals("CA-6")) {
                assertThat(m.get("rationale").asString()).contains("COUN 655").contains("not required");
            }
        }

        SeedLoader.Result westbrook = results.get(1);
        assertThat(westbrook.skipped()).isFalse();
        LexvayUser westbrookAdmin = users.findForLogin("admin@westbrook.example").orElseThrow();
        JsonNode programs = json.readTree(mvc.get().uri("/api/v1/programs").with(user(westbrookAdmin)).exchange().getResponse().getContentAsString());
        assertThat(programs).hasSize(1);
        assertThat(programs.get(0).get("id").asString()).isNotEqualTo(northfield.programId().toString());
        assertThat(mvc.get().uri("/api/v1/assessments/" + northfield.assessmentId()).with(user(westbrookAdmin)).exchange()).hasStatus(404);

        JsonNode changes = json.readTree(mvc.get().uri("/api/v1/change-events").with(user(dana)).exchange().getResponse().getContentAsString());
        assertThat(changes).hasSize(2);
        for (JsonNode c : changes) {
            assertThat(c.get("status").asString()).isEqualTo("OPEN");
            if (c.get("kind").asString().equals("RULE_VERSION")) {
                assertThat(c.get("jurisdiction").asString()).isEqualTo("CO");
                assertThat(c.get("newApproved").asBoolean()).isTrue();
            } else {
                assertThat(c.get("courseCode").asString()).isEqualTo("COUN 625");
                assertThat(c.get("newVersionHasDocument").asBoolean()).isTrue();
            }
        }
        assertThat(json.readTree(mvc.get().uri("/api/v1/assessments/" + northfield.assessmentId() + "/mappings?jurisdiction=CO")
                .with(user(dana)).exchange().getResponse().getContentAsString()).get("mappings")).as("old CO list still shown until re-run").hasSize(9);

        List<SeedLoader.Result> again = loader.load(Path.of("..", "seed"), "demo-pass");
        assertThat(again).allSatisfy(r -> assertThat(r.skipped()).isTrue());
        UUID id = northfield.programId();
        assertThat(id).isNotNull();
    }
}
