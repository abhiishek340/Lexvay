package com.lexvay.determination.register;

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
import com.lexvay.seed.SeedSigning;
import com.lexvay.tenant.AppUserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The three lists on the seeded demo program, loaded as a tenant of this test's own. */
class RegisterSeedTest extends AbstractDatabaseTest {

    private static final String EMAIL_DOMAIN = "register-seed.example";
    private static final List<String> TARGETS = List.of("AZ", "CA", "CO", "TX", "UT");

    @Autowired SeedLoader loader;
    @Autowired AppUserRepository users;
    @Autowired MockMvcTester mvc;
    @Autowired ObjectMapper json;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;

    private JsonNode body(MvcTestResult result) throws Exception {
        String content = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as(content).isBetween(200, 299);
        return json.readTree(content);
    }

    private static Map<String, List<String>> partition(JsonNode lists) {
        Map<String, List<String>> codes = new HashMap<>();
        for (String list : List.of("determinedToMeet", "determinedNotToMeet", "noDeterminationMade")) {
            List<String> in = new ArrayList<>();
            lists.get(list).forEach(e -> in.add(e.get("jurisdictionCode").asString()));
            codes.put(list, in);
        }
        return codes;
    }

    @Test
    void theSeededTargetsArePartitionedBeforeAndAfterASignature() throws Exception {
        runner.runPending();
        ((FakeLlmClient) llm).reset();
        var seed = SeedCopy.northfield("Register Seed University", EMAIL_DOMAIN);
        SeedLoader.Result loaded = loader.load(seed, "demo-pass", ScriptedFakeModel.load(seed, llm, json)).get(0);
        LexvayUser dana = users.findForLogin("dana.whitfield@" + EMAIL_DOMAIN).orElseThrow();
        LexvayUser priya = users.findForLogin("priya.raman@" + EMAIL_DOMAIN).orElseThrow();
        String lists = "/api/v1/programs/" + loaded.programId() + "/determination-lists";

        JsonNode before = body(mvc.get().uri(lists).with(user(priya)).exchange());
        assertThat(partition(before).get("noDeterminationMade")).as("the seed signs nothing").containsExactlyElementsOf(TARGETS);
        assertThat(before.get("noDeterminationMade")).allSatisfy(e -> {
            assertThat(e.get("reason").asString()).isEqualTo("NO_CURRENT_SIGNATURE");
            assertThat(e.get("reopenedDeterminationId").isNull()).isTrue();
        });

        String signedCode = SeedSigning.signFirstCurrentJurisdiction(mvc, json, TARGETS, loaded.assessmentId(), loaded.programId(), priya, dana)
                .jurisdiction();

        Map<String, List<String>> after = partition(body(mvc.get().uri(lists).with(user(priya)).exchange()));
        assertThat(after.get("determinedToMeet")).isEmpty();
        assertThat(after.get("determinedNotToMeet")).containsExactly(signedCode);
        assertThat(after.get("noDeterminationMade")).containsExactlyElementsOf(TARGETS.stream().filter(c -> !c.equals(signedCode)).toList());

        JsonNode register = body(mvc.get().uri("/api/v1/determinations/register?programId=" + loaded.programId()).with(user(priya)).exchange());
        assertThat(register).singleElement().satisfies(row -> {
            assertThat(row.get("jurisdictionCode").asString()).isEqualTo(signedCode);
            assertThat(row.get("signedByName").asString()).isEqualTo("Dana Whitfield");
            assertThat(row.get("courseVersionCount").asInt()).as("every course of the seeded program").isEqualTo(15);
        });
    }
}
