package com.lexvay.eval;

import java.util.List;
import java.util.Locale;

/** What one retrieval measurement found: both rankers over the same queries, as a console table and as docs/RETRIEVAL.md. */
public record RetrievalReport(int documents, List<RetrievalScorer.Result> baselineResults, List<RetrievalScorer.Result> bm25Results,
                              RetrievalScorer.Score baseline, RetrievalScorer.Score bm25, Thresholds.Retrieval limits, List<String> breaches) {

    public static RetrievalReport of(int documents, List<RetrievalScorer.Result> baselineResults, List<RetrievalScorer.Result> bm25Results,
                                     Thresholds.Retrieval limits) {
        RetrievalScorer.Score baseline = RetrievalScorer.score(baselineResults);
        RetrievalScorer.Score bm25 = RetrievalScorer.score(bm25Results);
        return new RetrievalReport(documents, baselineResults, bm25Results, baseline, bm25, limits,
                Thresholds.retrievalBreaches(limits, bm25.counted(), baseline.counted()));
    }

    public boolean passed() {
        return breaches.isEmpty();
    }

    public String render() {
        RetrievalScorer.Metrics base = baseline.counted();
        RetrievalScorer.Metrics now = bm25.counted();
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Retrieval (fictional corpus of %d documents; %d queries count, %d lexical gaps are reported apart)%n",
                documents, base.recallAtDeep().denominator(), baseline.gap().recallAtDeep().denominator()));
        out.append(String.format(Locale.ROOT, "%-28s %20s %20s   %-30s %s%n", "Metric", "ts_rank (baseline)", "BM25", "Limit", "Status"));
        row(out, "Recall@" + RetrievalScorer.DEEP, base.recallAtDeep(), now.recallAtDeep(),
                ">= " + Thresholds.percent(limits.minRecallAtDeep()) + ", not below baseline",
                Thresholds.meetsMinimum(now.recallAtDeep(), limits.minRecallAtDeep()) && notBelow(now.recallAtDeep(), base.recallAtDeep()));
        row(out, "Recall@" + RetrievalScorer.SHALLOW, base.recallAtShallow(), now.recallAtShallow(), "not below baseline",
                notBelow(now.recallAtShallow(), base.recallAtShallow()));
        out.append(String.format(Locale.ROOT, "%-28s %20.3f %20.3f   %-30s %s%n", "Mean reciprocal rank", base.meanReciprocalRank(),
                now.meanReciprocalRank(), "-", "-"));
        row(out, "Excerpt coverage", base.excerptCoverage(), now.excerptCoverage(), "= " + Thresholds.percent(limits.minExcerptCoverage()),
                Thresholds.meetsMinimum(now.excerptCoverage(), limits.minExcerptCoverage()));
        out.append(String.format(Locale.ROOT, "%nLexical gaps (not thresholded): recall@%d %s with ts_rank, %s with BM25; recall@%d %s and %s%n",
                RetrievalScorer.DEEP, baseline.gap().recallAtDeep().counts(), bm25.gap().recallAtDeep().counts(), RetrievalScorer.SHALLOW,
                baseline.gap().recallAtShallow().counts(), bm25.gap().recallAtShallow().counts()));
        bm25.misses().stream().filter(m -> !isGap(m.query())).forEach(m -> out.append("  BM25 ").append(m.query()).append(": ").append(m.problem()).append('\n'));
        out.append('\n').append(passed() ? "RETRIEVAL: pass" : "RETRIEVAL: FAIL").append('\n');
        breaches.forEach(b -> out.append("  - ").append(b).append('\n'));
        return out.toString();
    }

    /** The content of docs/RETRIEVAL.md. It has no date, so a run that changes nothing changes no line. */
    public String markdown() {
        StringBuilder out = new StringBuilder();
        out.append("# Retrieval quality\n\n");
        out.append("Written by `make eval` (`RetrievalService`); do not edit by hand. Fictional data: the corpus is the demo seed plus the syllabi in\n");
        out.append("`eval/retrieval/docs`, and the questions are in `eval/retrieval/queries.yml`. A query is recalled when its expected page is among\n");
        out.append("the first eight pages the ranker returns for the requirement's title and text. Excerpt coverage is the share of recalled queries\n");
        out.append("whose expected evidence is inside the excerpt that would be shown to the model (D-174).\n\n");
        out.append(String.format(Locale.ROOT, "Corpus: %d documents. Queries: %d counted and %d lexical gaps.%n%n", documents,
                baseline.counted().recallAtDeep().denominator(), baseline.gap().recallAtDeep().denominator()));
        out.append("## Queries that count toward the limits\n\n");
        table(out, baseline.counted(), bm25.counted());
        out.append("\nLimits (`eval/thresholds.yml`): recall@8 at least ").append(Thresholds.percent(limits.minRecallAtDeep()))
                .append(" and not below the baseline, recall@3 not below the baseline, excerpt coverage ")
                .append(Thresholds.percent(limits.minExcerptCoverage())).append(".\n\n");
        out.append("## Lexical gaps (reported, never thresholded)\n\n");
        out.append("The document says it in other words than the requirement does, so a search on the requirement's words cannot be expected to\n");
        out.append("find it. These numbers show what lexical search does not reach.\n\n");
        table(out, baseline.gap(), bm25.gap());
        out.append("\n## Every query\n\n");
        out.append("Rank is the position of the expected page in the first eight, `-` when it is not there.\n\n");
        out.append("| Query | Kind | Expected page | ts_rank | BM25 | Excerpt covers (BM25) |\n|---|---|---|---|---|---|\n");
        for (int i = 0; i < bm25Results.size(); i++) {
            RetrievalScorer.Result base = baselineResults.get(i);
            RetrievalScorer.Result now = bm25Results.get(i);
            out.append(String.format(Locale.ROOT, "| %s %s | %s | `%s` p. %d | %s | %s | %s |%n", now.query().id(), now.query().title(),
                    now.query().lexicalGap() ? "lexical gap" : "counted", now.query().file(), now.query().page(), rank(base), rank(now),
                    now.rank() == 0 ? "-" : now.excerptCoversEvidence() ? "yes" : "no"));
        }
        return out.toString();
    }

    private void table(StringBuilder out, RetrievalScorer.Metrics base, RetrievalScorer.Metrics now) {
        out.append("| Metric | ts_rank (baseline) | BM25 |\n|---|---|---|\n");
        mdRow(out, "Recall@" + RetrievalScorer.DEEP, base.recallAtDeep(), now.recallAtDeep());
        mdRow(out, "Recall@" + RetrievalScorer.SHALLOW, base.recallAtShallow(), now.recallAtShallow());
        out.append(String.format(Locale.ROOT, "| Mean reciprocal rank | %.3f | %.3f |%n", base.meanReciprocalRank(), now.meanReciprocalRank()));
        mdRow(out, "Excerpt coverage", base.excerptCoverage(), now.excerptCoverage());
    }

    private static void mdRow(StringBuilder out, String name, Ratio base, Ratio now) {
        out.append(String.format(Locale.ROOT, "| %s | %s (%s) | %s (%s) |%n", name, base.percent(), base.counts(), now.percent(), now.counts()));
    }

    private static void row(StringBuilder out, String name, Ratio base, Ratio now, String limit, boolean ok) {
        out.append(String.format(Locale.ROOT, "%-28s %20s %20s   %-30s %s%n", name, base.percent() + "  " + base.counts(),
                now.percent() + "  " + now.counts(), limit, ok ? "ok" : "FAIL"));
    }

    private static String rank(RetrievalScorer.Result result) {
        return result.rank() == 0 ? "-" : String.valueOf(result.rank());
    }

    /** Recall never gets worse by moving from the baseline to BM25. */
    private static boolean notBelow(Ratio now, Ratio base) {
        return now.denominator() > 0 && now.value() >= base.value();
    }

    private boolean isGap(String queryId) {
        return bm25Results.stream().anyMatch(r -> r.query().id().equals(queryId) && r.query().lexicalGap());
    }
}
