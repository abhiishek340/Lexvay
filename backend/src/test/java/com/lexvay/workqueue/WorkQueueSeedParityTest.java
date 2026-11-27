package com.lexvay.workqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.lexvay.assessment.AssessmentRepository;
import com.lexvay.auth.LexvayUser;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.seed.ScriptedFakeModel;
import com.lexvay.seed.SeedCopy;
import com.lexvay.seed.SeedLoader;
import com.lexvay.tenant.AppUserRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The work queue on the seeded tenant reads each program's "latest finished assessment" by
 * {@link AssessmentRepository#LATEST_FINISHED} (D-411): every item that names an assessment names the one
 * {@link AssessmentRepository#latestFinished} returns, and the queue is the same list the badge counts. The queue as
 * it was before the switch (its own copy of the ordering) was compared to this one on the same seed by hand
 * (docs/progress/FIX-5.md); this test keeps the two readings from drifting apart again.
 */
class WorkQueueSeedParityTest extends AbstractDatabaseTest {

    private static final String EMAIL_DOMAIN = "queue-seed.example";

    @Autowired SeedLoader loader;
    @Autowired AppUserRepository users;
    @Autowired AssessmentRepository assessments;
    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;

    private static Path seedCopy;

    @BeforeAll
    static void copySeed() throws IOException {
        seedCopy = SeedCopy.northfield("Queue University", EMAIL_DOMAIN);
    }

    private LexvayUser loadedUser(String local) {
        runner.runPending();
        ((FakeLlmClient) llm).reset();
        assertThat(loader.load(seedCopy, "demo-pass", ScriptedFakeModel.load(seedCopy, llm, json))).singleElement()
                .satisfies(r -> assertThat(r.skipped()).isFalse());
        return users.findForLogin(local + "@" + EMAIL_DOMAIN).orElseThrow();
    }

    private JsonNode get(LexvayUser as, String uri) throws Exception {
        var result = mvc.get().uri(uri).with(user(as)).exchange();
        assertThat(result).hasStatus(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void everyItemThatNamesAnAssessmentNamesTheProgramsLatestFinishedOne() throws Exception {
        LexvayUser signer = loadedUser("dana.whitfield");
        LexvayUser reviewer = users.findForLogin("priya.raman@" + EMAIL_DOMAIN).orElseThrow();
        LexvayUser admin = users.findForLogin("admin@" + EMAIL_DOMAIN).orElseThrow();

        List<String> checked = new ArrayList<>();
        for (LexvayUser as : List.of(signer, reviewer, admin)) {
            JsonNode queue = get(as, "/api/v1/work-queue");
            long sum = 0;
            for (JsonNode item : queue.get("items")) {
                sum += item.get("count").asLong();
                JsonNode assessment = item.get("ids").get("assessmentId");
                if (assessment != null) {
                    UUID program = UUID.fromString(item.get("programId").asString());
                    UUID latest = TenantContext.runAs(as.tenantId(), () -> assessments.latestFinished(program)).orElseThrow().id();
                    assertThat(assessment.asString()).as("%s %s", as.role(), item.get("kind").asString()).isEqualTo(latest.toString());
                    checked.add(as.role() + ":" + item.get("kind").asString());
                }
            }
            assertThat(get(as, "/api/v1/work-queue/count").get("total").asLong()).as("%s badge", as.role()).isEqualTo(sum);
        }
        assertThat(checked).as("the seeded tenant has items that name an assessment, or this test proves nothing").isNotEmpty();
    }
}
