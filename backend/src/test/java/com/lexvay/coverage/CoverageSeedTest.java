package com.lexvay.coverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.auth.LexvayUser;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.seed.ScriptedFakeModel;
import com.lexvay.seed.SeedCopy;
import com.lexvay.seed.SeedLoader;
import com.lexvay.tenant.AppUserRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The coverage matrix on the seeded program (fictional, D-066 change events open). The seed is loaded from a
 * copy of the manifest with its own tenant and email domain, so this class does not depend on other tests.
 */
class CoverageSeedTest extends AbstractDatabaseTest {

    private static final String EMAIL_DOMAIN = "coverage-seed.example";

    @Autowired SeedLoader loader;
    @Autowired AppUserRepository users;
    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;

    private static Path seedCopy;
    private static boolean loaded;

    @BeforeAll
    static void copySeed() throws IOException {
        seedCopy = SeedCopy.northfield("Coverage University", EMAIL_DOMAIN);
    }

    private LexvayUser priya() {
        if (!loaded) {
            runner.runPending();
            ((FakeLlmClient) llm).reset();
            assertThat(loader.load(seedCopy, "demo-pass", ScriptedFakeModel.load(seedCopy, llm, json))).singleElement()
                    .satisfies(r -> assertThat(r.skipped()).isFalse());
            loaded = true;
        }
        return users.findForLogin("priya.raman@" + EMAIL_DOMAIN).orElseThrow();
    }

