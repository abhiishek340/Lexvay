package com.lexvay.eval;

import com.lexvay.assessment.AssessmentService;
import com.lexvay.assessment.Mapping;
import com.lexvay.document.DocumentService;
import com.lexvay.llm.LlmCallBlockedException;
import com.lexvay.llm.LlmClient;
import com.lexvay.seed.SeedManifest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * The evaluation lab beside {@code make eval} (D-621): scores the pipeline, and optionally a generic model, against answer
 * keys whose labels were made independently of the system. It reuses {@link EvalService} to seed and assess a fresh tenant,
 * so the pipeline under test is the one {@code make eval} runs; this class only reads what it stored and scores it with
 * {@link LabScorer}. The key's program and rule versions must be the ones the seed holds: a key written for sources this
 * checkout does not have is refused, never scored against something else.
 */
@Service
public class LabService {

    /** What one key's assessment produced. {@code baseline} is null when the baseline was not run for it. */
    private record KeyRun(List<LabScorer.Row> rows, Ratio quoteVerification, EvalReport.Usage usage, List<String> notes,
                          BaselineRun baseline) {
    }

    /** The generic model's answers for one key, or, when the product refused a call, the reason in {@code blocked}. */
    private record BaselineRun(List<LabScorer.Row> rows, Ratio quoteVerification, EvalReport.Usage usage, List<String> notes,
                               String blocked) {

        static BaselineRun blocked(String reason) {
            return new BaselineRun(List.of(), new Ratio(0, 0), null, List.of(), reason);
        }
    }

    private final EvalService eval;
    private final AssessmentService assessments;
    private final EvalRepository repository;
    private final GenericBaseline baseline;
    private final LlmClient llm;
    private final Clock clock;

    LabService(EvalService eval, AssessmentService assessments, EvalRepository repository, GenericBaseline baseline, LlmClient llm,
               Clock clock) {
        this.eval = eval;
        this.assessments = assessments;
        this.repository = repository;
        this.baseline = baseline;
        this.llm = llm;
        this.clock = clock;
    }

    /**
     * @param keys         the keys of one split, already checked by {@link AnswerKeys}
     * @param withBaseline compare with the generic model; reported as blocked, never invented, when the model is the scripted one
     */
    public LabReport run(Path seedDir, AnswerKey.Split split, List<AnswerKeys.Loaded> keys, LabThresholds thresholds, boolean withBaseline) {
        if (keys.isEmpty()) {
            throw new IllegalStateException("There is no " + split + " answer key to run");
        }
        EvalReport.Mode mode = eval.mode();
        boolean askBaseline = withBaseline && mode.live();
        Set<String> seedHashes = seedHashes(seedDir);
        keys.forEach(k -> requireBundle(k, eval.demoTenant(seedDir), seedHashes));
        List<KeyRun> runs = keys.stream().map(k -> eval.assessed(seedDir, k.key(), a -> measure(a, k, askBaseline))).toList();

        LabScorer.Score pipelineScore = LabScorer.score(runs.stream().flatMap(r -> r.rows().stream()).toList());
        LabReport.Measured pipeline = new LabReport.Measured(pipelineScore, totalRatio(runs.stream().map(KeyRun::quoteVerification).toList()),
                totalUsage(runs.stream().map(KeyRun::usage).toList()), runs.stream().flatMap(r -> r.rows().stream()).toList(),
                runs.stream().flatMap(r -> r.notes().stream()).toList());
        List<String> breaches = new ArrayList<>(thresholds.breaches(pipelineScore.overall()));
        if (!pipeline.quoteVerification().isComplete() && pipeline.quoteVerification().denominator() > 0) {
            breaches.add("quote verification " + pipeline.quoteVerification().percent() + " is below 100.0 %");
        }
        return new LabReport(mode, llm.modelId(), llm.endpointHost().orElse(null), LocalDate.now(clock), split, keys, pipeline,
                baselineOf(withBaseline, askBaseline, runs), thresholds, breaches);
    }

    private static LabReport.Baseline baselineOf(boolean asked, boolean ran, List<KeyRun> runs) {
        if (!asked) {
            return LabReport.Baseline.notRequested();
        }
        if (!ran) {
            return LabReport.Baseline.blocked("needs a model");
        }
        List<BaselineRun> baselines = runs.stream().map(KeyRun::baseline).toList();
        return baselines.stream().filter(b -> b.blocked() != null).findFirst().map(b -> LabReport.Baseline.blocked(b.blocked()))
                .orElseGet(() -> LabReport.Baseline.measured(new LabReport.Measured(
                        LabScorer.score(baselines.stream().flatMap(b -> b.rows().stream()).toList()),
                        totalRatio(baselines.stream().map(BaselineRun::quoteVerification).toList()),
                        totalUsage(baselines.stream().map(BaselineRun::usage).toList()),
                        baselines.stream().flatMap(b -> b.rows().stream()).toList(),
                        baselines.stream().flatMap(b -> b.notes().stream()).toList())));
    }

