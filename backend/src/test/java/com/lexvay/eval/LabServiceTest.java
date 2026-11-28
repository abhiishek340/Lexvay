package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.lexvay.assessment.AssessmentService;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.YamlFile;
import com.lexvay.document.DocumentService;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/**
 * `make eval-lab` in a test: the real seed, the real demo key, the scripted model and a real database. The generic baseline
 * is blocked in this mode; its measured path is exercised by letting the service believe the model is a live one while the
 * scripted model, given one script per question, still answers.
 */
class LabServiceTest extends AbstractDatabaseTest {

    static final Path SEED = Path.of("..", "seed");
    static final Path EVAL = Path.of("..", "eval");

    @Autowired LabService labs;
    @Autowired EvalService eval;
    @Autowired AssessmentService assessments;
    @Autowired EvalRepository repository;
    @Autowired GenericBaseline baseline;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;

    @TempDir Path dir;
    final LabThresholds thresholds = YamlFile.read(EVAL.resolve("lab-thresholds.yml"), LabThresholds.class);

    @BeforeEach
    void freshScripts() {
        runner.runPending();
        ((FakeLlmClient) llm).reset();
    }

    private AnswerKeys.Loaded demo() {
        return new AnswerKeys(json, EVAL).dev().get(0);
    }

    private static AnswerKeys.Loaded with(AnswerKeys.Loaded loaded, UnaryOperator<AnswerKey.Requirement> change) {
        AnswerKey key = loaded.key();
        return new AnswerKeys.Loaded(loaded.id(), loaded.sha256(),
                new AnswerKey(key.split(), key.provenance(), key.requirements().stream().map(change).toList()));
    }

    private static AnswerKey.Requirement with(AnswerKey.Requirement r, AnswerKey.Expected expected, AnswerKey.Severity severity) {
        return new AnswerKey.Requirement(r.ref(), r.jurisdiction(), r.title(), r.text(), r.type(), r.numeric(), r.searchTerms(), expected, severity,
                expected == AnswerKey.Expected.EVIDENCE_MISSING ? List.of() : r.spans(), r.notes(), r.adjudication());
    }

    @Test
    void aScriptedDevRunOnTheDemoKeyReproducesItAndSaysFictionalOnTheFirstLine() {
        LabReport report = labs.run(SEED, AnswerKey.Split.dev, List.of(demo()), thresholds, false);

        assertThat(report.markdown().lines().findFirst().orElseThrow()).isEqualTo("# Lexvay evaluation lab: fictional, not evidence of accuracy");
        assertThat(report.passed()).isTrue();
        assertThat(report.mode()).isEqualTo(EvalReport.Mode.SCRIPTED);
        var m = report.pipeline().score().overall();
        assertThat(m.get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(0, 42));
        assertThat(m.get(LabMetric.FINDING_ACCURACY)).isEqualTo(new Ratio(42, 42));
        assertThat(m.get(LabMetric.ABSTENTION_RATE)).as("36 requirements are decided by the key; none was answered AMBIGUOUS").isEqualTo(new Ratio(0, 36));
        assertThat(m.get(LabMetric.EVIDENCE_PRECISION)).as("33 found + 6 ambiguous answers cite a span").isEqualTo(new Ratio(39, 39));
        assertThat(m.get(LabMetric.EVIDENCE_RECALL)).isEqualTo(new Ratio(39, 39));
        assertThat(m.get(LabMetric.NUMERIC_ACCURACY)).isEqualTo(new Ratio(11, 11));
        assertThat(report.pipeline().quoteVerification().isComplete()).isTrue();
        assertThat(report.pipeline().usage().calls()).isPositive();
        assertThat(report.baseline().status()).isEqualTo(LabReport.Baseline.Status.NOT_REQUESTED);
        assertThat(report.pipeline().score().breakdowns().get("state")).containsOnlyKeys("AZ", "CA", "CO", "TX", "UT");
        assertThat(report.markdown()).contains("fictional", "| Severe false assurance | 0 / 42, 0.0 % [0.0 %, 8.4 %] | <= 0.0 % | ok |", "RESULT: pass");
    }

