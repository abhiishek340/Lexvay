package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.assessment.Finding;
import com.lexvay.common.AbstractDatabaseTest;
import com.lexvay.common.YamlFile;
import com.lexvay.job.JobRunner;
import com.lexvay.llm.FakeLlmClient;
import com.lexvay.llm.LlmClient;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** `make eval` in a test: the real seed, the real golden set, the scripted model, a real database. */
class EvalServiceTest extends AbstractDatabaseTest {

    static final Path SEED = Path.of("..", "seed");
    static final Path EVAL = Path.of("..", "eval");

    @Autowired EvalService service;
    @Autowired LlmClient llm;
    @Autowired JobRunner runner;

    final Golden golden = YamlFile.read(EVAL.resolve("golden.yml"), Golden.class);
    final Thresholds thresholds = YamlFile.read(EVAL.resolve("thresholds.yml"), Thresholds.class);

    @BeforeEach
    void freshScripts() {
        runner.runPending();
        ((FakeLlmClient) llm).reset();
    }

    @Test
    void theSeededPipelineReproducesTheGoldenSet() {
        EvalReport report = service.run(SEED, golden, thresholds);

        assertThat(report.breaches()).isEmpty();
        assertThat(report.passed()).isTrue();
        assertThat(report.mismatches()).isEmpty();
        Metrics m = report.metrics();
        assertThat(m.falseEvidenceFound()).isEqualTo(new Ratio(0, 33));
        assertThat(m.recall()).isEqualTo(new Ratio(33, 33));
        assertThat(m.numericAccuracy()).isEqualTo(new Ratio(11, 11));
        assertThat(m.extractionAgreement()).isEqualTo(new Ratio(42, 42));
        assertThat(m.evidenceAgreement()).isEqualTo(new Ratio(39, 39));
        assertThat(m.quoteVerification().isComplete()).isTrue();
        assertThat(m.quoteVerification().denominator()).as("three program-fact spans plus one per matched requirement").isGreaterThan(3);
        assertThat(report.usage().calls()).as("one extraction per rule set plus a match per non-numeric requirement").isPositive();
        assertThat(report.usage().inputTokens()).isPositive();
        assertThat(report.usage().outputTokens()).isPositive();
        assertThat(report.render()).contains("False EVIDENCE_FOUND rate", "Recall on golden EVIDENCE_FOUND", "Quote verification",
                "Numeric accuracy", "Requirement extraction agreement", "Input tokens", "Output tokens", "Cost per assessment",
                "Wall time per assessment", "RESULT: pass");
        assertThat(report.termOverlap()).as("the scripted model's terms are the seed's, not a measurement").isNull();
        assertThat(report.render()).doesNotContain("Search terms");
    }

    @Test
    void aWrongGoldenEntryFailsTheRunSoTheProcessWouldExitNonZero() {
        List<Golden.Entry> wrong = golden.requirements().stream()
                .map(e -> e.key().equals("UT/UT-2") ? withFinding(e, Finding.EVIDENCE_MISSING) : e).toList();

        EvalReport report = service.run(SEED, new Golden(true, wrong), thresholds);

        assertThat(report.passed()).isFalse();
        assertThat(report.breaches()).anyMatch(b -> b.startsWith("false EVIDENCE_FOUND rate"));
        assertThat(report.metrics().falseEvidenceFound()).isEqualTo(new Ratio(1, 33));
        assertThat(report.mismatches()).extracting(EvalScorer.Mismatch::subject).contains("UT/UT-2");
        assertThat(report.render()).contains("RESULT: FAIL", "UT/UT-2");
    }

    @Test
    void anExpectedFindingTheModelDoesNotProposeLowersRecallAndFails() {
        List<Golden.Entry> wrong = golden.requirements().stream()
                .map(e -> e.key().equals("UT/UT-8") ? withFinding(e, Finding.EVIDENCE_FOUND) : e).toList();

        EvalReport report = service.run(SEED, new Golden(true, wrong), thresholds);

        assertThat(report.passed()).isFalse();
        assertThat(report.metrics().recall()).isEqualTo(new Ratio(33, 34));
        assertThat(report.metrics().falseEvidenceFound().numerator()).isZero();
    }

    private static Golden.Entry withFinding(Golden.Entry e, Finding finding) {
        return new Golden.Entry(e.ref(), e.jurisdiction(), e.title(), e.text(), e.type(), e.numeric(), finding, e.document(),
                e.quoteContains(), e.searchTerms(), e.reason());
    }
}
