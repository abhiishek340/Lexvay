package com.lexvay.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Shape of eval/thresholds.yml: one set of limits for the scripted model, one for a real model, and the retrieval
 * limits, which do not depend on a model. Quote verification has no entry on purpose: it must be 100 % (product
 * rule 2) and is fixed in code.
 */
public record Thresholds(Limits fake, Limits live, Retrieval retrieval) {

    public record Limits(double maxFalseEvidenceFoundRate, double minRecall, double minNumericAccuracy,
                         double minExtractionAgreement, double minEvidenceAgreement) {
    }

    /** Floors for the BM25 ranker on the queries that are not lexical gaps. Beyond them it may not fall below the baseline ranker. */
    public record Retrieval(double minRecallAtDeep, double minExcerptCoverage) {
    }

    public Limits forMode(EvalReport.Mode mode) {
        return mode == EvalReport.Mode.FAKE ? fake : live;
    }

    /** Every limit the metrics break, in plain language. Empty means the run passes. */
    public static List<String> breaches(Limits limits, Metrics m) {
        List<String> out = new ArrayList<>();
        if (!withinMaximum(m.falseEvidenceFound(), limits.maxFalseEvidenceFoundRate())) {
            out.add("false EVIDENCE_FOUND rate " + m.falseEvidenceFound().percent() + " exceeds " + percent(limits.maxFalseEvidenceFoundRate()));
        }
        if (!m.quoteVerification().isComplete()) {
            out.add("quote verification " + m.quoteVerification().percent() + " is below 100.0 %");
        }
        require(out, "recall", m.recall(), limits.minRecall());
        require(out, "numeric accuracy", m.numericAccuracy(), limits.minNumericAccuracy());
        require(out, "requirement extraction agreement", m.extractionAgreement(), limits.minExtractionAgreement());
        require(out, "evidence agreement", m.evidenceAgreement(), limits.minEvidenceAgreement());
        return out;
    }

    /** Every retrieval limit the BM25 ranker breaks, in plain language, including falling below the baseline. */
    public static List<String> retrievalBreaches(Retrieval limits, RetrievalScorer.Metrics bm25, RetrievalScorer.Metrics baseline) {
        List<String> out = new ArrayList<>();
        require(out, "recall@" + RetrievalScorer.DEEP, bm25.recallAtDeep(), limits.minRecallAtDeep());
        require(out, "recall@" + RetrievalScorer.DEEP + " against the baseline", bm25.recallAtDeep(), baseline.recallAtDeep().value());
        require(out, "recall@" + RetrievalScorer.SHALLOW + " against the baseline", bm25.recallAtShallow(), baseline.recallAtShallow().value());
        require(out, "excerpt coverage", bm25.excerptCoverage(), limits.minExcerptCoverage());
        return out;
    }

    private static void require(List<String> out, String name, Ratio actual, double minimum) {
        if (actual.denominator() == 0) {
            out.add(name + " cannot be measured: the golden set has nothing to compare");
        } else if (!meetsMinimum(actual, minimum)) {
            out.add(name + " " + actual.percent() + " is below " + percent(minimum));
        }
    }

    /** A rate that must not exceed a maximum; with nothing proposed there is nothing wrong. */
    static boolean withinMaximum(Ratio ratio, double maximum) {
        return ratio.denominator() == 0 || ratio.value() <= maximum;
    }

    /** A rate that must reach a minimum; with nothing to measure it cannot. */
    static boolean meetsMinimum(Ratio ratio, double minimum) {
        return ratio.denominator() > 0 && ratio.value() >= minimum;
    }

    static String percent(double fraction) {
        return String.format(java.util.Locale.ROOT, "%.1f %%", fraction * 100);
    }
}