    @Test
    void theBaselineInScriptedModeIsBlockedAndNoModelCallIsMadeForIt() {
        LabReport report = labs.run(SEED, AnswerKey.Split.dev, List.of(demo()), thresholds, true);

        assertThat(report.baseline().status()).isEqualTo(LabReport.Baseline.Status.BLOCKED);
        assertThat(report.baseline().reason()).isEqualTo("needs a model");
        assertThat(report.markdown()).contains("BLOCKED: needs a model");
        assertThat(((FakeLlmClient) llm).calls()).extracting(r -> r.scriptKey()).noneMatch(k -> k.startsWith("BASELINE:"));
        assertThat(report.passed()).as("a blocked baseline does not fail the pipeline").isTrue();
    }

    @Test
    void aLabelThatTheSystemContradictsShowsUpAsSevereFalseAssuranceAndFailsTheRun() {
        // The key now says UT-2 has no evidence; the scripted pipeline still proposes EVIDENCE_FOUND for it.
        AnswerKeys.Loaded contradicted = with(demo(), r -> r.key().equals("UT/UT-2") ? with(r, AnswerKey.Expected.EVIDENCE_MISSING, r.severity()) : r);

        LabReport report = labs.run(SEED, AnswerKey.Split.dev, List.of(contradicted), thresholds, false);

        assertThat(report.passed()).isFalse();
        assertThat(report.pipeline().score().overall().get(LabMetric.SEVERE_FALSE_ASSURANCE)).isEqualTo(new Ratio(1, 42));
        assertThat(report.breaches()).singleElement().asString().contains("severe false assurance 2.4 % (1 / 42) breaks the limit <= 0.0 %");
        assertThat(report.markdown()).contains("- demo-northfield UT/UT-2: key EVIDENCE_MISSING, answered EVIDENCE_FOUND (severe false assurance)", "RESULT: FAIL");
    }