    private JsonNode get(String uri) throws Exception {
        var result = mvc.get().uri(uri).with(user(priya())).exchange();
        assertThat(result).hasStatus(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private UUID program() throws Exception {
        return UUID.fromString(get("/api/v1/programs").get(0).get("id").asString());
    }

    private JsonNode matrix(String jurisdiction) throws Exception {
        return get("/api/v1/programs/" + program() + "/coverage?jurisdiction=" + jurisdiction);
    }

    private static JsonNode row(JsonNode matrix, String ref) {
        for (JsonNode requirement : matrix.get("requirements")) {
            if (requirement.get("ref").asString().equals(ref)) {
                return requirement;
            }
        }
        throw new AssertionError("No row for " + ref);
    }

    private static JsonNode course(JsonNode matrix, String code) {
        for (JsonNode course : matrix.get("courses")) {
            if (course.get("code").asString().equals(code)) {
                return course;
            }
        }
        throw new AssertionError("No course " + code);
    }

    @Test
    void californiaListsEveryRequirementInOrderWithTheExpectedCells() throws Exception {
        JsonNode m = matrix("CA");

        assertThat(m.get("program").get("name").asString()).isEqualTo("M.S. Clinical Mental Health Counseling");
        assertThat(m.get("jurisdiction").get("code").asString()).isEqualTo("CA");
        assertThat(m.get("ruleVersion").get("label").asString()).isEqualTo("Demo rule set - California (fictional) 2026");
        assertThat(m.get("requirements")).extracting(r -> r.get("ref").asString())
                .containsExactly("CA-1", "CA-2", "CA-3", "CA-4", "CA-5", "CA-6", "CA-7", "CA-8");
        JsonNode mappings = get("/api/v1/assessments/" + m.get("assessmentId").asString() + "/mappings?jurisdiction=CA").get("mappings");
        assertThat(m.get("requirements")).extracting(r -> r.get("mappingId").asString())
                .containsExactlyElementsOf(mappings.valueStream().map(x -> x.get("id").asString()).toList());

        assertThat(m.get("courses")).extracting(c -> c.get("code").asString()).containsExactly("COUN 605", "COUN 610", "COUN 615",
                "COUN 620", "COUN 625", "COUN 630", "COUN 635", "COUN 640", "COUN 660", "COUN 650", "COUN 655");
        JsonNode coun605 = course(m, "COUN 605");

        JsonNode ethics = row(m, "CA-2");
        assertThat(ethics.get("finding").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(ethics.get("cells")).hasSize(1);
        assertThat(ethics.get("cells").get(0).get("courseVersionId").asString()).isEqualTo(coun605.get("courseVersionId").asString());
        assertThat(ethics.get("cells").get(0).get("source").asString()).isEqualTo("SYSTEM");
        assertThat(ethics.get("cells").get(0).get("quoteCount").asInt()).isEqualTo(1);
        assertThat(ethics.get("programLevel")).isEmpty();
        assertThat(ethics.get("risk").asString()).isEqualTo("SINGLE_COURSE");
    }

    @Test
    void theElectiveOnlyRequirementIsFlaggedAndTheNumbersCarryNoCells() throws Exception {
        JsonNode m = matrix("CA");

        JsonNode pharmacology = row(m, "CA-6");
        assertThat(pharmacology.get("finding").asString()).as("an elective alone is never enough for a found finding").isEqualTo("AMBIGUOUS");
        assertThat(pharmacology.get("cells")).hasSize(1);
        assertThat(pharmacology.get("cells").get(0).get("courseVersionId").asString()).isEqualTo(course(m, "COUN 655").get("courseVersionId").asString());
        assertThat(course(m, "COUN 655").get("required").asBoolean()).isFalse();
        assertThat(pharmacology.get("risk").asString()).isEqualTo("ELECTIVE_ONLY");

        for (String ref : List.of("CA-1", "CA-5")) {
            assertThat(row(m, ref).get("type").asString()).isIn("NUMERIC", "SUPERVISED_HOURS");
            assertThat(row(m, ref).get("cells")).as(ref).isEmpty();
            assertThat(row(m, ref).get("programLevel")).as(ref).isEmpty();
            assertThat(row(m, ref).get("risk").asString()).as(ref).isEqualTo("NONE");
        }
        JsonNode telehealth = row(m, "CA-7");
        assertThat(telehealth.get("finding").asString()).isEqualTo("EVIDENCE_MISSING");
        assertThat(telehealth.get("risk").asString()).isEqualTo("NO_EVIDENCE");
    }

    @Test
    void accreditationEvidenceIsProgramLevelAndEvidenceOfASupersededCourseVersionIsOutdated() throws Exception {
        JsonNode utah = matrix("UT");

        JsonNode accreditation = row(utah, "UT-6");
        assertThat(accreditation.get("cells")).isEmpty();
        assertThat(accreditation.get("programLevel")).hasSize(1);
        assertThat(accreditation.get("programLevel").get(0).get("kind").asString()).isEqualTo("ACCREDITATION");
        assertThat(accreditation.get("programLevel").get(0).get("filename").asString()).isEqualTo("accreditation-letter.pdf");
        assertThat(accreditation.get("programLevel").get(0).get("source").asString()).isEqualTo("SYSTEM");
        assertThat(accreditation.get("risk").asString()).isEqualTo("NONE");

        for (String jurisdiction : List.of("UT", "AZ", "TX")) {
            JsonNode matrix = matrix(jurisdiction);
            String ref = Map.of("UT", "UT-7", "AZ", "AZ-4", "TX", "TX-8").get(jurisdiction);
            JsonNode group = row(matrix, ref);
            assertThat(group.get("cells")).as(ref + ": the cited syllabus belongs to a superseded course version").isEmpty();
            assertThat(group.get("outdatedCells")).as(ref).hasSize(1);
            JsonNode outdated = group.get("outdatedCells").get(0);
            assertThat(outdated.get("code").asString()).isEqualTo("COUN 625");
            assertThat(outdated.get("catalogYear").asString()).isEqualTo("2026-27");
            assertThat(outdated.get("supersededBy").asString()).isEqualTo(course(matrix, "COUN 625").get("courseVersionId").asString());
            assertThat(outdated.get("courseVersionId").asString()).isNotEqualTo(outdated.get("supersededBy").asString());
            assertThat(outdated.get("source").asString()).isEqualTo("SYSTEM");
            assertThat(outdated.get("quoteCount").asInt()).isEqualTo(1);
            assertThat(group.get("risk").asString()).as(ref).isEqualTo("OUTDATED_EVIDENCE");
        }
        assertThat(utah.get("courses").valueStream().filter(c -> c.get("code").asString().equals("COUN 625"))).hasSize(1);
    }

    @Test
    void theColoradoListStaysUntilTheRevisionIsRerun() throws Exception {
        JsonNode m = matrix("CO");

        assertThat(m.get("ruleVersion").get("label").asString()).isEqualTo("Demo rule set - Colorado (fictional) 2026");
        assertThat(m.get("requirements")).hasSize(9);
        assertThat(m.get("requirements")).filteredOn(r -> !r.get("type").asString().equals("NUMERIC") && !r.get("type").asString().equals("SUPERVISED_HOURS"))
                .isNotEmpty().allSatisfy(r -> assertThat(r.get("risk").asString()).isEqualTo("OUTDATED_EVIDENCE"));
    }
}
