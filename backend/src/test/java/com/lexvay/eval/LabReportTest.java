package com.lexvay.eval;

import static com.lexvay.eval.LabFixtures.eightRows;
import static com.lexvay.eval.LabFixtures.key;
import static com.lexvay.eval.LabFixtures.measured;
import static com.lexvay.eval.LabFixtures.report;
import static com.lexvay.eval.LabFixtures.requirement;
import static com.lexvay.eval.LabFixtures.span;
import static com.lexvay.eval.LabFixtures.thresholds;
import static org.assertj.core.api.Assertions.assertThat;

import com.lexvay.rule.RequirementType;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** What the Markdown and JSON reports say, with fixtures small enough to read. */
class LabReportTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final AnswerKey FICTIONAL = key(AnswerKey.Split.dev, true, false, List.of(
            requirement("UT", "UT-1", RequirementType.NUMERIC, AnswerKey.Expected.EVIDENCE_FOUND, AnswerKey.Severity.CRITICAL, span(LabFixtures.DOC_A, 1, 0, 50)),
            requirement("UT", "UT-2", RequirementType.SUBJECT, AnswerKey.Expected.EVIDENCE_MISSING, AnswerKey.Severity.STANDARD)));
    static final AnswerKey REAL = key(AnswerKey.Split.holdout, false, true, FICTIONAL.requirements());

    @Test
    void aFictionalKeysReportSaysSoOnTheFirstLine() {
        String markdown = report(EvalReport.Mode.SCRIPTED, FICTIONAL, LabReport.Baseline.notRequested(), thresholds(0.0), List.of()).markdown();

        assertThat(markdown.lines().findFirst().orElseThrow()).isEqualTo("# Lexvay evaluation lab: fictional, not evidence of accuracy");
    }

    @Test
    void aRealKeysReportDoesNotSayItIsFictional() {
        String markdown = report(EvalReport.Mode.LOCAL_LIVE, REAL, LabReport.Baseline.notRequested(), thresholds(0.0), List.of()).markdown();

        assertThat(markdown.lines().findFirst().orElseThrow()).isEqualTo("# Lexvay evaluation lab");
        assertThat(markdown).contains("- Mode: LOCAL_LIVE (local inference; this model has not been vetted by Lexvay, so the results describe it alone)",
                "- Model: model-x on host localhost, 2026-11-28, split holdout", "Accuracy claim supported: no objection recorded");
    }

    @Test
    void theHeaderStatesEveryReasonAnAccuracyClaimWouldFail() {
        LabReport scriptedDevRun = report(EvalReport.Mode.SCRIPTED, FICTIONAL, LabReport.Baseline.notRequested(), thresholds(0.0), List.of());

        assertThat(scriptedDevRun.claimBlockers()).containsExactly("the key is fictional",
                "the labels are not independent (a key needs at least two independent labellers)", "the answers are scripted, not a model's",
                "dev keys are the ones tuned against; only a holdout run can support a claim");
        assertThat(scriptedDevRun.markdown()).contains("- Accuracy claim supported: no, because the key is fictional; the labels are not independent");
        assertThat(report(EvalReport.Mode.PAID_LIVE, REAL, LabReport.Baseline.notRequested(), thresholds(0.0), List.of()).claimBlockers()).isEmpty();
    }

    @Test
    void theHeadlineAndEveryMetricShowTheCountsAndTheInterval() {
        String markdown = report(EvalReport.Mode.SCRIPTED, FICTIONAL, LabReport.Baseline.notRequested(), thresholds(0.0), List.of()).markdown();

        assertThat(markdown).contains("Severe false assurance, the headline: 0 / 2 (0.0 %), 95 % Wilson interval [0.0 %, 65.8 %]",
                "| Severe false assurance | 0 / 2, 0.0 % [0.0 %, 65.8 %] | <= 0.0 % | ok |",
                "| Finding accuracy | 2 / 2, 100.0 % [34.2 %, 100.0 %] | unset | unset |", "| Quote verification | 5 / 5, 100.0 % [56.6 %, 100.0 %] | = 100.0 % | ok |");
    }

    @Test
    void withoutADenominatorAMetricSaysSoInsteadOfInventingARate() {
        AnswerKey onlyMissing = key(AnswerKey.Split.dev, true, false, List.of(FICTIONAL.requirements().get(1)));

        String markdown = report(EvalReport.Mode.SCRIPTED, onlyMissing, LabReport.Baseline.notRequested(), thresholds(0.0), List.of()).markdown();

        assertThat(markdown).contains("| Numeric accuracy | n/a (0 / 0) | unset | unset |");
    }

    @Test
    void theBaselineInScriptedModeIsBlockedNeverInvented() {
        LabReport blocked = report(EvalReport.Mode.SCRIPTED, FICTIONAL, LabReport.Baseline.blocked("needs a model"), thresholds(0.0), List.of());

        assertThat(blocked.markdown()).contains("| Metric | Pipeline | Generic baseline | Limit | Status |",
                "| Severe false assurance | 0 / 2, 0.0 % [0.0 %, 65.8 %] | BLOCKED: needs a model | <= 0.0 % | ok |");
        JsonNode baseline = blocked.json(JSON).get("baseline");
        assertThat(baseline.get("status").asString()).isEqualTo("BLOCKED");
        assertThat(baseline.get("reason").asString()).isEqualTo("needs a model");
        assertThat(baseline.has("measured")).isFalse();
    }

    @Test
    void aMeasuredBaselineSitsBesideThePipelineWithItsOwnBreakdownsAndNotes() {
        List<LabScorer.Row> baselineRows = eightRows("demo");
        LabReport.Measured generic = new LabReport.Measured(LabScorer.score(baselineRows), new Ratio(3, 5), LabFixtures.usage(true), baselineRows,
                List.of("key demo: the model was given 13 of 14 pages (token budget); 1 of 42 answers were unusable"));

        LabReport compared = report(EvalReport.Mode.LOCAL_LIVE, FICTIONAL, LabReport.Baseline.measured(generic), thresholds(0.0), List.of());

        assertThat(compared.markdown()).contains("| Severe false assurance | 0 / 2, 0.0 % [0.0 %, 65.8 %] | 3 / 8, 37.5 % [13.7 %, 69.4 %] | <= 0.0 % | ok |",
                "| Quote verification | 5 / 5, 100.0 % [56.6 %, 100.0 %] | 3 / 5, 60.0 % [23.1 %, 88.2 %] | = 100.0 % | ok |",
                "### Generic baseline by state", "### Pipeline by state", "Generic baseline: key demo: the model was given 13 of 14 pages",
                "- Generic baseline: 3 calls, 1,000 input tokens, 200 output tokens, 2.0 s, cost 0 USD",
                "Token counts marked as estimates come from text length");
        JsonNode measured = compared.json(JSON).get("baseline").get("measured");
        assertThat(measured.get("metrics").get("severeFalseAssurance").get("numerator").asInt()).isEqualTo(3);
        assertThat(measured.get("quoteVerification").get("denominator").asInt()).isEqualTo(5);
    }

    @Test
    void whenNoBaselineWasAskedForTheReportSaysHowToAskAndShowsNoColumn() {
        String markdown = report(EvalReport.Mode.SCRIPTED, FICTIONAL, LabReport.Baseline.notRequested(), thresholds(0.0), List.of()).markdown();

        assertThat(markdown).contains("| Metric | Pipeline | Limit | Status |", "Generic baseline: not asked for (--baseline)");
    }

    @Test
    void disagreementsAndBreachesAreListedAndTheResultFails() {
        List<LabScorer.Row> rows = eightRows("demo");
        LabReport failing = new LabReport(EvalReport.Mode.SCRIPTED, "m", null, java.time.LocalDate.of(2026, 11, 28), AnswerKey.Split.dev,
                List.of(new AnswerKeys.Loaded("demo", "f".repeat(64), FICTIONAL)), measured(rows, new Ratio(1, 1)),
                LabReport.Baseline.notRequested(), thresholds(0.0), List.of("severe false assurance 37.5 % (3 / 8) breaks the limit <= 0.0 %"));

        String markdown = failing.markdown();

        assertThat(failing.passed()).isFalse();
        assertThat(markdown).contains("### Disagreements with the key (pipeline)", "- demo UT/UT-3: key EVIDENCE_MISSING, answered EVIDENCE_FOUND (severe false assurance)",
                "- demo AZ/AZ-1: key AMBIGUOUS, answered nothing", "RESULT: FAIL",
                "- severe false assurance 37.5 % (3 / 8) breaks the limit <= 0.0 %");
        assertThat(markdown).doesNotContain("UT/UT-1: key");
    }

    @Test
    void theJsonCarriesEverythingNeededToDiffTwoRuns() {
        LabReport run = report(EvalReport.Mode.SCRIPTED, FICTIONAL, LabReport.Baseline.notRequested(), thresholds(0.0), List.of());

        JsonNode json = run.json(JSON);

        assertThat(json.get("format").asString()).isEqualTo(LabReport.FORMAT);
        assertThat(json.get("mode").asString()).isEqualTo("SCRIPTED");
        assertThat(json.get("split").asString()).isEqualTo("dev");
        assertThat(json.get("host").isNull()).isTrue();
        assertThat(json.get("claimBlockers")).hasSize(4);
        assertThat(json.get("keys").get(0).get("fictional").asBoolean()).isTrue();
        assertThat(json.get("limits").get("severeFalseAssurance").asString()).isEqualTo("<= 0.0 %");
        JsonNode severe = json.get("pipeline").get("metrics").get("severeFalseAssurance");
        assertThat(severe.get("numerator").asInt()).isZero();
        assertThat(severe.get("denominator").asInt()).isEqualTo(2);
        assertThat(severe.get("high").asDouble()).isBetween(0.65, 0.66);
        assertThat(json.get("pipeline").get("breakdowns").get("state").get("UT").get("findingAccuracy").get("denominator").asInt()).isEqualTo(2);
        assertThat(json.get("pipeline").get("rows")).hasSize(2);
        assertThat(json.get("pipeline").get("rows").get(0).get("requirement").asString()).isEqualTo("UT/UT-1");
        assertThat(json.get("pipeline").get("rows").get(0).get("answered").asString()).isEqualTo("EVIDENCE_FOUND");
        assertThat(json.get("pipeline").get("metrics").get("numericAccuracy").has("rate")).isTrue();
        assertThat(run.fileStem()).isEqualTo("2026-11-28-scripted-dev");
        assertThat(report(EvalReport.Mode.LOCAL_LIVE, REAL, LabReport.Baseline.notRequested(), thresholds(0.0), List.of()).fileStem())
                .isEqualTo("2026-11-28-local-live-holdout");
    }
}