    @Test
    void aKeyWrittenForOtherSourcesThanTheSeedIsRefusedNotScoredAgainstSomethingElse() {
        AnswerKeys.Loaded demo = demo();
        AnswerKey.SourceBundle bundle = demo.key().provenance().sourceBundle();
        AnswerKey.Provenance other = new AnswerKey.Provenance(true, demo.key().provenance().date(), demo.key().provenance().labellers(),
                new AnswerKey.SourceBundle(new AnswerKey.Program("M.A. Another Program", "2027-28"), bundle.ruleVersions(),
                        List.of(new AnswerKey.BundleDocument("degree-plan.pdf", "9".repeat(64)))));
        AnswerKeys.Loaded foreign = new AnswerKeys.Loaded("foreign", demo.sha256(), new AnswerKey(AnswerKey.Split.dev, other, demo.key().requirements()));

        assertThatThrownBy(() -> labs.run(SEED, AnswerKey.Split.dev, List.of(foreign), thresholds, false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BLOCKED: answer key foreign was written for sources this checkout does not hold")
                .hasMessageContaining("program M.A. Another Program 2027-28").hasMessageContaining("document degree-plan.pdf " + "9".repeat(64));
    }

    @Test
    void aRunWithNoKeyIsAnErrorNotAPass() {
        assertThatThrownBy(() -> labs.run(SEED, AnswerKey.Split.dev, List.of(), thresholds, false)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no dev answer key");
    }

    @Test
    void aHoldoutKeyNeverReachesADevRun() throws IOException {
        Path keys = Files.createDirectories(dir.resolve("keys"));
        Files.copy(EVAL.resolve("keys").resolve("schema.json"), keys.resolve("schema.json"));
        Files.copy(EVAL.resolve("keys").resolve("demo-northfield.yml"), keys.resolve("demo-northfield.yml"));
        String heldOut = Files.readString(EVAL.resolve("keys").resolve("demo-northfield.yml")).replaceFirst("(?m)^split: dev", "split: holdout")
                .replace("Credit hours", "Held-out secret title");
        Files.writeString(keys.resolve("held-out-secret.yml"), heldOut);
        Files.writeString(dir.resolve("holdout.lock"), DocumentService.sha256(heldOut.getBytes(StandardCharsets.UTF_8))
                + "  held-out-secret.yml\n");

        LabReport report = labs.run(SEED, AnswerKey.Split.dev, new AnswerKeys(json, dir).dev(), thresholds, false);

        assertThat(report.split()).isEqualTo(AnswerKey.Split.dev);
        assertThat(report.keys()).extracting(AnswerKeys.Loaded::id).containsExactly("demo-northfield");
        assertThat(report.markdown()).doesNotContain("held-out-secret", "Held-out secret title");
        assertThat(report.json(json).toString()).doesNotContain("held-out-secret", "Held-out secret title");
        assertThat(report.pipeline().rows()).hasSize(42).extracting(LabScorer.Row::keyId).containsOnly("demo-northfield");
    }

    @Test
    void theMeasuredBaselineIsScoredWithTheSameMetricsAndItsCallsAreCountedApartFromThePipelines() {
        int pipelineCalls = labs.run(SEED, AnswerKey.Split.dev, List.of(demo()), thresholds, false).pipeline().usage().calls();
        FakeLlmClient fake = (FakeLlmClient) llm;
        AnswerKeys.Loaded critical = with(demo(), r -> r.key().equals("UT/UT-8") ? with(r, r.expected(), AnswerKey.Severity.CRITICAL) : r);
        critical.key().requirements().forEach(r -> fake.replaceScript("BASELINE:" + r.ref(), baselineAnswer("EVIDENCE_MISSING", null, null, null)));
        // UT-2: the right sentence. UT-9: claims a course that no document has, with a quote that is not on any page.
        // UT-8: quotes an elective, which is not on every student's path, for a requirement the key now calls CRITICAL.
        fake.replaceScript("BASELINE:UT-2", baselineAnswer("EVIDENCE_FOUND", "coun605-syllabus.pdf", 1,
                "Students examine the ACA Code of Ethics, state licensing law, confidentiality, duty to warn, and the legal standards that govern clinical mental health counselors."));
        fake.replaceScript("BASELINE:UT-9", baselineAnswer("EVIDENCE_FOUND", "coun605-syllabus.pdf", 1, "Students complete a course on the Utah practice act."));
        fake.replaceScript("BASELINE:UT-8", baselineAnswer("EVIDENCE_FOUND", "coun650-syllabus.pdf", 1,
                "This elective covers the etiology of substance use and addictive disorders, screening and assessment of addictions, and evidence-based treatment approaches across the lifespan."));
        EvalService believesInALiveModel = spy(eval);
        doReturn(EvalReport.Mode.LOCAL_LIVE).doReturn(EvalReport.Mode.SCRIPTED).when(believesInALiveModel).mode();
        LabService live = new LabService(believesInALiveModel, assessments, repository, baseline, llm, clock);

        LabReport report = live.run(SEED, AnswerKey.Split.dev, List.of(critical), thresholds, true);

        assertThat(report.mode()).isEqualTo(EvalReport.Mode.LOCAL_LIVE);
        assertThat(report.baseline().status()).isEqualTo(LabReport.Baseline.Status.MEASURED);
        LabReport.Measured generic = report.baseline().measured();
        var m = generic.score().overall();
        assertThat(m.get(LabMetric.SEVERE_FALSE_ASSURANCE)).as("UT-9 (key missing) and UT-8 (elective, CRITICAL)").isEqualTo(new Ratio(2, 42));
        assertThat(m.get(LabMetric.FINDING_ACCURACY)).as("UT-2, CA-7 and TX-7 are right; everything else is answered missing or wrongly found").isEqualTo(new Ratio(3, 42));
        assertThat(m.get(LabMetric.EVIDENCE_PRECISION)).as("only UT-2 and UT-8 quote a passage that is on the page").isEqualTo(new Ratio(2, 2));
        assertThat(generic.quoteVerification()).as("three quotes, one fabricated").isEqualTo(new Ratio(2, 3));
        assertThat(generic.usage().calls()).isEqualTo(42);
        assertThat(generic.notes()).singleElement().asString().contains("given 24 of 24 pages").contains("0 of 42 answers were unusable");
        assertThat(report.pipeline().usage().calls()).as("the baseline's 42 calls are not counted as the pipeline's").isEqualTo(pipelineCalls);
        assertThat(report.pipeline().score().overall().get(LabMetric.FINDING_ACCURACY)).isEqualTo(new Ratio(42, 42));
        assertThat(report.markdown()).contains("| Severe false assurance | 0 / 42, 0.0 % [0.0 %, 8.4 %] | 2 / 42, 4.8 % [1.3 %, 15.8 %] | <= 0.0 % | ok |");
        assertThat(report.passed()).as("the baseline is a comparison, never a limit").isTrue();
    }

    private String baselineAnswer(String finding, String document, Integer page, String quote) {
        return json.createObjectNode().put("finding", finding).put("document", document).put("page_number", page).put("quote", quote).toString();
    }
}
