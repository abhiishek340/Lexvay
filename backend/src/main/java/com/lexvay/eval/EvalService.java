package com.lexvay.eval;

import com.lexvay.assessment.Assessment;
import com.lexvay.assessment.AssessmentService;
import com.lexvay.assessment.AssessmentStatus;
import com.lexvay.assessment.Mapping;
import com.lexvay.common.TenantContext;
import com.lexvay.llm.LlmClient;
import com.lexvay.llm.LlmRates;
import com.lexvay.rule.RuleVersionService;
import com.lexvay.seed.ModelStrategy;
import com.lexvay.seed.ScriptedFakeModel;
import com.lexvay.seed.SeedLoader;
import com.lexvay.seed.SeedManifest;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Measures the pipeline against the golden set (ROADMAP WS-EVAL). Loads the demo program into a fresh,
 * uniquely named tenant through {@link SeedLoader}, lets the pipeline run, then scores what it stored.
 * With the scripted model the scripted answers are scored; with a real model, local or billed, its own answers are,
 * and the report is labelled with which it was (D-618).
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
    private final Clock clock;

    EvalService(SeedLoader seeds, AssessmentService assessments, RuleVersionService ruleVersions, EvalRepository repository,
                LlmClient llm, ObjectMapper json, LlmRates rates, Clock clock) {
        this.seeds = seeds;
        this.assessments = assessments;
        this.ruleVersions = ruleVersions;
        this.repository = repository;
        this.llm = llm;
        this.json = json;
        this.rates = rates;
        this.clock = clock;
    }

    /** The pipeline's answers for one fresh demo tenant, readable while {@link #assessed} runs: the tenant is the current one. */
    record Assessed(EvalReport.Mode mode, String tenant, UUID assessmentId, EvalModel model) {
    }

    public EvalReport run(Path seedDir, Golden golden, Thresholds thresholds) {
        if (!golden.fictional()) {
            throw new IllegalStateException("eval/golden.yml must declare fictional: true");
        }
        return assessed(seedDir, golden, a -> report(a.mode(), a.tenant(), a.assessmentId(), golden, thresholds, a.model()));
    }

    /**
     * Loads the demo program into a fresh, uniquely named tenant, lets the pipeline assess it, and hands the finished
     * assessment to {@code use} with that tenant current. A live model's extraction is replaced by {@code approved}, so its
     * matching is scored requirement by requirement; the scripted model keeps the seed's own extraction.
     */
    <T> T assessed(Path seedDir, RequirementList approved, Function<Assessed, T> use) {
        EvalReport.Mode mode = mode();
        EvalModel model = new EvalModel(mode.live() ? ModelStrategy.NONE : ScriptedFakeModel.load(seedDir, llm, json),
                ruleVersions, json, mode.live() ? approved : null);
        SeedManifest.Tenant tenant = demoTenant(seedDir).isolated(UUID.randomUUID().toString().substring(0, 8));
        SeedLoader.Result loaded = seeds.loadTenant(tenant, seedDir, UUID.randomUUID().toString(), model);
        return TenantContext.runAs(loaded.tenantId(), () -> use.apply(new Assessed(mode, tenant.name(), loaded.assessmentId(), model)));
    }

    /** The seed's demo tenant: the program an evaluation assesses, as the manifest declares it. */
    SeedManifest.Tenant demoTenant(Path seedDir) {
        return seeds.manifest(seedDir).tenants().stream().filter(SeedManifest.Tenant::demo).findFirst()
                .orElseThrow(() -> new IllegalStateException("seed/manifest.yml has no demo tenant"));
    }

    /** What the client is decides the label: scripted answers, a model that costs nothing per call, or a billed one. */
    EvalReport.Mode mode() {
        if (llm.scripted()) {
            return EvalReport.Mode.SCRIPTED;
        }
        return llm.metered() ? EvalReport.Mode.PAID_LIVE : EvalReport.Mode.LOCAL_LIVE;
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
        TermOverlap termOverlap = mode.live() ? TermOverlap.measure(golden, model.proposedTerms()) : null;
        return new EvalReport(mode, llm.modelId(), llm.endpointHost().orElse(null), LocalDate.now(clock), tenantName,
                golden.requirements().size(), score.metrics(), limits, usage(assessment), score.mismatches(), breaches, termOverlap);
    }

    private EvalReport.Usage usage(Assessment assessment) {
        Duration wall = assessment.startedAt() == null || assessment.finishedAt() == null ? Duration.ZERO
                : Duration.between(assessment.startedAt(), assessment.finishedAt());
        return usage(repository.modelUse(false), wall);
    }

    /** The calls, tokens and, when the model has a price, the cost of a set of model calls. */
    EvalReport.Usage usage(EvalRepository.ModelUse use, Duration wall) {
        BigDecimal cost = rates.priceOf(llm.modelId()).map(p -> p.cost(use.inputTokens(), use.outputTokens())).orElse(null);
        return new EvalReport.Usage(use.calls(), use.inputTokens(), use.outputTokens(), use.tokensEstimated(), cost, wall);
    }

    /** The pipeline's model use for the assessment, as the report prints it. */
    EvalReport.Usage pipelineUsage(UUID assessmentId) {
        return usage(assessments.require(assessmentId));
    }
}