    /** Reads the finished assessment of one key and scores it, then, when asked, puts the same questions to the generic model. */
    private KeyRun measure(EvalService.Assessed assessed, AnswerKeys.Loaded loaded, boolean askBaseline) {
        List<Mapping> mappings = assessments.mappings(assessed.assessmentId(), null).mappings();
        List<EvalRepository.PageText> pages = repository.pages();
        Map<UUID, EvalRepository.PageText> documents = new HashMap<>();
        pages.forEach(p -> documents.putIfAbsent(p.documentId(), p));
        Map<String, LabScorer.Answer> answers = new HashMap<>();
        for (Mapping m : mappings) {
            answers.put(Golden.key(m.jurisdiction(), m.requirement().ref()), answer(m, documents));
        }
        List<LabScorer.Row> rows = rows(loaded, answers);
        long unlabelled = mappings.stream().map(m -> Golden.key(m.jurisdiction(), m.requirement().ref()))
                .filter(k -> loaded.key().requirements().stream().noneMatch(r -> r.key().equals(k))).count();
        List<String> notes = unlabelled == 0 ? List.of() : List.of(unlabelled + " requirements of the assessment have no label in key "
                + loaded.id() + " and are not scored");
        Ratio quotes = EvalScorer.verifiedQuotes(repository.spans(), new ArrayList<>());
        EvalReport.Usage usage = eval.pipelineUsage(assessed.assessmentId());
        BaselineRun compared = askBaseline ? compare(assessed.assessmentId(), loaded, mappings, pages) : null;
        return new KeyRun(rows, quotes, usage, notes, compared);
    }

    private static List<LabScorer.Row> rows(AnswerKeys.Loaded loaded, Map<String, LabScorer.Answer> answers) {
        return loaded.key().requirements().stream()
                .map(r -> new LabScorer.Row(loaded.id(), r, answers.getOrDefault(r.key(), LabScorer.Answer.NONE))).toList();
    }

    /** The finding and the located evidence the pipeline stored for one requirement. */
    private static LabScorer.Answer answer(Mapping mapping, Map<UUID, EvalRepository.PageText> documents) {
        Mapping.Evidence e = mapping.evidence();
        if (e == null) {
            return new LabScorer.Answer(mapping.finding(), null);
        }
        EvalRepository.PageText document = documents.get(e.documentId());
        return new LabScorer.Answer(mapping.finding(), new LabScorer.Cited(document.sha256(), e.pageNumber(), e.charStart(), e.charEnd(),
                !Boolean.FALSE.equals(e.courseRequired())));
    }

    /**
     * The generic model's answers for the same requirements. A call the product refuses (the spend gate, for a billed model) is
     * not an error of the run: it leaves the comparison blocked, with the reason, and the pipeline's numbers stand.
     */
    private BaselineRun compare(UUID assessmentId, AnswerKeys.Loaded loaded, List<Mapping> mappings, List<EvalRepository.PageText> pages) {
        GenericBaseline.Run run;
        try {
            run = baseline.run(assessmentId, mappings, pages);
        } catch (LlmCallBlockedException blocked) {
            return BaselineRun.blocked(blocked.getMessage());
        }
        EvalRepository.ModelUse use = repository.modelUse(true);
        List<String> notes = List.of("key " + loaded.id() + ": the model was given " + run.pagesGiven() + " of " + run.pagesTotal()
                + " pages (token budget); " + run.unusableAnswers() + " of " + mappings.size() + " answers were unusable");
        return new BaselineRun(rows(loaded, run.answers()), run.quoteVerification(), eval.usage(use, Duration.ofMillis(use.durationMs())),
                notes, null);
    }

    /** A key written for other sources than the seed holds is refused: the labels would be scored against something else. */
    private static void requireBundle(AnswerKeys.Loaded loaded, SeedManifest.Tenant demo, Set<String> seedHashes) {
        AnswerKey.SourceBundle bundle = loaded.key().provenance().sourceBundle();
        List<String> missing = new ArrayList<>();
        if (!bundle.program().name().equals(demo.program().name()) || !bundle.program().catalogYear().equals(demo.program().catalogYear())) {
            missing.add("program " + bundle.program().name() + " " + bundle.program().catalogYear());
        }
        Set<String> seedRuleVersions = new HashSet<>();
        demo.ruleVersions().forEach(v -> seedRuleVersions.add(v.jurisdiction() + "|" + v.label() + "|" + v.legal().effectiveFrom()));
        bundle.ruleVersions().stream().filter(v -> !seedRuleVersions.contains(v.jurisdiction() + "|" + v.label() + "|" + v.effectiveFrom()))
                .forEach(v -> missing.add("rule version " + v.label() + " effective " + v.effectiveFrom()));
        bundle.documents().stream().filter(d -> !seedHashes.contains(d.sha256())).forEach(d -> missing.add("document " + d.file() + " " + d.sha256()));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("BLOCKED: answer key " + loaded.id() + " was written for sources this checkout does not hold ("
                    + String.join("; ", missing) + "). Loading real sources is not possible here: the seed is fictional and is the only source.");
        }
    }

    private static Set<String> seedHashes(Path seedDir) {
        try (Stream<Path> files = Files.list(seedDir.resolve("pdf"))) {
            Set<String> hashes = new HashSet<>();
            for (Path file : files.toList()) {
                hashes.add(DocumentService.sha256(Files.readAllBytes(file)));
            }
            return hashes;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the seed documents in " + seedDir, e);
        }
    }

    private static Ratio totalRatio(List<Ratio> ratios) {
        return new Ratio(ratios.stream().mapToInt(Ratio::numerator).sum(), ratios.stream().mapToInt(Ratio::denominator).sum());
    }

    /** Model use added up over keys; the cost is unknown when the model of any of them has no price. */
    private static EvalReport.Usage totalUsage(List<EvalReport.Usage> usages) {
        BigDecimal cost = usages.stream().anyMatch(u -> u.cost() == null) ? null
                : usages.stream().map(EvalReport.Usage::cost).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EvalReport.Usage(usages.stream().mapToInt(EvalReport.Usage::calls).sum(),
                usages.stream().mapToLong(EvalReport.Usage::inputTokens).sum(), usages.stream().mapToLong(EvalReport.Usage::outputTokens).sum(),
                usages.stream().anyMatch(EvalReport.Usage::tokensEstimated), cost,
                usages.stream().map(EvalReport.Usage::wallTime).reduce(Duration.ZERO, Duration::plus));
    }
}
