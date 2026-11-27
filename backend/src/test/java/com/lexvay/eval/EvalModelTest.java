package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.AssessmentService;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.TenantContext;
import com.lexvay.common.YamlFile;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.rule.RequirementType;
import com.lexvay.rule.RuleVersionService;
import com.lexvay.seed.ScriptedFakeModel;
import com.lexvay.seed.SeedLoader;
import com.lexvay.seed.SeedManifest;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/**
 * The live-mode mechanism, exercised with the scripted model: the strategy remembers what extraction produced,
 * then the golden list replaces it through the reviewer's replace-requirements path before approval.
 */
class EvalModelTest extends AbstractDatabaseTest {

    static final Path SEED = Path.of("..", "seed");

    @Autowired SeedLoader loader;
    @Autowired RuleVersionService ruleVersions;
    @Autowired AssessmentService assessments;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;
    @Autowired ObjectMapper json;

    @BeforeEach
    void freshScripts() {
        ((FakeLlmClient) llm).reset();
        runner.runPending();
    }

    @Test
    void goldenListReplacesTheExtractionBeforeApprovalAndTheModelsOwnListIsRemembered() {
        Golden golden = YamlFile.read(Path.of("..", "eval", "golden.yml"), Golden.class);
        List<Golden.Entry> renamed = golden.requirements().stream()
                .map(e -> e.key().equals("UT/UT-1") ? new Golden.Entry(e.ref(), e.jurisdiction(), "Golden credit hours", e.text(), e.type(),
                        e.numeric(), e.finding(), e.document(), e.quoteContains(), e.reason()) : e).toList();
        EvalModel model = new EvalModel(ScriptedFakeModel.load(SEED, llm, json), ruleVersions, json, new Golden(true, renamed));
        SeedManifest.Tenant tenant = loader.manifest(SEED).tenants().get(0).isolated(UUID.randomUUID().toString().substring(0, 8));

        SeedLoader.Result result = loader.loadTenant(tenant, SEED, "password", model);

        assertThat(model.extracted()).hasSize(42).containsEntry("UT/UT-1", RequirementType.NUMERIC).containsEntry("TX/TX-7", RequirementType.JURISDICTION_SPECIFIC);
        TenantContext.runAs(result.tenantId(), () -> {
            var utah = ruleVersions.list().stream().filter(v -> v.jurisdiction().equals("UT")).findFirst().orElseThrow();
            assertThat(utah.approval()).isNotNull();
            assertThat(ruleVersions.requirements(utah.id()).requirements()).extracting(r -> r.title()).contains("Golden credit hours");
            assertThat(assessments.mappings(result.assessmentId(), null).mappings()).hasSize(42);
        });
    }
}
