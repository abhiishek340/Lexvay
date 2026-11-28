package com.lexvay.eval;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Everything one evaluation run found, and the one table it prints.
 *
 * @param termOverlap the model's search terms against the golden ones; null for the scripted model, whose terms are the seed's
 */
public record EvalReport(Mode mode, String model, String tenant, int goldenSize, Metrics metrics, Thresholds.Limits limits,
                         Usage usage, List<EvalScorer.Mismatch> mismatches, List<String> breaches, TermOverlap termOverlap) {

    public enum Mode { FAKE, LIVE }

    /**
     * Model use of the one assessment, extraction included, summed from {@code llm_call}. Fake token counts are
     * estimates from text length; the cost is those tokens times the configured price, and absent when no price is set.
     */
    public record Usage(int calls, long inputTokens, long outputTokens, BigDecimal cost, Duration wallTime) {
    }

    public boolean passed() {
        return breaches.isEmpty();
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        out.append("Lexvay evaluation (fictional seed data)\n");
        out.append(String.format(Locale.ROOT, "  model   %s%s%n", model, mode == Mode.FAKE ? " (scripted; token counts are estimates)" : ""));
        out.append(String.format(Locale.ROOT, "  tenant  %s%n", tenant));
        out.append(String.format(Locale.ROOT, "  golden  %d requirements%n%n", goldenSize));
        out.append(String.format(Locale.ROOT, "%-42s %9s %10s   %-12s %s%n", "Metric", "Result", "Count", "Limit", "Status"));
        row(out, "False EVIDENCE_FOUND rate", metrics.falseEvidenceFound(), "<= " + Thresholds.percent(limits.maxFalseEvidenceFoundRate()),
                Thresholds.withinMaximum(metrics.falseEvidenceFound(), limits.maxFalseEvidenceFoundRate()));
        row(out, "Recall on golden EVIDENCE_FOUND", metrics.recall(), minimum(limits.minRecall()), Thresholds.meetsMinimum(metrics.recall(), limits.minRecall()));
        row(out, "Quote verification", metrics.quoteVerification(), "= 100.0 %", metrics.quoteVerification().isComplete());
        row(out, "Numeric accuracy (rules engine)", metrics.numericAccuracy(), minimum(limits.minNumericAccuracy()),
                Thresholds.meetsMinimum(metrics.numericAccuracy(), limits.minNumericAccuracy()));
        row(out, "Requirement extraction agreement", metrics.extractionAgreement(), minimum(limits.minExtractionAgreement()),
                Thresholds.meetsMinimum(metrics.extractionAgreement(), limits.minExtractionAgreement()));
        row(out, "Evidence agreement (document, quote)", metrics.evidenceAgreement(), minimum(limits.minEvidenceAgreement()),
                Thresholds.meetsMinimum(metrics.evidenceAgreement(), limits.minEvidenceAgreement()));
        if (termOverlap != null) {
            out.append(String.format(Locale.ROOT, "%-42s %9s%n", "Search terms vs golden (Jaccard, mean)", Thresholds.percent(termOverlap.mean())));
        }
        out.append(String.format(Locale.ROOT, "%n%-42s %s%n", "Model calls", usage.calls()));
        out.append(String.format(Locale.ROOT, "%-42s %,d%n", "Input tokens", usage.inputTokens()));
        out.append(String.format(Locale.ROOT, "%-42s %,d%n", "Output tokens", usage.outputTokens()));
        out.append(String.format(Locale.ROOT, "%-42s %s%n", "Cost per assessment (USD)",
                usage.cost() == null ? "n/a (no price for the model in LEXVAY_LLM_RATES)" : usage.cost().toPlainString()));
        out.append(String.format(Locale.ROOT, "%-42s %.1f s%n", "Wall time per assessment", usage.wallTime().toMillis() / 1000.0));
        if (termOverlap != null) {
            out.append("\nSearch terms the model proposed, by overlap with the golden terms\n");
            for (TermOverlap.Entry e : termOverlap.entries()) {
                out.append(String.format(Locale.ROOT, "  %-10s %9s   %s%n", e.subject(), Thresholds.percent(e.jaccard()),
                        e.proposed().isEmpty() ? "(none)" : String.join(", ", e.proposed())));
            }
        }
        if (!mismatches.isEmpty()) {
            out.append("\nDisagreements with the golden set\n");
            for (EvalScorer.Mismatch m : mismatches) {
                out.append(String.format(Locale.ROOT, "  %-10s %-22s expected %s, got %s%n", m.subject(), m.metric(), m.expected(), m.actual()));
            }
        }
        out.append('\n').append(passed() ? "RESULT: pass" : "RESULT: FAIL").append('\n');
        breaches.forEach(b -> out.append("  - ").append(b).append('\n'));
        return out.toString();
    }

    private static void row(StringBuilder out, String name, Ratio ratio, String limit, boolean ok) {
        out.append(String.format(Locale.ROOT, "%-42s %9s %10s   %-12s %s%n", name, ratio.percent(), ratio.counts(), limit, ok ? "ok" : "FAIL"));
    }

    private static String minimum(double fraction) {
        return ">= " + Thresholds.percent(fraction);
    }
}
