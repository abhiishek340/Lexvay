package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the header of a report says for each kind of run, and the name of the file a local run is kept in. Pure. */
class EvalReportTest {

    static final LocalDate DATE = LocalDate.of(2026, 11, 27);
    static final Metrics NONE = new Metrics(new Ratio(0, 0), new Ratio(0, 0), new Ratio(0, 0), new Ratio(0, 0), new Ratio(0, 0),
            new Ratio(0, 0));
    static final Thresholds.Limits LIMITS = new Thresholds.Limits(0.0, 0.0, 0.0, 0.0, 0.0);

    static EvalReport report(EvalReport.Mode mode, String model, String host, boolean estimated) {
        return new EvalReport(mode, model, host, DATE, "tenant", 3, NONE, LIMITS,
                new EvalReport.Usage(2, 100, 20, estimated, BigDecimal.ZERO, Duration.ZERO), List.of(), List.of(), null);
    }

    @Test
    void aScriptedRunSaysItIsScriptedAndHasNoHost() {
        String header = report(EvalReport.Mode.SCRIPTED, "fake-llm", null, true).render();

        assertThat(header).contains("  mode    SCRIPTED (scripted answers, not a model)", "  model   fake-llm", "  host    n/a", "  date    2026-11-27");
    }

    @Test
    void aLocalRunNamesTheModelAndTheHostAndSaysTheModelIsUnvetted() {
        String header = report(EvalReport.Mode.LOCAL_LIVE, "qwen-test", "localhost", false).render();

        assertThat(header).contains("  mode    LOCAL_LIVE (local inference; this model has not been vetted by Lexvay, so the results describe it alone)",
                "  model   qwen-test", "  host    localhost", "  date    2026-11-27");
    }

    @Test
    void aBilledRunSaysItIsBilled() {
        String header = report(EvalReport.Mode.PAID_LIVE, "claude-test", null, false).render();

        assertThat(header).contains("  mode    PAID_LIVE (billed provider)", "  model   claude-test", "  date    2026-11-27");
    }

    @Test
    void estimatedTokenCountsAreSaidToBeEstimatesAndReportedOnesAreNot() {
        assertThat(report(EvalReport.Mode.LOCAL_LIVE, "m", "localhost", true).render()).contains("token counts are estimates");
        assertThat(report(EvalReport.Mode.LOCAL_LIVE, "m", "localhost", false).render()).doesNotContain("token counts are estimates");
    }

    @Test
    void onlyTheScriptedModeUsesTheScriptedLimits() {
        Thresholds thresholds = new Thresholds(new Thresholds.Limits(0, 1, 1, 1, 1), new Thresholds.Limits(0, 0.8, 1, 0.9, 0.8),
                new Thresholds.Retrieval(0.95, 1.0, 0.66));

        assertThat(thresholds.forMode(EvalReport.Mode.SCRIPTED)).isSameAs(thresholds.scripted());
        assertThat(thresholds.forMode(EvalReport.Mode.LOCAL_LIVE)).isSameAs(thresholds.live());
        assertThat(thresholds.forMode(EvalReport.Mode.PAID_LIVE)).isSameAs(thresholds.live());
    }

    @Test
    void theFileIsNamedByDateAndModelWithOnlyLettersDigitsDotsAndDashes() {
        assertThat(report(EvalReport.Mode.LOCAL_LIVE, "Qwen2.5:7B-Instruct", "localhost", false).fileName())
                .isEqualTo("2026-11-27-qwen2.5-7b-instruct.txt");
        assertThat(report(EvalReport.Mode.LOCAL_LIVE, "/models/My Model.gguf", "localhost", false).fileName())
                .isEqualTo("2026-11-27-models-my-model.gguf.txt");
    }
}
