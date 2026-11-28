package com.lexvay.eval;

import com.lexvay.assessment.Assessment;
import com.lexvay.assessment.AssessmentService;
import com.lexvay.assessment.AssessmentStatus;
import com.lexvay.assessment.Mapping;
import com.lexvay.common.TenantContext;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import com.lexvay.llm.LlmRates;
import com.lexvay.rule.RuleVersionService;
import com.lexvay.seed.ModelStrategy;
import com.lexvay.seed.ScriptedFakeModel;
import com.lexvay.seed.SeedLoader;
import com.lexvay.seed.SeedManifest;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Measures the pipeline against the golden set (ROADMAP WS-EVAL). Loads the demo program into a fresh,
 * uniquely named tenant through {@link SeedLoader}, lets the pipeline run, then scores what it stored.
 * With the fake model the scripted answers are scored; with a real model its own answers are.
 */
@Service
public class EvalService {

    private final SeedLoader seeds;
    private final AssessmentService assessments;
    private final RuleVersionService ruleVersions;
    private final EvalRepository repository;
    private final LlmClient llm;
    private final ObjectMapper json;
    private final LlmRates rates;

    EvalService(SeedLoader seeds, AssessmentService assessments, RuleVersionService ruleVersions, EvalRepository repository,
                LlmClient llm, ObjectMapper json, LlmRates rates) {
        this.seeds = seeds;
        this.assessments = assessments;
        this.ruleVersions = ruleVersions;
        this.repository = repository;
        this.llm = llm;
        this.json = json;
        this.rates = rates;
    }

    public EvalReport run(Path seedDir, Golden golden, Thresholds thresholds) {
        if (!golden.fictional()) {
            throw new IllegalStateException("eval/golden.yml must declare fictional: true");
        }
        EvalReport.Mode mode = llm instanceof FakeLlmClient ? EvalReport.Mode.FAKE : EvalReport.Mode.LIVE;
        EvalModel model = new EvalModel(mode == EvalReport.Mode.FAKE ? ScriptedFakeModel.load(seedDir, llm, json) : ModelStrategy.NONE,
                ruleVersions, json, mode == EvalReport.Mode.LIVE ? golden : null);
        SeedManifest.Tenant tenant = seeds.manifest(seedDir).tenants().stream().filter(SeedManifest.Tenant::demo).findFirst()
                .orElseThrow(() -> new IllegalStateException("seed/manifest.yml has no demo tenant"))
                .isolated(UUID.randomUUID().toString().substring(0, 8));
        SeedLoader.Result loaded = seeds.loadTenant(tenant, seedDir, UUID.randomUUID().toString(), model);
        return TenantContext.runAs(loaded.tenantId(), () -> report(mode, tenant.name(), loaded.assessmentId(), golden, thresholds, model));
    }

    private EvalReport report(EvalReport.Mode mode, String tenantName, UUID assessmentId, Golden golden, Thresholds thresholds, EvalModel model) {
        Assessment assessment = assessments.require(assessmentId);
        List<Mapping> mappings = assessments.mappings(assessmentId, null).mappings();
        EvalScorer.Score score = EvalScorer.score(golden, mappings, repository.spans(), model.extracted());
        Thresholds.Limits limits = thresholds.forMode(mode);
        List<String> breaches = new ArrayList<>(Thresholds.breaches(limits, score.metrics()));
        if (assessment.status() != AssessmentStatus.FINISHED) {
            breaches.add("the assessment ended as " + assessment.status() + ", not FINISHED");
        }
        TermOverlap termOverlap = mode == EvalReport.Mode.LIVE ? TermOverlap.measure(golden, model.proposedTerms()) : null;
        return new EvalReport(mode, llm.modelId(), tenantName, golden.requirements().size(), score.metrics(), limits, usage(assessment),
                score.mismatches(), breaches, termOverlap);
    }

    private EvalReport.Usage usage(Assessment assessment) {
        EvalRepository.ModelUse use = repository.modelUse();
        BigDecimal cost = rates.priceOf(llm.modelId()).map(p -> p.cost(use.inputTokens(), use.outputTokens())).orElse(null);
        Duration wall = assessment.startedAt() == null || assessment.finishedAt() == null ? Duration.ZERO
                : Duration.between(assessment.startedAt(), assessment.finishedAt());
        return new EvalReport.Usage(use.calls(), use.inputTokens(), use.outputTokens(), cost, wall);
    }
}
