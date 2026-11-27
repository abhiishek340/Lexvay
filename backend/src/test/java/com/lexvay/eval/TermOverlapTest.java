package com.lexvay.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.lexvay.assessment.Finding;
import com.lexvay.rule.RequirementType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The live report's comparison of the model's search terms with the golden ones. Pure: no database, no model. */
class TermOverlapTest {

    static Golden.Entry entry(String ref, String... terms) {
        return new Golden.Entry(ref, "UT", "title", "text", RequirementType.SUBJECT, null, Finding.EVIDENCE_FOUND, "a.pdf", "quote",
                List.of(terms), "reason");
    }

    final Golden golden = new Golden(true, List.of(entry("UT-1", "degree plan", "program of study"),
            entry("UT-2", "code of ethics", "confidentiality", "informed consent", "professional conduct"), entry("UT-3", "aging")));

    @Test
    void jaccardIsSharedTermsOverAllDistinctTerms() {
        assertThat(TermOverlap.jaccard(List.of("a", "b", "c"), List.of("b", "c", "d"))).isCloseTo(0.5, within(1e-9));
        assertThat(TermOverlap.jaccard(List.of("a", "b"), List.of("b", "a"))).as("order does not matter").isEqualTo(1.0);
        assertThat(TermOverlap.jaccard(List.of("a"), List.of("b"))).isZero();
        assertThat(TermOverlap.jaccard(List.of("a", "a"), List.of("a"))).as("a repeated term counts once").isEqualTo(1.0);
        assertThat(TermOverlap.jaccard(List.of("a"), List.of())).as("the model proposed nothing").isZero();
        assertThat(TermOverlap.jaccard(List.of(), List.of())).isEqualTo(1.0);
    }

    @Test
    void everyGoldenRequirementIsMeasuredAndARequirementTheModelDidNotExtractScoresZero() {
        TermOverlap overlap = TermOverlap.measure(golden, Map.of(
                "UT/UT-1", List.of("degree plan", "curriculum"),
                "UT/UT-2", List.of("code of ethics", "confidentiality", "informed consent", "professional conduct")));

        assertThat(overlap.entries()).extracting(TermOverlap.Entry::subject).containsExactly("UT/UT-1", "UT/UT-2", "UT/UT-3");
        assertThat(overlap.entries().get(0).jaccard()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(overlap.entries().get(1).jaccard()).isEqualTo(1.0);
        assertThat(overlap.entries().get(2).jaccard()).isZero();
        assertThat(overlap.entries().get(2).proposed()).isEmpty();
        assertThat(overlap.mean()).isCloseTo((1.0 / 3 + 1.0) / 3, within(1e-9));
    }

    @Test
    void theLiveReportShowsTheMeanAndEveryRequirementAndTheScriptedReportShowsNeither() {
        TermOverlap overlap = TermOverlap.measure(golden, Map.of("UT/UT-1", List.of("degree plan", "curriculum")));
        Metrics none = new Metrics(new Ratio(0, 0), new Ratio(0, 0), new Ratio(0, 0), new Ratio(0, 0), new Ratio(0, 0), new Ratio(0, 0));
        Thresholds.Limits limits = new Thresholds.Limits(0.0, 0.0, 0.0, 0.0, 0.0);
        EvalReport.Usage usage = new EvalReport.Usage(0, 0, 0, BigDecimal.ZERO, Duration.ZERO);

        String live = new EvalReport(EvalReport.Mode.LIVE, "model", "tenant", 3, none, limits, usage, List.of(), List.of(), overlap).render();
        String scripted = new EvalReport(EvalReport.Mode.FAKE, "model", "tenant", 3, none, limits, usage, List.of(), List.of(), null).render();

        assertThat(live).contains("Search terms vs golden (Jaccard, mean)", "11.1 %", "UT/UT-1", "33.3 %", "degree plan, curriculum",
                "UT/UT-3", "(none)");
        assertThat(scripted).doesNotContain("Search terms");
    }
}
